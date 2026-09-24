package com.foodlog.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String zone, boolean seedSampleData, Match match, Gemini gemini, Bedrock bedrock) {

    public record Match(double autoThreshold, double minThreshold, int maxCandidates) {
    }

    public record Gemini(String apiKey, String baseUrl, String model, Duration connectTimeout,
                         Duration readTimeout, int maxAttempts, Duration retryBackoff) {
    }

    /** Credentials are resolved by the AWS default credential provider chain, never stored here. */
    public record Bedrock(String region, String model, int maxAttempts, Duration retryBackoff) {
    }
}
