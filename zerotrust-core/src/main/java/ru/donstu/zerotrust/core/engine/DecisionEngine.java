package ru.donstu.zerotrust.core.engine;

import ru.donstu.zerotrust.core.model.DecisionReason;
import ru.donstu.zerotrust.core.model.DecisionResult;
import ru.donstu.zerotrust.core.model.Level;
import ru.donstu.zerotrust.core.model.RuleOutcome;
import ru.donstu.zerotrust.core.model.TrustScore;
import ru.donstu.zerotrust.core.rule.BuiltInRules;

import java.util.List;
import java.util.UUID;

/**
 * Движок решения — алгоритм решения. Принимает решение ALLOW / STEP_UP / DENY и объясняет его
 * причиной, оценкой доверия, требуемым порогом и исходами правил.
 *
 * <p>Шаги 1–5 (личность, блокировка, отозванное устройство, роли, владелец) применяются всегда,
 * в том числе в режиме наблюдения. Шаги 7–11 в режиме {@code MONITOR} вычисляются и пишутся
 * в журнал, но вызов выполняется: у результата {@code enforced = false}.
 *
 * <p>Класс без состояния.
 */
public class DecisionEngine {

    private final TrustScoreCalculator calculator;
    private final ThresholdResolver thresholdResolver;
    private final SessionSettings sessionSettings;

    /** Движок с настройками по умолчанию. */
    public DecisionEngine() {
        this(new TrustScoreCalculator(), new ThresholdResolver(), SessionSettings.DEFAULT);
    }

    public DecisionEngine(TrustScoreCalculator calculator, ThresholdResolver thresholdResolver,
                          SessionSettings sessionSettings) {
        if (calculator == null || thresholdResolver == null || sessionSettings == null) {
            throw new IllegalArgumentException("Движок решения требует калькулятор, пороги и настройки сессии");
        }
        this.calculator = calculator;
        this.thresholdResolver = thresholdResolver;
        this.sessionSettings = sessionSettings;
    }

    /**
     * Принимает решение по вызову. Длительность конвейера проставляет аспект через
     * {@link DecisionResult#withLatency(long)}: движок не знает, сколько заняли шаги до него.
     */
    public DecisionResult decide(DecisionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Запрос решения не задан");
        }

        // Шаги 1-5: применяются всегда, даже в режиме наблюдения
        if (!request.authenticated()) {
            return gate(request, DecisionReason.DENY_UNAUTHENTICATED);
        }
        if (request.blocked()) {
            return gate(request, DecisionReason.DENY_BLOCKED);
        }
        if (request.deviceRevoked()) {
            return gate(request, DecisionReason.DENY_DEVICE_REVOKED);
        }
        if (!request.rolesSatisfied()) {
            return gate(request, DecisionReason.DENY_RBAC);
        }
        if (!request.ownershipSatisfied()) {
            return gate(request, DecisionReason.DENY_OWNERSHIP);
        }

        boolean enforced = request.policy().isEnforced();
        List<RuleOutcome> rules = request.rules();

        // Шаг 7: отказ правила решает сразу
        RuleOutcome denying = firstDeny(rules);
        if (denying != null) {
            DecisionReason reason = BuiltInRules.RATE_LIMIT.equals(denying.id())
                    ? DecisionReason.RATE_LIMITED
                    : DecisionReason.DENY_RULE;
            return result(request, reason, null, 0.0, enforced);
        }
        boolean forcedStepUp = hasStepUp(rules);

        // Шаг 6: на уровне AUTH_ONLY оценка доверия не считается, RAISE игнорируется
        if (request.context().level() == Level.AUTH_ONLY) {
            if (forcedStepUp) {
                return request.stepUpSatisfied()
                        ? result(request, DecisionReason.ALLOW_STEP_UP_SATISFIED, null, 0.0, enforced)
                        : stepUp(request, DecisionReason.STEP_UP_RULE, null, 0.0, enforced);
            }
            return result(request, DecisionReason.ALLOW_AUTHENTICATED, null, 0.0, enforced);
        }

        // Шаги 7-8: требуемый порог с учётом RAISE, оценка доверия, дрейф сессии
        double required = thresholdResolver.resolve(request.policy().thresholds(),
                request.context().level(), rules);
        TrustScore trustScore = calculator.compute(request.policy().weights(), request.factors(),
                request.policy().thresholds());
        boolean drift = sessionSettings.isDrift(request.baselineTs(), trustScore.value());

        // Шаг 10: ниже низкого порога подтверждение не помогает
        if (trustScore.value() < request.policy().thresholds().low()) {
            return result(request, DecisionReason.DENY_LOW_TRUST, trustScore, required, enforced);
        }

        // Шаг 11: правило, дрейф или недостаточное доверие требуют подтверждения
        if (forcedStepUp || drift || trustScore.value() < required) {
            if (request.stepUpSatisfied()) {
                return result(request, DecisionReason.ALLOW_STEP_UP_SATISFIED, trustScore, required, enforced);
            }
            DecisionReason reason = forcedStepUp
                    ? DecisionReason.STEP_UP_RULE
                    : (drift ? DecisionReason.STEP_UP_DRIFT : DecisionReason.STEP_UP_TRUST);
            return stepUp(request, reason, trustScore, required, enforced);
        }

        // Шаг 12
        return result(request, DecisionReason.ALLOW_TRUST, trustScore, required, enforced);
    }

    /** Шаг 14: подтверждать нечем — отказ вместо невыполнимого требования. */
    private DecisionResult stepUp(DecisionRequest request, DecisionReason reason,
                                  TrustScore trustScore, double required, boolean enforced) {
        if (!request.stepUpAvailable()) {
            return result(request, DecisionReason.DENY_STEP_UP_UNAVAILABLE, trustScore, required, enforced);
        }
        if (!request.stepUpEnrolled()) {
            return result(request, DecisionReason.DENY_STEP_UP_NOT_ENROLLED, trustScore, required, enforced);
        }
        return result(request, reason, trustScore, required, enforced);
    }

    /** Отказы шагов 1-5 применяются независимо от режима политики. */
    private DecisionResult gate(DecisionRequest request, DecisionReason reason) {
        return result(request, reason, null, 0.0, true);
    }

    private DecisionResult result(DecisionRequest request, DecisionReason reason,
                                  TrustScore trustScore, double required, boolean enforced) {
        return new DecisionResult(UUID.randomUUID(), reason.decision(), reason,
                trustScore, required, request.baselineTs(),
                request.factors(), request.rules(),
                request.stepUpSatisfied(), enforced, 0);
    }

    /**
     * Отказавшее правило, по которому объясняется решение. При нескольких отказах берётся
     * правило с наименьшим рангом в порядке вычисления, а среди правил одного
     * ранга — первое в списке: иначе один и тот же вызов мог бы попадать в журнал то с одной
     * причиной, то с другой.
     */
    private static RuleOutcome firstDeny(List<RuleOutcome> rules) {
        RuleOutcome denying = null;
        int bestRank = Integer.MAX_VALUE;
        for (RuleOutcome rule : rules) {
            if (rule == null || !rule.isDeny()) {
                continue;
            }
            int rank = BuiltInRules.rankOf(rule.id());
            if (rank < bestRank) {
                bestRank = rank;
                denying = rule;
            }
        }
        return denying;
    }

    private static boolean hasStepUp(List<RuleOutcome> rules) {
        for (RuleOutcome rule : rules) {
            if (rule != null && rule.isStepUp()) {
                return true;
            }
        }
        return false;
    }
}
