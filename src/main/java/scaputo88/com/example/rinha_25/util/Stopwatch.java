package scaputo88.com.example.rinha_25.util;

public class Stopwatch {
    private final long start;

    private Stopwatch() {
        this.start = System.nanoTime();
    }

    public static Stopwatch start() {
        return new Stopwatch();
    }

    public long elapsedMillis() {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
