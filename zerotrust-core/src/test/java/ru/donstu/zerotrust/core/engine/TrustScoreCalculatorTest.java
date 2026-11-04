package ru.donstu.zerotrust.core.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.donstu.zerotrust.core.model.FactorId;
import ru.donstu.zerotrust.core.model.FactorValue;
import ru.donstu.zerotrust.core.model.Thresholds;
import ru.donstu.zerotrust.core.model.TrustScore;
import ru.donstu.zerotrust.core.model.Weights;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/**
 * Тесты формулы оценки доверия — интегральной оценки по четырём факторам.
 *
 * <p>Главный из них — сценарии статьи: пока они дают 0,948 / 0,690 / 0,750 / 0,240,
 * обобщённая формула с достоверностями не противоречит формуле главы 1 ВКР, и таблицы
 * из опубликованной статьи остаются верными.
 */
class TrustScoreCalculatorTest {

    private final TrustScoreCalculator calculator = new TrustScoreCalculator();
    private final Weights weights = Weights.DEFAULT;
    private final Thresholds thresholds = Thresholds.DEFAULT;

    /** Четыре фактора с полной достоверностью: формула оценки доверия должна выродиться в формулу главы 1. */
    private TrustScore computeFullEvidence(double b, double n, double d, double r) {
        return calculator.compute(weights, List.of(
                FactorValue.of(FactorId.B, b),
                FactorValue.of(FactorId.N, n),
                FactorValue.of(FactorId.D, d),
                FactorValue.of(FactorId.R, r)), thresholds);
    }

    /** Фактор с произвольной достоверностью. */
    private static FactorValue factor(FactorId id, double value, double confidence) {
        return new FactorValue(id, value, confidence, Map.of(), false);
    }

    @Nested
    @DisplayName("Сценарии статьи: при c = 1 формула оценки доверия совпадает с формулой главы 1")
    class ArticleScenarios {

        @ParameterizedTest(name = "сценарий {0}: B={1} N={2} D={3} R={4} -> TS={5}")
        @CsvSource({
                // № сценария, B, N, D, R, ожидаемая TS (округление до трёх знаков)
                "1, 0.95, 0.90, 1.00, 0.95, 0.948",
                "2, 0.70, 0.40, 0.90, 0.80, 0.690",
                "3, 0.80, 0.70, 0.80, 0.70, 0.750",
                "4, 0.20, 0.30, 0.40, 0.10, 0.240"
        })
        @DisplayName("воспроизводят оценки из статьи")
        void reproduceArticleScores(int scenario, double b, double n, double d, double r, double expected) {
            TrustScore ts = computeFullEvidence(b, n, d, r);

            assertThat(ts.rounded())
                    .as("сценарий %d статьи", scenario)
                    .isEqualTo(expected);
            assertThat(ts.noEvidence()).isFalse();
            assertThat(ts.isFullEvidence())
                    .as("при c = 1 по всем факторам масса данных равна сумме весов, то есть единице")
                    .isTrue();
        }

        @Test
        @DisplayName("при полной достоверности знаменатель не влияет на результат")
        void denominatorIsNeutralWithFullConfidence() {
            double b = 0.95;
            double n = 0.90;
            double d = 1.00;
            double r = 0.95;
            double weightedSum = 0.30 * b + 0.25 * n + 0.20 * d + 0.25 * r;

            assertThat(computeFullEvidence(b, n, d, r).value())
                    .as("TS = wB*B + wN*N + wD*D + wR*R при c = 1")
                    .isEqualTo(weightedSum, within(1e-12));
        }
    }

    @Nested
    @DisplayName("Примеры расчёта оценки доверия")
    class CalculationExamples {

        @ParameterizedTest(name = "{0} -> TS={7}")
        @CsvSource({
                // описание, B, cB, N, D, R, cR, ожидаемая TS
                "'Алиса, обычный рабочий день, свой ноутбук, офис', 0.920, 1, 0.9, 1.0, 1.0, 1, 0.951",
                "'Украденный токен: ночь, новая страна, перелёт',   0.680, 1, 0.1, 0.4, 1.0, 1, 0.559",
                "'Тот же злоумышленник после IMPOSSIBLE_TRAVEL',    0.680, 1, 0.1, 0.4, 0.7, 1, 0.484",
                "'Новый пользователь, новое устройство, офис',      0.000, 0, 0.9, 0.4, 0.0, 0, 0.678",
                "'Новый пользователь, домашний публичный IP',       0.000, 0, 0.5, 0.4, 0.0, 0, 0.456",
                "'Сотрудник ночью выгружает отчёт',                 0.378, 1, 0.9, 1.0, 1.0, 1, 0.788",
                "'Скрипт: 300 запросов в минуту',                   0.650, 1, 0.9, 1.0, 1.0, 1, 0.870"
        })
        @DisplayName("совпадают со значениями из таблицы")
        void matchSpecificationTable(String description, double b, double cB, double n, double d,
                                     double r, double cR, double expected) {
            TrustScore ts = calculator.compute(weights, List.of(
                    factor(FactorId.B, b, cB),
                    FactorValue.of(FactorId.N, n),
                    FactorValue.of(FactorId.D, d),
                    factor(FactorId.R, r, cR)), thresholds);

            assertThat(ts.rounded()).as(description).isEqualTo(expected);
        }

