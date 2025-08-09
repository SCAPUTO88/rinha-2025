package scaputo88.com.example.rinha_25.service;

import org.springframework.stereotype.Service;
import scaputo88.com.example.rinha_25.model.ProcessorType;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

@Service
public class HealthCheckService {

    private final ProcessorClient processorClient;
    private final Map<ProcessorType, ProcessorClient.HealthStatus> cache = new EnumMap<>(ProcessorType.class);
    private final Map<ProcessorType, Instant> lastCheck = new EnumMap<>(ProcessorType.class);

    public HealthCheckService(ProcessorClient processorClient) {
        this.processorClient = processorClient;
    }

    public ProcessorClient.HealthStatus getStatus(ProcessorType type) {
        Instant now = Instant.now();
        Instant last = lastCheck.get(type);

        if (last == null || now.minusSeconds(5).isAfter(last)) {
            ProcessorClient.HealthStatus status =
                    processorClient.checkHealth(type == ProcessorType.DEFAULT ? "default" : "fallback");
            cache.put(type, status);
            lastCheck.put(type, now);
        }
        return cache.getOrDefault(type, new ProcessorClient.HealthStatus(false, Integer.MAX_VALUE));
    }
}
