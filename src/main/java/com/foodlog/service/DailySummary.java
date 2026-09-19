package com.foodlog.service;

import com.foodlog.domain.MealType;
import com.foodlog.domain.Nutrients;
import java.util.Map;

/** Nutrient totals for one user and day. Items that still need confirmation are counted but not summed. */
public record DailySummary(String userId, String date, Nutrients totals, Map<MealType, Nutrients> byMeal,
                           int itemCount, int unresolvedCount) {
}
