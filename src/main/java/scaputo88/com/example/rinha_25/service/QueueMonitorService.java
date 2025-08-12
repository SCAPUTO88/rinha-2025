package scaputo88.com.example.rinha_25.service;

import org.springframework.stereotype.Service;

@Service
public class QueueMonitorService {

    private final PaymentQueue paymentQueue;

    public QueueMonitorService(PaymentQueue paymentQueue) {
        this.paymentQueue = paymentQueue;
    }

    public QueueStatus snapshot() {
        return new QueueStatus(
                paymentQueue.getQueueSize(),
                paymentQueue.getRemainingCapacity(),
                paymentQueue.getActiveWorkerCount(),
                paymentQueue.getTotalWorkerCount()
        );
    }

    public static record QueueStatus(
            int queueSize,
            int remainingCapacity,
            int activeWorkerCount,
            int totalWorkerCount
    ) {}
}
