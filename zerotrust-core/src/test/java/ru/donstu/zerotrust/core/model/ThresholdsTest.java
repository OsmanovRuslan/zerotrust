package ru.donstu.zerotrust.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Тесты порогов политики: порядок 0 < low < high ≤ critical ≤ 0,95. */
class ThresholdsTest {

    @Test
    @DisplayName("пороги по умолчанию — из главы 1 ВКР")
    void defaultThresholdsMatchThesis() {
        assertThat(Thresholds.DEFAULT.low()).isEqualTo(0.40);
        assertThat(Thresholds.DEFAULT.high()).isEqualTo(0.70);
        assertThat(Thresholds.DEFAULT.critical()).isEqualTo(0.80);
    }

    @Test
    @DisplayName("high равный critical допустим: политика может не различать уровни")
    void highMayEqualCritical() {
        assertThat(new Thresholds(0.40, 0.80, 0.80).critical()).isEqualTo(0.80);
    }

    @Test
    @DisplayName("критический порог может быть ровно 0,95 — верхним пределом")
    void criticalMayReachUpperLimit() {
        assertThat(new Thresholds(0.40, 0.70, Thresholds.MAX).critical()).isEqualTo(0.95);
    }

    @ParameterizedTest(name = "low={0} high={1} critical={2} отвергаются")
    @CsvSource({
            "0.00, 0.70, 0.80",   // low должен быть строго больше нуля
            "0.70, 0.70, 0.80",   // low должен быть строго меньше high
            "0.80, 0.70, 0.80",   // порядок нарушен
            "0.40, 0.90, 0.80",   // high больше critical
            "0.40, 0.70, 0.96",   // critical выше предела 0,95
            "-0.10, 0.70, 0.80"   // отрицательный порог
    })
    @DisplayName("нарушение порядка порогов отвергается")
    void invalidOrderIsRejected(double low, double high, double critical) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Thresholds(low, high, critical))
                .withMessageContaining("порядок порогов");
    }

    @Test
    @DisplayName("NaN отвергается с указанием имени порога")
    void nanIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Thresholds(Double.NaN, 0.70, 0.80))
                .withMessageContaining("low");
    }

    @Test
    @DisplayName("базовый порог уровня: NORMAL даёт θ_H, CRITICAL — θ_C")
    void baseThresholdDependsOnLevel() {
        assertThat(Thresholds.DEFAULT.baseThresholdFor(Level.NORMAL)).isEqualTo(0.70);
        assertThat(Thresholds.DEFAULT.baseThresholdFor(Level.CRITICAL)).isEqualTo(0.80);
    }

    @Test
    @DisplayName("для уровня AUTH_ONLY порога нет: оценка доверия не вычисляется")
    void authOnlyHasNoThreshold() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Thresholds.DEFAULT.baseThresholdFor(Level.AUTH_ONLY))
                .withMessageContaining("AUTH_ONLY");
    }
}
