package ru.donstu.zerotrust.core.factor;

/**
 * Результат проверки перемещения между двумя вызовами.
 *
 * @param score      оценка s_travel ∈ [0, 1]
 * @param distanceKm расстояние между точками, км
 * @param speedKmh   требуемая скорость, км/ч
 * @param impossible истина, если скорость запредельная: нужно событие IMPOSSIBLE_TRAVEL
 *                   (не чаще одного на сессию)
 * @param assessed   ложь, если проверить перемещение было нечем: нет прошлых координат,
 *                   нет геолокации или адрес частный
 */
public record TravelAssessment(double score, double distanceKm, double speedKmh, boolean impossible,
                               boolean assessed) {

    /** Перемещение не оценивалось: на оценку фактора не влияет. */
    public static final TravelAssessment NOT_ASSESSED = new TravelAssessment(1.0, 0, 0, false, false);
}
