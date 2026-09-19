package com.foodlog.service;

import com.foodlog.client.llm.EntityExtractionClient;
import com.foodlog.client.llm.ExtractedFood;
import com.foodlog.client.stt.SpeechToTextClient;
import com.foodlog.domain.FoodItem;
import com.foodlog.domain.FoodLog;
import com.foodlog.domain.LogItem;
import com.foodlog.domain.LogSource;
import com.foodlog.domain.MatchStatus;
import com.foodlog.domain.MealType;
import com.foodlog.domain.Nutrients;
import com.foodlog.exception.ApiException;
import com.foodlog.matching.FoodMatcher;
import com.foodlog.matching.MatchOutcome;
import com.foodlog.portion.PortionConverter;
import com.foodlog.portion.PortionConverter.PortionResult;
import com.foodlog.repository.FoodLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The pipeline: (speech to text) -> entity extraction -> food matching -> portion conversion ->
 * nutrient calculation -> persistence.
 */
@Service
public class FoodLogService {

    private final SpeechToTextClient stt;
    private final EntityExtractionClient extraction;
    private final FoodMatcher matcher;
    private final FoodCatalog catalog;
    private final PortionConverter portions;
    private final FoodLogRepository repository;
    private final ZoneId zone;

    public FoodLogService(SpeechToTextClient stt, EntityExtractionClient extraction, FoodMatcher matcher,
                          FoodCatalog catalog, PortionConverter portions, FoodLogRepository repository,
                          ZoneId zone) {
        this.stt = stt;
        this.extraction = extraction;
        this.matcher = matcher;
        this.catalog = catalog;
        this.portions = portions;
        this.repository = repository;
        this.zone = zone;
    }

    public FoodLog logVoice(String userId, byte[] audio, String mimeType, Instant loggedAt) {
        String transcript = stt.transcribe(audio, mimeType);
        if (transcript == null || transcript.isBlank()) {
            throw ApiException.unprocessable("No speech was detected in the audio");
        }
        return logText(userId, transcript, loggedAt, LogSource.VOICE);
    }

    public FoodLog logText(String userId, String text, Instant loggedAt, LogSource source) {
        Instant at = loggedAt != null ? loggedAt : Instant.now();
        List<ExtractedFood> extracted = extraction.extract(text.trim());
        if (extracted.isEmpty()) {
            throw ApiException.unprocessable("No food or drink was found in: \"" + text.trim() + "\"");
        }
        List<LogItem> items = extracted.stream().map(this::toLogItem).toList();
        FoodLog log = new FoodLog(null, userId, at, at.atZone(zone).toLocalDate().toString(),
                source, text.trim(), items, totalOf(items));
        return repository.save(log);
    }

    /** Resolves an item that needed confirmation (or corrects a wrong match) by picking a food. */
    public FoodLog confirmItem(String logId, int itemIndex, String foodId) {
        FoodLog log = repository.findById(logId)
                .orElseThrow(() -> ApiException.notFound("Log not found: " + logId));
        if (itemIndex < 0 || itemIndex >= log.items().size()) {
            throw ApiException.badRequest("Item index out of range: " + itemIndex);
        }
        FoodItem food = catalog.findById(foodId)
                .orElseThrow(() -> ApiException.notFound("Food not found: " + foodId));

        LogItem old = log.items().get(itemIndex);
        PortionResult portion = portions.toGrams(food, old.quantity(), old.unit());
        LogItem updated = new LogItem(old.rawFood(), old.quantity(), old.unit(), old.mealType(),
                MatchStatus.CONFIRMED, food.id(), food.nameEn(), 1.0, portion.grams(), portion.estimated(),
                nutrientsFor(food, portion.grams()), old.candidates());

        List<LogItem> items = new ArrayList<>(log.items());
        items.set(itemIndex, updated);
        return repository.save(new FoodLog(log.id(), log.userId(), log.loggedAt(), log.logDate(),
                log.source(), log.rawText(), items, totalOf(items)));
    }

    public List<FoodLog> list(String userId, LocalDate date) {
        return repository.findByUserIdAndLogDateOrderByLoggedAtAsc(userId, date.toString());
    }

    public DailySummary summary(String userId, LocalDate date) {
        Map<MealType, Nutrients> byMeal = new EnumMap<>(MealType.class);
        Nutrients total = Nutrients.ZERO;
        int itemCount = 0;
        int unresolved = 0;
        for (FoodLog log : list(userId, date)) {
            for (LogItem item : log.items()) {
                itemCount++;
                if (item.counted()) {
                    total = total.plus(item.nutrients());
                    byMeal.merge(item.mealType(), item.nutrients(), Nutrients::plus);
                } else {
                    unresolved++;
                }
            }
        }
        byMeal.replaceAll((meal, n) -> n.rounded());
        return new DailySummary(userId, date.toString(), total.rounded(), byMeal, itemCount, unresolved);
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    private LogItem toLogItem(ExtractedFood e) {
        MatchOutcome match = matcher.match(e.food(), catalog.all());
        if (match.status() != MatchStatus.MATCHED) {
            return new LogItem(e.food(), e.quantity(), e.unit(), e.mealType(), match.status(), null, null,
                    match.confidence(), null, false, Nutrients.ZERO, match.candidates());
        }
        FoodItem food = match.best();
        PortionResult portion = portions.toGrams(food, e.quantity(), e.unit());
        return new LogItem(e.food(), e.quantity(), e.unit(), e.mealType(), MatchStatus.MATCHED, food.id(),
                food.nameEn(), match.confidence(), portion.grams(), portion.estimated(),
                nutrientsFor(food, portion.grams()), match.candidates());
    }

    private static Nutrients nutrientsFor(FoodItem food, double grams) {
        return food.per100g().scale(grams / 100.0).rounded();
    }

    private static Nutrients totalOf(List<LogItem> items) {
        Nutrients total = Nutrients.ZERO;
        for (LogItem item : items) {
            if (item.counted()) {
                total = total.plus(item.nutrients());
            }
        }
        return total.rounded();
    }
}
