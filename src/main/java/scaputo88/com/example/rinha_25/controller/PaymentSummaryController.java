package scaputo88.com.example.rinha_25.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scaputo88.com.example.rinha_25.model.PaymentSummaryData;
import scaputo88.com.example.rinha_25.model.ProcessorType;
import scaputo88.com.example.rinha_25.service.PaymentService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping
public class PaymentSummaryController {

    private final PaymentService paymentService;

    public PaymentSummaryController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/payments-summary")
    public Map<String, PaymentSummaryData> getSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        Map<ProcessorType, PaymentSummaryData> raw =
                (from == null && to == null)
                        ? paymentService.getSummary()
                        : paymentService.getSummary(from, to);

        Map<String, PaymentSummaryData> out = new LinkedHashMap<>(2);
        out.put(ProcessorType.DEFAULT.getValue(),
                raw.getOrDefault(ProcessorType.DEFAULT,
                        new PaymentSummaryData(0L, BigDecimal.ZERO)));
        out.put(ProcessorType.FALLBACK.getValue(),
                raw.getOrDefault(ProcessorType.FALLBACK,
                        new PaymentSummaryData(0L, BigDecimal.ZERO)));

        return out;
    }
}
