package com.foodlog.client.llm;

import com.foodlog.domain.MealType;

/** A food mention pulled out of free text: canonical English name, amount, canonical unit, meal. */
public record ExtractedFood(String food, double quantity, String unit, MealType mealType) {
}
