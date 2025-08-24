//package scaputo88.com.example.rinha_25.service;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.http.HttpEntity;
//import org.springframework.http.HttpMethod;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.test.util.ReflectionTestUtils;
//import org.springframework.web.client.HttpServerErrorException;
//import org.springframework.web.client.ResourceAccessException;
//import org.springframework.web.client.RestClientException;
//import org.springframework.web.client.RestTemplate;
//
//import java.math.BigDecimal;
//import java.net.ConnectException;
//import java.net.SocketTimeoutException;
//import java.time.Instant;
//import java.util.Map;
//import java.util.UUID;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class ProcessorClientTest {
//
//    @Mock
//    RestTemplate restTemplate;
//
//    ProcessorClient client;
//
//    @BeforeEach
//    void setUp() {
//        client = new ProcessorClient();
//        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
//    }
//
//
//    @Test
//    void sendPayment_sucesso_2xx() {
//        UUID id = UUID.randomUUID();
//        when(restTemplate.postForEntity(contains("/payments"), any(), eq(String.class)))
//                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));
//
//        boolean ok = client.sendPayment("default", id, BigDecimal.TEN);
//
//        assertTrue(ok);
//    }
//
//    @Test
//    void sendPayment_timeout_incrementa_TIMEOUT() {
//        UUID id = UUID.randomUUID();
//        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
//                .thenThrow(new ResourceAccessException("timeout", new SocketTimeoutException("t")));
//
//        boolean ok = client.sendPayment("fallback", id, BigDecimal.ONE);
//
//        assertFalse(ok);
//    }
//
//    @Test
//    void sendPayment_conexao_recusada_incrementa_CONNECTION_REFUSED() {
//        UUID id = UUID.randomUUID();
//        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
//                .thenThrow(new ResourceAccessException("refused", new ConnectException("ECONNREFUSED")));
//
//        boolean ok = client.sendPayment("default", id, BigDecimal.ONE);
//
//        assertFalse(ok);
//    }
//
//    @Test
//    void sendPayment_http_5xx_incrementa_SERVER_ERROR() {
//        UUID id = UUID.randomUUID();
//        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
//                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "boom"));
//
//        boolean ok = client.sendPayment("default", id, BigDecimal.ONE);
//
//        assertFalse(ok);
//    }
//
//    @Test
//    void sendPayment_erro_desconhecido_incrementa_UNKNOWN() {
//        UUID id = UUID.randomUUID();
//        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
//                .thenThrow(new RestClientException("weird"));
//
//        boolean ok = client.sendPayment("fallback", id, BigDecimal.ONE);
//
//        assertFalse(ok);
//    }
//
//
//    @Test
//    void checkHealth_sucesso_retorna_healthy_true_com_minResponseTime() {
//        Map<String, Object> body = Map.of("failing", false, "minResponseTime", 12);
//        when(restTemplate.getForEntity(contains("/payments/service-health"), eq(Map.class)))
//                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));
//
//        ProcessorClient.HealthStatus hs = client.checkHealth("default");
//
//        assertTrue(hs.healthy());
//        assertEquals(12, hs.minResponseTime());
//    }
//
//    @Test
//    void checkHealth_timeout_incrementa_TIMEOUT_e_retorna_unhealthy() {
//        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
//                .thenThrow(new ResourceAccessException("timeout", new SocketTimeoutException("t")));
//
//        ProcessorClient.HealthStatus hs = client.checkHealth("fallback");
//
//        assertFalse(hs.healthy());
//        assertEquals(Integer.MAX_VALUE, hs.minResponseTime());
//    }
//
//    @Test
//    void checkHealth_http_5xx_incrementa_SERVER_ERROR_e_retorna_unhealthy() {
//        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
//                .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY, "upstream"));
//
//        ProcessorClient.HealthStatus hs = client.checkHealth("default");
//
//        assertFalse(hs.healthy());
//    }
//
//
//    @Test
//    void getAdminSummary_sucesso_2xx() {
//        Map<String, Object> body = Map.of("totalRequests", 3, "totalAmount", "42.50");
//        when(restTemplate.exchange(
//                contains("/admin/payments-summary"),
//                eq(HttpMethod.GET),
//                any(HttpEntity.class),
//                eq(Map.class)))
//                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));
//
//        ProcessorClient.AdminSummary s = client.getAdminSummary("default", Instant.now().toString(), null);
//
//        assertEquals(3L, s.totalRequests());
//        assertEquals(new BigDecimal("42.50"), s.totalAmount());
//    }
//
//    @Test
//    void getAdminSummary_conexao_recusada_incrementa_CONNECTION_REFUSED_e_retorna_zeros() {
//        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
//                .thenThrow(new ResourceAccessException("refused", new ConnectException("ECONNREFUSED")));
//
//        ProcessorClient.AdminSummary s = client.getAdminSummary("fallback", null, null);
//
//        assertEquals(0L, s.totalRequests());
//        assertEquals(BigDecimal.ZERO, s.totalAmount());
//    }
//}
