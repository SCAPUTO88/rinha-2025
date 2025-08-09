package scaputo88.com.example.rinha_25.model;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.*;
import java.util.UUID;


@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    private UUID id;

    @Column(nullable = false, scale = 2, precision = 18)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProcessorType processor;

    @Column(nullable = false)
    private boolean success;

    public PaymentEntity() {}

    public PaymentEntity(UUID id, BigDecimal amount, Instant createdAt, ProcessorType processor, boolean success) {
        this.id = id;
        this.amount = amount;
        this.createdAt = createdAt;
        this.processor = processor;
        this.success = success;
    }

    // getters/setters omitidos por brevidade
}
