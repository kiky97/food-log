package com.foodlog.dto;

import com.foodlog.domain.FoodCandidate;
import com.foodlog.domain.FoodLog;
import com.foodlog.domain.LogItem;
import com.foodlog.domain.LogSource;
import com.foodlog.domain.MatchStatus;
import com.foodlog.domain.MealType;
import com.foodlog.domain.Nutrients;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

public record LogResponse(
        String id,
        String userId,
        Instant loggedAt,
        String logDate,
        LogSource source,
        String transcript,
        List<Item> items,
        Nutrients totals,
        int needsAttention) {

    public record Item(
            int index,
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

        static Item from(int index, LogItem i) {
            return new Item(index, i.rawFood(), i.quantity(), i.unit(), i.mealType(), i.status(), i.foodId(),
                    i.foodName(), i.confidence(), i.grams(), i.portionEstimated(), i.nutrients(), i.candidates());
        }
    }

    public static LogResponse from(FoodLog log) {
        List<Item> items = IntStream.range(0, log.items().size())
                .mapToObj(i -> Item.from(i, log.items().get(i)))
                .toList();
        int needsAttention = (int) log.items().stream().filter(i -> !i.counted()).count();
        return new LogResponse(log.id(), log.userId(), log.loggedAt(), log.logDate(), log.source(),
                log.rawText(), items, log.totals(), needsAttention);
    }
}
