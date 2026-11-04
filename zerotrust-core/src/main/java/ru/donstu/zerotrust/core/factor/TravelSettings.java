package ru.donstu.zerotrust.core.factor;

import ru.donstu.zerotrust.core.model.Scores;

/**
 * Настройки проверки перемещения ({@code zerotrust.factors.network.travel}).
 *
 * @param minDistanceKm  расстояние, ниже которого перемещение не оценивается
 * @param suspiciousKmh  скорость, с которой начинается снижение оценки
 * @param impossibleKmh  скорость, которая считается невозможной
 * @param minScore       оценка при невозможной скорости
 */
public record TravelSettings(double minDistanceKm, double suspiciousKmh, double impossibleKmh,
                             double minScore) {

    /** Умолчания: 50 км, 500 км/ч, 900 км/ч, 0,1. */
    public static final TravelSettings DEFAULT = new TravelSettings(50.0, 500.0, 900.0, 0.1);

    public TravelSettings {
        if (minDistanceKm < 0) {
            throw new IllegalArgumentException("minDistanceKm не может быть отрицательным");
        }
        if (suspiciousKmh <= 0 || impossibleKmh <= suspiciousKmh) {
            throw new IllegalArgumentException(
                    "Нужно 0 < suspiciousKmh < impossibleKmh, а задано "
                            + suspiciousKmh + " и " + impossibleKmh);
        }
        if (!Scores.isUnitValue(minScore)) {
            throw new IllegalArgumentException("minScore должно лежать в [0, 1], а не " + minScore);
        }
    }
}
