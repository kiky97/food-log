package com.foodlog.domain;

import java.util.Locale;

public enum MealType {
    BREAKFAST, LUNCH, DINNER, SNACK, UNKNOWN;

    /** Tolerant parse for LLM output; anything unrecognised becomes UNKNOWN. */
    public static MealType from(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.equals("SUPPER")) {
            return DINNER;
        }
        for (MealType t : values()) {
            if (t.name().equals(s)) {
                return t;
            }
        }
        return UNKNOWN;
    }
}
