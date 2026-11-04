package ru.donstu.zerotrust.core.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.donstu.zerotrust.core.model.Action;
import ru.donstu.zerotrust.core.model.Decision;
import ru.donstu.zerotrust.core.model.DecisionReason;
import ru.donstu.zerotrust.core.model.DecisionResult;
import ru.donstu.zerotrust.core.model.EffectivePolicy;
import ru.donstu.zerotrust.core.model.Enforcement;
import ru.donstu.zerotrust.core.model.FactorId;
import ru.donstu.zerotrust.core.model.FactorValue;
import ru.donstu.zerotrust.core.model.InvocationContext;
import ru.donstu.zerotrust.core.model.Level;
import ru.donstu.zerotrust.core.model.RuleOutcome;
import ru.donstu.zerotrust.core.model.Subject;
import ru.donstu.zerotrust.core.model.Thresholds;
import ru.donstu.zerotrust.core.model.Weights;
import ru.donstu.zerotrust.core.rule.BuiltInRules;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/** Тесты алгоритма решения по всем ветвям. */
class DecisionEngineTest {

    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

    private final DecisionEngine engine = new DecisionEngine();

    private static InvocationContext context(Level level) {
        return InvocationContext.builder()
                .requestId("req-1")
                .subject(Subject.of("alice", Set.of("USER"), Set.of("data:read")))
                .operation("PaymentController.transfer")
                .policyName("default")
                .level(level)
                .timestamp(NOW)
                .build();
    }

    private static EffectivePolicy policy(Enforcement enforcement) {
        return new EffectivePolicy("default", Weights.DEFAULT, Thresholds.DEFAULT, enforcement,
                Set.of(), Set.of(), List.of(), List.of());
    }

    private static DecisionRequest.Builder request(Level level) {
        return DecisionRequest.builder(context(level), policy(Enforcement.ENFORCE));
    }

    /**
     * Политика с единственным фактором: оценка доверия равна его значению точно, без
     * погрешности сложения, поэтому на ней проверяются граничные сравнения с порогом.
     */
    private static DecisionRequest.Builder requestWithSingleFactor(Level level, double n) {
        EffectivePolicy single = new EffectivePolicy("default", new Weights(Map.of(FactorId.N, 1.0)),
                Thresholds.DEFAULT, Enforcement.ENFORCE, Set.of(), Set.of(), List.of(), List.of());
        return DecisionRequest.builder(context(level), single)
                .factors(List.of(FactorValue.of(FactorId.N, n)));
    }

    /** Факторы с полной достоверностью, дающие заданную оценку доверия. */
    private static List<FactorValue> factors(double b, double n, double d, double r) {
        return List.of(FactorValue.of(FactorId.B, b), FactorValue.of(FactorId.N, n),
                FactorValue.of(FactorId.D, d), FactorValue.of(FactorId.R, r));
    }

    @Nested
    @DisplayName("Шаги 1-5: личность, блокировка, устройство, роли, владелец")
    class Gates {

        @Test
        @DisplayName("без аутентифицированного субъекта — 401")
        void unauthenticated() {
            DecisionResult result = engine.decide(request(Level.NORMAL).authenticated(false).build());

            assertThat(result.reason()).isEqualTo(DecisionReason.DENY_UNAUTHENTICATED);
            assertThat(result.decision()).isEqualTo(Decision.DENY);
            assertThat(result.httpStatus()).isEqualTo(401);
            assertThat(result.trustScore()).as("оценка доверия не считается").isNull();
        }

        @Test
        @DisplayName("заблокированный пользователь — 403")
        void blocked() {
            assertThat(engine.decide(request(Level.NORMAL).blocked(true).build()).reason())
                    .isEqualTo(DecisionReason.DENY_BLOCKED);
        }

        @Test
        @DisplayName("вызов с отозванного устройства — 403")
        void revokedDevice() {
            assertThat(engine.decide(request(Level.NORMAL).deviceRevoked(true).build()).reason())
                    .isEqualTo(DecisionReason.DENY_DEVICE_REVOKED);
        }

        @Test
        @DisplayName("нет роли или разрешения — 403")
        void rbac() {
            assertThat(engine.decide(request(Level.NORMAL).rolesSatisfied(false).build()).reason())
                    .isEqualTo(DecisionReason.DENY_RBAC);
        }

