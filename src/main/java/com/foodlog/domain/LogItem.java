package com.foodlog.domain;

import java.util.List;

/** One food mention from a log entry, after extraction, matching and portion conversion. */
public record LogItem(
        String rawFood,
        double quantity,
        String unit,
        MealType mealType,
        MatchStatus status,
        String foodId,
        String foodName,
        double confidence,
        Double grams,
        boolean portionEstimated,
        Nutrients nutrients,
        List<FoodCandidate> candidates) {

    /** Only resolved items contribute to nutrient totals. */
    public boolean counted() {
        return status == MatchStatus.MATCHED || status == MatchStatus.CONFIRMED;
    }
}
