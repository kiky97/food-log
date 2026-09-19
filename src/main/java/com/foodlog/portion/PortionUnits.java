package com.foodlog.portion;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Canonical portion units and the spellings (English and Chinese) that map to them. */
public final class PortionUnits {

    public static final String PIECE = "piece";

    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("g", "g"), Map.entry("gram", "g"), Map.entry("grams", "g"), Map.entry("gr", "g"),
            Map.entry("克", "g"),
            Map.entry("kg", "kg"), Map.entry("kilogram", "kg"), Map.entry("kilograms", "kg"),
            Map.entry("公斤", "kg"), Map.entry("千克", "kg"),
            Map.entry("oz", "oz"), Map.entry("ounce", "oz"), Map.entry("ounces", "oz"),
            Map.entry("ml", "ml"), Map.entry("milliliter", "ml"), Map.entry("milliliters", "ml"),
            Map.entry("millilitre", "ml"), Map.entry("millilitres", "ml"), Map.entry("毫升", "ml"),
            Map.entry("l", "l"), Map.entry("liter", "l"), Map.entry("liters", "l"),
            Map.entry("litre", "l"), Map.entry("litres", "l"), Map.entry("升", "l"),
            Map.entry("cup", "cup"), Map.entry("cups", "cup"), Map.entry("杯", "cup"),
            Map.entry("glass", "glass"), Map.entry("glasses", "glass"),
            Map.entry("tbsp", "tbsp"), Map.entry("tablespoon", "tbsp"), Map.entry("tablespoons", "tbsp"),
            Map.entry("tsp", "tsp"), Map.entry("teaspoon", "tsp"), Map.entry("teaspoons", "tsp"),
            Map.entry("slice", "slice"), Map.entry("slices", "slice"), Map.entry("片", "slice"),
            Map.entry("piece", PIECE), Map.entry("pieces", PIECE), Map.entry("pc", PIECE),
            Map.entry("pcs", PIECE), Map.entry("unit", PIECE), Map.entry("units", PIECE),
            Map.entry("个", PIECE), Map.entry("只", PIECE),
            Map.entry("bowl", "bowl"), Map.entry("bowls", "bowl"), Map.entry("碗", "bowl"),
            Map.entry("plate", "plate"), Map.entry("plates", "plate"), Map.entry("盘", "plate"),
            Map.entry("serving", "serving"), Map.entry("servings", "serving"),
            Map.entry("portion", "serving"), Map.entry("portions", "serving"), Map.entry("份", "serving"),
            Map.entry("handful", "handful"), Map.entry("handfuls", "handful"),
            Map.entry("can", "can"), Map.entry("cans", "can"), Map.entry("罐", "can"));

    private PortionUnits() {
    }

    public static Optional<String> canonical(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(SYNONYMS.get(raw.trim().toLowerCase(Locale.ROOT)));
    }
}
