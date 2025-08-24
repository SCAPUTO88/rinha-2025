package scaputo88.com.example.rinha_25.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scaputo88.com.example.rinha_25.dto.PaymentSummaryResponse;
import scaputo88.com.example.rinha_25.dto.ProcessorSummary;
import scaputo88.com.example.rinha_25.model.PaymentSummary;
import scaputo88.com.example.rinha_25.service.PaymentService;

import java.math.BigDecimal;
import java.time.Instant;

@RestController
public class PaymentSummaryController {

    private final PaymentService paymentService;

    public PaymentSummaryController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/payments-summary")
    public PaymentSummaryResponse getPaymentsSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        PaymentSummary summary = paymentService.getSummary(from, to);

        ProcessorSummary def = new ProcessorSummary(
                summary.default_total_requests(),
                summary.default_total_amount()
        );

        ProcessorSummary fb = new ProcessorSummary(
                summary.fallback_total_requests(),
                summary.fallback_total_amount()
        );

        return new PaymentSummaryResponse(def, fb);
    }
}




