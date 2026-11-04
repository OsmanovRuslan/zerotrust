package ru.donstu.zerotrust.core.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.donstu.zerotrust.core.model.Action;
import ru.donstu.zerotrust.core.model.RuleOutcome;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Тесты порядка вычисления правил. */
class BuiltInRulesTest {

    @Test
    @DisplayName("порядок встроенных правил задан явно и не зависит от рефлексии")
    void builtInOrder() {
        assertThat(BuiltInRules.ORDER).containsExactly(
                "amount-limit", "time-window", "allowed-networks", "allowed-regions", "rate-limit");
    }

    @ParameterizedTest(name = "ранг правила {0} = {1}")
    @CsvSource({
            "amount-limit,     0",
            "time-window,      1",
            "allowed-networks, 2",
            "allowed-regions,  3",
            "rate-limit,       4",
            "working-days,     5",
            "night-transfer,   5"
    })
    @DisplayName("встроенные правила имеют ранг по порядку, остальные идут после них")
    void rankOf(String ruleId, int expected) {
        assertThat(BuiltInRules.rankOf(ruleId)).isEqualTo(expected);
    }

    @Test
    @DisplayName("правило без идентификатора получает ранг последних")
    void rankOfUnknown() {
        assertThat(BuiltInRules.rankOf(null)).isEqualTo(BuiltInRules.ORDER.size());
    }

    @Test
    @DisplayName("исходы выстраиваются по порядку вычисления, свои правила сохраняют свой порядок")
    void inEvaluationOrder() {
        List<RuleOutcome> mixed = List.of(
                RuleOutcome.pass("working-days"),
                RuleOutcome.pass(BuiltInRules.RATE_LIMIT),
                RuleOutcome.pass("night-transfer"),
                RuleOutcome.pass(BuiltInRules.AMOUNT_LIMIT),
                RuleOutcome.pass(BuiltInRules.TIME_WINDOW));

        List<RuleOutcome> ordered = BuiltInRules.inEvaluationOrder(mixed);

        assertThat(ordered).extracting(RuleOutcome::id).containsExactly(
                "amount-limit", "time-window", "rate-limit", "working-days", "night-transfer");
    }

    @Test
    @DisplayName("пустой и одиночный список остаются как есть")
    void shortLists() {
        assertThat(BuiltInRules.inEvaluationOrder(null)).isEmpty();
        assertThat(BuiltInRules.inEvaluationOrder(List.of())).isEmpty();
        assertThat(BuiltInRules.inEvaluationOrder(List.of(RuleOutcome.pass("one"))))
                .extracting(RuleOutcome::id).containsExactly("one");
    }

    @Test
    @DisplayName("порядок не зависит от того, как правила пришли")
    void orderIsStableForAnyInput() {
        List<RuleOutcome> straight = List.of(
                RuleOutcome.fired(BuiltInRules.AMOUNT_LIMIT, Action.DENY, "сумма"),
                RuleOutcome.fired(BuiltInRules.RATE_LIMIT, Action.DENY, "частота"));
        List<RuleOutcome> reversed = List.of(
                RuleOutcome.fired(BuiltInRules.RATE_LIMIT, Action.DENY, "частота"),
                RuleOutcome.fired(BuiltInRules.AMOUNT_LIMIT, Action.DENY, "сумма"));

        assertThat(BuiltInRules.inEvaluationOrder(straight))
                .extracting(RuleOutcome::id)
                .isEqualTo(BuiltInRules.inEvaluationOrder(reversed).stream()
                        .map(RuleOutcome::id).toList());
    }
}
