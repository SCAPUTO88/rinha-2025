package scaputo88.com.example.rinha_25.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scaputo88.com.example.rinha_25.service.PaymentService;

@RestController
@RequestMapping("/maintenance")
public class MaintenanceController {

    private final PaymentService paymentService;

    public MaintenanceController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @DeleteMapping("/purge")
    public void purge() {
        paymentService.purgePayments();
    }
}

