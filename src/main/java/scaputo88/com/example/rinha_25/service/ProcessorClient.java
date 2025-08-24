package scaputo88.com.example.rinha_25.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class ProcessorClient {

    private static final Logger log = LoggerFactory.getLogger(ProcessorClient.class);

    private final RestTemplate restTemplate;
    private final String defaultBaseUrl;
    private final String fallbackBaseUrl;
    private final String adminToken;

    public ProcessorClient() {
        int timeoutMs = envOrInt("PP_TIMEOUT_MS", 250);
        this.restTemplate = buildRestTemplate(timeoutMs);

        this.defaultBaseUrl  = envOr("PAYMENT_PROCESSOR_URL_DEFAULT",  "http://payment-processor-default:8080");
        this.fallbackBaseUrl = envOr("PAYMENT_PROCESSOR_URL_FALLBACK", "http://payment-processor-fallback:8080");
        this.adminToken      = envOr("PP_ADMIN_TOKEN", "123");

        log.info("ProcessorClient inicializado: defaultBaseUrl={}, fallbackBaseUrl={}, timeout={}ms",
                defaultBaseUrl, fallbackBaseUrl, timeoutMs);
    }

    private RestTemplate buildRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }

    private String baseUrl(String processor) {
        return "fallback".equalsIgnoreCase(processor) ? fallbackBaseUrl : defaultBaseUrl;
    }

    public boolean sendPayment(String processor, UUID correlationId, BigDecimal amount) {
        String url = baseUrl(processor) + "/payments";
        Map<String, Object> body = Map.of(
                "correlationId", correlationId,
                "amount", amount,
                "requestedAt", Instant.now().toString()
        );
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(url, body, String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("Falha ao enviar pagamento para {}: {}", processor, e.getMessage());
            return false;
        }
    }

    public HealthStatus checkHealth(String processor) {
        String url = baseUrl(processor) + "/payments/service-health";
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                boolean failing = Boolean.TRUE.equals(resp.getBody().get("failing"));
                int minResponseTime = resp.getBody().get("minResponseTime") != null
                        ? ((Number) resp.getBody().get("minResponseTime")).intValue()
                        : Integer.MAX_VALUE;
                return new HealthStatus(!failing, minResponseTime);
            }
        } catch (RestClientException e) {
            log.warn("Falha ao consultar saúde de {}: {}", processor, e.getMessage());
        }
        return new HealthStatus(false, Integer.MAX_VALUE);
    }

    public AdminSummary getAdminSummary(String processor, String from, String to) {
        StringBuilder uri = new StringBuilder(baseUrl(processor)).append("/admin/payments-summary");
        boolean hasQuery = false;
        if (from != null && !from.isBlank()) {
            uri.append("?from=").append(from);
            hasQuery = true;
        }
        if (to != null && !to.isBlank()) {
            uri.append(hasQuery ? "&" : "?").append("to=").append(to);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Rinha-Token", adminToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(uri.toString(), HttpMethod.GET, entity, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                long totalRequests = resp.getBody().get("totalRequests") != null
                        ? ((Number) resp.getBody().get("totalRequests")).longValue()
                        : 0L;
                BigDecimal totalAmount = resp.getBody().get("totalAmount") != null
                        ? new BigDecimal(String.valueOf(resp.getBody().get("totalAmount")))
                        : BigDecimal.ZERO;
                return new AdminSummary(totalRequests, totalAmount);
            }
        } catch (RestClientException e) {
            log.warn("Falha ao obter resumo admin de {}: {}", processor, e.getMessage());
        }
        return new AdminSummary(0L, BigDecimal.ZERO);
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static int envOrInt(String key, int def) {
        try {
            String v = System.getenv(key);
            return (v == null || v.isBlank()) ? def : Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    public record HealthStatus(boolean healthy, int minResponseTime) {}
    public record AdminSummary(long totalRequests, BigDecimal totalAmount) {}
}
