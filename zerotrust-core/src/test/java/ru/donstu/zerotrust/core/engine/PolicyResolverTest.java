package ru.donstu.zerotrust.core.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.donstu.zerotrust.core.model.EffectivePolicy;
import ru.donstu.zerotrust.core.model.Enforcement;
import ru.donstu.zerotrust.core.model.FactorId;
import ru.donstu.zerotrust.core.model.Level;
import ru.donstu.zerotrust.core.model.PolicyOverride;
import ru.donstu.zerotrust.core.model.PolicyDefinition;
import ru.donstu.zerotrust.core.model.Subject;
import ru.donstu.zerotrust.core.model.SubjectType;
import ru.donstu.zerotrust.core.model.Thresholds;
import ru.donstu.zerotrust.core.model.Weights;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/** Тесты слияния политики и переопределений. */
class PolicyResolverTest {

    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

    private final PolicyResolver resolver = new PolicyResolver();
    private final PolicyDefinition payments = PolicyDefinition.of("payments", Weights.DEFAULT,
            new Thresholds(0.45, 0.75, 0.85));
    private final Subject carol = Subject.of("carol", Set.of("ACCOUNTANT", "USER"), Set.of());

    /** Переопределение, действующее сейчас. */
    private static Builder override(SubjectType type, String subjectId) {
        return new Builder(type, subjectId);
    }

    private static final class Builder {

        private final UUID id = UUID.randomUUID();
        private final SubjectType type;
        private final String subjectId;
        private String policyName;
        private Weights weights;
        private Double low;
        private Double high;
        private Double critical;
        private Set<FactorId> disabledFactors = Set.of();
        private Set<String> disabledRules = Set.of();
        private Enforcement enforcement;
        private int priority;
        private Instant createdAt = NOW.minus(Duration.ofDays(1));
        private Instant validFrom = NOW.minus(Duration.ofDays(1));
        private Instant validUntil = NOW.plus(Duration.ofDays(30));
        private Instant revokedAt;

        private Builder(SubjectType type, String subjectId) {
            this.type = type;
            this.subjectId = subjectId;
        }

        Builder policy(String value) {
            this.policyName = value;
            return this;
        }

        Builder weights(Weights value) {
            this.weights = value;
            return this;
        }

        Builder thresholds(Double low, Double high, Double critical) {
            this.low = low;
            this.high = high;
            this.critical = critical;
            return this;
        }

        Builder disabledFactors(Set<FactorId> value) {
            this.disabledFactors = value;
            return this;
        }

        Builder disabledRules(Set<String> value) {
            this.disabledRules = value;
            return this;
        }

        Builder enforcement(Enforcement value) {
            this.enforcement = value;
            return this;
        }

        Builder priority(int value) {
            this.priority = value;
            return this;
        }

        Builder createdAt(Instant value) {
            this.createdAt = value;
            return this;
        }

        Builder validity(Instant from, Instant until) {
            this.validFrom = from;
            this.validUntil = until;
            return this;
        }

        Builder revoked() {
            this.revokedAt = NOW.minus(Duration.ofHours(1));
            return this;
        }

        PolicyOverride build() {
            return new PolicyOverride(id, type, subjectId, policyName, weights, low, high, critical,
                    disabledFactors, disabledRules, enforcement, priority, "Причина переопределения",
                    validFrom, validUntil, createdAt, revokedAt);
        }
    }

    @Test
    @DisplayName("без переопределений действующая политика повторяет политику из хранилища")
    void noOverrides() {
        EffectivePolicy effective = resolver.resolve(payments, List.of(), carol, NOW);

        assertThat(effective.name()).isEqualTo("payments");
        assertThat(effective.weights()).isEqualTo(Weights.DEFAULT);
        assertThat(effective.thresholds()).isEqualTo(new Thresholds(0.45, 0.75, 0.85));
        assertThat(effective.appliedOverrides()).isEmpty();
        assertThat(effective.isEnforced()).isTrue();
        assertThat(effective.baseThresholdFor(Level.CRITICAL)).isEqualTo(0.85);
    }

    @Test
    @DisplayName("переопределение роли отключает правило и попадает в журнал")
    void roleOverrideDisablesRule() {
        PolicyOverride nightWork = override(SubjectType.ROLE, "ACCOUNTANT")
                .disabledRules(Set.of("time-window@reports.export"))
                .build();

        EffectivePolicy effective = resolver.resolve(payments, List.of(nightWork), carol, NOW);

        assertThat(effective.isRuleDisabled("time-window", "reports.export")).isTrue();
        assertThat(effective.isRuleDisabled("time-window", "payments.transfer")).isFalse();
        assertThat(effective.appliedOverrides()).containsExactly(nightWork.id());
    }

