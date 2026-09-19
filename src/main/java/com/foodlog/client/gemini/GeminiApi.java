package com.foodlog.client.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodlog.config.AppProperties;
import com.foodlog.exception.ApiException;
import com.foodlog.util.Retry;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin wrapper over the Gemini {@code generateContent} REST endpoint (Google AI Studio keys).
 * Adds timeouts and retries on 429 / 5xx / network errors. The API key is sent in a header,
 * never in the URL, and is never logged.
 */
@Component
public class GeminiApi {

    private static final Logger log = LoggerFactory.getLogger(GeminiApi.class);

    private final RestClient client;
    private final AppProperties.Gemini cfg;

    @Autowired
    public GeminiApi(AppProperties props) {
        this(buildClient(props.gemini()), props.gemini());
    }

    GeminiApi(RestClient client, AppProperties.Gemini cfg) {
        this.client = client;
        this.cfg = cfg;
    }

    private static RestClient buildClient(AppProperties.Gemini cfg) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(cfg.connectTimeout());
        factory.setReadTimeout(cfg.readTimeout());
        return RestClient.builder().baseUrl(cfg.baseUrl()).requestFactory(factory).build();
    }

    /** Sends the request body and returns the concatenated text of the first candidate. */
    public String generateText(Map<String, Object> body) {
        if (cfg.apiKey() == null || cfg.apiKey().isBlank()) {
            throw ApiException.unavailable("GEMINI_API_KEY is not configured");
        }
        JsonNode response;
        try {
            response = Retry.execute(cfg.maxAttempts(), cfg.retryBackoff(), () -> client.post()
                            .uri("/v1beta/models/{model}:generateContent", cfg.model())
                            .header("x-goog-api-key", cfg.apiKey())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(JsonNode.class),
                    GeminiApi::isRetryable);
        } catch (RestClientResponseException e) {
            log.warn("Gemini API returned HTTP {}", e.getStatusCode().value());
            throw ApiException.badGateway("Gemini API returned HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            log.warn("Gemini API call failed: {}", e.getMessage());
            throw ApiException.badGateway("Gemini API is unreachable (timeout or network error)");
        }
        return extractText(response);
    }

    private static String extractText(JsonNode response) {
        StringBuilder text = new StringBuilder();
        if (response != null) {
            for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
                text.append(part.path("text").asText(""));
            }
        }
        if (text.isEmpty()) {
            String blockReason = response == null ? "" : response.path("promptFeedback").path("blockReason").asText("");
            throw ApiException.badGateway("Gemini returned no content"
                    + (blockReason.isEmpty() ? "" : " (blocked: " + blockReason + ")"));
        }
        return text.toString();
    }

    private static boolean isRetryable(RuntimeException e) {
        if (e instanceof ResourceAccessException) {
            return true;
        }
        if (e instanceof RestClientResponseException r) {
            int status = r.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return false;
    }
}
