package ru.donstu.zerotrust.core.engine;

import ru.donstu.zerotrust.core.model.EffectivePolicy;
import ru.donstu.zerotrust.core.model.FactorValue;
import ru.donstu.zerotrust.core.model.InvocationContext;
import ru.donstu.zerotrust.core.model.RuleOutcome;

import java.util.List;

/**
 * Всё, что движок решения получает на вход (алгоритм решения).
 *
 * <p>Проверки шагов 1–5 делают отдельные компоненты слоя autoconfigure (блокировки, устройства,
 * RBAC, владелец), а движку передают их итог: так алгоритм остаётся чистой функцией и
 * проверяется unit-тестами по всем ветвям.
 *
 * @param context            контекст вызова
 * @param policy             действующая политика
 * @param authenticated      есть ли аутентифицированный субъект
 * @param blocked            заблокирован ли субъект
 * @param deviceRevoked      отозвано ли устройство
 * @param rolesSatisfied     выполнены ли требования по ролям и разрешениям
 * @param ownershipSatisfied выполнена ли проверка владельца
 * @param rules              исходы всех правил, включая не сработавшие
 * @param factors            значения факторов доверия
 * @param stepUpSatisfied    действует ли подтверждение вторым фактором
 * @param stepUpAvailable    есть ли вообще способ подтверждения (метод step-up не NONE)
 * @param stepUpEnrolled     подключён ли второй фактор у этого субъекта
 * @param baselineTs         базовая линия доверия сессии TS₀ или {@code null}
 */
public record DecisionRequest(InvocationContext context, EffectivePolicy policy,
                              boolean authenticated, boolean blocked, boolean deviceRevoked,
                              boolean rolesSatisfied, boolean ownershipSatisfied,
                              List<RuleOutcome> rules, List<FactorValue> factors,
                              boolean stepUpSatisfied, boolean stepUpAvailable,
                              boolean stepUpEnrolled, Double baselineTs) {

    public DecisionRequest {
        if (context == null) {
            throw new IllegalArgumentException("Контекст вызова не задан");
        }
        if (policy == null) {
            throw new IllegalArgumentException("Действующая политика не задана");
        }
        rules = (rules == null) ? List.of() : List.copyOf(rules);
        factors = (factors == null) ? List.of() : List.copyOf(factors);
    }

    /** Заготовка, в которой все проверки шагов 1–5 пройдены, а step-up доступен. */
    public static Builder builder(InvocationContext context, EffectivePolicy policy) {
        return new Builder(context, policy);
    }

    /**
     * Сборка запроса: у шагов 1–5 нормальный исход «проверка пройдена», поэтому в тестах
     * и в аспекте задаются только отличия.
     */
    public static final class Builder {

        private final InvocationContext context;
        private final EffectivePolicy policy;
        private boolean authenticated = true;
        private boolean blocked;
        private boolean deviceRevoked;
        private boolean rolesSatisfied = true;
        private boolean ownershipSatisfied = true;
        private List<RuleOutcome> rules = List.of();
        private List<FactorValue> factors = List.of();
        private boolean stepUpSatisfied;
        private boolean stepUpAvailable = true;
        private boolean stepUpEnrolled = true;
        private Double baselineTs;

        private Builder(InvocationContext context, EffectivePolicy policy) {
            this.context = context;
            this.policy = policy;
        }

        public Builder authenticated(boolean value) {
            this.authenticated = value;
            return this;
        }

        public Builder blocked(boolean value) {
            this.blocked = value;
            return this;
        }

        public Builder deviceRevoked(boolean value) {
            this.deviceRevoked = value;
            return this;
        }

        public Builder rolesSatisfied(boolean value) {
            this.rolesSatisfied = value;
            return this;
        }

        public Builder ownershipSatisfied(boolean value) {
            this.ownershipSatisfied = value;
            return this;
        }

        public Builder rules(List<RuleOutcome> value) {
            this.rules = value;
            return this;
        }

        public Builder factors(List<FactorValue> value) {
            this.factors = value;
            return this;
        }

        public Builder stepUpSatisfied(boolean value) {
            this.stepUpSatisfied = value;
            return this;
        }

        public Builder stepUpEnrolled(boolean value) {
            this.stepUpEnrolled = value;
            return this;
        }

        public Builder stepUpAvailable(boolean value) {
            this.stepUpAvailable = value;
            return this;
        }

        public Builder baselineTs(Double value) {
            this.baselineTs = value;
            return this;
        }

        public DecisionRequest build() {
            return new DecisionRequest(context, policy, authenticated, blocked, deviceRevoked,
                    rolesSatisfied, ownershipSatisfied, rules, factors,
                    stepUpSatisfied, stepUpAvailable, stepUpEnrolled, baselineTs);
        }
    }
}