        @Test
        @DisplayName("после подтверждения step-up устройство становится TRUSTED и оценка растёт до 0,944")
        void newUserAfterStepUp() {
            TrustScore ts = calculator.compute(weights, List.of(
                    FactorValue.unknown(FactorId.B),
                    FactorValue.of(FactorId.N, 0.9),
                    FactorValue.of(FactorId.D, 1.0),
                    FactorValue.unknown(FactorId.R)), thresholds);

            // (0,25*0,9 + 0,20*1,0) / (0,25 + 0,20) = 0,425 / 0,45
            assertThat(ts.rounded()).isEqualTo(0.944);
            assertThat(ts.evidenceMass()).isEqualTo(0.45, within(1e-12));
            assertThat(ts.isFullEvidence()).isFalse();
        }
    }

    @Nested
    @DisplayName("Холодный старт: фактор без данных не влияет на оценку")
    class ColdStart {

        @Test
        @DisplayName("фактор с нулевой достоверностью исключается из среднего")
        void zeroConfidenceFactorIsExcluded() {
            // Значение фактора B намеренно «плохое»: при c = 0 оно не должно влиять ни на что.
            TrustScore withZeroConfidence = calculator.compute(weights, List.of(
                    factor(FactorId.B, 0.0, 0.0),
                    FactorValue.of(FactorId.N, 0.9),
                    FactorValue.of(FactorId.D, 1.0),
                    FactorValue.of(FactorId.R, 1.0)), thresholds);

            TrustScore withoutFactorAtAll = calculator.compute(weights, List.of(
                    FactorValue.of(FactorId.N, 0.9),
                    FactorValue.of(FactorId.D, 1.0),
                    FactorValue.of(FactorId.R, 1.0)), thresholds);

            assertThat(withZeroConfidence.value()).isEqualTo(withoutFactorAtAll.value());
        }

        @Test
        @DisplayName("частичная достоверность смещает оценку к известным факторам")
        void partialConfidenceShiftsScore() {
            // Профиль обучен наполовину (c_B = 0,5): вклад фактора B в среднее вдвое меньше.
            TrustScore ts = calculator.compute(weights, List.of(
                    factor(FactorId.B, 0.2, 0.5),
                    FactorValue.of(FactorId.N, 1.0),
                    FactorValue.of(FactorId.D, 1.0),
                    FactorValue.of(FactorId.R, 1.0)), thresholds);

            // (0,30*0,5*0,2 + 0,25 + 0,20 + 0,25) / (0,15 + 0,25 + 0,20 + 0,25) = 0,73 / 0,85
            assertThat(ts.rounded()).isEqualTo(0.859);
        }
    }

    @Nested
    @DisplayName("Крайний случай: данных нет совсем (NO_EVIDENCE)")
    class NoEvidence {

        @Test
        @DisplayName("все факторы без данных дают низкий порог и пометку")
        void allFactorsUnknown() {
            TrustScore ts = calculator.compute(weights, List.of(
                    FactorValue.unknown(FactorId.B),
                    FactorValue.unknown(FactorId.N),
                    FactorValue.unknown(FactorId.D),
                    FactorValue.unknown(FactorId.R)), thresholds);

            assertThat(ts.noEvidence()).isTrue();
            assertThat(ts.value())
                    .as("TS приравнивается к θ_L: по алгоритму решения это даёт STEP_UP, а не отказ")
                    .isEqualTo(thresholds.low());
        }

        @Test
        @DisplayName("пустой список факторов тоже означает отсутствие данных")
        void emptyFactorList() {
            TrustScore ts = calculator.compute(weights, List.of(), thresholds);

            assertThat(ts.noEvidence()).isTrue();
            assertThat(ts.value()).isEqualTo(thresholds.low());
        }

