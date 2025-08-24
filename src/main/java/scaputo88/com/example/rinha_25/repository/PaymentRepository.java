package scaputo88.com.example.rinha_25.repository;

import scaputo88.com.example.rinha_25.model.Payment;
import scaputo88.com.example.rinha_25.model.PaymentSummary;

import java.time.Instant;

public interface PaymentRepository {
    void save(Payment payment);
    PaymentSummary getSummary(Instant from, Instant to);
    void purgePayments();

}
