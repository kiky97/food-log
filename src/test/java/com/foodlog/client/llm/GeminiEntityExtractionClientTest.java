package com.foodlog.client.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlog.client.gemini.GeminiApi;
import com.foodlog.client.gemini.TestGeminiApiFactory;
import com.foodlog.config.AppProperties;
import com.foodlog.domain.MealType;
import com.foodlog.exception.ApiException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiEntityExtractionClientTest {

    private static final String URL = "http://gemini.test/v1beta/models/test-model:generateContent";

    private MockRestServiceServer server;
    private GeminiEntityExtractionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test");
        server = MockRestServiceServer.bindTo(builder).build();
        AppProperties.Gemini cfg = new AppProperties.Gemini("test-key", "http://gemini.test", "test-model",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 3, Duration.ofMillis(1));
        client = new GeminiEntityExtractionClient(newApi(builder, cfg), new ObjectMapper());
    }

    private static GeminiApi newApi(RestClient.Builder builder, AppProperties.Gemini cfg) {
        // GeminiApi's test constructor is package-private, hence the factory in its package
        return TestGeminiApiFactory.create(builder.build(), cfg);
    }

    private static String envelope(String innerJson) {
        String escaped = innerJson.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + escaped + "\"}]}}]}";
    }

    @Test
    void parsesStructuredOutputAndSendsSchemaAndKeyHeader() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.type").value("OBJECT"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("two eggs and rice"))
                .andRespond(withSuccess(envelope("""
                        {"items":[{"food":"egg","quantity":2,"unit":"piece","mealType":"BREAKFAST"},
                                  {"food":"rice","quantity":1,"unit":"bowls","mealType":"weird"}]}"""),
                        MediaType.APPLICATION_JSON));

        List<ExtractedFood> items = client.extract("two eggs and rice");

        assertThat(items).containsExactly(
                new ExtractedFood("egg", 2, "piece", MealType.BREAKFAST),
                new ExtractedFood("rice", 1, "bowl", MealType.UNKNOWN));
        server.verify();
    }

    @Test
    void sanitisesBadQuantitiesAndBlankFoods() {
        server.expect(requestTo(URL)).andRespond(withSuccess(envelope("""
                {"items":[{"food":"  ","quantity":1,"unit":"g","mealType":"LUNCH"},
                          {"food":"bread","quantity":-3,"unit":"loaf","mealType":"LUNCH"},
                          {"food":"water","quantity":999999,"unit":"ml","mealType":"LUNCH"}]}"""),
                MediaType.APPLICATION_JSON));

        assertThat(client.extract("x")).containsExactly(
                new ExtractedFood("bread", 1, "piece", MealType.LUNCH),
                new ExtractedFood("water", 10_000, "ml", MealType.LUNCH));
    }

    @Test
    void retriesOnRateLimitAndServerError() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL)).andRespond(withServerError());
        server.expect(requestTo(URL)).andRespond(withSuccess(
                envelope("{\"items\":[{\"food\":\"apple\",\"quantity\":1,\"unit\":\"piece\",\"mealType\":\"SNACK\"}]}"),
                MediaType.APPLICATION_JSON));

        assertThat(client.extract("an apple")).hasSize(1);
        server.verify();
    }

    @Test
    void doesNotRetryClientErrors() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.extract("an apple"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.verify();
    }

    @Test
    void retriesOnceWhenOutputIsNotValidJson() {
        server.expect(requestTo(URL)).andRespond(withSuccess(envelope("not json at all"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(
                envelope("{\"items\":[]}"), MediaType.APPLICATION_JSON));

        assertThat(client.extract("nothing")).isEmpty();
        server.verify();
    }

    @Test
    void failsWhenOutputStaysMalformed() {
        server.expect(requestTo(URL)).andRespond(withSuccess(envelope("oops"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(envelope("oops"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.extract("x")).isInstanceOf(ApiException.class);
    }
}
