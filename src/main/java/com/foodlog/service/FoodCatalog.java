package com.foodlog.service;

import com.foodlog.domain.FoodItem;
import com.foodlog.repository.FoodItemRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * In-memory snapshot of the food database. The Swiss database has around a thousand entries, so
 * scoring every entry per query is cheap and avoids depending on database text-search behaviour.
 */
@Component
public class FoodCatalog {

    private final FoodItemRepository repository;
    private volatile List<FoodItem> all = List.of();
    private volatile Map<String, FoodItem> byId = Map.of();

    public FoodCatalog(FoodItemRepository repository) {
        this.repository = repository;
    }

    public void reload() {
        List<FoodItem> foods = repository.findAll();
        this.byId = foods.stream().collect(Collectors.toUnmodifiableMap(FoodItem::id, Function.identity()));
        this.all = List.copyOf(foods);
    }

    public List<FoodItem> all() {
        return all;
    }

    public Optional<FoodItem> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}
