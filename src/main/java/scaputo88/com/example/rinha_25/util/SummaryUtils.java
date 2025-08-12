package scaputo88.com.example.rinha_25.util;

import scaputo88.com.example.rinha_25.model.PaymentSummaryData;
import scaputo88.com.example.rinha_25.model.ProcessorType;
import static scaputo88.com.example.rinha_25.util.PaymentUtils.minuteKey;


import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.atomic.LongAdder;

import static scaputo88.com.example.rinha_25.util.PaymentUtils.centsToBigDecimal;


public final class SummaryUtils {

    private SummaryUtils() {}

    public static class Bucket {
        public final LongAdder requests = new LongAdder();
        public final LongAdder amountCents = new LongAdder();
    }

    public static class Aggregator {
        public final LongAdder requests = new LongAdder();
        public final LongAdder amountCents = new LongAdder();
    }

    public static EnumMap<ProcessorType, Aggregator> initAggregators() {
        EnumMap<ProcessorType, Aggregator> agg = new EnumMap<>(ProcessorType.class);
        for (ProcessorType t : ProcessorType.values()) {
            agg.put(t, new Aggregator());
        }
        return agg;
    }

    public static Map<ProcessorType, PaymentSummaryData> toSummaryData(EnumMap<ProcessorType, Aggregator> agg) {
        EnumMap<ProcessorType, PaymentSummaryData> out = new EnumMap<>(ProcessorType.class);
        for (ProcessorType t : ProcessorType.values()) {
            long req = agg.get(t).requests.sum();
            long cents = agg.get(t).amountCents.sum();
            out.put(t, new PaymentSummaryData(req, centsToBigDecimal(cents)));
        }
        return out;
    }

    // Preferencial: minuteBuckets como NavigableMap (ex.: ConcurrentSkipListMap)
    public static <B extends Bucket> void aggregateRange(
            Instant fromTs,
            Instant toTs,
            NavigableMap<Instant, EnumMap<ProcessorType, B>> minuteBuckets,
            EnumMap<ProcessorType, Aggregator> agg
    ) {
        minuteBuckets
                .subMap(minuteKey(fromTs), true, minuteKey(toTs), true)
                .forEach((minute, perType) -> {
                    for (ProcessorType t : ProcessorType.values()) {
                        B b = perType.get(t);
                        if (b != null) {
                            agg.get(t).requests.add(b.requests.sum());
                            agg.get(t).amountCents.add(b.amountCents.sum());
                        }
                    }
                });
    }

    // Alternativa: aceita Map genérico (filtrando manualmente o intervalo)
    public static <B extends Bucket> void aggregateRange(
            Instant fromTs,
            Instant toTs,
            Map<Instant, EnumMap<ProcessorType, B>> minuteBuckets,
            EnumMap<ProcessorType, Aggregator> agg,
            boolean ignoreOrdering
    ) {
        Instant fromKey = minuteKey(fromTs);
        Instant toKey = minuteKey(toTs);
        minuteBuckets.forEach((minute, perType) -> {
            if (minute.compareTo(fromKey) < 0 || minute.compareTo(toKey) > 0) return;
            for (ProcessorType t : ProcessorType.values()) {
                B b = perType.get(t);
                if (b != null) {
                    agg.get(t).requests.add(b.requests.sum());
                    agg.get(t).amountCents.add(b.amountCents.sum());
                }
            }
        });
    }
}
