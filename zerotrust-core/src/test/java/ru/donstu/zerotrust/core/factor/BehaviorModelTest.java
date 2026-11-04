package ru.donstu.zerotrust.core.factor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.donstu.zerotrust.core.model.FactorId;
import ru.donstu.zerotrust.core.model.FactorValue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/** Тесты фактора B — поведение: час суток, частота запросов, новизна операции. */
class BehaviorModelTest {

    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
    private static final String OPERATION = "PaymentController.transfer";

    private final BehaviorSettings settings = BehaviorSettings.DEFAULT;
    private final BehaviorModel model = new BehaviorModel(settings);

    private BehaviorProfile newProfile() {
        return BehaviorProfile.initial(settings, NOW);
    }

    /** Профиль с заданной долей активности в часах hour-1, hour и hour+1. */
    private BehaviorProfile profileWithHourShare(int hour, double share) {
        List<Double> histogram = new ArrayList<>(Collections.nCopies(BehaviorProfile.HOURS, 0.0));
        double rest = (1.0 - 3 * share) / (BehaviorProfile.HOURS - 3);
        for (int i = 0; i < BehaviorProfile.HOURS; i++) {
            histogram.set(i, rest);
        }
        histogram.set((hour + 23) % 24, share);
        histogram.set(hour, share);
        histogram.set((hour + 1) % 24, share);
        return new BehaviorProfile(100, histogram, settings.rateInitialMean(),
                settings.rateInitialVariance(), null, 0, 0, Map.of(OPERATION, NOW), NOW);
    }

    @Nested
    @DisplayName("Признак «час суток»")
    class TimeScore {

