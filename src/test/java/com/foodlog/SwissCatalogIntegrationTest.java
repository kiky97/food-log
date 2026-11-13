package com.foodlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodlog.repository.FoodItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("swiss")
@Testcontainers(disabledWithoutDocker = true)
class SwissCatalogIntegrationTest {
    @Container @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");
    @Autowired MockMvc mvc;
    @Autowired FoodItemRepository foods;

    @Test
    void officialCatalogueIsSeededWithoutSampleRecords() {
        assertThat(foods.count()).isEqualTo(1161);
        assertThat(foods.findById("S001")).isEmpty();
        assertThat(foods.findById("CH-378")).hasValueSatisfying(apple -> {
            assertThat(apple.nameEn()).isEqualTo("Apple, fresh");
            assertThat(apple.per100g().kcal()).isEqualTo(52);
        });
    }

    @Test
    void officialNutrientsReachPersistedLogsAndDailySummary() throws Exception {
        mvc.perform(post("/api/v1/logs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"swiss-test\",\"text\":\"200g fresh apple for breakfast\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].foodId").value("CH-378"))
                .andExpect(jsonPath("$.items[0].grams").value(200.0))
                .andExpect(jsonPath("$.totals.kcal").value(104.0))
                .andExpect(jsonPath("$.totals.carbG").value(23.2));
        mvc.perform(get("/api/v1/logs/summary").param("userId", "swiss-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.kcal").value(104.0))
                .andExpect(jsonPath("$.unresolvedCount").value(0));
    }
}
