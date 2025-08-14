package scaputo88.com.example.rinha_25.model;

public class PaymentSummary {
    private long totalCount;
    private long successCount;
    private long failedCount;
    private long totalAmountCents;
    private long successAmountCents;
    private long failedAmountCents;

    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }
    public long getSuccessCount() { return successCount; }
    public void setSuccessCount(long successCount) { this.successCount = successCount; }
    public long getFailedCount() { return failedCount; }
    public void setFailedCount(long failedCount) { this.failedCount = failedCount; }
    public long getTotalAmountCents() { return totalAmountCents; }
    public void setTotalAmountCents(long totalAmountCents) { this.totalAmountCents = totalAmountCents; }
    public long getSuccessAmountCents() { return successAmountCents; }
    public void setSuccessAmountCents(long successAmountCents) { this.successAmountCents = successAmountCents; }
    public long getFailedAmountCents() { return failedAmountCents; }
    public void setFailedAmountCents(long failedAmountCents) { this.failedAmountCents = failedAmountCents; }
}

