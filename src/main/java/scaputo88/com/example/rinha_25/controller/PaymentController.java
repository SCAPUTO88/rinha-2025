package scaputo88.com.example.rinha_25.controller;


import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import scaputo88.com.example.rinha_25.model.Payment;
import scaputo88.com.example.rinha_25.model.PaymentRequest;
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
    public ResponseEntity<Void> createPayment(@RequestBody PaymentRequest request) {
        paymentService.processPayment(request.correlationId(), request.amount());
        return ResponseEntity.accepted().build();
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



