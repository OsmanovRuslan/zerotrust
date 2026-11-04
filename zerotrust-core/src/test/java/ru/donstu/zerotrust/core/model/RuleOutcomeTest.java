package ru.donstu.zerotrust.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Тесты исхода правила условий. */
class RuleOutcomeTest {

    @Test
    @DisplayName("правило не сработало: действия нет")
    void passHasNoAction() {
        RuleOutcome outcome = RuleOutcome.pass("time-window");

        assertThat(outcome.isFired()).isFalse();
        assertThat(outcome.action()).isNull();
        assertThat(outcome.isDeny()).isFalse();
        assertThat(outcome.isStepUp()).isFalse();
        assertThat(outcome.isRaise()).isFalse();
    }

    @Test
    @DisplayName("правило запретило вызов")
    void denyOutcome() {
        RuleOutcome outcome = RuleOutcome.fired("amount-limit", Action.DENY, "сумма 2 000 000 выше denyAbove");

        assertThat(outcome.isFired()).isTrue();
        assertThat(outcome.isDeny()).isTrue();
        assertThat(outcome.message()).contains("2 000 000");
    }

    @Test
    @DisplayName("правило потребовало подтверждения вторым фактором")
    void stepUpOutcome() {
        RuleOutcome outcome = RuleOutcome.fired("amount-limit", Action.STEP_UP, null);

        assertThat(outcome.isStepUp()).isTrue();
        assertThat(outcome.raiseTo()).isNull();
    }

    @Test
    @DisplayName("правило подняло требуемый порог — динамическая критичность операции")
    void raiseOutcome() {
        RuleOutcome outcome = RuleOutcome.raise("amount-limit", 0.90, "сумма выше raiseAbove");

        assertThat(outcome.isRaise()).isTrue();
        assertThat(outcome.raiseTo()).isEqualTo(0.90);
    }

    @Test
    @DisplayName("RAISE без порога — ошибка: поднимать требование некуда")
    void raiseWithoutThresholdIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RuleOutcome("amount-limit", Action.RAISE, null, null))
                .withMessageContaining("raiseTo");
    }

    @Test
    @DisplayName("порог поднятия ограничен пределом 0,95")
    void raiseToIsBounded() {
        assertThat(RuleOutcome.raise("r", Thresholds.MAX, null).raiseTo()).isEqualTo(0.95);
        assertThatIllegalArgumentException().isThrownBy(() -> RuleOutcome.raise("r", 0.96, null));
        assertThatIllegalArgumentException().isThrownBy(() -> RuleOutcome.raise("r", 0.0, null));
        assertThatIllegalArgumentException().isThrownBy(() -> RuleOutcome.raise("r", -0.5, null));
    }

    @Test
    @DisplayName("идентификатор правила обязателен: без него исход не найти в журнале")
    void idIsRequired() {
        assertThatIllegalArgumentException().isThrownBy(() -> RuleOutcome.pass(null));
        assertThatIllegalArgumentException().isThrownBy(() -> RuleOutcome.pass("  "));
    }

    @Test
    @DisplayName("действие LOG только записывается и ничего не меняет")
    void logOutcome() {
        RuleOutcome outcome = RuleOutcome.fired("time-window", Action.LOG, "вне рабочего окна");

        assertThat(outcome.isFired()).isTrue();
        assertThat(outcome.isDeny()).isFalse();
        assertThat(outcome.isStepUp()).isFalse();
        assertThat(outcome.isRaise()).isFalse();
    }
}
