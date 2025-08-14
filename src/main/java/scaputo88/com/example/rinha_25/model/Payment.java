package scaputo88.com.example.rinha_25.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Payment {
    private UUID correlationId;
    private BigDecimal amount;
    private Instant requestedAt;
    private ProcessorType processor;
    private boolean success;

    public Payment() {}

    public Payment(UUID correlationId, BigDecimal amount, Instant requestedAt, ProcessorType processor, boolean success) {
        this.correlationId = correlationId;
        this.amount = amount;
        this.requestedAt = requestedAt;
        this.processor = processor;
        this.success = success;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public ProcessorType getProcessor() {
        return processor;
    }

    public void setProcessor(ProcessorType processor) {
        this.processor = processor;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
