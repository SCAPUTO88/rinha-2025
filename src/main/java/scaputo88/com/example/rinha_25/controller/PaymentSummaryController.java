package scaputo88.com.example.rinha_25.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scaputo88.com.example.rinha_25.dto.PaymentSummaryResponse;
import scaputo88.com.example.rinha_25.service.Payments;

import java.util.List;


@RestController
@RequestMapping
public class PaymentSummaryController {

    private final Payments payments;

    public PaymentSummaryController(Payments payments) {
        this.payments = payments;
    }

    @GetMapping("/payments-summary")
    public List<PaymentSummaryResponse> getSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return payments.getSummary(from, to);
    }
}