        @Test
        @DisplayName("чужой объект — отказ по владельцу")
        void ownership() {
            assertThat(engine.decide(request(Level.NORMAL).ownershipSatisfied(false).build()).reason())
                    .isEqualTo(DecisionReason.DENY_OWNERSHIP);
        }

        @Test
        @DisplayName("шаги проверяются в порядке алгоритма: блокировка раньше ролей")
        void gateOrder() {
            DecisionRequest both = request(Level.NORMAL).blocked(true).rolesSatisfied(false).build();

            assertThat(engine.decide(both).reason()).isEqualTo(DecisionReason.DENY_BLOCKED);
        }

        @Test
        @DisplayName("в режиме наблюдения эти отказы всё равно применяются")
        void gatesApplyInMonitorMode() {
            DecisionRequest monitored = DecisionRequest
                    .builder(context(Level.NORMAL), policy(Enforcement.MONITOR))
                    .rolesSatisfied(false)
                    .build();

            DecisionResult result = engine.decide(monitored);

            assertThat(result.enforced()).isTrue();
            assertThat(result.shouldProceed()).isFalse();
        }
    }

    @Nested
    @DisplayName("Шаг 6: уровень AUTH_ONLY — только личность и правила")
    class AuthOnly {

        @Test
        @DisplayName("обычный вызов разрешается без оценки доверия")
        void allowAuthenticated() {
            DecisionResult result = engine.decide(request(Level.AUTH_ONLY).build());

            assertThat(result.reason()).isEqualTo(DecisionReason.ALLOW_AUTHENTICATED);
            assertThat(result.trustScore()).isNull();
            assertThat(result.requiredThreshold()).isZero();
        }

        @Test
        @DisplayName("правило с отказом работает и здесь")
        void ruleDenies() {
            DecisionRequest denied = request(Level.AUTH_ONLY)
                    .rules(List.of(RuleOutcome.fired("amount-limit", Action.DENY, "запредельная сумма")))
                    .build();

            assertThat(engine.decide(denied).reason()).isEqualTo(DecisionReason.DENY_RULE);
        }

        @Test
        @DisplayName("правило требует подтверждения — step-up без условия по оценке")
        void ruleRequiresStepUp() {
            DecisionRequest stepUp = request(Level.AUTH_ONLY)
                    .rules(List.of(RuleOutcome.fired("time-window", Action.STEP_UP, "ночь")))
                    .build();

            assertThat(engine.decide(stepUp).reason()).isEqualTo(DecisionReason.STEP_UP_RULE);
            assertThat(engine.decide(stepUp).decision()).isEqualTo(Decision.STEP_UP);
        }

        @Test
        @DisplayName("с действующим подтверждением вызов проходит")
        void stepUpSatisfied() {
            DecisionRequest confirmed = request(Level.AUTH_ONLY)
                    .rules(List.of(RuleOutcome.fired("time-window", Action.STEP_UP, "ночь")))
                    .stepUpSatisfied(true)
                    .build();

            assertThat(engine.decide(confirmed).reason()).isEqualTo(DecisionReason.ALLOW_STEP_UP_SATISFIED);
        }

        @Test
        @DisplayName("поднятие порога на этом уровне игнорируется")
        void raiseIsIgnored() {
            DecisionRequest raised = request(Level.AUTH_ONLY)
                    .rules(List.of(RuleOutcome.raise("amount-limit", 0.9, "крупная сумма")))
                    .build();

            assertThat(engine.decide(raised).reason()).isEqualTo(DecisionReason.ALLOW_AUTHENTICATED);
        }
    }

    @Nested
    @DisplayName("Шаги 7-12: правила, оценка доверия, пороги")
    class TrustDecisions {

        @ParameterizedTest(name = "сценарий {0}: TS={5} -> {6}")
        @CsvSource({
                "1, 0.95, 0.90, 1.00, 0.95, NORMAL,   ALLOW_TRUST",
                "2, 0.70, 0.40, 0.90, 0.80, NORMAL,   STEP_UP_TRUST",
                "3, 0.80, 0.70, 0.80, 0.70, CRITICAL, STEP_UP_TRUST",
                "4, 0.20, 0.30, 0.40, 0.10, NORMAL,   DENY_LOW_TRUST"
        })
        @DisplayName("четыре сценария статьи дают ALLOW / STEP_UP / STEP_UP / DENY")
        void articleScenarios(int number, double b, double n, double d, double r,
                              Level level, DecisionReason expected) {
            DecisionResult result = engine.decide(request(level).factors(factors(b, n, d, r)).build());

            assertThat(result.reason()).isEqualTo(expected);
            assertThat(result.decision()).isEqualTo(expected.decision());
        }

