package scaputo88.com.example.rinha_25.model;

@FunctionalInterface
public interface QueueTask extends Runnable {
    @Override
    void run();
}

