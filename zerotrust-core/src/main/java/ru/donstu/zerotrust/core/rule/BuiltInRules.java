package ru.donstu.zerotrust.core.rule;

import ru.donstu.zerotrust.core.model.RuleOutcome;

import java.util.ArrayList;
import java.util.List;

/**
 * Адреса встроенных правил условий и порядок вычисления правил. По этим именам
 * правила отключаются переопределениями и попадают в журнал решения.
 *
 * <p>Служебный класс, экземпляры не создаются.
 */
public final class BuiltInRules {

    /** Ограничение суммы операции. */
    public static final String AMOUNT_LIMIT = "amount-limit";

    /** Рабочее окно по времени. */
    public static final String TIME_WINDOW = "time-window";

    /** Разрешённые сети. */
    public static final String ALLOWED_NETWORKS = "allowed-networks";

    /** Разрешённые регионы. */
    public static final String ALLOWED_REGIONS = "allowed-regions";

    /**
     * Ограничение частоты вызовов. Отказ этого правила отвечает статусом 429 и причиной
     * {@code RATE_LIMITED}, а не общим {@code DENY_RULE}, поэтому движок решения узнаёт
     * правило по этому адресу.
     */
    public static final String RATE_LIMIT = "rate-limit";

    /**
     * Порядок вычисления встроенных правил. Остальные правила идут после них:
     * сначала {@code @TrustRule} в порядке объявления, затем свои аннотации по возрастанию
     * {@code id}, затем правила политики по возрастанию {@code id}.
     *
     * <p>Порядок задан явно, потому что порядок аннотаций, который возвращает рефлексия,
     * не гарантирован: без фиксированного порядка один и тот же вызов мог бы попадать в журнал
     * то с одной причиной, то с другой.
     */
    public static final List<String> ORDER =
            List.of(AMOUNT_LIMIT, TIME_WINDOW, ALLOWED_NETWORKS, ALLOWED_REGIONS, RATE_LIMIT);

    private BuiltInRules() {
        throw new AssertionError("Служебный класс, экземпляры не создаются");
    }

    /**
     * Ранг правила в порядке вычисления: индекс в {@link #ORDER} для встроенных правил,
     * {@code ORDER.size()} для всех остальных.
     */
    public static int rankOf(String ruleId) {
        int index = (ruleId == null) ? -1 : ORDER.indexOf(ruleId);
        return (index < 0) ? ORDER.size() : index;
    }

    /**
     * Исходы правил в порядке вычисления: встроенные правила по {@link #ORDER}, остальные —
     * в том порядке, в котором пришли. Сортировка устойчивая, поэтому порядок, заданный
     * реестром правил, сохраняется.
     */
    public static List<RuleOutcome> inEvaluationOrder(List<RuleOutcome> outcomes) {
        if (outcomes == null || outcomes.size() < 2) {
            return (outcomes == null) ? List.of() : List.copyOf(outcomes);
        }
        List<RuleOutcome> ordered = new ArrayList<>(outcomes);
        ordered.sort((first, second) -> Integer.compare(
                rankOf(first == null ? null : first.id()),
                rankOf(second == null ? null : second.id())));
        return List.copyOf(ordered);
    }
}
