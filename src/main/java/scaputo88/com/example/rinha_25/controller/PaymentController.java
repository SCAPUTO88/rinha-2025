package scaputo88.com.example.rinha_25.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import scaputo88.com.example.rinha_25.model.Payment;
import scaputo88.com.example.rinha_25.model.ProcessorType;
import scaputo88.com.example.rinha_25.service.Payments;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final Payments payments;

    public PaymentController(Payments payments) {
        this.payments = payments;
    }

    @PostMapping
    public ResponseEntity<Void> createPayment(@RequestBody Map<String, Object> body) {
        try {
            Object idRaw = body.get("correlationId");
            Object amountRaw = body.get("amount");
            Object processorRaw = body.get("processor");

            if (idRaw == null || amountRaw == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            UUID id = UUID.fromString(idRaw.toString());
            BigDecimal amount = new BigDecimal(amountRaw.toString());
            if (amount.signum() <= 0) {
                return ResponseEntity.badRequest().build();
            }

            ProcessorType processor = resolveProcessor(processorRaw);

            boolean accepted = payments.enqueue(id, amount, processor);
            if (accepted) {
                return ResponseEntity.accepted().build();
            } else {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
            }
        } catch (IllegalArgumentException ex) { // UUID inválido ou processor inválido (já tratado)
            log.debug("Requisição inválida: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.warn("Erro ao criar pagamento", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPayment(@PathVariable UUID id) {
        return payments.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/service-health")
    public Map<String, String> serviceHealth() {
        var def = payments.getProcessorHealth(ProcessorType.DEFAULT);
        var fb = payments.getProcessorHealth(ProcessorType.FALLBACK);
        return Map.of(
                "default", def.healthy() ? "UP" : "DOWN", "fallback", fb.healthy() ? "UP" : "DOWN"
        );
    }

    private ProcessorType resolveProcessor(Object raw) {
        if (raw == null) return ProcessorType.DEFAULT;
        String value = raw.toString().trim();
        if (value.isEmpty()) return ProcessorType.DEFAULT;
        try {
            return ProcessorType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // Fallback para DEFAULT em caso de valor inválido
            return ProcessorType.DEFAULT;
        }
    }
}