    @Test
    @DisplayName("переопределение пользователя сильнее переопределения роли")
    void userOverrideBeatsRole() {
        PolicyOverride role = override(SubjectType.ROLE, "ACCOUNTANT").thresholds(null, 0.90, null).build();
        PolicyOverride user = override(SubjectType.USER, "carol").thresholds(null, 0.60, null).build();

        EffectivePolicy effective = resolver.resolve(payments, List.of(role, user), carol, NOW);

        assertThat(effective.thresholds().high()).isEqualTo(0.60);
        assertThat(effective.appliedOverrides()).containsExactly(role.id(), user.id());
    }

    @Test
    @DisplayName("из нескольких ролей выигрывает переопределение с большим приоритетом")
    void highestPriorityWins() {
        PolicyOverride accountant = override(SubjectType.ROLE, "ACCOUNTANT")
                .priority(10).thresholds(null, 0.60, null).build();
        PolicyOverride user = override(SubjectType.ROLE, "USER")
                .priority(1).thresholds(null, 0.90, null).build();

        EffectivePolicy effective = resolver.resolve(payments, List.of(accountant, user), carol, NOW);

        assertThat(effective.thresholds().high()).isEqualTo(0.60);
        assertThat(effective.appliedOverrides()).containsExactly(accountant.id());
    }

    @Test
    @DisplayName("при равном приоритете берётся более строгий порог")
    void equalPriorityTakesStricter() {
        PolicyOverride first = override(SubjectType.ROLE, "ACCOUNTANT").thresholds(0.50, 0.80, null).build();
        PolicyOverride second = override(SubjectType.ROLE, "USER").thresholds(0.46, 0.84, null).build();

        EffectivePolicy effective = resolver.resolve(payments, List.of(first, second), carol, NOW);

        assertThat(effective.thresholds().low()).isEqualTo(0.50);
        assertThat(effective.thresholds().high()).isEqualTo(0.84);
        assertThat(effective.appliedOverrides()).hasSize(2);
    }

    @Test
    @DisplayName("при равном приоритете веса берутся из созданного раньше")
    void equalPriorityTakesEarliestWeights() {
        Weights early = Weights.of(0.40, 0.20, 0.20, 0.20);
        Weights late = Weights.of(0.10, 0.30, 0.30, 0.30);
        PolicyOverride first = override(SubjectType.ROLE, "ACCOUNTANT")
                .weights(early).createdAt(NOW.minus(Duration.ofDays(5))).build();
        PolicyOverride second = override(SubjectType.ROLE, "USER")
                .weights(late).createdAt(NOW.minus(Duration.ofDays(1))).build();

        EffectivePolicy effective = resolver.resolve(payments, List.of(second, first), carol, NOW);

        assertThat(effective.weights()).isEqualTo(early);
        assertThat(effective.appliedOverrides()).containsExactly(first.id(), second.id());
    }

    @Test
    @DisplayName("отключённые факторы обнуляют вес, остальные перенормируются к сумме 1")
    void disabledFactorsAreRenormalized() {
        PolicyOverride withoutBehavior = override(SubjectType.USER, "carol")
                .disabledFactors(Set.of(FactorId.B))
                .build();

        EffectivePolicy effective = resolver.resolve(payments, List.of(withoutBehavior), carol, NOW);

        assertThat(effective.weights().weightOf(FactorId.B)).isZero();
        assertThat(effective.weights().weightOf(FactorId.N)).isEqualTo(0.25 / 0.70, within(1e-9));
        assertThat(effective.disabledFactors()).containsExactly(FactorId.B);
    }

    @Test
    @DisplayName("отключения правил и факторов объединяются: вернуть отключённое нельзя")
    void disabledSetsAreMerged() {
        PolicyOverride role = override(SubjectType.ROLE, "ACCOUNTANT")
                .disabledRules(Set.of("time-window")).build();
        PolicyOverride user = override(SubjectType.USER, "carol")
                .disabledRules(Set.of("amount-limit")).build();

        EffectivePolicy effective = resolver.resolve(payments, List.of(role, user), carol, NOW);

        assertThat(effective.disabledRules()).containsExactlyInAnyOrder("time-window", "amount-limit");
    }

