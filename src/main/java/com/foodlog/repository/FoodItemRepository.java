package com.foodlog.repository;

import com.foodlog.domain.FoodItem;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FoodItemRepository extends MongoRepository<FoodItem, String> {
}