        @Test
        @DisplayName("низкий порог берётся из политики, а не из умолчаний")
        void lowThresholdComesFromPolicy() {
            Thresholds stricter = new Thresholds(0.45, 0.75, 0.85);

            TrustScore ts = calculator.compute(weights, List.of(), stricter);

            assertThat(ts.value()).isEqualTo(0.45);
        }
    }

    @Nested
    @DisplayName("Отключённые, неизвестные и пользовательские факторы")
    class FactorSetVariations {

        @Test
        @DisplayName("фактор без веса в политике не участвует в оценке")
        void factorOutsidePolicyIsIgnored() {
            // Политика знает только N и D; присланные B и R игнорируются.
            Weights networkAndDeviceOnly = new Weights(Map.of(
                    FactorId.N, 0.5,
                    FactorId.D, 0.5));

            TrustScore ts = calculator.compute(networkAndDeviceOnly, List.of(
                    FactorValue.of(FactorId.B, 0.0),
                    FactorValue.of(FactorId.N, 0.8),
                    FactorValue.of(FactorId.D, 1.0),
                    FactorValue.of(FactorId.R, 0.0)), thresholds);

            assertThat(ts.rounded()).isEqualTo(0.900);
        }

        @Test
        @DisplayName("фактор с нулевым весом (отключённый переопределением) не участвует в оценке")
        void zeroWeightFactorIsIgnored() {
            Weights behaviorDisabled = new Weights(Map.of(
                    FactorId.B, 0.0,
                    FactorId.N, 0.40,
                    FactorId.D, 0.20,
                    FactorId.R, 0.40));

            TrustScore ts = calculator.compute(behaviorDisabled, List.of(
                    FactorValue.of(FactorId.B, 0.0),
                    FactorValue.of(FactorId.N, 1.0),
                    FactorValue.of(FactorId.D, 1.0),
                    FactorValue.of(FactorId.R, 1.0)), thresholds);

            assertThat(ts.value()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("пользовательский фактор учитывается наравне со встроенными")
        void customFactorParticipates() {
            // Приложение добавило фактор FRAUD с собственным весом в политике.
            FactorId fraud = FactorId.of("FRAUD");
            Weights withFraud = new Weights(Map.of(
                    FactorId.B, 0.25,
                    FactorId.N, 0.20,
                    FactorId.D, 0.15,
                    FactorId.R, 0.20,
                    fraud, 0.20));

            TrustScore ts = calculator.compute(withFraud, List.of(
                    FactorValue.of(FactorId.B, 1.0),
                    FactorValue.of(FactorId.N, 1.0),
                    FactorValue.of(FactorId.D, 1.0),
                    FactorValue.of(FactorId.R, 1.0),
                    FactorValue.of(fraud, 0.0)), thresholds);

            assertThat(ts.rounded()).isEqualTo(0.800);
        }

        @Test
        @DisplayName("перенормировка весов после отключения фактора не меняет оценку")
        void renormalizationDoesNotChangeScore() {
            // disabled_factors обнуляет веса, остальные перенормируются. На результат
            // это не влияет: общий множитель весов сокращается в числителе и знаменателе 4.2.
            List<FactorValue> factors = List.of(
                    FactorValue.of(FactorId.N, 0.8),
                    FactorValue.of(FactorId.D, 1.0),
                    FactorValue.of(FactorId.R, 0.6));

            double withOriginalWeights = calculator.compute(weights, factors, thresholds).value();
            double withRenormalized = calculator
                    .compute(weights.normalizeWithout(Set.of(FactorId.B)), factors, thresholds).value();

            assertThat(withRenormalized).isEqualTo(withOriginalWeights, within(1e-12));
        }
    }

    @Nested
    @DisplayName("Границы и защита от ошибок провайдеров")
    class Boundaries {

        @Test
        @DisplayName("все факторы равны единице — оценка равна единице")
        void allOnes() {
            assertThat(computeFullEvidence(1.0, 1.0, 1.0, 1.0).value()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("все факторы равны нулю — это данные, а не их отсутствие")
        void allZeros() {
            TrustScore ts = computeFullEvidence(0.0, 0.0, 0.0, 0.0);

            assertThat(ts.value()).isZero();
            assertThat(ts.noEvidence()).isFalse();
        }

        @Test
        @DisplayName("недопустимые значения провайдера не участвуют в оценке")
        void invalidProviderValuesAreDropped() {
            // Ошибка в провайдере не роняет запрос, но и не подменяет значение фактора границей:
            // B, N и D — отказ провайдера (c = 0), оценку определяет один фактор R.
            TrustScore ts = computeFullEvidence(5.0, -3.0, Double.NaN, 1.0);

            assertThat(ts.value()).isEqualTo(1.0);
            assertThat(ts.evidenceMass()).isEqualTo(0.25);
            assertThat(ts.effectiveWeights()).containsOnlyKeys(FactorId.R);
        }

        @Test
        @DisplayName("погрешность вычислений за границами отрезка обрезается молча")
        void roundingErrorIsTrimmed() {
            TrustScore ts = computeFullEvidence(1.0 + 1e-10, -1e-10, 1.0, 1.0);

            // B -> 1, N -> 0, D = 1, R = 1: TS = 0,30 + 0,20 + 0,25
            assertThat(ts.rounded()).isEqualTo(0.750);
            assertThat(ts.isFullEvidence()).isTrue();
        }

        @Test
        @DisplayName("один и тот же фактор дважды — ошибка конфигурации")
        void duplicateFactorIsRejected() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.compute(weights, List.of(
                            FactorValue.of(FactorId.B, 0.5),
                            FactorValue.of(FactorId.B, 0.9)), thresholds))
                    .withMessageContaining("дважды");
        }

        @Test
        @DisplayName("не заданные аргументы — ошибка")
        void missingArgumentsAreRejected() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.compute(null, List.of(), thresholds));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.compute(weights, null, thresholds));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.compute(weights, List.of(), null));
        }
    }

    @Nested
    @DisplayName("Итоговые веса факторов с учётом достоверности")
    class EffectiveWeights {

        @Test
        @DisplayName("при полной достоверности итоговые веса совпадают с весами политики")
        void fullConfidenceKeepsPolicyWeights() {
            TrustScore ts = computeFullEvidence(0.95, 0.90, 1.00, 0.95);

            assertThat(ts.effectiveWeightOf(FactorId.B)).isEqualTo(0.30, within(1e-12));
            assertThat(ts.effectiveWeightOf(FactorId.N)).isEqualTo(0.25, within(1e-12));
            assertThat(ts.effectiveWeightOf(FactorId.D)).isEqualTo(0.20, within(1e-12));
            assertThat(ts.effectiveWeightOf(FactorId.R)).isEqualTo(0.25, within(1e-12));
        }

        @Test
        @DisplayName("у нового пользователя весь вес делят между собой сеть и устройство")
        void coldStartRedistributesWeight() {
            TrustScore ts = calculator.compute(weights, List.of(
                    FactorValue.unknown(FactorId.B),
                    FactorValue.of(FactorId.N, 0.9),
                    FactorValue.of(FactorId.D, 0.4),
                    FactorValue.unknown(FactorId.R)), thresholds);

            assertThat(ts.effectiveWeights()).containsOnlyKeys(FactorId.N, FactorId.D);
            assertThat(ts.effectiveWeightOf(FactorId.N)).isEqualTo(0.25 / 0.45, within(1e-12));
            assertThat(ts.effectiveWeightOf(FactorId.D)).isEqualTo(0.20 / 0.45, within(1e-12));
        }

        @Test
        @DisplayName("оценка равна сумме значений факторов, взвешенных итоговыми весами")
        void scoreEqualsWeightedSum() {
            List<FactorValue> factors = List.of(
                    factor(FactorId.B, 0.2, 0.5),
                    FactorValue.of(FactorId.N, 0.9),
                    FactorValue.of(FactorId.D, 1.0),
                    FactorValue.of(FactorId.R, 0.8));

            TrustScore ts = calculator.compute(weights, factors, thresholds);

            double weightSum = 0.0;
            double weightedValues = 0.0;
            for (FactorValue f : factors) {
                weightSum += ts.effectiveWeightOf(f.id());
                weightedValues += ts.effectiveWeightOf(f.id()) * f.value();
            }
            assertThat(weightSum).isEqualTo(1.0, within(1e-12));
            assertThat(weightedValues).isEqualTo(ts.value(), within(1e-12));
        }

        @Test
        @DisplayName("при отсутствии данных разложение по весам пустое")
        void noEvidenceHasNoWeights() {
            assertThat(calculator.compute(weights, List.of(), thresholds).effectiveWeights()).isEmpty();
        }

        @Test
        @DisplayName("разложение неизменяемо")
        void effectiveWeightsAreImmutable() {
            TrustScore ts = computeFullEvidence(1.0, 1.0, 1.0, 1.0);

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> ts.effectiveWeights().put(FactorId.B, 1.0));
        }
    }
}
