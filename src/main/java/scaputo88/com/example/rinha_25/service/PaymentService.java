package scaputo88.com.example.rinha_25.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import scaputo88.com.example.rinha_25.model.Payment;
import scaputo88.com.example.rinha_25.model.PaymentEntity;
import scaputo88.com.example.rinha_25.model.PaymentSummaryData;
import scaputo88.com.example.rinha_25.model.ProcessorType;
import scaputo88.com.example.rinha_25.repository.PaymentRepository;
import scaputo88.com.example.rinha_25.util.NamedDaemonFactory;
import scaputo88.com.example.rinha_25.util.SummaryUtils.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

import static scaputo88.com.example.rinha_25.util.PaymentUtils.*;
import static scaputo88.com.example.rinha_25.util.SummaryUtils.*;

@Service
public class PaymentService {

    private final ProcessorClient processorClient;
    private final HealthCheckService healthCheckService;
    private final PaymentQueue paymentQueue;
    private final MetricsService metrics;
    private final PaymentRepository paymentRepository;
    private final ReplicationService peerReplicator;

    private final Map<UUID, Payment> store = new ConcurrentHashMap<>();
    private final Map<ProcessorType, LongAdder> totalRequests = new EnumMap<>(ProcessorType.class);
    private final Map<ProcessorType, LongAdder> totalAmountCents = new EnumMap<>(ProcessorType.class);
    private final ConcurrentSkipListMap<Instant, EnumMap<ProcessorType, Bucket>> minuteBuckets = new ConcurrentSkipListMap<>();

    private final ExecutorService persistExecutor;
    private final ExecutorService replicateExecutor;

