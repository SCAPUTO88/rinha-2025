package scaputo88.com.example.rinha_25.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class TotalSummaryResponse {
    private long totalRequests;
    private String totalAmount; // string com 2 casas, compatível com processors

    public TotalSummaryResponse() {
        this(0L, BigDecimal.ZERO);
    }

    public TotalSummaryResponse(long totalRequests, BigDecimal totalAmount) {
        this.totalRequests = totalRequests;
        this.totalAmount = format(totalAmount);
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public String getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(String totalAmount) {
        this.totalAmount = totalAmount;
    }

    private static String format(BigDecimal v) {
        if (v == null) return "0.00";
        return v.setScale(2, java.math.RoundingMode.DOWN).toPlainString();
    }
}
