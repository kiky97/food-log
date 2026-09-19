package com.foodlog.controller;

import com.foodlog.domain.FoodItem;
import com.foodlog.exception.ApiException;
import com.foodlog.matching.FoodMatcher;
import com.foodlog.matching.FoodMatcher.ScoredFood;
import com.foodlog.service.FoodCatalog;
import com.foodlog.service.FoodImportService;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/foods")
public class FoodController {

    private final FoodCatalog catalog;
    private final FoodMatcher matcher;
    private final FoodImportService importService;

    public FoodController(FoodCatalog catalog, FoodMatcher matcher, FoodImportService importService) {
        this.catalog = catalog;
        this.matcher = matcher;
        this.importService = importService;
    }

    public record SearchHit(String foodId, String name, String category, double score) {
    }

    @GetMapping("/search")
    public List<SearchHit> search(@RequestParam @NotBlank String q,
                                  @RequestParam(defaultValue = "5") int limit) {
        int n = Math.max(1, Math.min(limit, 20));
        return matcher.rank(q, catalog.all()).stream()
                .limit(n)
                .map((ScoredFood s) -> new SearchHit(s.food().id(), s.food().nameEn(), s.food().category(),
                        Math.round(s.score() * 100.0) / 100.0))
                .toList();
    }

    @GetMapping("/{id}")
    public FoodItem get(@PathVariable String id) {
        return catalog.findById(id).orElseThrow(() -> ApiException.notFound("Food not found: " + id));
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Integer> importCsv(@RequestPart("file") MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return Map.of("imported", importService.importCsv(in));
        }
    }
}
