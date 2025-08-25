package scaputo88.com.example.rinha_25.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import scaputo88.com.example.rinha_25.model.Payment;
import scaputo88.com.example.rinha_25.model.PaymentRequest;
import scaputo88.com.example.rinha_25.model.PaymentSummary;
import scaputo88.com.example.rinha_25.repository.PaymentRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository redisRepo;
    private final ProcessorClient processorClient;
    private final ExecutorService asyncPool;

    public PaymentService(PaymentRepository redisRepo,
                          ProcessorClient processorClient) {
        this.redisRepo = redisRepo;
        this.processorClient = processorClient;
        this.asyncPool = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
    }

    public void processAsync(PaymentRequest request) {
        asyncPool.submit(() -> {
            Instant now = Instant.now();
            UUID correlationId = UUID.fromString(request.getCorrelationId());
            BigDecimal amount = request.getAmount();
            if (amount == null) {
                log.warn("Pagamento {} ignorado: amount nulo", correlationId);
                return;
            }

            log.debug("Iniciando processamento do pagamento {} no valor de R${}", correlationId, amount);

            boolean success = processorClient.sendPayment("default", correlationId, amount, now);
            String processorUsed = null;

            if (success) {
                processorUsed = "default";
            } else {
                log.warn("Processor default falhou, tentando fallback...");
                success = processorClient.sendPayment("fallback", correlationId, amount, now);
                if (success) {
                    processorUsed = "fallback";
                }
            }

            if (success && processorUsed != null) {
                BigDecimal fee = "fallback".equals(processorUsed)
                        ? amount.multiply(BigDecimal.valueOf(0.15))
                        : amount.multiply(BigDecimal.valueOf(0.05));
                fee = fee.setScale(2, RoundingMode.DOWN);

                Payment payment = new Payment(
                        correlationId,
                        processorUsed,
                        amount,
                        fee,
                        now,
                        "fallback".equals(processorUsed)
                );

                try {
                    redisRepo.save(payment);
                } catch (Exception e) {
                    log.error("Falha ao salvar pagamento no Redis: {}", e.getMessage(), e);
                }
            } else {
                log.debug("Pagamento {} não persistido (sem sucesso em default/fallback)", correlationId);
            }

            log.debug("Pagamento {} via {}, valor R${}, sucesso: {}",
                    correlationId, processorUsed != null ? processorUsed : "none", amount, success);
        });
    }

    public PaymentSummary getSummary(Instant from, Instant to) {
        log.debug("Buscando resumo de pagamentos entre {} e {}", from, to);
        PaymentSummary summary = null;
        try {
            summary = redisRepo.getSummary(from, to);
        } catch (Exception e) {
            log.error("Erro ao buscar resumo no Redis: {}", e.getMessage(), e);
        }

        if (summary == null) {
            log.warn("Resumo inexistente ou erro na consulta, retornando valores zerados.");
            summary = new PaymentSummary(
                    BigDecimal.ZERO, // default_total_amount
                    BigDecimal.ZERO, // default_total_fee
                    0L,               // default_total_requests
                    BigDecimal.ZERO, // fallback_total_amount
                    BigDecimal.ZERO, // fallback_total_fee
                    0L               // fallback_total_requests
            );
        }
        return summary;
    }

    public void purgePayments() {
        log.warn("Limpando todos os pagamentos armazenados no Redis...");
        try {
            redisRepo.purgePayments();
            log.info("Todos os pagamentos foram removidos.");
        } catch (Exception e) {
            log.error("Erro ao limpar pagamentos: {}", e.getMessage(), e);
        }
    }
}
