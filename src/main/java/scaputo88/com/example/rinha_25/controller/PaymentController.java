package scaputo88.com.example.rinha_25.controller;


import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import scaputo88.com.example.rinha_25.model.Payment;
import scaputo88.com.example.rinha_25.service.PaymentService;

import java.math.BigDecimal;
import java.util.UUID;



@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody @Valid PaymentRequest request) {
        service.processPayment(request.correlationId(), request.amount());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> get(@PathVariable UUID id) {
        Payment payment = service.getPayment(id);
        return payment != null ? ResponseEntity.ok(payment) : ResponseEntity.notFound().build();
    }

    public record PaymentRequest(
            @NotNull UUID correlationId,
            @NotNull @DecimalMin("0.00") BigDecimal amount
    ) {}
}


