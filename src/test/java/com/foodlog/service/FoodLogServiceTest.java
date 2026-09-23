package com.foodlog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodlog.client.llm.RuleBasedEntityExtractionClient;
import com.foodlog.client.stt.AudioMimeTypes;
import com.foodlog.client.stt.MockSpeechToTextClient;
import com.foodlog.domain.FoodItem;
import com.foodlog.domain.FoodLog;
import com.foodlog.domain.LogItem;
import com.foodlog.domain.LogSource;
import com.foodlog.domain.MatchStatus;
import com.foodlog.domain.MealType;
import com.foodlog.domain.Nutrients;
import com.foodlog.exception.ApiException;
import com.foodlog.matching.FoodMatcher;
import com.foodlog.portion.PortionConverter;
import com.foodlog.repository.FoodItemRepository;
import com.foodlog.repository.FoodLogRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the whole logVoice/logText -> match -> confirm -> summary pipeline with fakes for
 * both repositories, so it runs everywhere (unlike {@code FoodLogApiIntegrationTest}, this needs
 * neither Docker nor a real Mongo instance).
 */
class FoodLogServiceTest {

    private static final FoodItem APPLE = new FoodItem("S001", "Apple", null, null, List.of("apples"), "Fruit",
            new Nutrients(52, 0.3, 0.2, 13.8, 2.4, 10.4, 0), Map.of("piece", 180.0));
    private static final FoodItem CHICKEN = new FoodItem("S024", "Chicken breast cooked", null, null,
            List.of("chicken", "chicken breast", "grilled chicken"), "Meat",
            new Nutrients(165, 31, 3.6, 0, 0, 0, 0.1), Map.of("piece", 120.0));

    private FoodLogService service;
    private FoodLogRepository logRepository;

    @BeforeEach
    void setUp() {
        FoodItemRepository foodRepository = mock(FoodItemRepository.class);
        when(foodRepository.findAll()).thenReturn(List.of(APPLE, CHICKEN));
        FoodCatalog catalog = new FoodCatalog(foodRepository);
        catalog.reload();

        Map<String, FoodLog> store = new LinkedHashMap<>();
        AtomicInteger ids = new AtomicInteger();
        logRepository = mock(FoodLogRepository.class);
        when(logRepository.save(any(FoodLog.class))).thenAnswer(inv -> {
            FoodLog log = inv.getArgument(0);
            String id = log.id() != null ? log.id() : "log-" + ids.incrementAndGet();
            FoodLog saved = new FoodLog(id, log.userId(), log.loggedAt(), log.logDate(), log.source(),
                    log.rawText(), log.items(), log.totals());
            store.put(id, saved);
            return saved;
        });
        when(logRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
        when(logRepository.findByUserIdAndLogDateOrderByLoggedAtAsc(anyString(), anyString())).thenAnswer(inv ->
                store.values().stream()
                        .filter(l -> l.userId().equals(inv.getArgument(0)) && l.logDate().equals(inv.getArgument(1)))
                        .sorted(Comparator.comparing(FoodLog::loggedAt))
                        .toList());

        service = new FoodLogService(new MockSpeechToTextClient(), new RuleBasedEntityExtractionClient(),
                new FoodMatcher(0.85, 0.6, 3), catalog, new PortionConverter(), logRepository,
                ZoneId.of("Europe/Zurich"));
    }

    @Test
    void logTextMatchesConvertsPortionsAndTotals() {
        FoodLog log = service.logText("u1", "I ate an apple", Instant.parse("2026-01-01T08:00:00Z"), LogSource.TEXT);

        assertThat(log.items()).hasSize(1);
        LogItem item = log.items().get(0);
        assertThat(item.status()).isEqualTo(MatchStatus.MATCHED);
        assertThat(item.foodId()).isEqualTo("S001");
        assertThat(item.grams()).isEqualTo(180.0);
        assertThat(log.totals().kcal()).isEqualTo(93.6);
    }

    @Test
    void logTextWithNoFoodIsRejected() {
        assertThatThrownBy(() -> service.logText("u1", "for lunch", null, LogSource.TEXT))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void confirmItemResolvesMatchAndUpdatesTotals() {
        FoodLog log = service.logText("u2", "chicken soup", null, LogSource.TEXT);
        assertThat(log.items().get(0).status()).isEqualTo(MatchStatus.NEEDS_CONFIRMATION);
        assertThat(log.totals().kcal()).isEqualTo(0.0);

        FoodLog confirmed = service.confirmItem(log.id(), 0, "S024");

        assertThat(confirmed.items().get(0).status()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(confirmed.items().get(0).grams()).isEqualTo(120.0);
        assertThat(confirmed.totals().kcal()).isEqualTo(198.0);
    }

    @Test
    void confirmItemRejectsUnknownLogOrIndexOrFood() {
        FoodLog log = service.logText("u2", "chicken soup", null, LogSource.TEXT);

        assertThatThrownBy(() -> service.confirmItem("missing", 0, "S024")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.confirmItem(log.id(), 5, "S024")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.confirmItem(log.id(), 0, "unknown")).isInstanceOf(ApiException.class);
    }

    @Test
    void summaryAggregatesByMealAndCountsUnresolvedSeparately() {
        service.logText("u3", "an apple for breakfast", Instant.parse("2026-01-01T07:00:00Z"), LogSource.TEXT);
        service.logText("u3", "mystery snack", Instant.parse("2026-01-01T09:00:00Z"), LogSource.TEXT);

        DailySummary summary = service.summary("u3", LocalDate.parse("2026-01-01"));

        assertThat(summary.itemCount()).isEqualTo(2);
        assertThat(summary.unresolvedCount()).isEqualTo(1);
        assertThat(summary.totals().kcal()).isEqualTo(93.6);
        assertThat(summary.byMeal().get(MealType.BREAKFAST).kcal()).isEqualTo(93.6);
        assertThat(summary.byMeal()).doesNotContainKey(MealType.SNACK);
    }

    @Test
    void logVoiceTranscribesThenRunsTheSamePipelineAsText() {
        FoodLog log = service.logVoice("u4", "an apple".getBytes(StandardCharsets.UTF_8),
                AudioMimeTypes.TEXT_PLAIN, null);

        assertThat(log.source()).isEqualTo(LogSource.VOICE);
        assertThat(log.items().get(0).foodId()).isEqualTo("S001");
    }
}
