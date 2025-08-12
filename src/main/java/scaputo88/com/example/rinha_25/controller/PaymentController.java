package scaputo88.com.example.rinha_25.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import scaputo88.com.example.rinha_25.model.Payment;
import scaputo88.com.example.rinha_25.model.PaymentSummaryData;
import scaputo88.com.example.rinha_25.model.ProcessorType;
import scaputo88.com.example.rinha_25.service.PaymentService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<Void> createPayment(@RequestBody Map<String, Object> body) {
        try {
            Object idRaw = body.get("correlationId");
            Object amountRaw = body.get("amount");
            if (idRaw == null || amountRaw == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            UUID id = UUID.fromString(idRaw.toString());
            BigDecimal amount = new BigDecimal(amountRaw.toString());
            if (amount.signum() <= 0) return ResponseEntity.badRequest().build();
            paymentService.processPayment(id, amount);
            return ResponseEntity.accepted().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<ProcessorType, PaymentSummaryData>> getSummary() {
        return ResponseEntity.ok(paymentService.getSummary());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPayment(@PathVariable UUID id) {
        Payment payment = paymentService.getPayment(id);
        return payment != null ? ResponseEntity.ok(payment) : ResponseEntity.notFound().build();
    }
}
