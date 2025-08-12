package scaputo88.com.example.rinha_25.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedDaemonFactory implements ThreadFactory {

    private final String namePrefix;
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    public NamedDaemonFactory(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true); // importante para não impedir o encerramento da JVM
        t.setName(namePrefix + "-" + threadNumber.getAndIncrement());
        return t;
    }
}

