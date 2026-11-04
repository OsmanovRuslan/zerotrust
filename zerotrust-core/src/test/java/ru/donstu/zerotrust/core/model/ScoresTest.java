package ru.donstu.zerotrust.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Тесты операций над величинами модели: ограничение отрезком и округление до трёх знаков. */
class ScoresTest {

    @ParameterizedTest(name = "clamp01({0}) = {1}")
    @CsvSource({
            "0.0,  0.0",
            "1.0,  1.0",
            "0.5,  0.5",
            "-0.1, 0.0",
            "1.7,  1.0",
            "-999, 0.0"
    })
    @DisplayName("ограничение отрезком [0, 1]")
    void clamp01(double value, double expected) {
        assertThat(Scores.clamp01(value)).isEqualTo(expected);
    }

    @Test
    @DisplayName("NaN приводится к нулю: одна ошибка в провайдере не отравляет всю оценку")
    void nanBecomesZero() {
        assertThat(Scores.clamp01(Double.NaN)).isZero();
        assertThat(Scores.clamp01(Double.NEGATIVE_INFINITY)).isZero();
        assertThat(Scores.clamp01(Double.POSITIVE_INFINITY)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("ограничение произвольным отрезком: нужно формулам с минимумом, отличным от нуля")
    void clampToArbitraryRange() {
        assertThat(Scores.clamp(0.05, 0.1, 1.0)).isEqualTo(0.1);
        assertThat(Scores.clamp(0.5, 0.1, 1.0)).isEqualTo(0.5);
        assertThat(Scores.clamp(2.0, 0.1, 1.0)).isEqualTo(1.0);
    }

    @ParameterizedTest(name = "round3({0}) = {1}")
    @CsvSource({
            "0.9475,             0.948",
            "0.6899999999999999, 0.690",
            "0.75,               0.750",
            "0.24,               0.240",
            "0.6777777777777777, 0.678",
            "0.4555555555555555, 0.456",
            "0.0005,             0.001",
            "0.0004,             0.000",
            "1.0,                1.0"
    })
    @DisplayName("округление до трёх знаков по правилу «половина вверх»")
    void round3(double value, double expected) {
        assertThat(Scores.round3(value)).isEqualTo(expected);
    }

    @Test
    @DisplayName("округление не падает на NaN и бесконечности")
    void round3IsSafe() {
        assertThat(Scores.round3(Double.NaN)).isZero();
        assertThat(Scores.round3(Double.POSITIVE_INFINITY)).isZero();
    }

    @Test
    @DisplayName("величиной модели считается конечное значение из [0, 1] с погрешностью 1e-9")
    void isUnitValue() {
        assertThat(Scores.isUnitValue(0.0)).isTrue();
        assertThat(Scores.isUnitValue(1.0)).isTrue();
        assertThat(Scores.isUnitValue(1.0 + 1e-10)).isTrue();
        assertThat(Scores.isUnitValue(-1e-10)).isTrue();
        assertThat(Scores.isUnitValue(1.001)).isFalse();
        assertThat(Scores.isUnitValue(-0.001)).isFalse();
        assertThat(Scores.isUnitValue(Double.NaN)).isFalse();
        assertThat(Scores.isUnitValue(Double.POSITIVE_INFINITY)).isFalse();
        assertThat(Scores.isUnitValue(Double.NEGATIVE_INFINITY)).isFalse();
    }
}
