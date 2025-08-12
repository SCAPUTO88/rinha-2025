package scaputo88.com.example.rinha_25.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class ErrorMetrics {

    public enum ErrorType { TIMEOUT, SERVER_ERROR, CONNECTION_REFUSED, UNKNOWN }

    private final Map<ErrorType, Counter> counters = new EnumMap<>(ErrorType.class);

    public ErrorMetrics(MeterRegistry registry) {
        for (ErrorType type : ErrorType.values()) {
            counters.put(type, Counter.builder("payments.errors")
                    .tag("type", type.name().toLowerCase())
                    .register(registry));
        }
    }

    public void increment(ErrorType type) {
        counters.getOrDefault(type, counters.get(ErrorType.UNKNOWN)).increment();
    }
}
