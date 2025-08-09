package scaputo88.com.example.rinha_25.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import scaputo88.com.example.rinha_25.model.QueueTask;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class PaymentQueue {

    private final BlockingQueue<QueueTask> queue = new LinkedBlockingQueue<>();
    private ExecutorService workers;
    private final MeterRegistry registry;

    public PaymentQueue(MetricsService metricsService) {
        this.registry = metricsService.getRegistry();
        // Gauge do tamanho da fila
        registry.gauge("payments.queue.size", queue, BlockingQueue::size);
    }

    @PostConstruct
    public void startWorkers() {
        ExecutorService raw = Executors.newFixedThreadPool(4);
        // Métricas do pool de threads
        this.workers = ExecutorServiceMetrics.monitor(registry, raw, "payments.workers");
        for (int i = 0; i < 4; i++) {
            workers.submit(() -> {
                while (true) {
                    try {
                        QueueTask task = queue.take();
                        task.run();
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    public void submit(QueueTask task) {
        queue.offer(task);
    }
}

