package scaputo88.com.example.rinha_25.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import scaputo88.com.example.rinha_25.dto.PaymentSummaryResponse;
import scaputo88.com.example.rinha_25.model.Payment;
import scaputo88.com.example.rinha_25.model.ProcessorType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Serviço responsável por gerenciar pagamentos.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final Map<UUID, Payment> payments = new ConcurrentHashMap<>();

    // Contadores globais por ProcessorType
    private final Map<ProcessorType, LongAdder> totalRequests = new ConcurrentHashMap<>();
    private final Map<ProcessorType, LongAdder> totalAmountCents = new ConcurrentHashMap<>();

    // Buckets por minuto por ProcessorType
    private final Map<ProcessorType, Map<Long, MinuteBucket>> minuteBuckets = new ConcurrentHashMap<>();

    public PaymentService() {
        // Inicializa as estruturas para cada tipo
        for (ProcessorType type : ProcessorType.values()) {
            totalRequests.put(type, new LongAdder());
            totalAmountCents.put(type, new LongAdder());
            minuteBuckets.put(type, new ConcurrentHashMap<>());
        }
        log.info("PaymentService inicializado com suporte a múltiplos ProcessorType");
    }

    public void registerIdempotent(Payment payment) {
        ProcessorType type = payment.getProcessor();
        if (type == null) type = ProcessorType.DEFAULT;
        payment.setProcessor(type);

        // Idempotência por correlationId
        Payment existing = payments.putIfAbsent(payment.getCorrelationId(), payment);
        if (existing != null) {
            return;
        }

        totalRequests.get(type).increment();
        totalAmountCents.get(type).add(toCents(payment.getAmount()));

        long minute = truncateToMinute(payment.getRequestedAt());
        minuteBuckets.get(type)
                .computeIfAbsent(minute, m -> new MinuteBucket())
                .add(payment.getAmount());
    }

    public void applyReplication(Payment payment) {
        registerIdempotent(payment);
    }

    public Optional<Payment> findById(UUID id) {
        return Optional.ofNullable(payments.get(id));
    }

    public PaymentSummaryResponse getSummaryResponse(String from, String to) {
        // Get default processor summary
        PaymentSummary defaultSummary = (from == null && to == null)
                ? getSummary(ProcessorType.DEFAULT)
                : getSummary(ProcessorType.DEFAULT, from, to);
        
        // Get fallback processor summary
        PaymentSummary fallbackSummary = (from == null && to == null)
                ? getSummary(ProcessorType.FALLBACK)
                : getSummary(ProcessorType.FALLBACK, from, to);
        
        return new PaymentSummaryResponse(
            new PaymentSummaryResponse.ProcessorSummary(
                defaultSummary.getTotalCount(),
                centsToBigDecimal(defaultSummary.getTotalAmountCents())
            ),
            new PaymentSummaryResponse.ProcessorSummary(
                fallbackSummary.getTotalCount(),
                centsToBigDecimal(fallbackSummary.getTotalAmountCents())
            )
        );
    }

    private PaymentSummary getSummary(ProcessorType type) {
        return new PaymentSummary(
                totalRequests.get(type).sum(),
                totalAmountCents.get(type).sum()
        );
    }

    private PaymentSummary getSummary(ProcessorType type, String from, String to) {
        Instant fromInstant = parseDate(from);
        Instant toInstant = parseDate(to);

        long fromMinute = truncateToMinute(fromInstant);
        long toMinute = truncateToMinute(toInstant);

        long count = 0;
        long amount = 0;

        for (Map.Entry<Long, MinuteBucket> entry : minuteBuckets.get(type).entrySet()) {
            long minute = entry.getKey();
            if (minute >= fromMinute && minute <= toMinute) {
                MinuteBucket bucket = entry.getValue();
                count += bucket.getCount();
                amount += bucket.getTotalCents();
            }
        }

        return new PaymentSummary(count, amount);
    }

    private long truncateToMinute(Instant instant) {
        return instant.getEpochSecond() / 60;
    }

    private Instant parseDate(String date) {
        if (date == null) return Instant.now();
        try {
            return Instant.parse(date);
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }

    private long toCents(BigDecimal amount) {
        return amount.movePointRight(2).longValue();
    }

    private BigDecimal centsToBigDecimal(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    private static class PaymentSummary {
        private final long totalCount;
        private final long totalAmountCents;

        public PaymentSummary(long totalCount, long totalAmountCents) {
            this.totalCount = totalCount;
            this.totalAmountCents = totalAmountCents;
        }

        public long getTotalCount() {
            return totalCount;
        }

        public long getTotalAmountCents() {
            return totalAmountCents;
        }
    }

    private static class MinuteBucket {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalCents = new LongAdder();

        public void add(BigDecimal amount) {
            count.increment();
            totalCents.add(amount.movePointRight(2).longValue());
        }

        public long getCount() {
            return count.sum();
        }

        public long getTotalCents() {
            return totalCents.sum();
        }
    }
}
