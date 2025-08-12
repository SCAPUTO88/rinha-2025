package scaputo88.com.example.rinha_25.service;

import io.micrometer.common.util.StringUtils;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import scaputo88.com.example.rinha_25.metrics.ErrorMetrics;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class ProcessorClient {

    private final RestTemplate restTemplate;
    private final ErrorMetrics errorMetrics;

    private final String DEFAULT_URL = envOr("PP_DEFAULT_URL", "http://payment-processor-default:8080");
    private final String FALLBACK_URL = envOr("PP_FALLBACK_URL", "http://payment-processor-fallback:8080");
    private final String ADMIN_TOKEN = envOr("PP_ADMIN_TOKEN", "123");

    public ProcessorClient(ErrorMetrics errorMetrics) {
        this.errorMetrics = errorMetrics;

        int maxTotal = envOrInt("PP_MAX_TOTAL", 200);
        int maxPerRoute = envOrInt("PP_MAX_PER_ROUTE", 100);
        int connectTimeoutMs = envOrInt("PP_CONNECT_TIMEOUT_MS", 250);
        int responseTimeoutMs = envOrInt("PP_RESPONSE_TIMEOUT_MS", 600);

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(maxTotal);
        cm.setDefaultMaxPerRoute(maxPerRoute);

        RequestConfig rc = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(responseTimeoutMs))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(rc)
                .disableAutomaticRetries()
                .build();

        this.restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }

    public boolean sendPayment(String processor, UUID correlationId, BigDecimal amount) {
        Map<String, Object> body = Map.of(
                "correlationId", correlationId,
                "amount", amount,
                "requestedAt", Instant.now().toString()
        );

        String url = (processor.equalsIgnoreCase("default") ? DEFAULT_URL : FALLBACK_URL) + "/payments";

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, body, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (ResourceAccessException e) {
            if (rootCause(e, SocketTimeoutException.class)) {
                errorMetrics.increment(ErrorMetrics.ErrorType.TIMEOUT);
            } else if (rootCause(e, ConnectException.class)) {
                errorMetrics.increment(ErrorMetrics.ErrorType.CONNECTION_REFUSED);
            } else {
                errorMetrics.increment(ErrorMetrics.ErrorType.UNKNOWN);
            }
            return false;
        } catch (HttpServerErrorException e) {
            errorMetrics.increment(ErrorMetrics.ErrorType.SERVER_ERROR);
            return false;
        } catch (RestClientException e) {
            errorMetrics.increment(ErrorMetrics.ErrorType.UNKNOWN);
            return false;
        }
    }

    public HealthStatus checkHealth(String processor) {
        String url = (processor.equalsIgnoreCase("default") ? DEFAULT_URL : FALLBACK_URL) + "/payments/service-health";
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<?, ?> json = response.getBody();
                boolean failing = Boolean.TRUE.equals(json.get("failing"));
                int minResponseTime = ((Number) json.get("minResponseTime")).intValue();
                return new HealthStatus(!failing, minResponseTime);
            }
        } catch (ResourceAccessException e) {
            if (rootCause(e, SocketTimeoutException.class)) {
                errorMetrics.increment(ErrorMetrics.ErrorType.TIMEOUT);
            } else if (rootCause(e, ConnectException.class)) {
                errorMetrics.increment(ErrorMetrics.ErrorType.CONNECTION_REFUSED);
            } else {
                errorMetrics.increment(ErrorMetrics.ErrorType.UNKNOWN);
            }
        } catch (HttpServerErrorException e) {
            errorMetrics.increment(ErrorMetrics.ErrorType.SERVER_ERROR);
        } catch (RestClientException e) {
            errorMetrics.increment(ErrorMetrics.ErrorType.UNKNOWN);
        }
        return new HealthStatus(false, Integer.MAX_VALUE);
    }

    public record HealthStatus(boolean healthy, int minResponseTime) {}

    public record AdminSummary(long totalRequests, BigDecimal totalAmount) {}

    public AdminSummary getAdminSummary(String processor, String from, String to) {
        String base = processor.equalsIgnoreCase("default") ? DEFAULT_URL : FALLBACK_URL;
        StringBuilder url = new StringBuilder(base + "/admin/payments-summary");
        boolean hasQuery = false;
        if (StringUtils.isNotBlank(from)) {
            url.append("?from=").append(from);
            hasQuery = true;
        }
        if (StringUtils.isNotBlank(to)) {
            url.append(hasQuery ? "&" : "?").append("to=").append(to);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Rinha-Token", ADMIN_TOKEN);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url.toString(), HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<?, ?> json = response.getBody();
                long totalRequests = ((Number) json.get("totalRequests")).longValue();
                BigDecimal totalAmount = new BigDecimal(String.valueOf(json.get("totalAmount")));
                return new AdminSummary(totalRequests, totalAmount);
            }
        } catch (ResourceAccessException e) {
            if (rootCause(e, SocketTimeoutException.class)) {
                errorMetrics.increment(ErrorMetrics.ErrorType.TIMEOUT);
            } else if (rootCause(e, ConnectException.class)) {
                errorMetrics.increment(ErrorMetrics.ErrorType.CONNECTION_REFUSED);
            } else {
                errorMetrics.increment(ErrorMetrics.ErrorType.UNKNOWN);
            }
        } catch (HttpServerErrorException e) {
            errorMetrics.increment(ErrorMetrics.ErrorType.SERVER_ERROR);
        } catch (RestClientException e) {
            errorMetrics.increment(ErrorMetrics.ErrorType.UNKNOWN);
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

    private static boolean rootCause(Throwable t, Class<? extends Throwable> target) {
        Throwable cur = t;
        while (cur != null) {
            if (target.isInstance(cur)) return true;
            cur = cur.getCause();
        }
        return false;
    }
}
