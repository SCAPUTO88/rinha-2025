package scaputo88.com.example.rinha_25.model;


import java.math.BigDecimal;

public record PaymentSummary(
        BigDecimal default_total_amount,
        BigDecimal default_total_fee,
        long default_total_requests,
        BigDecimal fallback_total_amount,
        BigDecimal fallback_total_fee,
        long fallback_total_requests
) {}

