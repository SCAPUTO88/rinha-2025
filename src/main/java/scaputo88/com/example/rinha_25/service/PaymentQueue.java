package scaputo88.com.example.rinha_25.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PaymentQueue implements AutoCloseable {

    private final Logger log = LoggerFactory.getLogger(PaymentQueue.class);

    private final ThreadPoolExecutor executor;
    private final int offerTimeoutMs;

    public PaymentQueue() {
        int workers = getEnvIntSafe("WORKERS", 32);
        int queueCapacity = getEnvIntSafe("QUEUE_CAPACITY", 65536);
        this.offerTimeoutMs = getEnvIntSafe("QUEUE_OFFER_TIMEOUT_MS", 50);

        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(queueCapacity);
        ThreadFactory tf = new NamedThreadFactory("pay-worker");
        RejectedExecutionHandler reh = new BlockOrLogPolicy(queue, offerTimeoutMs);

        this.executor = new ThreadPoolExecutor(
                workers,
                workers,
                30L, TimeUnit.SECONDS,
                queue,
                tf,
                reh
        );
        this.executor.allowCoreThreadTimeOut(false);

        log.info("✅ PaymentQueue inicializada: workers={}, queueCapacity={}, offerTimeoutMs={}",
                workers, queueCapacity, offerTimeoutMs);
    }

    public void submit(Runnable task) {
        Objects.requireNonNull(task, "task");
        try {
            executor.execute(task);
        } catch (RejectedExecutionException rex) {
            boolean offered = false;
            try {
                offered = ((BlockingQueue<Runnable>) executor.getQueue())
                        .offer(task, offerTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            if (!offered) {
                log.warn("⚠️ Fila de pagamentos saturada: rejeitando task após {} ms", offerTimeoutMs);
                throw rex;
            }
        }
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    public int getActiveCount() {
        return executor.getActiveCount();
    }

    @Override
    public void close() {
        shutdownGracefully(5, TimeUnit.SECONDS);
    }

    public void shutdownGracefully(long timeout, TimeUnit unit) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout, unit)) {
                log.warn("⏳ Timeout ao finalizar PaymentQueue; forçando shutdownNow()");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private int getEnvIntSafe(String key, int def) {
        String v = System.getenv(key);
        if (v == null) {
            log.warn("Variável {} não definida. Usando valor padrão: {}", key, def);
            return def;
        }
        try {
            int val = Integer.parseInt(v.trim());
            if (val <= 0) {
                log.warn("Valor inválido para {}: {}. Usando valor padrão: {}", key, val, def);
                return def;
            }
            return val;
        } catch (NumberFormatException e) {
            log.warn("Valor inválido para {}: {}. Usando valor padrão: {}", key, v, def);
            return def;
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String base;
        private final AtomicInteger idx = new AtomicInteger(1);

        NamedThreadFactory(String base) {
            this.base = base;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, base + "-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }

    private static final class BlockOrLogPolicy implements RejectedExecutionHandler {
        private final BlockingQueue<Runnable> queue;
        private final int offerTimeoutMs;

        BlockOrLogPolicy(BlockingQueue<Runnable> queue, int offerTimeoutMs) {
            this.queue = queue;
            this.offerTimeoutMs = offerTimeoutMs;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            boolean offered = false;
            try {
                offered = queue.offer(r, offerTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!offered) {
                throw new RejectedExecutionException("Fila cheia após " + offerTimeoutMs + "ms");
            }
        }
    }
}

