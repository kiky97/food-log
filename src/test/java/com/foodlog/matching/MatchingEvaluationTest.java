package com.foodlog.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlog.domain.FoodItem;
import com.foodlog.domain.MatchStatus;
import com.foodlog.service.FoodCsvParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Opt-in offline evaluation. Labels are fixtures, never generated from matcher output. */
@EnabledIfSystemProperty(named = "foodlog.evaluate", matches = "true")
class MatchingEvaluationTest {
    private final FoodMatcher matcher = new FoodMatcher(0.85, 0.6, 3);
    private static volatile int blackhole;
    record Query(String query, String expectedId, String language, String category) {}
    record Prediction(String status, String foodId, List<String> candidates) {}
    record Result(Query label, Prediction prediction, boolean correctDecision, boolean candidateHit) {}

    @Test
    void produceReproducibleReports() throws Exception {
        Path output = Path.of("target/evaluation");
        Files.createDirectories(output);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("java", System.getProperty("java.version"));
        report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        report.put("processors", Runtime.getRuntime().availableProcessors());
        report.put("revision", System.getProperty("evaluation.revision", "unspecified"));
        report.put("thresholds", Map.of("auto", 0.85, "minimum", 0.6, "candidates", 3));
        for (String dataset : List.of("sample", "swiss")) {
            String catalogPath = "data/foods_" + (dataset.equals("sample") ? "sample" : "swiss_v7_1") + ".csv";
            String queryPath = "evaluation/" + dataset + "_queries.csv";
            List<FoodItem> foods;
            try (InputStream in = resource(catalogPath)) { foods = FoodCsvParser.parse(in); }
            List<Query> queries = new ArrayList<>();
            try (var reader = new InputStreamReader(resource(queryPath), StandardCharsets.UTF_8);
                 var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
                for (var r : parser) queries.add(new Query(r.get("query"), r.get("expected_id"), r.get("language"), r.get("category")));
            }
            assertThat(queries).isNotEmpty();
            assertThat(queries.stream().map(Query::query).distinct().count()).isEqualTo(queries.size());
            Set<String> ids = new HashSet<>();
            foods.forEach(f -> ids.add(f.id()));
            assertThat(foods).hasSize(ids.size());
            queries.stream().filter(q -> !q.expectedId.isEmpty()).forEach(q -> assertThat(ids).contains(q.expectedId));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("catalogueSize", foods.size());
            data.put("catalogueSha256", sha(catalogPath));
            data.put("queriesSha256", sha(queryPath));
            data.put("queryCount", queries.size());
            for (String method : List.of("exact", "fuzzy")) {
                List<Result> results = queries.stream().map(q -> {
                    Prediction p = predict(method, q.query, foods);
                    boolean auto = p.status.equals("MATCHED");
                    boolean correct = q.expectedId.isEmpty() ? !auto : auto && q.expectedId.equals(p.foodId);
                    return new Result(q, p, correct, p.candidates.contains(q.expectedId));
                }).toList();
                Map<String, Object> measurement = new LinkedHashMap<>(metrics(results));
                Map<String, Object> groups = new TreeMap<>();
                for (String category : queries.stream().map(Query::category).distinct().sorted().toList()) {
                    groups.put(category, metrics(results.stream().filter(r -> r.label.category.equals(category)).toList()));
                }
                measurement.put("byCategory", groups);
                Map<String, Object> languages = new TreeMap<>();
                for (String language : queries.stream().map(Query::language).distinct().sorted().toList()) {
                    languages.put(language, metrics(results.stream().filter(r -> r.label.language.equals(language)).toList()));
                }
                measurement.put("byLanguage", languages);
                measurement.put("cases", results);
                // Warmup 3 full passes, then measure 10 passes. Single-thread in-process only.
                for (int i = 0; i < 3; i++) for (Query q : queries) blackhole = predict(method, q.query, foods).hashCode();
                long[] times = new long[queries.size() * 10];
                int n = 0;
                for (int i = 0; i < 10; i++) for (Query q : queries) {
                    long start = System.nanoTime();
                    blackhole = predict(method, q.query, foods).hashCode();
                    times[n++] = System.nanoTime() - start;
                }
                Arrays.sort(times);
                measurement.put("timing", Map.of("samples", n, "warmupPasses", 3,
                        "p50Ms", times[(int) Math.ceil(n * .50) - 1] / 1e6,
                        "p95Ms", times[(int) Math.ceil(n * .95) - 1] / 1e6));
                data.put(method, measurement);
            }
            report.put(dataset, data);
        }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("matching.json").toFile(), report);
        System.out.println("Evaluation written to " + output.toAbsolutePath());
    }

    private Prediction predict(String method, String query, List<FoodItem> foods) {
        if (method.equals("exact")) {
            // Fair baseline: all names/aliases, case-insensitive, with surrounding whitespace removed.
            List<String> hits = foods.stream().filter(f -> f.allNames().stream().anyMatch(name -> name.trim().equalsIgnoreCase(query.trim())))
                    .map(FoodItem::id).toList();
            return new Prediction(hits.size() == 1 ? "MATCHED" : hits.isEmpty() ? "UNMATCHED" : "NEEDS_CONFIRMATION",
                    hits.size() == 1 ? hits.get(0) : "", hits.stream().limit(3).toList());
        }
        MatchOutcome out = matcher.match(query, foods);
        return new Prediction(out.status().name(), out.best() == null ? "" : out.best().id(),
                out.candidates().stream().map(c -> c.foodId()).toList());
    }

    private static Map<String, Object> metrics(List<Result> rows) {
        long positives = rows.stream().filter(r -> !r.label.expectedId.isEmpty()).count();
        long auto = rows.stream().filter(r -> r.prediction.status.equals("MATCHED")).count();
        long correctAuto = rows.stream().filter(r -> !r.label.expectedId.isEmpty() && r.correctDecision).count();
        long correctRejections = rows.stream().filter(r -> r.label.expectedId.isEmpty() && r.correctDecision).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("queries", rows.size()); m.put("positiveQueries", positives); m.put("negativeQueries", rows.size() - positives);
        m.put("autoAccepted", auto); m.put("correctAutoAccepted", correctAuto);
        m.put("falseAutoAccepted", auto - correctAuto); m.put("correctRejections", correctRejections);
        m.put("decisionAccuracy", ratio(correctAuto + correctRejections, rows.size()));
        m.put("autoPrecision", ratio(correctAuto, auto)); m.put("autoCoverage", ratio(auto, rows.size()));
        m.put("positiveAutoRecall", ratio(correctAuto, positives));
        m.put("candidateRecallAt3", ratio(rows.stream().filter(r -> !r.label.expectedId.isEmpty() && r.candidateHit).count(), positives));
        m.put("safeRejectionRate", ratio(correctRejections, rows.size() - positives));
        return m;
    }
    private static Double ratio(long numerator, long denominator) { return denominator == 0 ? null : (double) numerator / denominator; }
    private static InputStream resource(String path) { return Objects.requireNonNull(MatchingEvaluationTest.class.getClassLoader().getResourceAsStream(path), path); }
    private static String sha(String path) throws Exception {
        try (InputStream in = resource(path)) { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())); }
    }
}
