package com.foodlog.client.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodlog.client.bedrock.BedrockApi;
import com.foodlog.domain.MealType;
import com.foodlog.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;

class BedrockEntityExtractionClientTest {

    private BedrockApi bedrock;
    private BedrockEntityExtractionClient client;

    @BeforeEach
    void setUp() {
        bedrock = mock(BedrockApi.class);
        client = new BedrockEntityExtractionClient(bedrock);
    }

    private void stub(Document response) {
        when(bedrock.invokeTool(anyString(), anyString(), eq("log_food_items"), any(Document.class)))
                .thenReturn(response);
    }

    private static Document item(String food, double quantity, String unit, String mealType) {
        return Document.mapBuilder()
                .putString("food", food)
                .putDocument("quantity", Document.fromNumber(quantity))
                .putString("unit", unit)
                .putString("mealType", mealType)
                .build();
    }

    @Test
    void parsesStructuredToolOutput() {
        stub(Document.mapBuilder()
                .putDocument("items", Document.listBuilder()
                        .addDocument(item("egg", 2, "piece", "BREAKFAST"))
                        .addDocument(item("rice", 1, "bowls", "weird"))
                        .build())
                .build());

        List<ExtractedFood> items = client.extract("two eggs and rice");

        assertThat(items).containsExactly(
                new ExtractedFood("egg", 2, "piece", MealType.BREAKFAST),
                new ExtractedFood("rice", 1, "bowl", MealType.UNKNOWN));
    }

    @Test
    void sanitisesBadQuantitiesAndBlankFoods() {
        stub(Document.mapBuilder()
                .putDocument("items", Document.listBuilder()
                        .addDocument(item("  ", 1, "g", "LUNCH"))
                        .addDocument(item("bread", -3, "loaf", "LUNCH"))
                        .addDocument(item("water", 999999, "ml", "LUNCH"))
                        .build())
                .build());

        assertThat(client.extract("x")).containsExactly(
                new ExtractedFood("bread", 1, "piece", MealType.LUNCH),
                new ExtractedFood("water", 10_000, "ml", MealType.LUNCH));
    }

    @Test
    void failsWhenItemsArrayIsMissing() {
        stub(Document.mapBuilder().putString("nope", "nope").build());

        assertThatThrownBy(() -> client.extract("x")).isInstanceOf(ApiException.class);
    }
}
