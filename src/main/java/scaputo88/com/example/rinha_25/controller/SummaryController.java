package scaputo88.com.example.rinha_25.controller;

import org.springframework.web.bind.annotation.*;
import scaputo88.com.example.rinha_25.model.PaymentSummaryData;
import scaputo88.com.example.rinha_25.model.ProcessorType;
import scaputo88.com.example.rinha_25.service.PaymentService;

import java.util.EnumMap;
import java.util.Map;

@RestController
@RequestMapping("/payments-summary")
public class SummaryController {

    private final PaymentService service;

    public SummaryController(PaymentService service) {
        this.service = service;
    }

    @GetMapping
    public Map<ProcessorType, PaymentSummaryData> summary() {
        return service.getSummary();
    }
}


