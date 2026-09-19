package com.foodlog.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** logDate is stored as an ISO date string (yyyy-MM-dd) in the app time zone to avoid zone drift. */
@Document("food_logs")
@CompoundIndex(name = "user_date", def = "{'userId': 1, 'logDate': 1}")
public record FoodLog(
        @Id String id,
        String userId,
        Instant loggedAt,
        String logDate,
        LogSource source,
        String rawText,
        List<LogItem> items,
        Nutrients totals) {
}
