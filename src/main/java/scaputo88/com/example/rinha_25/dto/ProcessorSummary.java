package scaputo88.com.example.rinha_25.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class ProcessorSummary {

    @JsonProperty("totalRequests")
    private final long totalRequests;

    @JsonProperty("totalAmount")
    private final BigDecimal totalAmount;

    public ProcessorSummary() {
        this(0L, BigDecimal.ZERO);
    }

    public ProcessorSummary(long totalRequests, BigDecimal totalAmount) {
        this.totalRequests = totalRequests;
        this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
}

