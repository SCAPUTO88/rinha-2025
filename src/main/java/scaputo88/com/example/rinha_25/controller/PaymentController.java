package scaputo88.com.example.rinha_25.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import scaputo88.com.example.rinha_25.model.PaymentRequest;
import scaputo88.com.example.rinha_25.model.PaymentSummary;
import scaputo88.com.example.rinha_25.service.PaymentService;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping(path = "/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> createPayment(@Valid @RequestBody PaymentRequest request) {
        paymentService.processAsync(request);
        return ResponseEntity.accepted().build();
    }
    @GetMapping(path = "/payments-summary")
    public ResponseEntity<PaymentSummary> getSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        PaymentSummary summary = paymentService.getSummary(from, to);
        return ResponseEntity.ok(summary);
    }

    @PostMapping(path = "/purge-payments")
    public ResponseEntity<Void> purgePayments() {
        paymentService.purgePayments();
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler({ MethodArgumentTypeMismatchException.class, DateTimeParseException.class, IllegalArgumentException.class })
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Invalid request"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleServerError(Exception ex) {
        return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
    }
}
