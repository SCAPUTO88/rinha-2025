package scaputo88.com.example.rinha_25.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import scaputo88.com.example.rinha_25.dto.PaymentSummaryResponse;
import scaputo88.com.example.rinha_25.dto.TotalSummaryResponse;
import scaputo88.com.example.rinha_25.model.Payment;
import scaputo88.com.example.rinha_25.model.ProcessorType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Payments: componente unificado (Etapa 2.2) que centraliza fila, workers,
 * integração com ProcessorClient, agregação de métricas e health-check com cache TTL.
 */
@Service
public class Payments {
    private static final Logger log = LoggerFactory.getLogger(Payments.class);

    // Config health
    private static final int BASE_TTL_SEC = 2;
    private static final int MAX_TTL_SEC = 10;

    private final ProcessorClient processorClient;

    // Fila e workers
    private final LinkedBlockingQueue<Payment> queue;
    private final int workers;

    // Estado de pagamentos e agregações
    private final Map<UUID, Payment> payments = new ConcurrentHashMap<>();
    private final Map<ProcessorType, LongAdder> totalRequests = new ConcurrentHashMap<>();
    private final Map<ProcessorType, LongAdder> totalAmountCents = new ConcurrentHashMap<>();
    private final Map<ProcessorType, Map<Long, MinuteBucket>> minuteBuckets = new ConcurrentHashMap<>();

    // Health cache
    private final Map<ProcessorType, ProcessorClient.HealthStatus> cache = new ConcurrentHashMap<>();
    private final Map<ProcessorType, Instant> lastCheck = new ConcurrentHashMap<>();
    private final Map<ProcessorType, AtomicInteger> failStreaks = new ConcurrentHashMap<>();
    private final Map<ProcessorType, AtomicInteger> currentTtls = new ConcurrentHashMap<>();
    private final Map<ProcessorType, ReentrantLock> locks = new ConcurrentHashMap<>();

    // Replicação
    private final List<String> peers;
    private final ExecutorService replicatorPool;
    private final RestTemplate replicateClient;

    private volatile boolean shutdownRequested = false;

    public Payments(ProcessorClient processorClient) {
        this.processorClient = processorClient;
        int capacityCfg = envOrInt("QUEUE_CAPACITY", 10000);
        int capacity = Math.max(1, capacityCfg);
        if (capacityCfg < 1) {
            log.warn("QUEUE_CAPACITY={} inválido. Usando capacidade mínima de {}.", capacityCfg, capacity);
        }
        this.workers = Math.max(1, envOrInt("WORKERS", Runtime.getRuntime().availableProcessors()));
        this.queue = new LinkedBlockingQueue<>(capacity);

        // Inicializa estruturas por tipo de processor
        for (ProcessorType type : ProcessorType.values()) {
            totalRequests.put(type, new LongAdder());
            totalAmountCents.put(type, new LongAdder());
            minuteBuckets.put(type, new ConcurrentHashMap<>());
            failStreaks.put(type, new AtomicInteger(0));
            currentTtls.put(type, new AtomicInteger(BASE_TTL_SEC));
            locks.put(type, new ReentrantLock());
        }

        // Replicação: lista de peers (urls) e cliente HTTP com timeouts curtos
        this.peers = parsePeers(envOr("PEERS", ""));
        this.replicatorPool = Executors.newFixedThreadPool(Math.min(4, Math.max(1, this.workers)));
        int replTimeoutCfg = envOrInt("REPL_TIMEOUT_MS", 250);
        int replTimeout = Math.min(1000, Math.max(50, replTimeoutCfg));
        if (replTimeoutCfg != replTimeout) {
            log.warn("REPL_TIMEOUT_MS={} fora de faixa. Ajustando para {}ms (50-1000).", replTimeoutCfg, replTimeout);
        }
        this.replicateClient = buildSmallTimeoutClient(replTimeout);

        log.info("Payments inicializado. capacity={} workers={} peers={}", capacity, workers, peers.size());
    }

