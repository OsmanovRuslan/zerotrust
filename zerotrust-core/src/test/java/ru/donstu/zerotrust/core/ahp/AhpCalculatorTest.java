package ru.donstu.zerotrust.core.ahp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.donstu.zerotrust.core.model.FactorId;
import ru.donstu.zerotrust.core.model.Weights;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/** Тесты метода анализа иерархий на примере экспертной матрицы. */
class AhpCalculatorTest {

    private final AhpCalculator calculator = new AhpCalculator();

    /** Экспертная матрица в порядке B, N, D, R. */
    private static double[][] expertMatrix() {
        return new double[][]{
                {1, 2, 2, 1},
                {0.5, 1, 1, 1},
                {0.5, 1, 1, 0.5},
                {1, 1, 2, 1}
        };
    }

    @Test
    @DisplayName("пример 4.13: веса 0,338 / 0,205 / 0,169 / 0,288 и CR = 0,023")
    void expertMatrixExample() {
        AhpResult result = calculator.calculate(expertMatrix());

        assertThat(result.weights().get(0)).isEqualTo(0.338, within(0.002));
        assertThat(result.weights().get(1)).isEqualTo(0.205, within(0.002));
        assertThat(result.weights().get(2)).isEqualTo(0.169, within(0.002));
        assertThat(result.weights().get(3)).isEqualTo(0.288, within(0.002));
        assertThat(result.lambdaMax()).isEqualTo(4.061, within(0.005));
        assertThat(result.consistencyIndex()).isEqualTo(0.020, within(0.002));
        assertThat(result.consistencyRatio()).isEqualTo(0.023, within(0.003));
        assertThat(result.consistent()).isTrue();
    }

    @Test
    @DisplayName("веса в сумме дают 1 и годятся для политики")
    void weightsFormPolicy() {
        Weights weights = calculator.weights(expertMatrix(),
                List.of(FactorId.B, FactorId.N, FactorId.D, FactorId.R));

        assertThat(weights.weightOf(FactorId.B)).isEqualTo(0.338, within(0.002));
        assertThat(weights.values().values().stream().mapToDouble(Double::doubleValue).sum())
                .isEqualTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("матрица из одинаковых суждений даёт равные веса и полную согласованность")
    void uniformMatrix() {
        double[][] uniform = {
                {1, 1, 1},
                {1, 1, 1},
                {1, 1, 1}
        };

        AhpResult result = calculator.calculate(uniform);

        assertThat(result.weights()).allSatisfy(weight ->
                assertThat(weight).isEqualTo(1.0 / 3, within(1e-9)));
        assertThat(result.lambdaMax()).isEqualTo(3.0, within(1e-9));
        assertThat(result.consistencyRatio()).isEqualTo(0.0, within(1e-9));
        assertThat(result.consistent()).isTrue();
    }

    @Test
    @DisplayName("согласованная по построению матрица даёт CR = 0")
    void perfectlyConsistentMatrix() {
        // Веса 0,5 / 0,3 / 0,2: a_ij = w_i / w_j
        double[][] matrix = {
                {1, 5.0 / 3, 2.5},
                {3.0 / 5, 1, 1.5},
                {1 / 2.5, 1 / 1.5, 1}
        };

        AhpResult result = calculator.calculate(matrix);

        assertThat(result.weights().get(0)).isEqualTo(0.5, within(1e-6));
        assertThat(result.weights().get(1)).isEqualTo(0.3, within(1e-6));
        assertThat(result.weights().get(2)).isEqualTo(0.2, within(1e-6));
        assertThat(result.consistencyRatio()).isEqualTo(0.0, within(1e-6));
    }

    @Test
    @DisplayName("противоречивые суждения: CR не меньше 0,10, веса не принимаются")
    void inconsistentMatrix() {
        // B важнее N, N важнее D, но D важнее B — цикл
        double[][] cyclic = {
                {1, 9, 1.0 / 9},
                {1.0 / 9, 1, 9},
                {9, 1.0 / 9, 1}
        };

        AhpResult result = calculator.calculate(cyclic);

        assertThat(result.consistencyRatio()).isGreaterThanOrEqualTo(AhpCalculator.MAX_CONSISTENCY_RATIO);
        assertThat(result.consistent()).isFalse();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.weights(cyclic,
                        List.of(FactorId.B, FactorId.N, FactorId.D)))
                .withMessageContaining("несогласована");
    }

    @Test
    @DisplayName("матрица 2x2 согласована всегда")
    void twoByTwoIsAlwaysConsistent() {
        AhpResult result = calculator.calculate(new double[][]{{1, 3}, {1.0 / 3, 1}});

        assertThat(result.weights().get(0)).isEqualTo(0.75, within(1e-6));
        assertThat(result.consistencyRatio()).isZero();
        assertThat(result.consistent()).isTrue();
    }

    @Test
    @DisplayName("не квадратная, не обратно симметричная и выходящая за шкалу матрица отвергается")
    void invalidMatrices() {
        assertThatIllegalArgumentException()
                .as("не квадратная")
                .isThrownBy(() -> calculator.calculate(new double[][]{{1, 2, 3}, {0.5, 1, 1}}))
                .withMessageContaining("квадратной");
        assertThatIllegalArgumentException()
                .as("не обратно симметричная")
                .isThrownBy(() -> calculator.calculate(new double[][]{{1, 2}, {2, 1}}))
                .withMessageContaining("обратно симметричной");
        assertThatIllegalArgumentException()
                .as("вне шкалы Саати")
                .isThrownBy(() -> calculator.calculate(new double[][]{{1, 10}, {0.1, 1}}))
                .withMessageContaining("шкалы Саати");
        assertThatIllegalArgumentException()
                .as("не единица на диагонали")
                .isThrownBy(() -> calculator.calculate(new double[][]{{2, 2}, {0.5, 1}}));
        assertThatIllegalArgumentException()
                .as("отрицательное сравнение")
                .isThrownBy(() -> calculator.calculate(new double[][]{{1, -2}, {-0.5, 1}}));
        assertThatIllegalArgumentException()
                .as("слишком маленькая")
                .isThrownBy(() -> calculator.calculate(new double[][]{{1}}));
        assertThatIllegalArgumentException()
                .as("без матрицы")
                .isThrownBy(() -> calculator.calculate(null));
    }

    @Test
    @DisplayName("число факторов должно совпадать с размером матрицы")
    void factorCountMustMatch() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.weights(expertMatrix(), List.of(FactorId.B, FactorId.N)))
                .withMessageContaining("строк в матрице");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.weights(expertMatrix(), List.of()));
    }
}
