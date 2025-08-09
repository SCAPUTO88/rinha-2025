package scaputo88.com.example.rinha_25.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRequest(
        UUID correlationId,
        BigDecimal amount,
        Instant requestedAt
) {}

