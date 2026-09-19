package com.foodlog.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodlog.domain.FoodItem;
import com.foodlog.domain.MatchStatus;
import com.foodlog.service.FoodCsvParser;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class FoodMatcherTest {

    private static List<FoodItem> foods;
    private final FoodMatcher matcher = new FoodMatcher(0.85, 0.6, 3);

    @BeforeAll
    static void loadFoods() throws IOException {
        try (InputStream in = new ClassPathResource("data/foods_sample.csv").getInputStream()) {
            foods = FoodCsvParser.parse(in);
        }
    }

    /** Query -> expected food id. Includes plurals, typos, word order, German and Chinese names. */
    private static final Map<String, String> LABELLED = Map.ofEntries(
            Map.entry("apple", "S001"), Map.entry("apples", "S001"), Map.entry("Apfel", "S001"),
            Map.entry("苹果", "S001"), Map.entry("banana", "S002"), Map.entry("bananna", "S002"),
            Map.entry("tomatoes", "S007"), Map.entry("white rice", "S012"), Map.entry("rice", "S012"),
            Map.entry("米饭", "S012"), Map.entry("brown rice", "S013"), Map.entry("spaghetti", "S014"),
            Map.entry("bread", "S015"), Map.entry("whole grain bread", "S016"), Map.entry("Vollkornbrot", "S016"),
            Map.entry("egg", "S018"), Map.entry("boiled egg", "S018"), Map.entry("eggs", "S018"),
            Map.entry("fried egg", "S019"), Map.entry("egg fried", "S019"), Map.entry("milk", "S020"),
            Map.entry("yoghurt", "S021"), Map.entry("swiss cheese", "S022"), Map.entry("grilled chicken", "S024"),
            Map.entry("steak", "S025"), Map.entry("salmon", "S027"), Map.entry("tofu", "S029"),
            Map.entry("peanut butter", "S032"), Map.entry("olive oil", "S033"), Map.entry("dark chocolate", "S034"),
            Map.entry("black coffee", "S035"), Map.entry("orange juice", "S036"), Map.entry("coke", "S037"),
            Map.entry("pizza", "S038"), Map.entry("burger", "S039"), Map.entry("fries", "S040"),
            Map.entry("dumplings", "S041"), Map.entry("sushi", "S045"));

    @Test
    void labelledQueriesResolveToExpectedFood() {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, String> e : LABELLED.entrySet()) {
            MatchOutcome out = matcher.match(e.getKey(), foods);
            String got = out.status() == MatchStatus.MATCHED ? out.best().id() : out.status().name();
            if (!e.getValue().equals(got)) {
                wrong.add(e.getKey() + " -> expected " + e.getValue() + " but got " + got);
            }
        }
        double accuracy = 1.0 - (double) wrong.size() / LABELLED.size();
        System.out.printf("Auto-match accuracy on labelled set: %.1f%% (%d/%d)%n",
                accuracy * 100, LABELLED.size() - wrong.size(), LABELLED.size());
        assertThat(wrong).isEmpty();
    }

    @Test
    void partialMatchAsksForConfirmationWithCandidates() {
        MatchOutcome out = matcher.match("chicken soup", foods);
        assertThat(out.status()).isEqualTo(MatchStatus.NEEDS_CONFIRMATION);
        assertThat(out.candidates()).isNotEmpty();
        assertThat(out.candidates().get(0).foodId()).isEqualTo("S024");
    }

    @Test
    void unknownFoodIsUnmatched() {
        MatchOutcome out = matcher.match("sparkling unicorn", foods);
        assertThat(out.status()).isEqualTo(MatchStatus.UNMATCHED);
    }

    @Test
    void blankQueryIsUnmatched() {
        assertThat(matcher.match("   ", foods).status()).isEqualTo(MatchStatus.UNMATCHED);
    }

    @Test
    void shortUnrelatedWordsAreNotTypoMatches() {
        List<String> price = FoodNameNormalizer.tokens("price");
        List<String> rice = FoodNameNormalizer.tokens("rice");
        assertThat(matcher.score(price, rice)).isLessThan(0.6);
    }
}
