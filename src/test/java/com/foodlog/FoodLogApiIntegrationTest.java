package com.foodlog;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** End-to-end through the REST layer with mock STT/LLM and a real MongoDB. Skipped when Docker is not running. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class FoodLogApiIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    private String body(String userId, String text) throws Exception {
        return mapper.writeValueAsString(java.util.Map.of("userId", userId, "text", text));
    }

    @Test
    void textLogIsMatchedConvertedAndSummarised() throws Exception {
        mvc.perform(post("/api/v1/logs").contentType(MediaType.APPLICATION_JSON)
                        .content(body("u-text", "I had two boiled eggs, a bowl of rice and an apple for breakfast")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].status").value("MATCHED"))
                .andExpect(jsonPath("$.items[0].foodId").value("S018"))
                .andExpect(jsonPath("$.items[0].grams").value(100.0))
                .andExpect(jsonPath("$.items[1].foodId").value("S012"))
                .andExpect(jsonPath("$.items[1].grams").value(200.0))
                .andExpect(jsonPath("$.items[2].foodId").value("S001"))
                .andExpect(jsonPath("$.items[2].mealType").value("BREAKFAST"))
                // eggs 155 + rice 260 + apple 93.6
                .andExpect(jsonPath("$.totals.kcal").value(closeTo(508.6, 0.11)))
                .andExpect(jsonPath("$.needsAttention").value(0));

        mvc.perform(get("/api/v1/logs/summary").param("userId", "u-text"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.kcal").value(closeTo(508.6, 0.11)))
                .andExpect(jsonPath("$.byMeal.BREAKFAST.kcal").value(closeTo(508.6, 0.11)))
                .andExpect(jsonPath("$.unresolvedCount").value(0));
    }

    @Test
    void ambiguousItemNeedsConfirmationThenCountsAfterConfirming() throws Exception {
        String created = mvc.perform(post("/api/v1/logs").contentType(MediaType.APPLICATION_JSON)
                        .content(body("u-confirm", "I had chicken soup")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].status").value("NEEDS_CONFIRMATION"))
                .andExpect(jsonPath("$.items[0].candidates[0].foodId").value("S024"))
                .andExpect(jsonPath("$.totals.kcal").value(0.0))
                .andExpect(jsonPath("$.needsAttention").value(1))
                .andReturn().getResponse().getContentAsString();
        String logId = mapper.readTree(created).path("id").asText();

        mvc.perform(post("/api/v1/logs/{id}/items/0/confirm", logId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"foodId\":\"S024\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].portionEstimated").value(false))
                // 1 piece of chicken breast = 120 g * 165 kcal / 100
                .andExpect(jsonPath("$.totals.kcal").value(198.0))
                .andExpect(jsonPath("$.needsAttention").value(0));
    }

    @Test
    void voiceEndpointWorksWithMockSttTextFile() throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "meal.txt", "text/plain",
                "for lunch I ate 150g of salmon".getBytes());

        mvc.perform(multipart("/api/v1/logs/voice").file(audio).param("userId", "u-voice"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("VOICE"))
                .andExpect(jsonPath("$.transcript").value("for lunch I ate 150g of salmon"))
                .andExpect(jsonPath("$.items[0].foodId").value("S027"))
                .andExpect(jsonPath("$.items[0].mealType").value("LUNCH"))
                .andExpect(jsonPath("$.totals.kcal").value(309.0));
    }

    @Test
    void realAudioIsRejectedByMockStt() throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "meal.wav", "audio/wav", new byte[] {1, 2, 3});

        mvc.perform(multipart("/api/v1/logs/voice").file(audio).param("userId", "u-voice2"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void textWithoutFoodIsUnprocessable() throws Exception {
        mvc.perform(post("/api/v1/logs").contentType(MediaType.APPLICATION_JSON)
                        .content(body("u-none", "for lunch")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void validationErrorsReturn400() throws Exception {
        mvc.perform(post("/api/v1/logs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"\",\"text\":\"apple\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void foodSearchAndUnknownLookups() throws Exception {
        mvc.perform(get("/api/v1/foods/search").param("q", "bananna"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].foodId").value("S002"));
        mvc.perform(get("/api/v1/foods/NOPE")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/logs/{id}/items/0/confirm", "missing").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"foodId\":\"S001\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void importEndpointUpsertsFoods() throws Exception {
        MockMultipartFile csv = new MockMultipartFile("file", "foods.csv", "text/csv",
                "id,name_en,kcal\nT1,Dragon fruit,60\n".getBytes());

        mvc.perform(multipart("/api/v1/foods/import").file(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));
        mvc.perform(get("/api/v1/foods/search").param("q", "dragon fruit"))
                .andExpect(jsonPath("$[0].foodId").value("T1"));
    }
}
