package scaputo88.com.example.rinha_25.service;

import org.springframework.stereotype.Service;
import scaputo88.com.example.rinha_25.model.Payment;
import scaputo88.com.example.rinha_25.model.PaymentSummaryData;
import scaputo88.com.example.rinha_25.model.ProcessorType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PaymentService {

    private final ProcessorClient processorClient;
    private final HealthCheckService healthCheckService;
    private final Map<UUID, Payment> store = new ConcurrentHashMap<>();

    private final Map<ProcessorType, AtomicLong> totalRequests = new EnumMap<>(ProcessorType.class);
    private final Map<ProcessorType, AtomicReference<BigDecimal>> totalAmounts = new EnumMap<>(ProcessorType.class);

    public PaymentService(ProcessorClient processorClient, HealthCheckService healthCheckService) {
        this.processorClient = processorClient;
        this.healthCheckService = healthCheckService;
        for (ProcessorType type : ProcessorType.values()) {
            totalRequests.put(type, new AtomicLong());
            totalAmounts.put(type, new AtomicReference<>(BigDecimal.ZERO));
        }
    }

    public Payment processPayment(UUID correlationId, BigDecimal amount) {
        Instant now = Instant.now();
        ProcessorType chosen = chooseProcessor();
        boolean success = processorClient.sendPayment(chosen.name().toLowerCase(), correlationId, amount);

        if (!success) {
            ProcessorType other = (chosen == ProcessorType.DEFAULT) ? ProcessorType.FALLBACK : ProcessorType.DEFAULT;
            var otherStatus = healthCheckService.getStatus(other);
            if (otherStatus.healthy()) {
                boolean retry = processorClient.sendPayment(other.name().toLowerCase(), correlationId, amount);
                if (retry) {
                    register(other, correlationId, amount, now, true);
                    return store.get(correlationId);
                }
            }
        }

        register(chosen, correlationId, amount, now, success);
        return store.get(correlationId);
    }

    private void register(ProcessorType type, UUID correlationId, BigDecimal amount, Instant ts, boolean success) {
        store.put(correlationId, new Payment(correlationId, amount, ts, type, success));
        totalRequests.get(type).incrementAndGet();
        totalAmounts.get(type).updateAndGet(prev -> prev.add(amount));
    }

    private ProcessorType chooseProcessor() {
        var statusDefault = healthCheckService.getStatus(ProcessorType.DEFAULT);
        var statusFallback = healthCheckService.getStatus(ProcessorType.FALLBACK);

        if (statusDefault.healthy() && !statusFallback.healthy()) return ProcessorType.DEFAULT;
        if (!statusDefault.healthy() && statusFallback.healthy()) return ProcessorType.FALLBACK;

        if (statusDefault.healthy() && statusFallback.healthy()) {
            return (statusDefault.minResponseTime() <= statusFallback.minResponseTime())
                    ? ProcessorType.DEFAULT : ProcessorType.FALLBACK;
        }
        return ProcessorType.DEFAULT;
    }

    public Map<ProcessorType, PaymentSummaryData> getSummary() {
        Map<ProcessorType, PaymentSummaryData> map = new EnumMap<>(ProcessorType.class);
        for (ProcessorType type : ProcessorType.values()) {
            map.put(type, new PaymentSummaryData(
                    totalRequests.get(type).get(),
                    totalAmounts.get(type).get()
            ));
        }
        return map;
    }

    public Payment getPayment(UUID id) {
        return store.get(id);
    }
}



