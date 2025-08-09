package scaputo88.com.example.rinha_25.service;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Service;
import scaputo88.com.example.rinha_25.model.ProcessorType;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;


@Service
public class MetricsService {

    private final MeterRegistry registry;
    private final Map<ProcessorType, Counter> successCounters = new EnumMap<>(ProcessorType.class);
    private final Map<ProcessorType, Counter> failureCounters = new EnumMap<>(ProcessorType.class);
    private final Map<ProcessorType, DistributionSummary> amountSummaries = new EnumMap<>(ProcessorType.class);
    private final Timer processorLatency;

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;

        for (ProcessorType p : ProcessorType.values()) {
            successCounters.put(p, Counter.builder("payments.total")
                    .tag("status", "success").tag("processor", p.name().toLowerCase())
                    .description("Total de pagamentos com sucesso")
                    .register(registry));

            failureCounters.put(p, Counter.builder("payments.total")
                    .tag("status", "failure").tag("processor", p.name().toLowerCase())
                    .description("Total de pagamentos com falha")
                    .register(registry));

            amountSummaries.put(p, DistributionSummary.builder("payments.amount")
                    .tag("processor", p.name().toLowerCase())
                    .description("Distribuição dos valores processados")
                    .baseUnit("BRL")
                    .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                    .register(registry));
        }

        processorLatency = Timer.builder("payments.processor.latency")
                .description("Latência das chamadas aos processors")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .maximumExpectedValue(Duration.ofSeconds(5))
                .register(registry);
    }

    public void recordSuccess(ProcessorType processor, BigDecimal amount, long nanos) {
        successCounters.get(processor).increment();
        amountSummaries.get(processor).record(amount.doubleValue());
        processorLatency.record(Duration.ofNanos(nanos));
    }

    public void recordFailure(ProcessorType processor, long nanos) {
        failureCounters.get(processor).increment();
        processorLatency.record(Duration.ofNanos(nanos));
    }

    public MeterRegistry getRegistry() {
        return registry;
    }
}

