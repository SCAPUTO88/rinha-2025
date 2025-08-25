package scaputo88.com.example.rinha_25.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Payment {
    private final UUID correlationId;
    private final String processorUsed;
    private final BigDecimal amount;
    private final BigDecimal fee;
    private final Instant timestamp;
    private final boolean usedFallback;

    public Payment(UUID correlationId,
                   String processorUsed,
                   BigDecimal amount,
                   BigDecimal fee,
                   Instant timestamp,
                   boolean usedFallback) {
        this.correlationId = correlationId;
        this.processorUsed = processorUsed;
        this.amount = amount;
        this.fee = fee;
        this.timestamp = timestamp;
        this.usedFallback = usedFallback;
    }

    public UUID correlationId() {
        return correlationId;
    }

    public String processorUsed() {
        return processorUsed;
    }

    public BigDecimal amount() {
        return amount;
    }

    public BigDecimal fee() {
        return fee;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public boolean usedFallback() {
        return usedFallback;
    }
}
