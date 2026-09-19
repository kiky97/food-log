package com.foodlog.matching;

import com.foodlog.domain.FoodCandidate;
import com.foodlog.domain.FoodItem;
import com.foodlog.domain.MatchStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;

/**
 * Fuzzy food-name matcher. A query is compared against every name and alias of a food and the
 * best score wins. Scoring is token based (so word order does not matter) with typo tolerance
 * on individual tokens:
 *
 * <pre>
 *   score = 0.7 * coverage(query tokens found in candidate) + 0.3 * precision(candidate tokens found in query)
 * </pre>
 *
 * A score above {@code autoThreshold} is accepted automatically, between {@code minThreshold} and
 * {@code autoThreshold} the user is asked to confirm, below that the food is reported as unmatched.
 * A near-tie between two different foods always requires confirmation.
 */
public class FoodMatcher {

    private static final double FUZZY_TOKEN_MIN = 0.9;
    private static final double CJK_CONTAINS_SCORE = 0.85;
    private static final double SUGGESTION_MIN = 0.4;
    private static final double TIE_MARGIN = 0.03;

    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();
    private final double autoThreshold;
    private final double minThreshold;
    private final int maxCandidates;

    public FoodMatcher(double autoThreshold, double minThreshold, int maxCandidates) {
        this.autoThreshold = autoThreshold;
        this.minThreshold = minThreshold;
        this.maxCandidates = maxCandidates;
    }

    public MatchOutcome match(String query, Collection<FoodItem> foods) {
        List<ScoredFood> ranked = rank(query, foods);
        if (ranked.isEmpty()) {
            return new MatchOutcome(MatchStatus.UNMATCHED, null, 0, List.of());
        }
        ScoredFood best = ranked.get(0);
        List<FoodCandidate> candidates = ranked.stream()
                .filter(s -> s.score >= SUGGESTION_MIN)
                .limit(maxCandidates)
                .map(s -> new FoodCandidate(s.food.id(), s.food.nameEn(), round(s.score)))
                .toList();

        if (best.score < minThreshold) {
            return new MatchOutcome(MatchStatus.UNMATCHED, null, round(best.score), candidates);
        }
        boolean nearTie = ranked.size() > 1
                && best.score < 1.0
                && best.score - ranked.get(1).score < TIE_MARGIN;
        if (best.score >= autoThreshold && !nearTie) {
            return new MatchOutcome(MatchStatus.MATCHED, best.food, round(best.score), candidates);
        }
        return new MatchOutcome(MatchStatus.NEEDS_CONFIRMATION, null, round(best.score), candidates);
    }

    /** All foods with a non-zero score, best first. */
    public List<ScoredFood> rank(String query, Collection<FoodItem> foods) {
        List<String> q = FoodNameNormalizer.tokens(query);
        if (q.isEmpty()) {
            return List.of();
        }
        List<ScoredFood> scored = new ArrayList<>();
        for (FoodItem food : foods) {
            double best = 0;
            for (String name : food.allNames()) {
                best = Math.max(best, score(q, FoodNameNormalizer.tokens(name)));
            }
            if (best > 0) {
                scored.add(new ScoredFood(food, best));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredFood::score).reversed()
                .thenComparing(s -> s.food.nameEn() == null ? 0 : s.food.nameEn().length())
                .thenComparing(s -> s.food.id()));
        return scored;
    }

    double score(List<String> query, List<String> candidate) {
        if (query.isEmpty() || candidate.isEmpty()) {
            return 0;
        }
        if (new HashSet<>(query).equals(new HashSet<>(candidate))) {
            return 1.0;
        }
        double coverage = bestSimilaritySum(query, candidate) / query.size();
        double precision = bestSimilaritySum(candidate, query) / candidate.size();
        return 0.7 * coverage + 0.3 * precision;
    }

    private double bestSimilaritySum(List<String> from, List<String> against) {
        double sum = 0;
        for (String a : from) {
            double best = 0;
            for (String b : against) {
                best = Math.max(best, tokenSimilarity(a, b));
            }
            sum += best;
        }
        return sum;
    }

    private double tokenSimilarity(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }
        if (FoodNameNormalizer.containsCjk(a) || FoodNameNormalizer.containsCjk(b)) {
            boolean contains = a.contains(b) || b.contains(a);
            return contains && Math.min(a.length(), b.length()) >= 2 ? CJK_CONTAINS_SCORE : 0;
        }
        // Typo tolerance only for reasonably long tokens that start with the same letter,
        // otherwise unrelated short words like "rice" and "price" would count as a match.
        if (Math.min(a.length(), b.length()) >= 4 && a.charAt(0) == b.charAt(0)) {
            double jw = jaroWinkler.apply(a, b);
            return jw >= FUZZY_TOKEN_MIN ? jw : 0;
        }
        return 0;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public record ScoredFood(FoodItem food, double score) {
    }
}