    @Test
    @DisplayName("не подходящие переопределения отбрасываются: срок, отзыв, политика, субъект")
    void inapplicableOverridesAreIgnored() {
        List<PolicyOverride> overrides = List.of(
                override(SubjectType.USER, "carol").thresholds(null, 0.60, null)
                        .validity(NOW.plus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(2))).build(),
                override(SubjectType.USER, "carol").thresholds(null, 0.61, null)
                        .validity(NOW.minus(Duration.ofDays(5)), NOW.minus(Duration.ofDays(1))).build(),
                override(SubjectType.USER, "carol").thresholds(null, 0.62, null).revoked().build(),
                override(SubjectType.USER, "carol").thresholds(null, 0.63, null).policy("default").build(),
                override(SubjectType.USER, "bob").thresholds(null, 0.64, null).build(),
                override(SubjectType.ROLE, "ADMIN").thresholds(null, 0.65, null).build());

        EffectivePolicy effective = resolver.resolve(payments, overrides, carol, NOW);

        assertThat(effective.thresholds().high()).isEqualTo(0.75);
        assertThat(effective.appliedOverrides()).isEmpty();
    }

    @Test
    @DisplayName("переопределение без имени политики действует на все политики")
    void overrideWithoutPolicyAppliesEverywhere() {
        PolicyOverride any = override(SubjectType.USER, "carol").thresholds(null, 0.60, null).build();
        PolicyOverride onlyPayments = override(SubjectType.USER, "carol").policy("payments")
                .thresholds(null, 0.61, null).priority(1).build();

        assertThat(resolver.resolve(payments, List.of(any), carol, NOW).thresholds().high())
                .isEqualTo(0.60);
        assertThat(resolver.resolve(payments, List.of(any, onlyPayments), carol, NOW).thresholds().high())
                .isEqualTo(0.61);
    }

    @Test
    @DisplayName("режим наблюдения приходит из переопределения, ENFORCE строже MONITOR")
    void enforcementOverride() {
        PolicyOverride monitor = override(SubjectType.USER, "carol").enforcement(Enforcement.MONITOR).build();
        PolicyOverride enforce = override(SubjectType.ROLE, "USER").enforcement(Enforcement.ENFORCE).build();
        PolicyOverride alsoMonitor = override(SubjectType.ROLE, "ACCOUNTANT")
                .enforcement(Enforcement.MONITOR).build();

        assertThat(resolver.resolve(payments, List.of(monitor), carol, NOW).enforcement())
                .isEqualTo(Enforcement.MONITOR);
        assertThat(resolver.resolve(payments, List.of(enforce, alsoMonitor), carol, NOW).enforcement())
                .isEqualTo(Enforcement.ENFORCE);
    }

    @Test
    @DisplayName("переопределение, ломающее порядок порогов, отвергается с понятной ошибкой")
    void brokenThresholdOrderIsRejected() {
        PolicyOverride broken = override(SubjectType.USER, "carol").thresholds(null, 0.40, null).build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolver.resolve(payments, List.of(broken), carol, NOW))
                .withMessageContaining("ломают порядок порогов");
    }

    @Test
    @DisplayName("переопределение проверяет срок, обоснование и диапазон порогов")
    void overrideIsValidated() {
        assertThatIllegalArgumentException()
                .as("обоснование короче 5 символов")
                .isThrownBy(() -> new PolicyOverride(UUID.randomUUID(), SubjectType.USER, "carol", null,
                        null, null, null, null, Set.of(), Set.of(), null, 0, "нет",
                        NOW, NOW.plus(Duration.ofDays(1)), NOW, null));
        assertThatIllegalArgumentException()
                .as("конец срока раньше начала")
                .isThrownBy(() -> new PolicyOverride(UUID.randomUUID(), SubjectType.USER, "carol", null,
                        null, null, null, null, Set.of(), Set.of(), null, 0, "Причина",
                        NOW, NOW.minus(Duration.ofDays(1)), NOW, null));
        assertThatIllegalArgumentException()
                .as("порог выше предела 0,95")
                .isThrownBy(() -> new PolicyOverride(UUID.randomUUID(), SubjectType.USER, "carol", null,
                        null, null, 0.99, null, Set.of(), Set.of(), null, 0, "Причина",
                        NOW, NOW.plus(Duration.ofDays(1)), NOW, null));
    }

    @Test
    @DisplayName("без политики и времени слияние невозможно")
    void argumentsAreChecked() {
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.resolve(null, List.of(), carol, NOW));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolver.resolve(payments, List.of(), carol, null));
        assertThatIllegalArgumentException()
                .as("имя политики ограничено тем же шаблоном, что и в таблице zt_policy")
                .isThrownBy(() -> PolicyDefinition.of("Payments!", Weights.DEFAULT, Thresholds.DEFAULT));
    }
}
