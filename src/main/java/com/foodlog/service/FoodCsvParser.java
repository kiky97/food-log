package com.foodlog.service;

import com.foodlog.domain.FoodItem;
import com.foodlog.domain.Nutrients;
import com.foodlog.exception.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Parses a food composition CSV (UTF-8, header row). Required columns: id, name_en, kcal.
 * All nutrient columns are per 100 g; aliases are separated with ';'. The Swiss Food Composition
 * Database export has to be reshaped into this layout first (see README).
 */
public final class FoodCsvParser {

    private static final Map<String, String> PORTION_COLUMNS = Map.of(
            "piece_g", "piece", "slice_g", "slice", "cup_g", "cup", "bowl_g", "bowl", "serving_g", "serving");

    private FoodCsvParser() {
    }

    public static List<FoodItem> parse(InputStream in) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true).build();
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            for (String required : List.of("id", "name_en", "kcal")) {
                if (!parser.getHeaderMap().containsKey(required)) {
                    throw ApiException.badRequest("CSV is missing required column: " + required);
                }
            }
            List<FoodItem> foods = new ArrayList<>();
            for (CSVRecord r : parser) {
                foods.add(toFood(r));
            }
            return foods;
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read CSV: " + e.getMessage());
        }
    }

    private static FoodItem toFood(CSVRecord r) {
        long line = r.getRecordNumber() + 1;
        String id = text(r, "id");
        String nameEn = text(r, "name_en");
        if (id.isEmpty() || nameEn.isEmpty()) {
            throw ApiException.badRequest("CSV row " + line + ": id and name_en must not be empty");
        }
        Nutrients per100g = new Nutrients(
                number(r, "kcal", line), number(r, "protein_g", line), number(r, "fat_g", line),
                number(r, "carb_g", line), number(r, "fiber_g", line), number(r, "sugar_g", line),
                number(r, "salt_g", line));

        Map<String, Double> portions = new LinkedHashMap<>();
        PORTION_COLUMNS.forEach((column, unit) -> {
            double grams = number(r, column, line);
            if (grams > 0) {
                portions.put(unit, grams);
            }
        });

        List<String> aliases = text(r, "aliases").isEmpty() ? List.of()
                : Arrays.stream(text(r, "aliases").split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList();

        return new FoodItem(id, nameEn, text(r, "name_de"), text(r, "name_zh"), aliases,
                text(r, "category"), per100g, portions);
    }

    private static String text(CSVRecord r, String column) {
        return r.isMapped(column) && r.isSet(column) ? r.get(column).trim() : "";
    }

    private static double number(CSVRecord r, String column, long line) {
        String v = text(r, column);
        if (v.isEmpty()) {
            return 0;
        }
        try {
            double d = Double.parseDouble(v);
            if (d < 0 || !Double.isFinite(d)) {
                throw new NumberFormatException();
            }
            return d;
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("CSV row " + line + ": invalid value '" + v + "' in column " + column);
        }
    }
}
