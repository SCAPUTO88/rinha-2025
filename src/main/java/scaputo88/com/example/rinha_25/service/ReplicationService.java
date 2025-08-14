package scaputo88.com.example.rinha_25.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import scaputo88.com.example.rinha_25.model.Payment;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class ReplicationService {

    private static final Logger log = LoggerFactory.getLogger(ReplicationService.class);

    private final RestClient restClient;
    private final List<String> peers;

    // Dedupe simples com TTL: armazena timestamp (millis) da última replicação por ID
    private final ConcurrentHashMap<UUID, Long> recentlyReplicated;
    private final long dedupeTtlMillis;

    private final ExecutorService replicateExecutor;

    public ReplicationService(
            @Value("${replication.peers:}") String peersCsv,
            @Value("${replication.dedupe-ttl-seconds:30}") long dedupeTtlSeconds,
            @Value("${replication.http.connect-timeout-ms:1500}") int connectTimeoutMs,
            @Value("${replication.http.read-timeout-ms:1500}") int readTimeoutMs,
            @Value("${replication.threads:4}") int replicateThreads
    ) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();

        this.peers = parsePeers(peersCsv);

        this.dedupeTtlMillis = Math.max(1000L, dedupeTtlSeconds * 1000L);
        this.recentlyReplicated = new ConcurrentHashMap<>();

        int threads = Math.max(1, replicateThreads);
        this.replicateExecutor = new ThreadPoolExecutor(
                threads, threads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1024),
                new ThreadFactory() {
                    private final ThreadFactory df = Executors.defaultThreadFactory();
                    @Override public Thread newThread(Runnable r) {
                        Thread t = df.newThread(r);
                        t.setName("replicator-" + t.getId());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.DiscardPolicy()
        );
        log.info("ReplicationService iniciado com {} threads, {} peers", threads, peers.size());
    }

    public void replicate(Payment payment) {
        if (payment == null || payment.getCorrelationId() == null) {
            log.warn("Skipping replication: payment or correlationId is null");
            return;
        }

        UUID id = payment.getCorrelationId();

        if (isRecentlyReplicated(id)) {
            log.debug("Skipping duplicate replication for {}", id);
            return;
        }

        markReplicatedNow(id);

        for (String peer : peers) {
            replicateExecutor.submit(() -> {
                String url = peer + "/internal/replicate";
                try {
                    restClient.post()
                            .uri(url)
                            .body(payment)
                            .retrieve()
                            .toBodilessEntity();
                    log.debug("Replicated {} to {}", id, url);
                } catch (Exception ex) {
                    log.warn("Replication to {} failed for {}: {}", url, id, ex.getMessage());
                }
            });
        }
    }

    public int peersCount() {
        return peers.size();
    }

    public boolean isRecentlyReplicated(UUID id) {
        Long ts = recentlyReplicated.get(id);
        long now = System.currentTimeMillis();
        if (ts == null) return false;
        if (now - ts > dedupeTtlMillis) {
            // expirou: remove e considera não recente
            recentlyReplicated.remove(id, ts);
            return false;
        }
        return true;
    }

    private void markReplicatedNow(UUID id) {
        long now = System.currentTimeMillis();
        recentlyReplicated.put(id, now);
        // limpeza ocasional e best-effort para não crescer sem limite
        if (recentlyReplicated.size() > 10000) {
            cleanupExpired(now);
        }
    }

    private void cleanupExpired(long now) {
        for (Map.Entry<UUID, Long> e : recentlyReplicated.entrySet()) {
            Long ts = e.getValue();
            if (ts == null || now - ts > dedupeTtlMillis) {
                recentlyReplicated.remove(e.getKey(), ts);
            }
        }
    }

    private static List<String> parsePeers(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptyList();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(ReplicationService::stripTrailingSlash)
                .collect(Collectors.toUnmodifiableList());
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
