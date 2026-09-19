package com.foodlog.portion;

import com.foodlog.domain.FoodItem;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Converts "quantity + unit" into grams. Weight and volume units are exact (volumes assume a
 * density of 1 g/ml). Count-like units use the food's own portion weights when the database
 * has them; otherwise a generic default is used and the result is flagged as estimated.
 */
@Component
public class PortionConverter {

    private static final Map<String, Double> FIXED_GRAMS = Map.of(
            "g", 1.0, "kg", 1000.0, "oz", 28.35, "ml", 1.0, "l", 1000.0, "tbsp", 15.0, "tsp", 5.0);

    private static final Map<String, Double> GENERIC_GRAMS = Map.of(
            "cup", 240.0, "glass", 200.0, "bowl", 250.0, "plate", 300.0, "serving", 100.0,
            "handful", 30.0, "can", 330.0, "slice", 30.0, PortionUnits.PIECE, 100.0);

    public record PortionResult(double grams, boolean estimated) {
    }

    public PortionResult toGrams(FoodItem food, double quantity, String unit) {
        String u = PortionUnits.canonical(unit).orElse(PortionUnits.PIECE);
        Double fixed = FIXED_GRAMS.get(u);
        if (fixed != null) {
            return new PortionResult(quantity * fixed, false);
        }
        return food.portion(u)
                .map(g -> new PortionResult(quantity * g, false))
                .orElseGet(() -> new PortionResult(quantity * GENERIC_GRAMS.getOrDefault(u, 100.0), true));
    }
}
