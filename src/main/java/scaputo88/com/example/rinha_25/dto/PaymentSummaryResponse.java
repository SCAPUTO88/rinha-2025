package scaputo88.com.example.rinha_25.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentSummaryResponse {
    
    @JsonProperty("default")
    private final ProcessorSummary defaultSummary;
    
    @JsonProperty("fallback")
    private final ProcessorSummary fallbackSummary;

    public PaymentSummaryResponse(ProcessorSummary defaultSummary, ProcessorSummary fallbackSummary) {
        this.defaultSummary = defaultSummary != null ? defaultSummary : new ProcessorSummary();
        this.fallbackSummary = fallbackSummary != null ? fallbackSummary : new ProcessorSummary();
    }

    public static class ProcessorSummary {
        private final long totalRequests;
        private final double totalAmount;

        public ProcessorSummary() {
            this(0, BigDecimal.ZERO);
        }

        public ProcessorSummary(long totalRequests, BigDecimal totalAmount) {
            this.totalRequests = totalRequests;
            this.totalAmount = totalAmount != null ? totalAmount.doubleValue() : 0.0;
        }

        @JsonProperty("totalRequests")
        public long getTotalRequests() {
            return totalRequests;
        }

        @JsonProperty("totalAmount")
        public double getTotalAmount() {
            return totalAmount;
        }

        @Override
        public String toString() {
            return "{\"totalRequests\":" + totalRequests + ",\"totalAmount\":" + totalAmount + "}";
        }
    }

    // Getters for JSON serialization
    public ProcessorSummary getDefault() {
        return defaultSummary;
    }

    public ProcessorSummary getFallback() {
        return fallbackSummary;
    }

    @Override
    public String toString() {
        return "{\"default\":" + defaultSummary + ",\"fallback\":" + fallbackSummary + "}";
    }
}
