package scaputo88.com.example.rinha_25.service;

import org.springframework.stereotype.Service;


@Service
public class QueueMonitorService {

    private final PaymentQueue paymentQueue;

    public QueueMonitorService(PaymentQueue paymentQueue) {
        this.paymentQueue = paymentQueue;
    }

    public QueueStatus snapshot() {
        return new QueueStatus(paymentQueue.getQueueSize());
    }

    public static record QueueStatus(
            int queueSize
    ) {}
}

