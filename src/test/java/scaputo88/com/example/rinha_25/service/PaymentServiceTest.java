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

        PaymentSummaryResponse summary = payments.getSummaryResponse(null, null);
        assertEquals(1L, summary.getDefault().getTotalRequests());
        assertEquals(10.0, summary.getDefault().getTotalAmount());
    }

    @Test
    void naoDeveDuplicarPagamentoIdempotente() {
        UUID id = UUID.randomUUID();
        Instant agora = Instant.now();
        Payment p = new Payment(id, BigDecimal.TEN, agora, ProcessorType.DEFAULT, true);

        payments.applyReplication(p);
        payments.applyReplication(p); // repetido — deve ser ignorado

        PaymentSummaryResponse summary = payments.getSummaryResponse(null, null);
        assertEquals(1L, summary.getDefault().getTotalRequests());
        assertEquals(10.0, summary.getDefault().getTotalAmount());
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

        PaymentSummaryResponse summary = payments.getSummaryResponse(from, to);

        assertEquals(1L, summary.getDefault().getTotalRequests());
        assertEquals(1.0, summary.getDefault().getTotalAmount());
    }
}
