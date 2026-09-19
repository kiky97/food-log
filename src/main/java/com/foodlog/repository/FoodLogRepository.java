package com.foodlog.repository;

import com.foodlog.domain.FoodLog;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FoodLogRepository extends MongoRepository<FoodLog, String> {

    List<FoodLog> findByUserIdAndLogDateOrderByLoggedAtAsc(String userId, String logDate);
}
