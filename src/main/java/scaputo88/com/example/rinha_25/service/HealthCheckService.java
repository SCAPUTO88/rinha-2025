package scaputo88.com.example.rinha_25.service;

import org.springframework.stereotype.Service;
import scaputo88.com.example.rinha_25.model.ProcessorType;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class HealthCheckService {

    private static final int BASE_TTL_SEC = 2;
    private static final int MAX_TTL_SEC = 10;

    private final ProcessorClient processorClient;

    private final Map<ProcessorType, ProcessorClient.HealthStatus> cache = new ConcurrentHashMap<>();
    private final Map<ProcessorType, Instant> lastCheck = new ConcurrentHashMap<>();
    private final Map<ProcessorType, AtomicInteger> failStreaks = new ConcurrentHashMap<>();
    private final Map<ProcessorType, AtomicInteger> currentTtls = new ConcurrentHashMap<>();
    private final Map<ProcessorType, ReentrantLock> locks = new ConcurrentHashMap<>();

    public HealthCheckService(ProcessorClient processorClient) {
        this.processorClient = processorClient;
        for (ProcessorType type : ProcessorType.values()) {
            failStreaks.put(type, new AtomicInteger(0));
            currentTtls.put(type, new AtomicInteger(BASE_TTL_SEC));
            locks.put(type, new ReentrantLock());
        }
    }

    public ProcessorClient.HealthStatus getStatus(ProcessorType type) {
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
}
