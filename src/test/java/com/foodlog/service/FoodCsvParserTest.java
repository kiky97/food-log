package com.foodlog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodlog.domain.FoodItem;
import com.foodlog.exception.ApiException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class FoodCsvParserTest {

    private static List<FoodItem> parse(String csv) {
        return FoodCsvParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parsesNutrientsAliasesAndPortions() {
        List<FoodItem> foods = parse("""
                id,name_en,name_zh,aliases,kcal,protein_g,piece_g,bowl_g
                X1,Test food,测试,foo; bar ,120.5,3,50,
                """);

        assertThat(foods).hasSize(1);
        FoodItem f = foods.get(0);
        assertThat(f.id()).isEqualTo("X1");
        assertThat(f.nameZh()).isEqualTo("测试");
        assertThat(f.aliases()).containsExactly("foo", "bar");
        assertThat(f.per100g().kcal()).isEqualTo(120.5);
        assertThat(f.per100g().fatG()).isZero();
        assertThat(f.portionGrams()).containsOnlyKeys("piece");
    }

    @Test
    void rejectsMissingRequiredColumn() {
        assertThatThrownBy(() -> parse("id,name_en\nX1,Test\n"))
                .isInstanceOf(ApiException.class).hasMessageContaining("kcal");
    }

    @Test
    void rejectsInvalidNumberWithRowNumber() {
        assertThatThrownBy(() -> parse("id,name_en,kcal\nX1,Test,abc\n"))
                .isInstanceOf(ApiException.class).hasMessageContaining("row 2").hasMessageContaining("abc");
    }

    @Test
    void bundledSampleFileIsValid() throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream("data/foods_sample.csv")) {
            List<FoodItem> foods = FoodCsvParser.parse(in);
            assertThat(foods).hasSize(45);
            assertThat(foods).extracting(FoodItem::id).doesNotHaveDuplicates();
        }
    }
}
