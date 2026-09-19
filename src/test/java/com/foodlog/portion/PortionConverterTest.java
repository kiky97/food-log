package com.foodlog.portion;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodlog.domain.FoodItem;
import com.foodlog.domain.Nutrients;
import com.foodlog.portion.PortionConverter.PortionResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortionConverterTest {

    private final PortionConverter converter = new PortionConverter();
    private final FoodItem rice = new FoodItem("S012", "Rice", null, null, List.of(), "Cereal",
            Nutrients.ZERO, Map.of("bowl", 200.0, "cup", 158.0));

    @Test
    void weightUnitsAreExact() {
        assertThat(converter.toGrams(rice, 150, "g")).isEqualTo(new PortionResult(150, false));
        assertThat(converter.toGrams(rice, 0.2, "kg")).isEqualTo(new PortionResult(200, false));
        assertThat(converter.toGrams(rice, 2, "tbsp")).isEqualTo(new PortionResult(30, false));
    }

    @Test
    void usesFoodSpecificPortionWhenAvailable() {
        assertThat(converter.toGrams(rice, 2, "bowl")).isEqualTo(new PortionResult(400, false));
        assertThat(converter.toGrams(rice, 1, "cups")).isEqualTo(new PortionResult(158, false));
    }

    @Test
    void fallsBackToGenericDefaultAndFlagsEstimate() {
        assertThat(converter.toGrams(rice, 1, "plate")).isEqualTo(new PortionResult(300, true));
        assertThat(converter.toGrams(rice, 2, "piece")).isEqualTo(new PortionResult(200, true));
    }

    @Test
    void unknownUnitIsTreatedAsPiece() {
        assertThat(converter.toGrams(rice, 1, "zorb")).isEqualTo(new PortionResult(100, true));
        assertThat(converter.toGrams(rice, 1, null)).isEqualTo(new PortionResult(100, true));
    }

    @Test
    void chineseUnitsAreRecognised() {
        assertThat(converter.toGrams(rice, 1, "碗")).isEqualTo(new PortionResult(200, false));
    }
}
