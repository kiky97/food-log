package com.foodlog.config;

import com.foodlog.matching.FoodMatcher;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public FoodMatcher foodMatcher(AppProperties props) {
        AppProperties.Match m = props.match();
        return new FoodMatcher(m.autoThreshold(), m.minThreshold(), m.maxCandidates());
    }

    @Bean
    public ZoneId appZone(AppProperties props) {
        return ZoneId.of(props.zone());
    }
}
