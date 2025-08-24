package scaputo88.com.example.rinha_25.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    public ProcessorSummary getDefault() {
        return defaultSummary;
    }

    public ProcessorSummary getFallback() {
        return fallbackSummary;
    }
}



