package com.foodlog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FoodLogApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoodLogApplication.class, args);
    }
}
