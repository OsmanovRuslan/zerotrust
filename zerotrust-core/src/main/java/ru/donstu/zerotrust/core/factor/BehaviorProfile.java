package ru.donstu.zerotrust.core.factor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Профиль поведения пользователя (таблица zt_behavior_profile): то, что фактор B считает
 * обычным для этого человека. Обновляется только после разрешённых вызовов.
 *
 * @param sampleCount             число успешных вызовов: от него зависит достоверность c_B
 * @param hourHistogram           распределение активности по часам, 24 значения с суммой 1
 * @param rateMean                среднее число запросов в активную минуту
 * @param rateVariance            дисперсия этого числа
 * @param currentMinute           минута, за которую ведётся счётчик, или {@code null}
 * @param currentMinuteCount      сколько вызовов было в этой минуте, включая отклонённые
 * @param currentMinuteSuccesses  сколько из них разрешено: по ним решается, учитывать ли минуту
 * @param knownOperations         операция -> время последнего успешного вызова
 * @param firstSeen               первый вызов пользователя
 */
public record BehaviorProfile(long sampleCount, List<Double> hourHistogram,
                              double rateMean, double rateVariance,
                              Instant currentMinute, int currentMinuteCount, int currentMinuteSuccesses,
                              Map<String, Instant> knownOperations, Instant firstSeen) {

    /** Число часов в сутках: длина гистограммы. */
    public static final int HOURS = 24;

    public BehaviorProfile {
        if (hourHistogram == null || hourHistogram.size() != HOURS) {
            throw new IllegalArgumentException("Гистограмма часов должна содержать " + HOURS + " значений");
        }
        for (Double value : hourHistogram) {
            if (value == null || value < 0 || Double.isNaN(value)) {
                throw new IllegalArgumentException("Недопустимое значение гистограммы: " + value);
            }
        }
        if (sampleCount < 0) {
            throw new IllegalArgumentException("Число успешных вызовов не может быть отрицательным");
        }
        if (rateVariance < 0) {
            throw new IllegalArgumentException("Дисперсия не может быть отрицательной");
        }
        hourHistogram = List.copyOf(hourHistogram);
        knownOperations = (knownOperations == null) ? Map.of() : Map.copyOf(knownOperations);
    }

    /** Профиль нового пользователя: равномерная гистограмма и начальные среднее с дисперсией. */
    public static BehaviorProfile initial(BehaviorSettings settings, Instant firstSeen) {
        if (settings == null) {
            throw new IllegalArgumentException("Настройки фактора поведения не заданы");
        }
        List<Double> uniform = new ArrayList<>(Collections.nCopies(HOURS, 1.0 / HOURS));
        return new BehaviorProfile(0, uniform, settings.rateInitialMean(), settings.rateInitialVariance(),
                null, 0, 0, Map.of(), firstSeen);
    }

    /** Доля активности в указанном часе. */
    public double hour(int index) {
        if (index < 0 || index >= HOURS) {
            throw new IllegalArgumentException("Час " + index + " вне диапазона [0, 23]");
        }
        return hourHistogram.get(index);
    }

    /** Среднеквадратичное отклонение числа запросов в минуту. */
    public double rateDeviation() {
        return Math.sqrt(rateVariance);
    }
}
