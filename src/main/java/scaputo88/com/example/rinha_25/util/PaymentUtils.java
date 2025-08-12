package scaputo88.com.example.rinha_25.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class PaymentUtils {

    private PaymentUtils() {}

    public static Instant parseInstant(String s) {
        try {
            return (s != null && !s.isBlank()) ? Instant.parse(s) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Instant minuteKey(Instant ts) {
        return ts.truncatedTo(ChronoUnit.MINUTES);
    }

    public static long toCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    public static BigDecimal centsToBigDecimal(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    public static int parseIntEnv(String key, int def) {
        try {
            String v = System.getenv(key);
            if (v == null || v.isBlank()) return def;
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }
}

