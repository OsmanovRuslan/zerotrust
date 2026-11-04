package ru.donstu.zerotrust.core.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.donstu.zerotrust.core.model.Action;
import ru.donstu.zerotrust.core.model.Level;
import ru.donstu.zerotrust.core.model.RuleOutcome;
import ru.donstu.zerotrust.core.model.Thresholds;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Тесты требуемого порога θ_req — динамической критичности операции (формула требуемого порога). */
class ThresholdResolverTest {

    private final ThresholdResolver resolver = new ThresholdResolver();
    private final Thresholds thresholds = Thresholds.DEFAULT;

    @Test
    @DisplayName("без правил порог берётся из уровня операции")
    void baseThresholdByLevel() {
        assertThat(resolver.resolve(thresholds, Level.NORMAL, List.of())).isEqualTo(0.70);
        assertThat(resolver.resolve(thresholds, Level.CRITICAL, List.of())).isEqualTo(0.80);
        assertThat(resolver.resolve(thresholds, Level.NORMAL, null)).isEqualTo(0.70);
    }

    @Test
    @DisplayName("на уровне AUTH_ONLY порога не существует: оценка доверия не считается")
    void authOnlyHasNoThreshold() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolver.resolve(thresholds, Level.AUTH_ONLY, List.of()));
    }

    @Test
    @DisplayName("правило поднимает порог выше уровня операции")
    void ruleRaisesThreshold() {
        List<RuleOutcome> rules = List.of(
                RuleOutcome.pass("time-window"),
                RuleOutcome.raise("amount-limit", 0.85, "сумма выше raiseAbove"));

        assertThat(resolver.resolve(thresholds, Level.NORMAL, rules)).isEqualTo(0.85);
    }

    @Test
    @DisplayName("из нескольких поднятий берётся самое высокое")
    void highestRaiseWins() {
        List<RuleOutcome> rules = List.of(
                RuleOutcome.raise("amount-limit", 0.75, "сумма"),
                RuleOutcome.raise("night-work", 0.90, "ночь"),
                RuleOutcome.raise("new-recipient", 0.82, "новый получатель"));

        assertThat(resolver.resolve(thresholds, Level.NORMAL, rules)).isEqualTo(0.90);
    }

    @Test
    @DisplayName("поднятие ниже порога уровня ничего не меняет")
    void lowerRaiseIsIgnored() {
        List<RuleOutcome> rules = List.of(RuleOutcome.raise("amount-limit", 0.50, "небольшая сумма"));

        assertThat(resolver.resolve(thresholds, Level.CRITICAL, rules)).isEqualTo(0.80);
    }

    @Test
    @DisplayName("порог ограничен сверху 0,95: требование не должно стать невыполнимым")
    void thresholdIsCapped() {
        List<RuleOutcome> rules = List.of(RuleOutcome.raise("paranoid", 0.95, "предел"));

        assertThat(resolver.resolve(thresholds, Level.CRITICAL, rules)).isEqualTo(Thresholds.MAX);
    }

    @Test
    @DisplayName("не сработавшие правила и правила других действий порог не меняют")
    void onlyRaiseMatters() {
        List<RuleOutcome> rules = List.of(
                RuleOutcome.pass("allowed-networks"),
                RuleOutcome.fired("time-window", Action.STEP_UP, "ночь"),
                RuleOutcome.fired("audit", Action.LOG, "записать"));

        assertThat(resolver.resolve(thresholds, Level.NORMAL, rules)).isEqualTo(0.70);
    }

    @Test
    @DisplayName("пороги политики, а не умолчания: payments строже")
    void policyThresholdsAreUsed() {
        Thresholds payments = new Thresholds(0.45, 0.75, 0.85);

        assertThat(resolver.resolve(payments, Level.NORMAL, List.of())).isEqualTo(0.75);
        assertThat(resolver.resolve(payments, Level.CRITICAL, List.of())).isEqualTo(0.85);
    }

    @Test
    @DisplayName("без порогов политики расчёт невозможен")
    void thresholdsAreRequired() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolver.resolve(null, Level.NORMAL, List.of()));
    }
}