    public PaymentService(ProcessorClient processorClient,
                          HealthCheckService healthCheckService,
                          PaymentQueue paymentQueue,
                          MetricsService metrics,
                          ObjectProvider<PaymentRepository> paymentRepositoryProvider,
                          ReplicationService peerReplicator) {
        this.processorClient = processorClient;
        this.healthCheckService = healthCheckService;
        this.paymentQueue = paymentQueue;
        this.metrics = metrics;
        this.paymentRepository = paymentRepositoryProvider.getIfAvailable();
        this.peerReplicator = peerReplicator;

        for (ProcessorType type : ProcessorType.values()) {
            totalRequests.put(type, new LongAdder());
            totalAmountCents.put(type, new LongAdder());
        }

        int persistThreads = Math.max(1, parseIntEnv("PERSIST_THREADS", 2));
        int replicaThreads = Math.max(1, parseIntEnv("REPLICA_THREADS",
                Math.min(8, Math.max(2, peerReplicator.peersCount()))));

        this.persistExecutor = new ThreadPoolExecutor(
                persistThreads, persistThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(2048),
                new NamedDaemonFactory("persist"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        this.replicateExecutor = new ThreadPoolExecutor(
                replicaThreads, replicaThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(4096),
                new NamedDaemonFactory("replicate"),
                new ThreadPoolExecutor.DiscardPolicy()
        );
    }

    public Payment processPayment(UUID correlationId, BigDecimal amount) {
        Instant now = Instant.now();
        ProcessorType chosen = chooseProcessor();

        paymentQueue.submit(() -> {
            long start = System.nanoTime();
            boolean success = processorClient.sendPayment(chosen.name().toLowerCase(), correlationId, amount);
            long elapsed = System.nanoTime() - start;

            if (!success) {
                ProcessorType other = (chosen == ProcessorType.DEFAULT) ? ProcessorType.FALLBACK : ProcessorType.DEFAULT;
                var otherStatus = healthCheckService.getStatus(other);
                if (otherStatus.healthy()) {
                    long start2 = System.nanoTime();
                    boolean retry = processorClient.sendPayment(other.name().toLowerCase(), correlationId, amount);
                    long elapsed2 = System.nanoTime() - start2;

                    if (retry) {
                        registerIdempotent(other, correlationId, amount, now, true);
                        metrics.recordSuccess(other, amount, elapsed2);
                        return;
                    } else {
                        metrics.recordFailure(other, elapsed2);
                    }
                }
                metrics.recordFailure(chosen, elapsed);
            } else {
                metrics.recordSuccess(chosen, amount, elapsed);
            }
            registerIdempotent(chosen, correlationId, amount, now, success);
        });

        return null;
    }

    private void registerIdempotent(ProcessorType type, UUID correlationId, BigDecimal amount, Instant ts, boolean success) {
        Payment newPayment = new Payment(correlationId, amount, ts, type, success);

        store.compute(correlationId, (id, cur) -> {
            if (cur == null) {
                if (success) {
                    incrementCounters(type, amount);
                    updateMinuteBucket(ts, type, amount);
                    asyncPersist(newPayment);
                    asyncReplicate(newPayment);
                } else {
                    asyncPersist(newPayment);
                }
                return newPayment;
            }
            if (cur.success()) {
                return cur;
            }
            if (success) {
                incrementCounters(type, amount);
                updateMinuteBucket(ts, type, amount);
                asyncPersist(newPayment);
                asyncReplicate(newPayment);
                return newPayment;
            }
            return cur;
        });
    }

    public void applyReplication(Payment payment) {
        if (payment == null || payment.correlationId() == null) return;

        store.compute(payment.correlationId(), (id, cur) -> {
            if (cur == null) {
                if (payment.success()) {
                    incrementCounters(payment.processor(), payment.amount());
                    updateMinuteBucket(payment.requestedAt(), payment.processor(), payment.amount());
                }
                asyncPersist(payment);
                return payment;
            }
            if (cur.success()) return cur;
            if (payment.success()) {
                incrementCounters(payment.processor(), payment.amount());
                updateMinuteBucket(payment.requestedAt(), payment.processor(), payment.amount());
                asyncPersist(payment);
                return payment;
            }
            return cur;
        });
    }

    private ProcessorType chooseProcessor() {
        var statusDefault = healthCheckService.getStatus(ProcessorType.DEFAULT);
        var statusFallback = healthCheckService.getStatus(ProcessorType.FALLBACK);

        if (statusDefault.healthy() && !statusFallback.healthy()) return ProcessorType.DEFAULT;
        if (!statusDefault.healthy() && statusFallback.healthy()) return ProcessorType.FALLBACK;

        if (statusDefault.healthy() && statusFallback.healthy()) {
            if (Math.abs(statusDefault.minResponseTime() - statusFallback.minResponseTime()) < 10) {
                return ProcessorType.DEFAULT;
            }
            return (statusDefault.minResponseTime() <= statusFallback.minResponseTime())
                    ? ProcessorType.DEFAULT : ProcessorType.FALLBACK;
        }
        return ProcessorType.DEFAULT;
    }

    public Map<ProcessorType, PaymentSummaryData> getSummary(String from, String to) {
        Instant fromTs = parseInstant(from);
        Instant toTs = parseInstant(to);

        if (fromTs == null && toTs == null) {
            return getSummary();
        }
        if (fromTs == null && toTs != null) {
            fromTs = minuteKey(minuteBuckets.isEmpty() ? Instant.now() : minuteBuckets.firstKey());
        }
        if (fromTs != null && toTs == null) {
            toTs = minuteKey(minuteBuckets.isEmpty() ? Instant.now() : minuteBuckets.lastKey())
                    .plus(1, ChronoUnit.MINUTES);
        }

        var agg = initAggregators();
        if (!minuteBuckets.isEmpty()) {
            aggregateRange(fromTs, toTs, minuteBuckets, agg);
        }

        return toSummaryData(agg);
    }

    public Map<ProcessorType, PaymentSummaryData> getSummary() {
        EnumMap<ProcessorType, PaymentSummaryData> map = new EnumMap<>(ProcessorType.class);
        for (ProcessorType type : ProcessorType.values()) {
            long req = totalRequests.get(type).sum();
            long cents = totalAmountCents.get(type).sum();
            map.put(type, new PaymentSummaryData(req, centsToBigDecimal(cents)));
        }
        return map;
    }

    public Payment getPayment(UUID id) {
        return store.get(id);
    }

    private void incrementCounters(ProcessorType type, BigDecimal amount) {
        totalRequests.get(type).increment();
        totalAmountCents.get(type).add(toCents(amount));
    }

    private void updateMinuteBucket(Instant ts, ProcessorType type, BigDecimal amount) {
        Instant key = minuteKey(ts);
        EnumMap<ProcessorType, Bucket> map = minuteBuckets
                .computeIfAbsent(key, k -> new EnumMap<>(ProcessorType.class));
        Bucket b = map.computeIfAbsent(type, t -> new Bucket());
        b.requests.increment();
        b.amountCents.add(toCents(amount));
    }

    private void asyncPersist(Payment payment) {
        if (paymentRepository == null) return;
        persistExecutor.submit(() -> {
            try {
                PaymentEntity entity = new PaymentEntity(
                        payment.correlationId(), payment.amount(),
                        payment.requestedAt(), payment.processor(), payment.success()
                );
                paymentRepository.save(entity);
            } catch (Exception ignored) {
                // opcional: log/metrics de falha de persistência
            }
        });
    }

    private void asyncReplicate(Payment payment) {
        replicateExecutor.submit(() -> {
            try {
                peerReplicator.replicate(payment);
            } catch (Exception ignored) {
                // opcional: log/metrics de falha de replicação
            }
        });
    }
}


