package scaputo88.com.example.rinha_25.metrics;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ErrorMetrics {

    public enum ErrorType { TIMEOUT, SERVER_ERROR, CONNECTION_REFUSED, UNKNOWN }

    private final Map<ErrorType, LongAdder> counters = new EnumMap<>(ErrorType.class);

    public ErrorMetrics() {
        for (ErrorType type : ErrorType.values()) {
            counters.put(type, new LongAdder());
        }
    }

    public void increment(ErrorType type) {
        counters.getOrDefault(type, counters.get(ErrorType.UNKNOWN)).increment();
    }
}
