package ru.donstu.zerotrust.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Тесты правил политики и адресации отключённых правил. */
class PolicyRuleTest {

    private static PolicyRule rule(List<String> operations) {
        return new PolicyRule("night-transfer", "#amount > 100000", Action.STEP_UP, null,
                "Крупный перевод ночью", operations);
    }

    @ParameterizedTest(name = "маска {0} и операция {1} -> {2}")
    @CsvSource({
            "payments.transfer, payments.transfer, true",
            "payments.transfer, payments.cancel,   false",
            "payments.*,        payments.transfer, true",
            "payments.*,        reports.export,    false",
            "*,                 payments.transfer, true"
    })
    @DisplayName("маска операций поддерживает звёздочку в конце")
    void operationMask(String mask, String operation, boolean expected) {
        assertThat(rule(List.of(mask)).appliesTo(operation)).isEqualTo(expected);
    }

    @Test
    @DisplayName("без масок правило применяется ко всем операциям")
    void emptyMaskAppliesToAll() {
        assertThat(rule(List.of()).appliesTo("payments.transfer")).isTrue();
        assertThat(rule(null).appliesTo("payments.transfer")).isTrue();
    }

    @Test
    @DisplayName("хотя бы одна подходящая маска включает правило")
    void anyMaskIsEnough() {
        PolicyRule withSeveral = rule(List.of("reports.*", "payments.transfer"));

        assertThat(withSeveral.appliesTo("payments.transfer")).isTrue();
        assertThat(withSeveral.appliesTo("reports.export")).isTrue();
        assertThat(withSeveral.appliesTo("accounts.read")).isFalse();
    }

    @Test
    @DisplayName("правило с поднятием порога обязано задать raiseTo в допустимых границах")
    void raiseRequiresThreshold() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PolicyRule("r", "true", Action.RAISE, null, null, List.of()))
                .withMessageContaining("raiseTo");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PolicyRule("r", "true", Action.RAISE, 0.99, null, List.of()));
        assertThat(new PolicyRule("r", "true", Action.RAISE, 0.95, null, List.of()).raiseTo())
                .isEqualTo(0.95);
    }

    @Test
    @DisplayName("без идентификатора, условия или действия правило не создаётся")
    void requiredFields() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PolicyRule(" ", "true", Action.LOG, null, null, List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PolicyRule("r", " ", Action.LOG, null, null, List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PolicyRule("r", "true", null, null, null, List.of()));
    }

    @ParameterizedTest(name = "адрес {0} отключает правило {1} в операции {2}: {3}")
    @CsvSource({
            "time-window,                      time-window,  reports.export, true",
            "time-window@reports.export,       time-window,  reports.export, true",
            "time-window@reports.export,       time-window,  payments.pay,   false",
            "*@reports.export,                 time-window,  reports.export, true",
            "*@reports.export,                 amount-limit, reports.export, true",
            "amount-limit,                     time-window,  reports.export, false",
            "*,                                time-window,  reports.export, true",
            "*@*,                              amount-limit, payments.pay,   true"
    })
    @DisplayName("формы адреса отключённого правила, включая «все правила везде»")
    void disabledRuleAddressing(String disabled, String ruleId, String operation, boolean expected) {
        EffectivePolicy policy = new EffectivePolicy("default", Weights.DEFAULT, Thresholds.DEFAULT,
                Enforcement.ENFORCE, Set.of(), Set.of(disabled),
                List.of(), List.of());

        assertThat(policy.isRuleDisabled(ruleId, operation)).isEqualTo(expected);
    }

    @Test
    @DisplayName("без идентификатора правила и операции отключение не срабатывает")
    void disabledRuleEdgeCases() {
        EffectivePolicy policy = new EffectivePolicy("default", Weights.DEFAULT, Thresholds.DEFAULT,
                Enforcement.ENFORCE, Set.of(), Set.of("*@reports.export"),
                List.of(), List.of());

        assertThat(policy.isRuleDisabled(null, "reports.export")).isFalse();
        assertThat(policy.isRuleDisabled("time-window", null)).isFalse();
    }

    @Test
    @DisplayName("«все правила везде» действует и без имени операции")
    void allRulesDisabled() {
        EffectivePolicy policy = new EffectivePolicy("default", Weights.DEFAULT, Thresholds.DEFAULT,
                Enforcement.ENFORCE, Set.of(), Set.of("*"),
                List.of(), List.of());

        assertThat(policy.isRuleDisabled("time-window", null)).isTrue();
        assertThat(policy.isRuleDisabled(null, "reports.export")).isFalse();
    }
}