        @ParameterizedTest(name = "час {0}")
        @ValueSource(ints = {0, 3, 10, 12, 23})
        @DisplayName("у нового пользователя гистограмма равномерная и любой час обычный")
        void uniformHistogramGivesOne(int hour) {
            assertThat(model.timeScore(newProfile(), hour)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("после обучения на одном часе соседние часы остаются обычными, далёкие — нет")
        void trainedProfile() {
            BehaviorProfile profile = newProfile();
            for (int i = 0; i < 100; i++) {
                profile = model.recordSuccess(profile, 10, OPERATION, NOW);
            }

            assertThat(model.timeScore(profile, 10)).isEqualTo(1.0);
            assertThat(model.timeScore(profile, 9)).isEqualTo(1.0);
            assertThat(model.timeScore(profile, 3))
                    .as("ночь для этого пользователя не характерна")
                    .isLessThan(0.05);
        }

        @Test
        @DisplayName("сумма гистограммы остаётся единицей после любого числа обновлений")
        void histogramKeepsSumOne() {
            BehaviorProfile profile = newProfile();
            for (int i = 0; i < 500; i++) {
                profile = model.recordSuccess(profile, i % 7 + 8, OPERATION, NOW);
            }

            double sum = profile.hourHistogram().stream().mapToDouble(Double::doubleValue).sum();
            assertThat(sum).isEqualTo(1.0, within(1e-9));
        }

        @Test
        @DisplayName("час 0 сглаживается часом 23: сутки замкнуты в кольцо")
        void hoursAreRing() {
            BehaviorProfile profile = profileWithHourShare(0, 0.3);

            assertThat(profile.hour(23)).isEqualTo(0.3);
            assertThat(model.timeScore(profile, 0)).isEqualTo(1.0);
            assertThat(model.timeScore(profile, 12)).isLessThan(1.0);
        }

        @Test
        @DisplayName("час вне диапазона 0..23 — ошибка")
        void hourIsChecked() {
            assertThatIllegalArgumentException().isThrownBy(() -> model.timeScore(newProfile(), 24));
            assertThatIllegalArgumentException().isThrownBy(() -> model.timeScore(newProfile(), -1));
        }
    }

    @Nested
    @DisplayName("Признак «частота запросов»")
    class RateScore {

        @ParameterizedTest(name = "{0} запросов в минуту -> z = {1}, s_rate = {2}")
        @CsvSource({
                "1,   -0.5, 1.0",
                "2,   0.0,  1.0",
                "6,   2.0,  1.0",
                "10,  4.0,  0.5",
                "14,  6.0,  0.0",
                "300, 149,  0.0"
        })
        @DisplayName("норма до двух сигм, нуль от шести сигм, между ними линейно")
        void rateScoreByZ(long requests, double expectedZ, double expectedScore) {
            BehaviorProfile profile = newProfile();

            assertThat(model.zScore(profile, requests)).isEqualTo(expectedZ, within(1e-9));
            assertThat(model.rateScore(profile, requests)).isEqualTo(expectedScore, within(1e-9));
        }

        @Test
        @DisplayName("отклонение меньше единицы считается единицей: иначе ровный профиль даёт нули")
        void deviationHasFloor() {
            BehaviorProfile steady = new BehaviorProfile(100,
                    Collections.nCopies(BehaviorProfile.HOURS, 1.0 / 24), 2.0, 0.01,
                    null, 0, 0, Map.of(), NOW);

            assertThat(steady.rateDeviation()).isEqualTo(0.1);
            assertThat(model.zScore(steady, 5)).isEqualTo(3.0, within(1e-9));
        }
    }

    @Nested
    @DisplayName("Признак «новизна операции»")
    class ResourceScore {

        @Test
        @DisplayName("знакомая операция даёт 1, незнакомая — 0,5")
        void knownAndUnknownOperation() {
            BehaviorProfile profile = new BehaviorProfile(100,
                    Collections.nCopies(BehaviorProfile.HOURS, 1.0 / 24), 2, 4, null, 0, 0,
                    Map.of(OPERATION, NOW.minus(Duration.ofDays(1))), NOW);

            assertThat(model.resourceScore(profile, OPERATION, NOW)).isEqualTo(1.0);
            assertThat(model.resourceScore(profile, "ReportController.export", NOW)).isEqualTo(0.5);
            assertThat(model.resourceScore(profile, null, NOW)).isEqualTo(0.5);
        }

        @ParameterizedTest(name = "последний вызов {0} дней назад -> s_res = {1}")
        @CsvSource({"1, 1.0", "89, 1.0", "90, 1.0", "91, 0.5", "365, 0.5"})
        @DisplayName("операция помнится 90 дней")
        void operationMemory(long days, double expected) {
            BehaviorProfile profile = new BehaviorProfile(100,
                    Collections.nCopies(BehaviorProfile.HOURS, 1.0 / 24), 2, 4, null, 0, 0,
                    Map.of(OPERATION, NOW.minus(Duration.ofDays(days))), NOW);

            assertThat(model.resourceScore(profile, OPERATION, NOW)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Итог фактора и достоверность")
    class FactorValueCases {

        @ParameterizedTest(name = "{0} успешных вызовов -> c_B = {1}")
        @CsvSource({"0, 0.0", "25, 0.5", "49, 0.98", "50, 1.0", "500, 1.0"})
        @DisplayName("достоверность растёт до 50 успешных вызовов")
        void confidenceGrowsWithProfile(long samples, double expected) {
            BehaviorProfile profile = new BehaviorProfile(samples,
                    Collections.nCopies(BehaviorProfile.HOURS, 1.0 / 24), 2, 4, null, 0, 0,
                    Map.of(), NOW);

            assertThat(model.confidence(profile)).isEqualTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("ночная выгрузка отчёта даёт B = 0,378")
        void nightlyExport() {
            // s_time = 0,1 (ночь нехарактерна), z = 5 -> s_rate = 0,25, операция знакомая
            BehaviorProfile profile = profileWithHourShare(3, 0.1 * settings.hourKappa() / 24);

            FactorValue b = model.evaluate(profile, OPERATION, 3, 12, NOW);

            assertThat(b.parts().get("time")).isEqualTo(0.1, within(1e-9));
            assertThat(b.parts().get("rate")).isEqualTo(0.25, within(1e-9));
            assertThat(b.parts()).containsEntry("res", 1.0);
            assertThat(b.value()).isEqualTo(0.3775, within(1e-9));
            assertThat(b.id()).isEqualTo(FactorId.B);
        }

        @Test
        @DisplayName("скрипт с 300 запросами в минуту даёт B = 0,65")
        void scriptedScraping() {
            BehaviorProfile profile = new BehaviorProfile(100,
                    Collections.nCopies(BehaviorProfile.HOURS, 1.0 / 24), 2, 4, null, 0, 0,
                    Map.of(OPERATION, NOW), NOW);

            FactorValue b = model.evaluate(profile, OPERATION, 12, 300, NOW);

            assertThat(b.parts()).containsEntry("time", 1.0).containsEntry("rate", 0.0)
                    .containsEntry("res", 1.0);
            assertThat(b.value()).isEqualTo(0.65, within(1e-9));
            assertThat(b.confidence()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("у нового пользователя фактор B не участвует в оценке")
        void newUser() {
            FactorValue b = model.evaluate(newProfile(), OPERATION, 12, 1, NOW);

            assertThat(b.confidence()).isZero();
            assertThat(b.hasEvidence()).isFalse();
            assertThat(b.value()).isEqualTo(0.4 + 0.35 + 0.25 * 0.5, within(1e-9));
        }
    }

    @Nested
    @DisplayName("Обновление профиля")
    class ProfileUpdate {

        @Test
        @DisplayName("счётчик минуты растёт на каждом вызове, в том числе отклонённом")
        void countsEveryRequest() {
            BehaviorProfile profile = model.countRequest(newProfile(), NOW);
            profile = model.countRequest(profile, NOW.plusSeconds(10));

            assertThat(profile.currentMinuteCount()).isEqualTo(2);
            assertThat(profile.currentMinuteSuccesses()).isZero();
            assertThat(profile.sampleCount()).isZero();
        }

        @Test
        @DisplayName("смена минуты обновляет среднее и дисперсию по закрытой минуте")
        void closedMinuteUpdatesStatistics() {
            BehaviorProfile profile = newProfile();
            for (int i = 0; i < 12; i++) {
                profile = model.countRequest(profile, NOW.plusSeconds(i));
            }
            profile = model.recordSuccess(profile, 12, OPERATION, NOW);

            BehaviorProfile nextMinute = model.countRequest(profile, NOW.plusSeconds(90));

            // delta = 12 - 2 = 10: mean = 2 + 0,1*10 = 3, var = 0,9*(4 + 0,1*100) = 12,6
            assertThat(nextMinute.rateMean()).isEqualTo(3.0, within(1e-9));
            assertThat(nextMinute.rateVariance()).isEqualTo(12.6, within(1e-9));
            assertThat(nextMinute.currentMinuteCount()).isEqualTo(1);
            assertThat(nextMinute.currentMinuteSuccesses()).isZero();
        }

        @Test
        @DisplayName("минута без разрешённых вызовов статистику не меняет")
        void deniedMinuteDoesNotTeachProfile() {
            BehaviorProfile profile = newProfile();
            for (int i = 0; i < 50; i++) {
                profile = model.countRequest(profile, NOW.plusSeconds(i % 60));
            }

            BehaviorProfile nextMinute = model.countRequest(profile, NOW.plusSeconds(90));

            assertThat(nextMinute.rateMean()).isEqualTo(settings.rateInitialMean());
            assertThat(nextMinute.rateVariance()).isEqualTo(settings.rateInitialVariance());
        }

        @Test
        @DisplayName("разрешённый вызов увеличивает историю и запоминает операцию")
        void recordSuccess() {
            BehaviorProfile profile = model.recordSuccess(newProfile(), 12, OPERATION, NOW);

            assertThat(profile.sampleCount()).isEqualTo(1);
            assertThat(profile.currentMinuteSuccesses()).isEqualTo(1);
            assertThat(profile.knownOperations()).containsEntry(OPERATION, NOW);
            assertThat(model.resourceScore(profile, OPERATION, NOW)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("словарь операций ограничен: вытесняется самая давняя")
        void oldestOperationIsEvicted() {
            BehaviorSettings small = new BehaviorSettings(0.40, 0.35, 0.25, 0.05, 0.5, 0.10, 2, 4,
                    2.0, 6.0, 50, 2, Duration.ofDays(90), 0.5);
            BehaviorModel narrowModel = new BehaviorModel(small);

            BehaviorProfile profile = BehaviorProfile.initial(small, NOW);
            profile = narrowModel.recordSuccess(profile, 12, "first", NOW);
            profile = narrowModel.recordSuccess(profile, 12, "second", NOW.plusSeconds(60));
            profile = narrowModel.recordSuccess(profile, 12, "third", NOW.plusSeconds(120));

            assertThat(profile.knownOperations()).containsOnlyKeys("second", "third");
        }

        @Test
        @DisplayName("первый вызов запоминается, если профиль создан без даты")
        void firstSeenIsFilled() {
            BehaviorProfile withoutDate = new BehaviorProfile(0,
                    Collections.nCopies(BehaviorProfile.HOURS, 1.0 / 24), 2, 4, null, 0, 0,
                    Map.of(), null);

            assertThat(model.recordSuccess(withoutDate, 12, OPERATION, NOW).firstSeen()).isEqualTo(NOW);
        }
    }

    @Test
    @DisplayName("профиль проверяет гистограмму, а настройки — внутренние веса")
    void argumentsAreChecked() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BehaviorModel(null));
        assertThatIllegalArgumentException().isThrownBy(() -> model.evaluate(null, OPERATION, 12, 1, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> new BehaviorProfile(0,
                List.of(1.0), 2, 4, null, 0, 0, Map.of(), NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> new BehaviorProfile(0,
                Collections.nCopies(BehaviorProfile.HOURS, -0.1), 2, 4, null, 0, 0, Map.of(), NOW));
        assertThatIllegalArgumentException()
                .as("внутренние веса фактора B должны давать в сумме 1")
                .isThrownBy(() -> new BehaviorSettings(0.5, 0.35, 0.25, 0.05, 0.5, 0.10, 2, 4,
                        2.0, 6.0, 50, 500, Duration.ofDays(90), 0.5));
    }
}
