package ru.donstu.zerotrust.core.factor;

import ru.donstu.zerotrust.core.model.Scores;

/**
 * Настройки фактора N ({@code zerotrust.factors.network}).
 *
 * @param anonymizer       оценка адреса из списка анонимайзеров
 * @param privateAddress   оценка частного или локального адреса
 * @param knownPrefix      оценка адреса из знакомой пользователю подсети
 * @param newPublic        оценка нового публичного адреса
 * @param newCountry       оценка новой для пользователя страны
 * @param maxKnownPrefixes сколько префиксов хранить в профиле
 * @param travel           настройки проверки перемещения
 */
public record NetworkSettings(double anonymizer, double privateAddress, double knownPrefix,
                              double newPublic, double newCountry, int maxKnownPrefixes,
                              TravelSettings travel) {

    /** Умолчания. */
    public static final NetworkSettings DEFAULT =
            new NetworkSettings(0.1, 0.9, 0.8, 0.5, 0.6, 50, TravelSettings.DEFAULT);

    public NetworkSettings {
        requireUnit(anonymizer, "anonymizer");
        requireUnit(privateAddress, "privateAddress");
        requireUnit(knownPrefix, "knownPrefix");
        requireUnit(newPublic, "newPublic");
        requireUnit(newCountry, "newCountry");
        if (maxKnownPrefixes < 1) {
            throw new IllegalArgumentException("maxKnownPrefixes должно быть не меньше 1");
        }
        if (travel == null) {
            throw new IllegalArgumentException("Настройки перемещения не заданы");
        }
    }

    private static void requireUnit(double value, String name) {
        if (!Scores.isUnitValue(value)) {
            throw new IllegalArgumentException("Значение " + name + " должно лежать в [0, 1], а не " + value);
        }
    }
}
