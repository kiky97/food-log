package com.foodlog.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One entry of the food composition database. Nutrient values are per 100 g;
 * {@code portionGrams} holds food-specific weights for units such as "piece" or "cup".
 */
@Document("foods")
public record FoodItem(
        @Id String id,
        String nameEn,
        String nameDe,
        String nameZh,
        List<String> aliases,
        String category,
        Nutrients per100g,
        Map<String, Double> portionGrams) {

    public List<String> allNames() {
        List<String> names = new ArrayList<>();
        addIfPresent(names, nameEn);
        addIfPresent(names, nameDe);
        addIfPresent(names, nameZh);
        if (aliases != null) {
            aliases.forEach(a -> addIfPresent(names, a));
        }
        return names;
    }

    public Optional<Double> portion(String unit) {
        return portionGrams == null ? Optional.empty() : Optional.ofNullable(portionGrams.get(unit));
    }

    private static void addIfPresent(List<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value);
        }
    }
}
