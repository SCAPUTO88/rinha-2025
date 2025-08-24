package scaputo88.com.example.rinha_25.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import scaputo88.com.example.rinha_25.model.Payment;
import scaputo88.com.example.rinha_25.model.PaymentSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Repository
public class RedisPaymentRepository implements PaymentRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisPaymentRepository.class);

    private static final String SUMMARY_KEY = "payments_summary";
    private static final String ZSET_KEY = "payments_zset";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisPaymentRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(Payment payment) {
        try {
            String prefix = payment.processorUsed();

            incrementHash(prefix + "_total_requests", 1);
            incrementHash(prefix + "_total_amount", toDouble(payment.amount()));
            incrementHash(prefix + "_total_fee", toDouble(payment.fee()));
            String value = String.join("|",
                    payment.processorUsed(),
                    payment.amount() != null ? payment.amount().toString() : "0",
                    payment.fee() != null ? payment.fee().toString() : "0",
                    String.valueOf(payment.usedFallback()),
                    UUID.randomUUID().toString());
            redisTemplate.opsForZSet().add(ZSET_KEY, value, payment.timestamp().toEpochMilli());

        } catch (Exception e) {
            log.error("Erro ao salvar pagamento no Redis: {}", e.getMessage(), e);
        }
    }

    @Override
    public PaymentSummary getSummary(Instant from, Instant to) {
        BigDecimal defAmount = BigDecimal.ZERO;
        BigDecimal defFee = BigDecimal.ZERO;
        long defRequests = 0;

        BigDecimal fbAmount = BigDecimal.ZERO;
        BigDecimal fbFee = BigDecimal.ZERO;
        long fbRequests = 0;

        try {
            double min = (from != null) ? from.toEpochMilli() : Double.NEGATIVE_INFINITY;
            double max = (to != null) ? to.toEpochMilli() : Double.POSITIVE_INFINITY;

            Set<String> results = redisTemplate.opsForZSet().rangeByScore(ZSET_KEY, min, max);

            if (results != null) {
                for (String r : results) {
                    String[] parts = r.split("\\|");
                    if (parts.length < 4) continue;

                    String proc = parts[0];
                    BigDecimal amount = safeBigDecimal(parts[1]);
                    BigDecimal fee = safeBigDecimal(parts[2]);
                    boolean fallback = Boolean.parseBoolean(parts[3]);

                    if ("default".equalsIgnoreCase(proc)) {
                        defRequests++;
                        defAmount = defAmount.add(amount);
                        defFee = defFee.add(fee);
                    } else {
                        fbRequests++;
                        fbAmount = fbAmount.add(amount);
                        fbFee = fbFee.add(fee);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Erro ao gerar resumo no Redis: {}", e.getMessage(), e);
        }

        return new PaymentSummary(defAmount, defFee, defRequests, fbAmount, fbFee, fbRequests);
    }

    @Override
    public void purgePayments() {
        try {
            redisTemplate.delete(SUMMARY_KEY);
            redisTemplate.delete(ZSET_KEY);
        } catch (Exception e) {
            log.error("Erro ao limpar pagamentos: {}", e.getMessage(), e);
        }
    }

    private void incrementHash(String field, double delta) {
        redisTemplate.opsForHash().increment(SUMMARY_KEY, field, delta);
    }

    private double toDouble(BigDecimal bd) {
        return bd != null ? bd.doubleValue() : 0.0;
    }

    private BigDecimal safeBigDecimal(String s) {
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
