package com.foodlog.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FoodNameNormalizerTest {

    @Test
    void lowercasesAndStripsPunctuation() {
        assertThat(FoodNameNormalizer.tokens("Rice, Brown (cooked)!")).containsExactly("rice", "brown", "cooked");
    }

    @Test
    void stripsAccents() {
        assertThat(FoodNameNormalizer.tokens("Müesli")).containsExactly("muesli");
        assertThat(FoodNameNormalizer.tokens("Olivenöl")).containsExactly("olivenol");
    }

    @Test
    void singularisesConsistently() {
        assertThat(FoodNameNormalizer.tokens("tomatoes")).isEqualTo(FoodNameNormalizer.tokens("Tomato"));
        assertThat(FoodNameNormalizer.tokens("strawberries")).isEqualTo(FoodNameNormalizer.tokens("strawberry"));
        assertThat(FoodNameNormalizer.tokens("cheese")).containsExactly("cheese");
        assertThat(FoodNameNormalizer.tokens("hummus")).containsExactly("hummus");
    }

    @Test
    void dropsStopWordsAndHandlesBlank() {
        assertThat(FoodNameNormalizer.tokens("a bowl of rice")).containsExactly("bowl", "rice");
        assertThat(FoodNameNormalizer.tokens("  ")).isEqualTo(List.of());
        assertThat(FoodNameNormalizer.tokens(null)).isEqualTo(List.of());
    }

    @Test
    void keepsChineseIntact() {
        assertThat(FoodNameNormalizer.tokens("米饭")).containsExactly("米饭");
    }
}
