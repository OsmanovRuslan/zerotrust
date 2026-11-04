package ru.donstu.zerotrust.core.factor;

import ru.donstu.zerotrust.core.model.Scores;

import java.time.Duration;

/**
 * Настройки фактора D ({@code zerotrust.factors.device}).
 *
 * @param trusted             значение для доверенного устройства
 * @param known               значение для знакомого устройства
 * @param seen                значение для устройства, которое видели, но оно ещё не знакомое
 * @param newDevice           значение для нового устройства
 * @param noId                значение, когда ключ получен из User-Agent, а не из X-Device-Id
 * @param knownAfterSightings сколько раз нужно увидеть устройство, чтобы считать знакомым
 * @param knownAfterAge       сколько времени должно пройти с первой встречи
 */
public record DeviceSettings(double trusted, double known, double seen, double newDevice, double noId,
                             int knownAfterSightings, Duration knownAfterAge) {

    /** Умолчания. */
    public static final DeviceSettings DEFAULT =
            new DeviceSettings(1.0, 0.8, 0.6, 0.4, 0.3, 3, Duration.ofHours(24));

    public DeviceSettings {
        requireUnit(trusted, "trusted");
        requireUnit(known, "known");
        requireUnit(seen, "seen");
        requireUnit(newDevice, "newDevice");
        requireUnit(noId, "noId");
        if (knownAfterSightings < 1) {
            throw new IllegalArgumentException("knownAfterSightings должно быть не меньше 1");
        }
        if (knownAfterAge == null || knownAfterAge.isNegative()) {
            throw new IllegalArgumentException("knownAfterAge должно быть задано и неотрицательно");
        }
    }

    private static void requireUnit(double value, String name) {
        if (!Scores.isUnitValue(value)) {
            throw new IllegalArgumentException("Значение " + name + " должно лежать в [0, 1], а не " + value);
        }
    }
}
