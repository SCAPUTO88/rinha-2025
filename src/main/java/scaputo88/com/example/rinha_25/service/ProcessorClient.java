package scaputo88.com.example.rinha_25.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class ProcessorClient {

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String DEFAULT_URL = "http://payment-processor-default:8080";
    private static final String FALLBACK_URL = "http://payment-processor-fallback:8080";

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
        } catch (Exception e) {
            return false;
        }
    }

    public HealthStatus checkHealth(String processor) {
        String url = (processor.equalsIgnoreCase("default") ? DEFAULT_URL : FALLBACK_URL) + "/payments/service-health";
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<?, ?> json = response.getBody();
                boolean failing = Boolean.TRUE.equals(json.get("failing"));
                int minResponseTime = ((Number) json.get("minResponseTime")).intValue();
                return new HealthStatus(!failing, minResponseTime);
            }
        } catch (Exception ignored) {}
        return new HealthStatus(false, Integer.MAX_VALUE);
    }

    public record HealthStatus(boolean healthy, int minResponseTime) {}
}
