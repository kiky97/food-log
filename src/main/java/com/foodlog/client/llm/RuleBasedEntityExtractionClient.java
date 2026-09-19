package com.foodlog.client.llm;

import com.foodlog.domain.MealType;
import com.foodlog.portion.PortionUnits;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Offline stand-in for the LLM so the whole pipeline runs without an API key. It handles simple
 * English sentences such as "I had two eggs and a bowl of rice for breakfast" and is deliberately
 * not smart: use the Gemini client for real input.
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "mock", matchIfMissing = true)
public class RuleBasedEntityExtractionClient implements EntityExtractionClient {

    private static final Pattern MEAL_PHRASE =
            Pattern.compile("\\b(?:for|as)\\s+(?:a\\s+|my\\s+)?(breakfast|lunch|dinner|supper|snack)\\b");
    private static final Pattern MEAL_WORD = Pattern.compile("\\b(breakfast|lunch|dinner|supper|snack)\\b");
    private static final Pattern FILLER = Pattern.compile(
            "\\b(?:i|i'm|i've|just|had|have|ate|eat|eaten|drank|drink|consumed|some|about|also|today|yesterday"
                    + "|this morning|tonight)\\b");
    private static final Pattern SEPARATOR = Pattern.compile(",|;|\\band\\b|\\bplus\\b|\\bthen\\b");
    private static final Pattern DIGIT_LETTER = Pattern.compile("(?<=\\d)(?=[a-z])");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private static final Map<String, Double> NUMBER_WORDS = Map.ofEntries(
            Map.entry("a", 1.0), Map.entry("an", 1.0), Map.entry("one", 1.0), Map.entry("two", 2.0),
            Map.entry("three", 3.0), Map.entry("four", 4.0), Map.entry("five", 5.0), Map.entry("six", 6.0),
            Map.entry("seven", 7.0), Map.entry("eight", 8.0), Map.entry("nine", 9.0), Map.entry("ten", 10.0),
            Map.entry("half", 0.5));

    @Override
    public List<ExtractedFood> extract(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        MealType meal = detectMeal(lower);
        String cleaned = MEAL_PHRASE.matcher(lower).replaceAll(" ");
        cleaned = MEAL_WORD.matcher(cleaned).replaceAll(" ");
        cleaned = FILLER.matcher(cleaned).replaceAll(" ");

        List<ExtractedFood> result = new ArrayList<>();
        for (String segment : SEPARATOR.split(cleaned)) {
            parseSegment(segment, meal).ifPresent(result::add);
        }
        return result;
    }

    private static MealType detectMeal(String lower) {
        Matcher m = MEAL_WORD.matcher(lower);
        return m.find() ? MealType.from(m.group(1)) : MealType.UNKNOWN;
    }

    private java.util.Optional<ExtractedFood> parseSegment(String segment, MealType meal) {
        String s = DIGIT_LETTER.matcher(segment.replaceAll("[^a-z0-9.' ]", " ").trim()).replaceAll(" ");
        if (s.isEmpty()) {
            return java.util.Optional.empty();
        }
        List<String> tokens = new ArrayList<>(List.of(s.split("\\s+")));

        double quantity = 1;
        boolean hadQuantity = false;
        String first = tokens.get(0);
        if (tokens.size() > 1 && (NUMBER.matcher(first).matches() || NUMBER_WORDS.containsKey(first))) {
            quantity = NUMBER.matcher(first).matches() ? Double.parseDouble(first) : NUMBER_WORDS.get(first);
            hadQuantity = true;
            tokens.remove(0);
        }

        String unit = PortionUnits.PIECE;
        if (hadQuantity && tokens.size() > 1) {
            var canonical = PortionUnits.canonical(tokens.get(0));
            if (canonical.isPresent()) {
                unit = canonical.get();
                tokens.remove(0);
                if (!tokens.isEmpty() && tokens.get(0).equals("of")) {
                    tokens.remove(0);
                }
            }
        }

        String food = String.join(" ", tokens).trim();
        if (food.isEmpty() || quantity <= 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new ExtractedFood(food, quantity, unit, meal));
    }
}
