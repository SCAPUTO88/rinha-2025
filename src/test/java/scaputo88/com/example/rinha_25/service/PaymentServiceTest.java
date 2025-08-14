package scaputo88.com.example.rinha_25.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import scaputo88.com.example.rinha_25.dto.PaymentSummaryResponse;
import scaputo88.com.example.rinha_25.model.Payment;
import scaputo88.com.example.rinha_25.model.ProcessorType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private Payments payments;

    @Mock
    private ProcessorClient processorClient;

    @BeforeEach
    void setup() {
        // Payments requer ProcessorClient, mas os testes aqui não acionam os workers
        // pois usamos applyReplication (registro direto/idempotente)
        payments = new Payments(processorClient);
    }

    @Test
    void deveRegistrarPagamentoComSucesso() {
        UUID id = UUID.randomUUID();
        Instant agora = Instant.now();

        Payment p = new Payment(id, BigDecimal.TEN, agora, ProcessorType.DEFAULT, true);

        payments.applyReplication(p);

        PaymentSummaryResponse def = summaryForDefault();
        assertEquals(1L, def.getTotalRequests());
        assertEquals(new BigDecimal("10.00"), def.getTotalAmount());
    }

    @Test
    void naoDeveDuplicarPagamentoIdempotente() {
        UUID id = UUID.randomUUID();
        Instant agora = Instant.now();
        Payment p = new Payment(id, BigDecimal.TEN, agora, ProcessorType.DEFAULT, true);

        payments.applyReplication(p);
        payments.applyReplication(p); // repetido — deve ser ignorado

        PaymentSummaryResponse def = summaryForDefault();
        assertEquals(1L, def.getTotalRequests());
        assertEquals(new BigDecimal("10.00"), def.getTotalAmount());
    }

    @Test
    void resumoPorIntervaloConsideraApenasBucketsDentroDoRange() {
        Instant agora = Instant.now();
        Payment dentro = new Payment(UUID.randomUUID(), BigDecimal.ONE, agora, ProcessorType.DEFAULT, true);
        Payment fora = new Payment(UUID.randomUUID(), BigDecimal.ONE, agora.minus(2, ChronoUnit.DAYS), ProcessorType.DEFAULT, true);

        payments.applyReplication(dentro);
        payments.applyReplication(fora);

        String from = agora.minus(1, ChronoUnit.HOURS).toString();
        String to = agora.plus(1, ChronoUnit.HOURS).toString();

        PaymentSummaryResponse defRange = summaryForDefault(from, to);

        assertEquals(1L, defRange.getTotalRequests());
        // 1 real = 1.00
        assertEquals(new BigDecimal("1.00"), defRange.getTotalAmount());
    }

    // Helpers

    private PaymentSummaryResponse summaryForDefault() {
        return summaryForDefault(null, null);
    }

    private PaymentSummaryResponse summaryForDefault(String from, String to) {
        List<PaymentSummaryResponse> summaries = payments.getSummary(from, to);
        return summaries.stream()
                .filter(s -> s.getType() == ProcessorType.DEFAULT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Resumo DEFAULT não encontrado"));
    }
}
