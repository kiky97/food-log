package com.foodlog.domain;

public enum MatchStatus {
    /** Matched automatically with high confidence. */
    MATCHED,
    /** Plausible candidates exist but the user must pick one. */
    NEEDS_CONFIRMATION,
    /** Nothing in the food database is close enough. */
    UNMATCHED,
    /** User picked the food explicitly. */
    CONFIRMED
}