    private RestTemplate buildSmallTimeoutClient(int timeoutMs) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(timeoutMs);
        f.setReadTimeout(timeoutMs);
        return new RestTemplate(f);
    }

    private static List<String> parsePeers(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String[] parts = raw.split(",");
        List<String> list = new ArrayList<>();
        for (String p : parts) {
            String s = p.trim();
            if (!s.isEmpty()) list.add(s);
        }
        return List.copyOf(list);
    }

    @PostConstruct
    void startWorkers() {
        for (int i = 0; i < workers; i++) {
            Thread t = new Thread(this::runWorker, "payments-worker-" + i);
            t.setDaemon(true);
            t.start();
        }
        log.info("{} workers de pagamentos iniciados", workers);
    }

    private void runWorker() {
        while (!shutdownRequested) {
            try {
                Payment p = queue.poll(200, TimeUnit.MILLISECONDS);
                if (p == null) continue;

                String proc = (p.getProcessor() == null ? ProcessorType.DEFAULT : p.getProcessor()).getValue();
                boolean ok = processorClient.sendPayment(proc, p.getCorrelationId(), p.getAmount());
                p.setSuccess(ok);
                if (ok) {
                    registerIdempotent(p);
                    replicateAsync(p);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Nunca deixar o worker morrer
                log.warn("[Payments] erro no worker: {}", e.getMessage());
            }
        }
        log.info("Worker thread finalizada");
    }

    private void replicateAsync(Payment p) {
        if (peers.isEmpty()) return;
        for (String peer : peers) {
            replicatorPool.submit(() -> {
                try {
                    String url = peer.endsWith("/") ? (peer + "internal/replicate") : (peer + "/internal/replicate");
                    ResponseEntity<Void> resp = replicateClient.postForEntity(url, p, Void.class);
                    if (!resp.getStatusCode().is2xxSuccessful()) {
                        log.debug("[Replicator] {} -> status {}", url, resp.getStatusCode().value());
                    }
                } catch (RestClientException ex) {
                    // Best-effort: não propagar falha
                    log.debug("[Replicator] falha ao replicar para {}: {}", peer, ex.getMessage());
                } catch (Exception ex) {
                    log.debug("[Replicator] erro inesperado {}: {}", peer, ex.getMessage());
                }
            });
        }
    }

    // --- API pública ---
    public boolean enqueue(UUID correlationId, BigDecimal amount, ProcessorType processor) {
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(amount, "amount");
        ProcessorType proc = (processor == null ? ProcessorType.DEFAULT : processor);

        Payment p = new Payment(correlationId, amount, java.time.Instant.now(), proc, false);
        boolean offered = queue.offer(p); // não bloqueante
        if (!offered) {
            log.debug("[Payments] fila cheia, descartando correlationId={} proc={} amount={}", correlationId, proc, amount);
        }
        return offered;
    }

    public TotalSummaryResponse getTotalSummary(String from, String to) {
        long totalCount = 0;
        long totalCents = 0;
        for (ProcessorType type : ProcessorType.values()) {
            PaymentSummary s = (from == null && to == null)
                    ? getSummary(type)
                    : getSummary(type, from, to);
            totalCount += s.totalCount;
            totalCents += s.totalAmountCents;
        }
        return new TotalSummaryResponse(totalCount, centsToBigDecimal(totalCents));
    }

    public ProcessorClient.HealthStatus getProcessorHealth(ProcessorType type) {
        Instant now = Instant.now();
        int ttl = currentTtls.get(type).get();
        Instant last = lastCheck.get(type);
        if (last != null && !now.minusSeconds(ttl).isAfter(last)) {
            return cache.getOrDefault(type, new ProcessorClient.HealthStatus(false, Integer.MAX_VALUE));
        }
        ReentrantLock lock = locks.get(type);
        if (!lock.tryLock()) {
            return cache.getOrDefault(type, new ProcessorClient.HealthStatus(false, Integer.MAX_VALUE));
        }
        try {
            Instant last2 = lastCheck.get(type);
            int ttl2 = currentTtls.get(type).get();
            if (last2 == null || now.minusSeconds(ttl2).isAfter(last2)) {
                ProcessorClient.HealthStatus status = processorClient.checkHealth(type.getValue());
                cache.put(type, status);
                lastCheck.put(type, now);
                if (status.healthy()) {
                    failStreaks.get(type).set(0);
                    currentTtls.get(type).set(BASE_TTL_SEC);
                } else {
                    int streak = failStreaks.get(type).incrementAndGet();
                    if (streak >= 3) {
                        currentTtls.get(type).set(MAX_TTL_SEC);
                    }
                }
            }
            return cache.getOrDefault(type, new ProcessorClient.HealthStatus(false, Integer.MAX_VALUE));
        } finally {
            lock.unlock();
        }
    }

    public java.util.Optional<Payment> findById(UUID id) {
        return java.util.Optional.ofNullable(payments.get(id));
    }

    // Replicação interna entre peers: aplica os efeitos idempotentes localmente
    public void applyReplication(Payment payment) {
        if (payment == null || payment.getCorrelationId() == null || payment.getAmount() == null) return;
        if (payment.getProcessor() == null) payment.setProcessor(ProcessorType.DEFAULT);
        registerIdempotent(payment);
    }

    // --- Internals de agregação/idempotência ---
    private void registerIdempotent(Payment payment) {
        ProcessorType type = payment.getProcessor() == null ? ProcessorType.DEFAULT : payment.getProcessor();
        payment.setProcessor(type);
        Payment existing = payments.putIfAbsent(payment.getCorrelationId(), payment);
        if (existing != null) return;
        totalRequests.get(type).increment();
        totalAmountCents.get(type).add(toCents(payment.getAmount()));
        long minute = truncateToMinute(payment.getRequestedAt());
        minuteBuckets.get(type).computeIfAbsent(minute, m -> new MinuteBucket()).add(payment.getAmount());
    }

    private PaymentSummary getSummary(ProcessorType type) {
        return new PaymentSummary(totalRequests.get(type).sum(), totalAmountCents.get(type).sum());
    }

    private PaymentSummary getSummary(ProcessorType type, String from, String to) {
        Instant fromInstant = parseDate(from);
        Instant toInstant = parseDate(to);
        long fromMinute = truncateToMinute(fromInstant);
        long toMinute = truncateToMinute(toInstant);
        long count = 0;
        long amount = 0;
        for (Map.Entry<Long, MinuteBucket> entry : minuteBuckets.get(type).entrySet()) {
            long minute = entry.getKey();
            if (minute >= fromMinute && minute <= toMinute) {
                MinuteBucket bucket = entry.getValue();
                count += bucket.getCount();
                amount += bucket.getTotalCents();
            }
        }
        return new PaymentSummary(count, amount);
    }

    private long truncateToMinute(Instant instant) {
        return instant.getEpochSecond() / 60;
    }

    private Instant parseDate(String date) {
        if (date == null) return Instant.now();
        try {
            return Instant.parse(date);
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }

    private long toCents(BigDecimal amount) {
        return amount.movePointRight(2).longValue();
    }

    private BigDecimal centsToBigDecimal(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    private static class PaymentSummary {
        private final long totalCount;
        private final long totalAmountCents;
        private PaymentSummary(long totalCount, long totalAmountCents) {
            this.totalCount = totalCount;
            this.totalAmountCents = totalAmountCents;
        }
    }

    private static class MinuteBucket {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalCents = new LongAdder();
        public void add(BigDecimal amount) {
            count.increment();
            totalCents.add(amount.movePointRight(2).longValue());
        }
        public long getCount() { return count.sum(); }
        public long getTotalCents() { return totalCents.sum(); }
    }

    private static int envOrInt(String key, int def) {
        try {
            String v = System.getenv(key);
            return (v == null || v.isBlank()) ? def : Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    @PreDestroy
    void shutdown() {
        shutdownRequested = true;
        if (replicatorPool != null) {
            replicatorPool.shutdown();
            try {
                if (!replicatorPool.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    replicatorPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                replicatorPool.shutdownNow();
            }
        }
    }

    public long getCount(ProcessorType type, String from, String to) {
        PaymentSummary summary = (from == null && to == null)
                ? getSummary(type)
                : getSummary(type, from, to);
        return summary.totalCount;
    }

    public BigDecimal getTotalAmount(ProcessorType type, String from, String to) {
        PaymentSummary summary = (from == null && to == null)
                ? getSummary(type)
                : getSummary(type, from, to);
        return centsToBigDecimal(summary.totalAmountCents);
    }

    public PaymentSummaryResponse getSummaryResponse(String from, String to) {
        // Get default processor summary
        long defaultCount = getCount(ProcessorType.DEFAULT, from, to);
        BigDecimal defaultAmount = getTotalAmount(ProcessorType.DEFAULT, from, to);
        
        // Get fallback processor summary
        long fallbackCount = getCount(ProcessorType.FALLBACK, from, to);
        BigDecimal fallbackAmount = getTotalAmount(ProcessorType.FALLBACK, from, to);
        
        return new PaymentSummaryResponse(
            new PaymentSummaryResponse.ProcessorSummary(defaultCount, defaultAmount),
            new PaymentSummaryResponse.ProcessorSummary(fallbackCount, fallbackAmount)
        );
    }
}
