package scaputo88.com.example.rinha_25.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import scaputo88.com.example.rinha_25.model.ProcessorType;
import scaputo88.com.example.rinha_25.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private ProcessorClient processorClient;

    @Mock
    private HealthCheckService healthCheckService;

    @Mock
    private PaymentQueue paymentQueue;

    @Mock
    private MetricsService metrics;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ReplicationService peerReplicator;

    @Mock
    private ObjectProvider<PaymentRepository> paymentRepositoryProvider;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        // Provider devolve o repository mock
        when(paymentRepositoryProvider.getIfAvailable()).thenReturn(paymentRepository);

        // Instancia o service manualmente
        paymentService = new PaymentService(
                processorClient,
                healthCheckService,
                paymentQueue,
                metrics,
                paymentRepositoryProvider,
                peerReplicator
        );

        doAnswer(inv -> {
            Runnable r = inv.getArgument(0);
            r.run();
            return null; // ajuste se o método submit tiver outro retorno
        }).when(paymentQueue).submit(any());
    }

    @Test
    void deveProcessarPagamentoComSucesso() {
        UUID id = UUID.randomUUID();

        // Escolha determinística: DEFAULT mais rápido que FALLBACK
        when(healthCheckService.getStatus(ProcessorType.DEFAULT))
                .thenReturn(new ProcessorClient.HealthStatus(true, 10));
        when(healthCheckService.getStatus(ProcessorType.FALLBACK))
                .thenReturn(new ProcessorClient.HealthStatus(true, 20));

        when(processorClient.sendPayment(eq("default"), eq(id), eq(BigDecimal.TEN)))
                .thenReturn(true);

        paymentService.processPayment(id, BigDecimal.TEN);

        verify(processorClient, times(1))
                .sendPayment(eq("default"), eq(id), eq(BigDecimal.TEN));
         verify(metrics).recordSuccess(eq(ProcessorType.DEFAULT), eq(BigDecimal.TEN), anyLong());
    }

    @Test
    void deveTentarFallbackQuandoPrimeiroProcessorFalhar() {
        UUID id = UUID.randomUUID();
        BigDecimal amt = BigDecimal.valueOf(50);

        when(healthCheckService.getStatus(ProcessorType.DEFAULT))
                .thenReturn(new ProcessorClient.HealthStatus(true, 10));
        when(healthCheckService.getStatus(ProcessorType.FALLBACK))
                .thenReturn(new ProcessorClient.HealthStatus(true, 20));

        // Primeira tentativa (default) falha, segunda (fallback) sucesso
        when(processorClient.sendPayment(eq("default"), eq(id), eq(amt))).thenReturn(false);
        when(processorClient.sendPayment(eq("fallback"), eq(id), eq(amt))).thenReturn(true);

        paymentService.processPayment(id, amt);

        InOrder inOrder = inOrder(processorClient);
        inOrder.verify(processorClient).sendPayment(eq("default"), eq(id), eq(amt));
        inOrder.verify(processorClient).sendPayment(eq("fallback"), eq(id), eq(amt));
//         verify(metrics).recordFailure(eq(ProcessorType.DEFAULT), anyLong());
//         verify(metrics).recordSuccess(eq(ProcessorType.FALLBACK), eq(amt), anyLong());
    }

    @Test
    void naoDeveChamarFallbackSeNaoEstiverSaudavel() {
        UUID id = UUID.randomUUID();
        BigDecimal amt = BigDecimal.valueOf(30);

        when(healthCheckService.getStatus(ProcessorType.DEFAULT))
                .thenReturn(new ProcessorClient.HealthStatus(true, 10));
        when(healthCheckService.getStatus(ProcessorType.FALLBACK))
                .thenReturn(new ProcessorClient.HealthStatus(false, 999));

        when(processorClient.sendPayment(eq("default"), eq(id), eq(amt))).thenReturn(false);

        paymentService.processPayment(id, amt);

        verify(processorClient, times(1))
                .sendPayment(eq("default"), eq(id), eq(amt));
        verify(processorClient, never())
                .sendPayment(eq("fallback"), any(), any());
         verify(metrics, atLeastOnce()).recordFailure(eq(ProcessorType.DEFAULT), anyLong());
    }
}

