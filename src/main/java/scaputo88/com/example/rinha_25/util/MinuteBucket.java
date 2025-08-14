package scaputo88.com.example.rinha_25.util;

import java.util.concurrent.atomic.LongAdder;

public final class MinuteBucket {
    public final LongAdder totalCount = new LongAdder();
    public final LongAdder successCount = new LongAdder();
    public final LongAdder failedCount = new LongAdder();
    public final LongAdder totalAmountCents = new LongAdder();
    public final LongAdder successAmountCents = new LongAdder();
    public final LongAdder failedAmountCents = new LongAdder();
}

