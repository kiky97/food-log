package com.foodlog.client.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodlog.domain.MealType;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleBasedEntityExtractionClientTest {

    private final RuleBasedEntityExtractionClient client = new RuleBasedEntityExtractionClient();

    @Test
    void parsesQuantitiesUnitsAndMeal() {
        List<ExtractedFood> items = client.extract("I had two eggs, a bowl of rice and 200g of chicken for breakfast");

        assertThat(items).containsExactly(
                new ExtractedFood("eggs", 2, "piece", MealType.BREAKFAST),
                new ExtractedFood("rice", 1, "bowl", MealType.BREAKFAST),
                new ExtractedFood("chicken", 200, "g", MealType.BREAKFAST));
    }

    @Test
    void defaultsToOnePieceAndUnknownMeal() {
        assertThat(client.extract("I ate an apple")).containsExactly(
                new ExtractedFood("apple", 1, "piece", MealType.UNKNOWN));
    }

    @Test
    void handlesDecimalsAndGlasses() {
        assertThat(client.extract("1.5 glasses of milk")).containsExactly(
                new ExtractedFood("milk", 1.5, "glass", MealType.UNKNOWN));
    }

    @Test
    void returnsNothingForTextWithoutFood() {
        assertThat(client.extract("for lunch")).isEmpty();
    }

    @Test
    void dropsATrailingBareNumberInsteadOfTreatingItAsFood() {
        assertThat(client.extract("I had an apple and then 2")).containsExactly(
                new ExtractedFood("apple", 1, "piece", MealType.UNKNOWN));
    }
}
