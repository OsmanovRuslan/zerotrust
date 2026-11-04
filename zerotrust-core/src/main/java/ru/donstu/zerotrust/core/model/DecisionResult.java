package ru.donstu.zerotrust.core.model;

import java.util.List;
import java.util.UUID;

/**
 * Решение PDP вместе со всем, что потребовалось его принять.
 *
 * <p>Принцип наблюдаемости: каждое решение должно быть объяснимо. Поэтому результат
 * несёт не только «можно/нельзя», но и значения факторов с достоверностями, исходы правил,
 * требуемый порог и базовую линию сессии. Из этой записи целиком собирается строка журнала
 * {@code zt_decision_log} и, при {@code expose-details}, тело ответа для демонстрации.
 *
 * @param decisionId       идентификатор решения: криптостойкий UUID, попадает
 *                         в ответ и в журнал, чтобы по жалобе пользователя можно было найти
 *                         конкретное решение
 * @param decision         итоговое решение
 * @param reason           причина: она же задаёт HTTP-статус и тип события безопасности
 * @param trustScore       оценка доверия вместе с массой данных, признаком {@code NO_EVIDENCE}
 *                         и итоговыми весами факторов; {@code null}, если оценка не вычислялась
 *                         (уровень {@link Level#AUTH_ONLY} или отказ на шагах 1–5 алгоритма
 * решения)
 * @param requiredThreshold требуемый порог θ_req с учётом уровня операции и поднятий правилами
 *                         (формула требуемого порога) — именно он показывает динамическую
 * критичность
 * @param baselineTs       базовая линия доверия сессии TS₀ или {@code null}, если сессии нет
 *                         либо дрейф не проверялся
 * @param factors          значения факторов, участвовавших в оценке
 * @param rules            исходы всех проверенных правил, включая не сработавшие
 * @param stepUpSatisfied  истина, если подтверждение вторым фактором действовало на момент вызова
 * @param enforced         истина, если решение применено; {@code false} в режиме
 *                         {@link Enforcement#MONITOR}
 * @param latencyMicros    длительность работы конвейера в микросекундах: по ней считается
 *                         накладной расход модуля для главы 3
 */
public record DecisionResult(UUID decisionId,
                             Decision decision,
                             DecisionReason reason,
                             TrustScore trustScore,
                             double requiredThreshold,
                             Double baselineTs,
                             List<FactorValue> factors,
                             List<RuleOutcome> rules,
                             boolean stepUpSatisfied,
                             boolean enforced,
                             long latencyMicros) {

    public DecisionResult {
        if (decisionId == null) {
            throw new IllegalArgumentException("Идентификатор решения не может быть null");
        }
        if (reason == null) {
            throw new IllegalArgumentException("Причина решения не может быть null");
        }
        if (decision == null) {
            decision = reason.decision();
        }
        if (decision != reason.decision()) {
            throw new IllegalArgumentException(
                    "Решение " + decision + " не соответствует причине " + reason
                            + " (ожидалось " + reason.decision() + ")");
        }
        factors = (factors == null) ? List.of() : List.copyOf(factors);
        rules = (rules == null) ? List.of() : List.copyOf(rules);
    }

    /**
     * Истина, если вызов должен быть выполнен: либо решение разрешающее, либо действует режим
     * наблюдения, в котором решение только записывается.
     */
    public boolean shouldProceed() {
        return decision == Decision.ALLOW || !enforced;
    }

    /**
     * То же решение с измеренной длительностью конвейера: движок решения её не знает,
     * её проставляет аспект после всех шагов.
     */
    public DecisionResult withLatency(long micros) {
        return new DecisionResult(decisionId, decision, reason, trustScore, requiredThreshold,
                baselineTs, factors, rules, stepUpSatisfied, enforced, micros);
    }

    /** Округлённая оценка доверия для журнала и ответа; {@code null}, если оценка не вычислялась. */
    public Double trustScoreValue() {
        return (trustScore == null) ? null : trustScore.rounded();
    }

    /** HTTP-статус ответа при отказе или требовании step-up. */
    public int httpStatus() {
        return reason.httpStatus();
    }

    /** Значение фактора по идентификатору, если он участвовал в оценке. */
    public FactorValue factor(FactorId id) {
        for (FactorValue f : factors) {
            if (f.id().equals(id)) {
                return f;
            }
        }
        return null;
    }

    /** Только сработавшие правила — то, что интересно в журнале и в разборе инцидента. */
    public List<RuleOutcome> firedRules() {
        return rules.stream().filter(RuleOutcome::isFired).toList();
    }
}
