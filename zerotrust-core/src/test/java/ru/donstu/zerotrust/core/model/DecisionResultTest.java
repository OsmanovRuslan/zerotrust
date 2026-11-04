package ru.donstu.zerotrust.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Тесты результата решения: согласованность с причиной и поведение в режиме наблюдения. */
class DecisionResultTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static DecisionResult result(Decision decision, DecisionReason reason, boolean enforced) {
        return new DecisionResult(ID, decision, reason, new TrustScore(0.951, 1.0, false), 0.70, null,
                List.of(FactorValue.of(FactorId.B, 0.92), FactorValue.of(FactorId.N, 0.9)),
                List.of(RuleOutcome.pass("time-window"),
                        RuleOutcome.fired("amount-limit", Action.STEP_UP, "сумма выше stepUpAbove")),
                false, enforced, 812);
    }

    @Test
    @DisplayName("решение можно не указывать: оно однозначно задано причиной")
    void decisionIsDerivedFromReason() {
        DecisionResult r = new DecisionResult(ID, null, DecisionReason.ALLOW_TRUST,
                new TrustScore(0.95, 1.0, false), 0.70, null,
                List.of(), List.of(), false, true, 100);

        assertThat(r.decision()).isEqualTo(Decision.ALLOW);
    }

    @Test
    @DisplayName("на уровне AUTH_ONLY оценки доверия нет: в журнал пишется пусто")
    void trustScoreMayBeAbsent() {
        DecisionResult r = new DecisionResult(ID, null, DecisionReason.ALLOW_AUTHENTICATED, null, 0, null,
                List.of(), List.of(), false, true, 100);

        assertThat(r.trustScore()).isNull();
        assertThat(r.trustScoreValue()).isNull();
    }

    @Test
    @DisplayName("оценка в журнал и ответ попадает округлённой до трёх знаков")
    void trustScoreValueIsRounded() {
        DecisionResult r = new DecisionResult(ID, null, DecisionReason.ALLOW_TRUST,
                new TrustScore(0.9475, 1.0, false), 0.70, null,
                List.of(), List.of(), false, true, 100);

        assertThat(r.trustScoreValue()).isEqualTo(0.948);
    }

    @Test
    @DisplayName("решение, не соответствующее причине, отвергается")
    void inconsistentDecisionIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> result(Decision.ALLOW, DecisionReason.DENY_RBAC, true))
                .withMessageContaining("не соответствует причине");
    }

    @Test
    @DisplayName("причина обязательна: без неё решение нельзя объяснить")
    void reasonIsRequired() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DecisionResult(ID, Decision.ALLOW, null, null, 0, null,
                        List.of(), List.of(), false, true, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DecisionResult(null, Decision.ALLOW, DecisionReason.ALLOW_TRUST, null, 0, null,
                        List.of(), List.of(), false, true, 0));
    }

    @Test
    @DisplayName("в режиме ENFORCE отказ останавливает вызов")
    void enforcedDenyStopsInvocation() {
        assertThat(result(Decision.STEP_UP, DecisionReason.STEP_UP_RULE, true).shouldProceed()).isFalse();
    }

    @Test
    @DisplayName("в режиме MONITOR вызов выполняется, решение только записывается")
    void monitorModeProceeds() {
        DecisionResult r = result(Decision.STEP_UP, DecisionReason.STEP_UP_RULE, false);

        assertThat(r.shouldProceed()).isTrue();
        assertThat(r.enforced()).isFalse();
        assertThat(r.reason()).isEqualTo(DecisionReason.STEP_UP_RULE);
    }

    @Test
    @DisplayName("разрешающее решение выполняется всегда")
    void allowProceeds() {
        assertThat(result(Decision.ALLOW, DecisionReason.ALLOW_TRUST, true).shouldProceed()).isTrue();
    }

    @Test
    @DisplayName("статус ответа берётся из причины")
    void httpStatusComesFromReason() {
        assertThat(result(Decision.DENY, DecisionReason.RATE_LIMITED, true).httpStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("фактор ищется по идентификатору, сработавшие правила отбираются отдельно")
    void factorsAndRulesAreQueryable() {
        DecisionResult r = result(Decision.STEP_UP, DecisionReason.STEP_UP_RULE, true);

        assertThat(r.factor(FactorId.B).value()).isEqualTo(0.92);
        assertThat(r.factor(FactorId.D)).isNull();
        assertThat(r.firedRules()).hasSize(1);
        assertThat(r.firedRules().getFirst().id()).isEqualTo("amount-limit");
        assertThat(r.rules()).hasSize(2);
    }

    @Test
    @DisplayName("списки факторов и правил копируются и неизменяемы")
    void listsAreImmutable() {
        DecisionResult r = new DecisionResult(ID, Decision.ALLOW, DecisionReason.ALLOW_TRUST,
                new TrustScore(0.95, 1.0, false), 0.70, null,
                null, null, false, true, 0);

        assertThat(r.factors()).isEmpty();
        assertThat(r.rules()).isEmpty();
    }
}
