package ru.donstu.zerotrust.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/** Тесты весов факторов: инвариант «сумма равна 1» и перенормировка. */
class WeightsTest {

    @Test
    @DisplayName("веса по умолчанию — экспертные веса главы 1 ВКР")
    void defaultWeightsMatchThesis() {
        assertThat(Weights.DEFAULT.weightOf(FactorId.B)).isEqualTo(0.30);
        assertThat(Weights.DEFAULT.weightOf(FactorId.N)).isEqualTo(0.25);
        assertThat(Weights.DEFAULT.weightOf(FactorId.D)).isEqualTo(0.20);
        assertThat(Weights.DEFAULT.weightOf(FactorId.R)).isEqualTo(0.25);
    }

    @Test
    @DisplayName("вес отсутствующего фактора равен нулю: такой фактор в оценке не участвует")
    void unknownFactorHasZeroWeight() {
        assertThat(Weights.DEFAULT.weightOf(FactorId.of("FRAUD"))).isZero();
    }

    @Test
    @DisplayName("веса, полученные методом анализа иерархий, принимаются как есть")
    void ahpWeightsAreAccepted() {
        Weights ahp = Weights.of(0.338, 0.205, 0.169, 0.288);

        assertThat(ahp.values().values().stream().mapToDouble(Double::doubleValue).sum())
                .isEqualTo(1.0, within(Scores.WEIGHT_SUM_TOLERANCE));
    }

    @ParameterizedTest(name = "сумма {0}+{1}+{2}+{3} отвергается")
    @CsvSource({
            "0.30, 0.25, 0.20, 0.30",   // сумма 1,05
            "0.20, 0.20, 0.20, 0.20",   // сумма 0,80
            "0.00, 0.00, 0.00, 0.00"    // все нули
    })
    @DisplayName("сумма весов, отличная от единицы, отвергается")
    void weightSumMustBeOne(double b, double n, double d, double r) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Weights.of(b, n, d, r))
                .withMessageContaining("Сумма весов");
    }

    @Test
    @DisplayName("допуск 0,001 позволяет задать веса в виде 1/3 без потери точности")
    void toleranceAllowsRoundedThirds() {
        assertThat(new Weights(Map.of(
                FactorId.B, 0.333,
                FactorId.N, 0.333,
                FactorId.D, 0.334)).weightOf(FactorId.B)).isEqualTo(0.333);
    }

    @Test
    @DisplayName("отрицательный вес отвергается")
    void negativeWeightIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Weights.of(0.50, 0.30, 0.30, -0.10))
                .withMessageContaining("отрицательным");
    }

    @Test
    @DisplayName("пустой набор весов отвергается")
    void emptyWeightsAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Weights(Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Weights(null));
    }

    @Test
    @DisplayName("активными считаются только факторы с положительным весом")
    void activeFactorsSkipZeroWeights() {
        Weights behaviorDisabled = new Weights(Map.of(
                FactorId.B, 0.0,
                FactorId.N, 0.40,
                FactorId.D, 0.20,
                FactorId.R, 0.40));

        assertThat(behaviorDisabled.activeFactors()).containsExactlyInAnyOrder(FactorId.N, FactorId.D, FactorId.R);
    }

    @Test
    @DisplayName("перенормировка исключает отключённые факторы и сохраняет сумму 1")
    void normalizeWithoutKeepsSumOne() {
        Weights withoutBehavior = Weights.DEFAULT.normalizeWithout(Set.of(FactorId.B));

        assertThat(withoutBehavior.values()).doesNotContainKey(FactorId.B);
        assertThat(withoutBehavior.values().values().stream().mapToDouble(Double::doubleValue).sum())
                .isEqualTo(1.0, within(1e-12));
        // 0,25 / (0,25 + 0,20 + 0,25) = 0,25 / 0,70
        assertThat(withoutBehavior.weightOf(FactorId.N)).isEqualTo(0.25 / 0.70, within(1e-12));
    }

    @Test
    @DisplayName("перенормировка без отключённых факторов возвращает те же веса")
    void normalizeWithoutNothingIsIdentity() {
        assertThat(Weights.DEFAULT.normalizeWithout(Set.of())).isSameAs(Weights.DEFAULT);
        assertThat(Weights.DEFAULT.normalizeWithout(null)).isSameAs(Weights.DEFAULT);
    }

    @Test
    @DisplayName("отключить все факторы нельзя: оценивать доверие станет нечем")
    void disablingAllFactorsIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Weights.DEFAULT
                        .normalizeWithout(Set.of(FactorId.B, FactorId.N, FactorId.D, FactorId.R)))
                .withMessageContaining("Нельзя отключить все факторы");
    }

    @Test
    @DisplayName("карта весов неизменяема и не зависит от переданной извне")
    void weightsAreImmutable() {
        Map<FactorId, Double> mutable = new HashMap<>();
        mutable.put(FactorId.B, 0.5);
        mutable.put(FactorId.N, 0.5);
        Weights weights = new Weights(mutable);

        mutable.put(FactorId.D, 99.0);

        assertThat(weights.weightOf(FactorId.D)).isZero();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> weights.values().put(FactorId.D, 1.0));
    }
}
