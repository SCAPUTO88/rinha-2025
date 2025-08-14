package scaputo88.com.example.rinha_25.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PaymentEntity {

    private UUID id;

    private BigDecimal amount;

    private Instant createdAt;

    private ProcessorType processor;

    private boolean success;

    public PaymentEntity() {}

    public PaymentEntity(UUID id, BigDecimal amount, Instant createdAt, ProcessorType processor, boolean success) {
        this.id = id;
        this.amount = amount;
        this.createdAt = createdAt;
        this.processor = processor;
        this.success = success;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public ProcessorType getProcessor() { return processor; }
    public void setProcessor(ProcessorType processor) { this.processor = processor; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}
