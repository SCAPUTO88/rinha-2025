package scaputo88.com.example.rinha_25.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import scaputo88.com.example.rinha_25.metrics.ErrorMetrics;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class ProcessorClient {

    private RestTemplate restTemplate;
    private final ErrorMetrics errorMetrics;
    private static final Logger log = LoggerFactory.getLogger(ProcessorClient.class);

    private final String defaultBaseUrl;
    private final String fallbackBaseUrl;
    private final String ADMIN_TOKEN = envOr("PP_ADMIN_TOKEN", "123");

    public ProcessorClient(ErrorMetrics errorMetrics) {
        this.errorMetrics = errorMetrics;

        int timeoutMs = envOrInt("PP_TIMEOUT_MS", 600);
        this.restTemplate = buildRestTemplate(timeoutMs);

        this.defaultBaseUrl = envOr("PP_DEFAULT_URL", "http://payment-processor-default:8080");
        this.fallbackBaseUrl = envOr("PP_FALLBACK_URL", "http://payment-processor-fallback:8080");
    }

    private RestTemplate buildRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }

    private String baseUrl(String processor) {
        return processor.equalsIgnoreCase("default") ? defaultBaseUrl : fallbackBaseUrl;
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
            log.info("[ProcessorClient] POST /payments status {}", resp.getStatusCode());
            return resp.getStatusCode().is2xxSuccessful();
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                errorMetrics.increment(ErrorMetrics.ErrorType.TIMEOUT);
            } else if (e.getCause() instanceof ConnectException) {
                errorMetrics.increment(ErrorMetrics.ErrorType.CONNECTION_REFUSED);
            } else {
                errorMetrics.increment(ErrorMetrics.ErrorType.UNKNOWN);
            }
            log.warn("[ProcessorClient] ERRO POST /payments: {}", e.getMessage());
            return false;
        } catch (HttpServerErrorException e) {
            errorMetrics.increment(ErrorMetrics.ErrorType.SERVER_ERROR);
            log.warn("[ProcessorClient] 5xx POST /payments: {}", e.getStatusCode());
            return false;
        } catch (RestClientException e) {
            errorMetrics.increment(ErrorMetrics.ErrorType.UNKNOWN);
            log.warn("[ProcessorClient] ERRO POST /payments: {}", e.getMessage());
            return false;
        }
    }

    public HealthStatus checkHealth(String processor) {
        String url = baseUrl(processor) + "/payments/service-health";
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> json = resp.getBody();
            boolean failing = json != null && Boolean.TRUE.equals(json.get("failing"));
            int minResponseTime = json != null && json.get("minResponseTime") != null
                    ? ((Number) json.get("minResponseTime")).intValue()
                    : Integer.MAX_VALUE;
            return new HealthStatus(!failing, minResponseTime);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                errorMetrics.increment(ErrorMetrics.ErrorType.TIMEOUT);
            } else if (e.getCause() instanceof ConnectException) {
                errorMetrics.increment(ErrorMetrics.ErrorType.CONNECTION_REFUSED);
            } else {
                errorMetrics.increment(ErrorMetrics.ErrorType.UNKNOWN);
            }
            log.warn("[ProcessorClient] ERRO /service-health: {}", e.getMessage());
            return new HealthStatus(false, Integer.MAX_VALUE);
        } catch (HttpServerErrorException e) {
            errorMetrics.increment(ErrorMetrics.ErrorType.SERVER_ERROR);
            log.warn("[ProcessorClient] 5xx /service-health: {}", e.getStatusCode());
            return new HealthStatus(false, Integer.MAX_VALUE);
        } catch (RestClientException e) {
            errorMetrics.increment(ErrorMetrics.ErrorType.UNKNOWN);
            log.warn("[ProcessorClient] ERRO /service-health: {}", e.getMessage());
            return new HealthStatus(false, Integer.MAX_VALUE);
        }
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
        headers.set("X-Rinha-Token", ADMIN_TOKEN);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(uri.toString(), HttpMethod.GET, entity, Map.class);
            Map<String, Object> json = resp.getBody();
            long totalRequests = json != null && json.get("totalRequests") != null
                    ? ((Number) json.get("totalRequests")).longValue()
                    : 0L;
            BigDecimal totalAmount = json != null && json.get("totalAmount") != null
                    ? new BigDecimal(String.valueOf(json.get("totalAmount")))
                    : BigDecimal.ZERO;
            return new AdminSummary(totalRequests, totalAmount);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                errorMetrics.increment(ErrorMetrics.ErrorType.TIMEOUT);
            } else if (e.getCause() instanceof ConnectException) {
                errorMetrics.increment(ErrorMetrics.ErrorType.CONNECTION_REFUSED);
            } else {
                errorMetrics.increment(ErrorMetrics.ErrorType.UNKNOWN);
            }
            log.warn("[ProcessorClient] ERRO /admin/payments-summary: {}", e.getMessage());
            return new AdminSummary(0L, BigDecimal.ZERO);
        } catch (HttpServerErrorException e) {
            errorMetrics.increment(ErrorMetrics.ErrorType.SERVER_ERROR);
            log.warn("[ProcessorClient] 5xx /admin/payments-summary: {}", e.getStatusCode());
            return new AdminSummary(0L, BigDecimal.ZERO);
        } catch (RestClientException e) {
            errorMetrics.increment(ErrorMetrics.ErrorType.UNKNOWN);
            log.warn("[ProcessorClient] ERRO /admin/payments-summary: {}", e.getMessage());
            return new AdminSummary(0L, BigDecimal.ZERO);
        }
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