        @Test
        @DisplayName("оценка ровно на пороге разрешает вызов")
        void scoreExactlyAtThreshold() {
            DecisionResult result = engine.decide(requestWithSingleFactor(Level.NORMAL, 0.70).build());

            assertThat(result.trustScore().value()).isEqualTo(0.70);
            assertThat(result.reason()).isEqualTo(DecisionReason.ALLOW_TRUST);
        }

        @Test
        @DisplayName("оценка ровно на низком пороге даёт step-up, а не отказ")
        void scoreExactlyAtLowThreshold() {
            DecisionResult result = engine.decide(requestWithSingleFactor(Level.NORMAL, 0.40).build());

            assertThat(result.trustScore().value()).isEqualTo(0.40);
            assertThat(result.reason()).isEqualTo(DecisionReason.STEP_UP_TRUST);
        }

        @Test
        @DisplayName("при отсутствии данных оценка равна θ_L и требуется подтверждение")
        void noEvidenceRequiresStepUp() {
            DecisionResult result = engine.decide(request(Level.NORMAL)
                    .factors(List.of(FactorValue.unknown(FactorId.B), FactorValue.unknown(FactorId.N),
                            FactorValue.unknown(FactorId.D), FactorValue.unknown(FactorId.R)))
                    .build());

            assertThat(result.trustScore().noEvidence()).isTrue();
            assertThat(result.reason()).isEqualTo(DecisionReason.STEP_UP_TRUST);
        }

