package com.foodlog.service;

import com.foodlog.domain.FoodItem;
import com.foodlog.repository.FoodItemRepository;
import java.io.InputStream;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FoodImportService {

    private final FoodItemRepository repository;
    private final FoodCatalog catalog;

    public FoodImportService(FoodItemRepository repository, FoodCatalog catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    /** Upserts foods by id, then refreshes the in-memory catalog. */
    public int importCsv(InputStream in) {
        List<FoodItem> foods = FoodCsvParser.parse(in);
        repository.saveAll(foods);
        catalog.reload();
        return foods.size();
    }
}
