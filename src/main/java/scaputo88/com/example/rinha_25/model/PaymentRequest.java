package scaputo88.com.example.rinha_25.model;

import java.math.BigDecimal;

public class PaymentRequest {
    private String correlationId;
    private BigDecimal amount;

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
