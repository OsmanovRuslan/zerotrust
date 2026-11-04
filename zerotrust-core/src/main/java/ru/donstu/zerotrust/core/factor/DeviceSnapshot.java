package ru.donstu.zerotrust.core.factor;

import java.time.Instant;

/**
 * Устройство пользователя, как оно записано в хранилище (таблица zt_device).
 *
 * @param deviceKey SHA-256 от X-Device-Id или от User-Agent, hex
 * @param status    состояние
 * @param hasId     истина, если ключ получен из заголовка X-Device-Id
 * @param sightings сколько раз устройство видели при разрешённых вызовах
 * @param firstSeen когда увидели впервые
 * @param lastSeen  когда видели в последний раз
 */
public record DeviceSnapshot(String deviceKey, DeviceStatus status, boolean hasId, long sightings,
                             Instant firstSeen, Instant lastSeen) {

    public DeviceSnapshot {
        if (deviceKey == null || deviceKey.isBlank()) {
            throw new IllegalArgumentException("Ключ устройства не задан");
        }
        if (status == null) {
            throw new IllegalArgumentException("Состояние устройства не задано");
        }
        if (sightings < 0) {
            throw new IllegalArgumentException("Счётчик встреч не может быть отрицательным");
        }
    }

    /** Устройство, которое только что увидели впервые. */
    public static DeviceSnapshot firstSighting(String deviceKey, boolean hasId, Instant now) {
        return new DeviceSnapshot(deviceKey, DeviceStatus.NEW, hasId, 1, now, now);
    }

    /** Истина, если устройство отозвано: вызов отклоняется до расчёта оценки доверия. */
    public boolean isRevoked() {
        return status == DeviceStatus.REVOKED;
    }
}
