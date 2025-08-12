package scaputo88.com.example.rinha_25.service;


import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import scaputo88.com.example.rinha_25.model.QueueTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

@Component
public class PaymentQueue {

    private static final Logger log = LoggerFactory.getLogger(PaymentQueue.class);

    private final BlockingQueue<EnqueuedTask> queue;
    private ExecutorService workers;
    private final MeterRegistry registry;

    private final Counter droppedCounter;
    private final Timer waitTimer;
    private final Timer execTimer;

    private final int offerTimeoutMs;

    public PaymentQueue(MetricsService metricsService) {
        this.registry = metricsService.getRegistry();

        int capacity = parseIntEnv("QUEUE_CAPACITY", -1);
        this.queue = capacity > 0 ? new LinkedBlockingQueue<>(capacity) : new LinkedBlockingQueue<>();

        this.offerTimeoutMs = parseIntEnv("QUEUE_OFFER_TIMEOUT_MS", 50);

        // Métricas
        registry.gauge("payments.queue.size", queue, BlockingQueue::size);
        this.droppedCounter = Counter.builder("payments.queue.dropped").register(registry);
        this.waitTimer = Timer.builder("payments.queue.wait").publishPercentiles(0.5, 0.9, 0.95, 0.99).register(registry);
        this.execTimer = Timer.builder("payments.task.duration").publishPercentiles(0.5, 0.9, 0.95, 0.99).register(registry);
    }

    @PostConstruct
    public void startWorkers() {
        int nWorkers = Math.max(1, parseIntEnv("WORKERS", 4));
        ExecutorService raw = Executors.newFixedThreadPool(nWorkers);
        // Se você usa Micrometer executor metrics, mantenha aqui. Caso não, pode remover.
        this.workers = ExecutorServiceMetrics.monitor(registry, raw, "payments.workers");

        for (int i = 0; i < nWorkers; i++) {
            workers.submit(() -> {
                while (true) {
                    try {
                        EnqueuedTask task = queue.take();
                        long startExecNs = System.nanoTime();
                        long waitedNs = System.nanoTime() - task.enqueuedAtNs;
                        waitTimer.record(waitedNs, TimeUnit.NANOSECONDS);
                        try {
                            task.delegate.run();
                        } finally {
                            execTimer.record(System.nanoTime() - startExecNs, TimeUnit.NANOSECONDS);
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception ex) {
                        log.warn("Erro ao executar task da fila: {}", ex.getMessage());
                    }
                }
            });
        }
        log.info("PaymentQueue iniciada com {} workers", nWorkers);
    }

    public void submit(QueueTask task) {
        EnqueuedTask wrapped = new EnqueuedTask(task, System.nanoTime());
        boolean offered = false;
        try {
            offered = queue.offer(wrapped, offerTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!offered) {
            droppedCounter.increment();
            log.warn("Task descartada: fila cheia (timeout {}ms, size={}, remaining={})",
                    offerTimeoutMs, queue.size(), queue.remainingCapacity());
        }
    }

    public int getQueueSize() { return queue.size(); }

    public int getRemainingCapacity() { return queue.remainingCapacity(); }

    public int getActiveWorkerCount() {
        if (workers instanceof ThreadPoolExecutor tpe) return tpe.getActiveCount();
        return -1;
    }

    public int getTotalWorkerCount() {
        if (workers instanceof ThreadPoolExecutor tpe) return tpe.getPoolSize();
        return -1;
    }

    private int parseIntEnv(String key, int def) {
        try {
            String v = System.getenv(key);
            if (v == null || v.isBlank()) return def;
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static final class EnqueuedTask {
        final QueueTask delegate;
        final long enqueuedAtNs;

        EnqueuedTask(QueueTask delegate, long enqueuedAtNs) {
            this.delegate = delegate;
            this.enqueuedAtNs = enqueuedAtNs;
        }
    }
}