        @Test
        @DisplayName("правило с отказом сильнее высокой оценки доверия и свежего step-up")
        void ruleDenyBeatsHighTrust() {
            DecisionResult result = engine.decide(request(Level.NORMAL)
                    .factors(factors(0.95, 0.95, 1.00, 0.95))
                    .rules(List.of(RuleOutcome.fired("amount-limit", Action.DENY, "сумма выше denyAbove")))
                    .stepUpSatisfied(true)
                    .build());

            assertThat(result.reason()).isEqualTo(DecisionReason.DENY_RULE);
            assertThat(result.httpStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("превышение частоты отвечает 429, а не общим отказом правила")
        void rateLimitHasOwnReason() {
            DecisionResult result = engine.decide(request(Level.NORMAL)
                    .factors(factors(0.95, 0.95, 1.00, 0.95))
                    .rules(List.of(RuleOutcome.fired(BuiltInRules.RATE_LIMIT, Action.DENY,
                            "61 запрос за минуту")))
                    .build());

            assertThat(result.reason()).isEqualTo(DecisionReason.RATE_LIMITED);
            assertThat(result.httpStatus()).isEqualTo(429);
        }

        @Test
        @DisplayName("при нескольких отказах причина выбирается по порядку вычисления правил")
        void severalDeniesGiveDeterministicReason() {
            List<RuleOutcome> straight = List.of(
                    RuleOutcome.fired(BuiltInRules.AMOUNT_LIMIT, Action.DENY, "сумма выше denyAbove"),
                    RuleOutcome.fired(BuiltInRules.RATE_LIMIT, Action.DENY, "61 запрос за минуту"));
            List<RuleOutcome> reversed = List.of(
                    RuleOutcome.fired(BuiltInRules.RATE_LIMIT, Action.DENY, "61 запрос за минуту"),
                    RuleOutcome.fired(BuiltInRules.AMOUNT_LIMIT, Action.DENY, "сумма выше denyAbove"));

            DecisionResult first = engine.decide(request(Level.NORMAL).rules(straight).build());
            DecisionResult second = engine.decide(request(Level.NORMAL).rules(reversed).build());

            assertThat(first.reason())
                    .as("amount-limit стоит в порядке раньше rate-limit")
                    .isEqualTo(DecisionReason.DENY_RULE);
            assertThat(second.reason())
                    .as("порядок списка не должен менять причину в журнале")
                    .isEqualTo(DecisionReason.DENY_RULE);
        }

        @Test
        @DisplayName("своё правило уступает встроенному: причина берётся у встроенного")
        void builtInDenyWinsOverCustom() {
            List<RuleOutcome> rules = List.of(
                    RuleOutcome.fired("working-days", Action.DENY, "выходной"),
                    RuleOutcome.fired(BuiltInRules.RATE_LIMIT, Action.DENY, "61 запрос за минуту"));

            assertThat(engine.decide(request(Level.NORMAL).rules(rules).build()).reason())
                    .isEqualTo(DecisionReason.RATE_LIMITED);
        }

        @Test
        @DisplayName("правило требует подтверждения, хотя оценка доверия прошла бы порог")
        void ruleStepUpBeatsSufficientTrust() {
            DecisionResult result = engine.decide(request(Level.NORMAL)
                    .factors(factors(0.378, 0.9, 1.0, 1.0))
                    .rules(List.of(RuleOutcome.fired("time-window", Action.STEP_UP, "вне рабочего окна")))
                    .build());

            assertThat(result.trustScore().value()).isGreaterThan(result.requiredThreshold());
            assertThat(result.reason()).isEqualTo(DecisionReason.STEP_UP_RULE);
        }

        @Test
        @DisplayName("поднятие порога правилом превращает разрешение в step-up")
        void raiseTurnsAllowIntoStepUp() {
            DecisionResult result = engine.decide(request(Level.NORMAL)
                    .factors(factors(0.80, 0.80, 0.80, 0.80))
                    .rules(List.of(RuleOutcome.raise("amount-limit", 0.90, "перевод 150 000")))
                    .build());

            assertThat(result.requiredThreshold()).isEqualTo(0.90);
            assertThat(result.reason()).isEqualTo(DecisionReason.STEP_UP_TRUST);
        }

        @Test
        @DisplayName("ниже низкого порога подтверждение не помогает")
        void lowTrustIsNotSavedByStepUp() {
            DecisionResult result = engine.decide(request(Level.NORMAL)
                    .factors(factors(0.20, 0.30, 0.40, 0.10))
                    .stepUpSatisfied(true)
                    .rules(List.of(RuleOutcome.fired("time-window", Action.STEP_UP, "ночь")))
                    .build());

            assertThat(result.reason()).isEqualTo(DecisionReason.DENY_LOW_TRUST);
        }
    }

    @Nested
    @DisplayName("Шаг 11: дрейф сессии и приоритет причин")
    class Drift {

        @Test
        @DisplayName("падение оценки относительно базовой линии требует подтверждения")
        void driftRequiresStepUp() {
            DecisionResult result = engine.decide(request(Level.NORMAL)
                    .factors(factors(0.68, 0.10, 0.40, 1.00))
                    .baselineTs(0.95)
                    .build());

            assertThat(result.trustScore().rounded()).isEqualTo(0.559);
            assertThat(result.reason()).isEqualTo(DecisionReason.STEP_UP_DRIFT);
            assertThat(result.baselineTs()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("дрейф ловится даже при достаточной оценке доверия")
        void driftWithSufficientScore() {
            DecisionResult result = engine.decide(requestWithSingleFactor(Level.NORMAL, 0.70)
                    .baselineTs(0.96)
                    .build());

            assertThat(result.trustScore().value()).isGreaterThanOrEqualTo(result.requiredThreshold());
            assertThat(result.reason()).isEqualTo(DecisionReason.STEP_UP_DRIFT);
        }

        @Test
        @DisplayName("небольшое колебание дрейфом не считается")
        void smallDropIsNotDrift() {
            DecisionResult result = engine.decide(request(Level.NORMAL)
                    .factors(factors(0.95, 0.90, 1.00, 0.95))
                    .baselineTs(0.99)
                    .build());

            assertThat(result.reason()).isEqualTo(DecisionReason.ALLOW_TRUST);
        }

        @Test
        @DisplayName("приоритет причин: правило важнее дрейфа, дрейф важнее оценки")
        void reasonPriority() {
            DecisionResult ruleAndDrift = engine.decide(request(Level.NORMAL)
                    .factors(factors(0.68, 0.10, 0.40, 1.00))
                    .baselineTs(0.95)
                    .rules(List.of(RuleOutcome.fired("time-window", Action.STEP_UP, "ночь")))
                    .build());
            DecisionResult driftAndTrust = engine.decide(request(Level.NORMAL)
                    .factors(factors(0.68, 0.10, 0.40, 1.00))
                    .baselineTs(0.95)
                    .build());

            assertThat(ruleAndDrift.reason()).isEqualTo(DecisionReason.STEP_UP_RULE);
            assertThat(driftAndTrust.reason()).isEqualTo(DecisionReason.STEP_UP_DRIFT);
        }

        @Test
        @DisplayName("базовая линия сессии подтягивается к текущей оценке")
        void baselineUpdate() {
            assertThat(SessionSettings.DEFAULT.updateBaseline(0.95, 0.75)).isEqualTo(0.91, within(1e-9));
            assertThat(SessionSettings.DEFAULT.isDrift(null, 0.1)).isFalse();
        }
    }

    @Nested
    @DisplayName("Подтверждение второго фактора")
    class StepUp {

        @Test
        @DisplayName("после подтверждения повторный вызов проходит")
        void satisfiedStepUpAllows() {
            DecisionResult result = engine.decide(request(Level.NORMAL)
                    .factors(factors(0.70, 0.40, 0.90, 0.80))
                    .stepUpSatisfied(true)
                    .build());

            assertThat(result.reason()).isEqualTo(DecisionReason.ALLOW_STEP_UP_SATISFIED);
            assertThat(result.stepUpSatisfied()).isTrue();
        }

        @Test
        @DisplayName("шаг 14: если подтверждать нечем, вместо step-up отказ")
        void stepUpUnavailable() {
            DecisionResult result = engine.decide(request(Level.NORMAL)
                    .factors(factors(0.70, 0.40, 0.90, 0.80))
                    .stepUpAvailable(false)
                    .build());

            assertThat(result.reason()).isEqualTo(DecisionReason.DENY_STEP_UP_UNAVAILABLE);
            assertThat(result.decision()).isEqualTo(Decision.DENY);
        }
    }

    @Nested
    @DisplayName("Режим наблюдения")
    class MonitorMode {

        @Test
        @DisplayName("решение записывается, но вызов выполняется")
        void stepUpIsOnlyLogged() {
            DecisionRequest monitored = DecisionRequest
                    .builder(context(Level.NORMAL), policy(Enforcement.MONITOR))
                    .factors(factors(0.70, 0.40, 0.90, 0.80))
                    .rules(List.of(RuleOutcome.fired("amount-limit", Action.STEP_UP, "крупный перевод")))
                    .build();

            DecisionResult result = engine.decide(monitored);

            assertThat(result.reason()).isEqualTo(DecisionReason.STEP_UP_RULE);
            assertThat(result.enforced()).isFalse();
            assertThat(result.shouldProceed()).isTrue();
        }

        @Test
        @DisplayName("в режиме наблюдения считаются и оценка, и порог")
        void scoreIsStillComputed() {
            DecisionRequest monitored = DecisionRequest
                    .builder(context(Level.CRITICAL), policy(Enforcement.MONITOR))
                    .factors(factors(0.80, 0.70, 0.80, 0.70))
                    .build();

            DecisionResult result = engine.decide(monitored);

            assertThat(result.trustScore().rounded()).isEqualTo(0.750);
            assertThat(result.requiredThreshold()).isEqualTo(0.80);
            assertThat(result.enforced()).isFalse();
        }
    }

    @Test
    @DisplayName("у каждого решения свой идентификатор, длительность проставляется позже")
    void decisionIdAndLatency() {
        DecisionResult first = engine.decide(request(Level.NORMAL).factors(factors(1, 1, 1, 1)).build());
        DecisionResult second = engine.decide(request(Level.NORMAL).factors(factors(1, 1, 1, 1)).build());

        assertThat(first.decisionId()).isNotEqualTo(second.decisionId());
        assertThat(first.latencyMicros()).isZero();
        assertThat(first.withLatency(812).latencyMicros()).isEqualTo(812);
        assertThat(first.withLatency(812).decisionId()).isEqualTo(first.decisionId());
    }

    @Test
    @DisplayName("без запроса, контекста или политики решение не принимается")
    void argumentsAreChecked() {
        assertThatIllegalArgumentException().isThrownBy(() -> engine.decide(null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DecisionRequest(null, policy(Enforcement.ENFORCE), true, false,
                        false, true, true, List.of(), List.of(), false, true, true, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DecisionRequest(context(Level.NORMAL), null, true, false,
                        false, true, true, List.of(), List.of(), false, true, true, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new DecisionEngine(null, null, null));
    }
}
