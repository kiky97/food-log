package com.foodlog.matching;

import com.foodlog.domain.FoodCandidate;
import com.foodlog.domain.FoodItem;
import com.foodlog.domain.MatchStatus;
import java.util.List;

/** {@code best} is only set when the status is MATCHED; otherwise the caller works from candidates. */
public record MatchOutcome(MatchStatus status, FoodItem best, double confidence, List<FoodCandidate> candidates) {
}
