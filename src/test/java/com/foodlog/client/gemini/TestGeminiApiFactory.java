package com.foodlog.client.gemini;

import com.foodlog.config.AppProperties;
import org.springframework.web.client.RestClient;

/** Lives in the same package as GeminiApi so tests can use its package-private constructor. */
public final class TestGeminiApiFactory {

    private TestGeminiApiFactory() {
    }

    public static GeminiApi create(RestClient client, AppProperties.Gemini cfg) {
        return new GeminiApi(client, cfg);
    }
}
