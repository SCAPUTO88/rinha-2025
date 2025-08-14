package scaputo88.com.example.rinha_25.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import scaputo88.com.example.rinha_25.model.ProcessorType;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PaymentSummaryResponse {

    private ProcessorType type;
    private long totalRequests;
    private BigDecimal totalAmount;

    public PaymentSummaryResponse() {
        this(null, 0L, BigDecimal.ZERO);
    }

    public PaymentSummaryResponse(ProcessorType type, long totalRequests, BigDecimal totalAmount) {
        this.type = type;
        this.totalRequests = totalRequests;
        this.totalAmount = (totalAmount != null ? totalAmount : BigDecimal.ZERO);
    }

    public ProcessorType getType() {
        return type;
    }

    public void setType(ProcessorType type) {
        this.type = type;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = (totalAmount != null ? totalAmount : BigDecimal.ZERO);
    }

    @Override
    public String toString() {
        return "PaymentSummaryResponse{" +
                "type=" + type +
                ", totalRequests=" + totalRequests +
                ", totalAmount=" + totalAmount +
                '}';
    }
}

