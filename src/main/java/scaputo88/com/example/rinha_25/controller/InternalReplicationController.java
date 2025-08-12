package scaputo88.com.example.rinha_25.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scaputo88.com.example.rinha_25.model.Payment;
import scaputo88.com.example.rinha_25.service.PaymentService;

@RestController
@RequestMapping("/internal")
public class InternalReplicationController {

    private final PaymentService paymentService;

    public InternalReplicationController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/replicate")
    public ResponseEntity<Void> replicate(@RequestBody Payment payment) {
        paymentService.applyReplication(payment);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
