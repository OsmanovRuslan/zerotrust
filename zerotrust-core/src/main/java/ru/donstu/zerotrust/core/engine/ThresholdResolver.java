package ru.donstu.zerotrust.core.engine;

import ru.donstu.zerotrust.core.model.Level;
import ru.donstu.zerotrust.core.model.RuleOutcome;
import ru.donstu.zerotrust.core.model.Thresholds;

import java.util.Collection;

/**
 * Требуемый порог доверия для вызова — формула требуемого порога:
 * θ_req = min(0,95; max(θ_level; max по сработавшим правилам RAISE)).
 *
 * <p>Это и есть динамическая критичность: один и тот же метод требует разного доверия
 * в зависимости от аргументов вызова. Предел 0,95 нужен, чтобы требование не стало
 * недостижимым.
 *
 * <p>Класс без состояния.
 */
public class ThresholdResolver {

    /**
     * Считает требуемый порог.
     *
     * @param thresholds пороги действующей политики
     * @param level      уровень операции
     * @param rules      исходы правил; {@code null} и не сработавшие правила игнорируются
     * @throws IllegalArgumentException если уровень {@link Level#AUTH_ONLY}: на нём оценка
     *                                  доверия не считается и порога не существует
     */
    public double resolve(Thresholds thresholds, Level level, Collection<RuleOutcome> rules) {
        if (thresholds == null) {
            throw new IllegalArgumentException("Пороги политики не заданы");
        }
        double required = thresholds.baseThresholdFor(level);
        if (rules != null) {
            for (RuleOutcome rule : rules) {
                if (rule != null && rule.isRaise()) {
                    required = Math.max(required, rule.raiseTo());
                }
            }
        }
        return Math.min(Thresholds.MAX, required);
    }
}
