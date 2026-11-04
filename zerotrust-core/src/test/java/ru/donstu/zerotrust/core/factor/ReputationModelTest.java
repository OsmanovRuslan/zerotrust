package ru.donstu.zerotrust.core.factor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.donstu.zerotrust.core.model.FactorId;
import ru.donstu.zerotrust.core.model.FactorValue;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/** Тесты фактора R — репутация: затухание штрафа и достоверность. */
class ReputationModelTest {

    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

    private final ReputationModel model = new ReputationModel(ReputationSettings.DEFAULT);

    @ParameterizedTest(name = "штраф 0,30 через {0} ч = {1}")
    @CsvSource({
            "0,   0.300",
            "24,  0.238",
            "72,  0.150",
            "144, 0.075"
    })
    @DisplayName("штраф затухает с периодом полураспада 72 часа")
    void penaltyDecays(long hours, double expected) {
        double penalty = model.decayedPenalty(0.30, NOW.minus(Duration.ofHours(hours)), NOW);

        assertThat(penalty).isEqualTo(expected, within(0.0005));
    }

    @Test
    @DisplayName("попытка доступа к чужому объекту (0,25) опускает репутацию до 0,75")
    void ownershipViolation() {
        double penalty = model.applyEvent(0.0, NOW, 0.25, NOW);
        FactorValue r = model.evaluate(penalty, NOW, NOW.minus(Duration.ofDays(30)), NOW);

        assertThat(penalty).isEqualTo(0.25);
        assertThat(r.value()).isEqualTo(0.75);
        assertThat(r.id()).isEqualTo(FactorId.R);
        assertThat(r.parts()).containsEntry("penalty", 0.25);
    }

    @Test
    @DisplayName("после события IMPOSSIBLE_TRAVEL (0,30) репутация равна 0,7")
    void impossibleTravelEvent() {
        double penalty = model.applyEvent(0.0, NOW, 0.30, NOW);

        assertThat(model.evaluate(penalty, NOW, NOW.minus(Duration.ofDays(30)), NOW).value())
                .isEqualTo(0.7, within(1e-9));
    }

    @Test
    @DisplayName("успешный step-up частично восстанавливает репутацию, но ниже нуля штраф не уходит")
    void stepUpSuccessReducesPenalty() {
        double afterFailure = model.applyEvent(0.0, NOW, 0.10, NOW);
        double afterSuccess = model.applyEvent(afterFailure, NOW, -0.10, NOW);
        double extraSuccess = model.applyEvent(afterSuccess, NOW, -0.10, NOW);

        assertThat(afterFailure).isEqualTo(0.10, within(1e-9));
        assertThat(afterSuccess).isZero();
        assertThat(extraSuccess).isZero();
    }

    @Test
    @DisplayName("события накапливаются: сначала затухание прошлого штрафа, потом новое событие")
    void eventsAccumulate() {
        double first = model.applyEvent(0.0, NOW, 0.30, NOW);
        double second = model.applyEvent(first, NOW, 0.15, NOW.plus(Duration.ofHours(72)));

        assertThat(second).isEqualTo(0.30, within(0.0005));
    }

    @Test
    @DisplayName("штраф не может увести репутацию ниже нуля")
    void reputationIsBounded() {
        FactorValue r = model.evaluate(5.0, NOW, NOW.minus(Duration.ofDays(30)), NOW);

        assertThat(r.value()).isZero();
        assertThat(r.degraded()).isFalse();
    }

    @ParameterizedTest(name = "история {0} дней -> c_R = {1}")
    @CsvSource({
            "0,  0.0",
            "1,  0.143",
            "3,  0.429",
            "7,  1.0",
            "30, 1.0"
    })
    @DisplayName("без штрафа достоверность растёт вместе с историей пользователя")
    void confidenceGrowsWithHistory(long days, double expected) {
        double confidence = model.confidence(0.0, NOW.minus(Duration.ofDays(days)), NOW);

        assertThat(confidence).isEqualTo(expected, within(0.001));
    }

    @Test
    @DisplayName("при заметном штрафе достоверность сразу 1: наказание не ждёт истории")
    void penaltyGivesFullConfidence() {
        assertThat(model.confidence(0.05, NOW, NOW)).isEqualTo(1.0);
        assertThat(model.confidence(0.049, NOW, NOW)).isZero();
        assertThat(model.evaluate(0.20, NOW, NOW, NOW).confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("у нового пользователя фактор R не участвует в оценке")
    void newUserHasNoReputation() {
        FactorValue r = model.evaluate(0.0, null, null, NOW);

        assertThat(r.value()).isEqualTo(1.0);
        assertThat(r.confidence()).isZero();
        assertThat(r.hasEvidence()).isFalse();
    }

    @Test
    @DisplayName("не заданные настройки и время — ошибка")
    void argumentsAreChecked() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ReputationModel(null));
        assertThatIllegalArgumentException().isThrownBy(() -> model.applyEvent(0.1, NOW, 0.1, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReputationSettings(Duration.ZERO, 7, 0.05));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ReputationSettings(Duration.ofHours(72), 0, 0.05));
    }
}
