package scaputo88.com.example.rinha_25.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import scaputo88.com.example.rinha_25.dto.PaymentSummaryResponse;
import scaputo88.com.example.rinha_25.service.Payments;
import scaputo88.com.example.rinha_25.model.ProcessorType;

import java.math.BigDecimal;

@RestController
@RequestMapping
public class PaymentSummaryController {
    private static final Logger log = LoggerFactory.getLogger(PaymentSummaryController.class);
    private final Payments payments;

    public PaymentSummaryController(Payments payments) {
        this.payments = payments;
    }

    @GetMapping("/payments-summary")
    public PaymentSummaryResponse getSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        
        log.info("Received request for payments-summary. from={}, to={}", from, to);
            
        // Get default processor summary
        long defaultCount = payments.getCount(ProcessorType.DEFAULT, from, to);
        BigDecimal defaultAmount = payments.getTotalAmount(ProcessorType.DEFAULT, from, to);
        
        // Get fallback processor summary
        long fallbackCount = payments.getCount(ProcessorType.FALLBACK, from, to);
        BigDecimal fallbackAmount = payments.getTotalAmount(ProcessorType.FALLBACK, from, to);
        
        PaymentSummaryResponse response = new PaymentSummaryResponse(
            new PaymentSummaryResponse.ProcessorSummary(defaultCount, defaultAmount),
            new PaymentSummaryResponse.ProcessorSummary(fallbackCount, fallbackAmount)
        );
        
        log.info("Returning payments-summary: {}", response);
        return response;
    }
}
