package scaputo88.com.example.rinha_25.service;

import org.springframework.stereotype.Service;
import scaputo88.com.example.rinha_25.model.ProcessorType;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Serviço responsável por registrar métricas de processamento de pagamentos.
 */
@Service
public class MetricsService {

    private final Map<ProcessorType, LongAdder> successCounters = new EnumMap<>(ProcessorType.class);
    private final Map<ProcessorType, LongAdder> failureCounters = new EnumMap<>(ProcessorType.class);
    private final Map<ProcessorType, LongAdder> amountCounters = new EnumMap<>(ProcessorType.class);
    private final LongAdder latencySamples = new LongAdder();
    private final LongAdder latencyTotalNanos = new LongAdder();

    /**
     * Construtor do serviço de métricas.
     */
    public MetricsService() {
        for (ProcessorType p : ProcessorType.values()) {
            successCounters.put(p, new LongAdder());
            failureCounters.put(p, new LongAdder());
            amountCounters.put(p, new LongAdder());
        }
    }

    /**
     * Registra um pagamento com sucesso.
     *
     * @param processor tipo do processador
     * @param amount    valor do pagamento
     * @param nanos     tempo de processamento em nanossegundos
     */
    public void recordSuccess(ProcessorType processor, BigDecimal amount, long nanos) {
        LongAdder s = successCounters.get(processor);
        if (s != null) s.increment();
        // soma a quantidade de eventos de amount só para manter compatibilidade mínima
        amountCounters.get(processor).increment();
        recordLatency(nanos);
    }

    /**
     * Registra um pagamento com falha.
     *
     * @param processor tipo do processador
     * @param nanos     tempo de processamento em nanossegundos
     */
    public void recordFailure(ProcessorType processor, long nanos) {
        LongAdder f = failureCounters.get(processor);
        if (f != null) f.increment();
        recordLatency(nanos);
    }

    /**
     * Registra a latência de processamento.
     *
     * @param nanos tempo de processamento em nanossegundos
     */
    private void recordLatency(long nanos) {
        if (nanos <= 0) return;
        latencySamples.increment();
        latencyTotalNanos.add(nanos);
    }

    /**
     * Retorna a latência média de processamento em milissegundos.
     *
     * @return latência média em milissegundos
     */
    public double getAverageLatencyMillis() {
        long samples = latencySamples.sum();
        if (samples == 0) return 0.0;
        return (latencyTotalNanos.sum() / 1_000_000.0) / samples;
    }
}
