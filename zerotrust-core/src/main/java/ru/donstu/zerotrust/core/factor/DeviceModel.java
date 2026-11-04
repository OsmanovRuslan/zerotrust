package ru.donstu.zerotrust.core.factor;

import ru.donstu.zerotrust.core.model.FactorId;
import ru.donstu.zerotrust.core.model.FactorValue;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Фактор D — устройство. Достоверность всегда равна 1: состояние устройства
 * известно точно.
 *
 * <p>Состояние {@link DeviceStatus#REVOKED} здесь не обрабатывается: отозванное устройство
 * отклоняется на шаге 3 алгоритма решения, до расчёта оценки доверия.
 */
public class DeviceModel {

    private final DeviceSettings settings;

    public DeviceModel(DeviceSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("Настройки фактора устройства не заданы");
        }
        this.settings = settings;
    }

    /**
     * Считает фактор D.
     *
     * @param device устройство из хранилища или {@code null}, если оно встречается впервые
     * @param hasId  истина, если ключ устройства получен из заголовка X-Device-Id, а не из
     *               User-Agent
     * @param now    текущее время
     */
    public FactorValue evaluate(DeviceSnapshot device, boolean hasId, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Время вызова не задано");
        }
        double score = score(device, hasId, now);
        long sightings = (device == null) ? 0 : device.sightings();
        return FactorValue.of(FactorId.D, score, Map.of("sightings", (double) sightings));
    }

    /** Значение фактора без упаковки в {@link FactorValue}. */
    public double score(DeviceSnapshot device, boolean hasId, Instant now) {
        if (device != null && device.isRevoked()) {
            // Сюда расчёт доходить не должен: отозванное устройство отклоняется раньше.
            return 0.0;
        }
        if (!hasId) {
            // Ключ по User-Agent не опознаёт устройство: такой же ключ получит любой клиент
            // с тем же браузером и языком, поэтому доверия к нему минимум.
            return settings.noId();
        }
        if (device == null) {
            return settings.newDevice();
        }
        if (device.status() == DeviceStatus.TRUSTED) {
            return settings.trusted();
        }
        if (isKnown(device, now)) {
            return settings.known();
        }
        return settings.seen();
    }

    /**
     * Истина, если устройству можно присвоить статус {@link DeviceStatus#TRUSTED}.
     *
     * <p>Проверять обязаны оба перехода: успешный step-up при
     * {@code trust-device-after-step-up: true} и админский API. Ключ, полученный из User-Agent,
     * доверенным не становится: он одинаков у всех клиентов с таким же браузером и языком,
     * а запись со статусом {@code TRUSTED} и оценкой 0,3 противоречила бы журналу решения.
     * Отозванное устройство тоже нельзя сделать доверенным — его сначала возвращают в обычное
     * состояние.
     */
    public boolean canBecomeTrusted(DeviceSnapshot device) {
        return device != null && device.hasId() && !device.isRevoked();
    }

    /** Устройство считается знакомым, если его видели достаточно часто и впервые — достаточно давно. */
    public boolean isKnown(DeviceSnapshot device, Instant now) {
        if (device == null || device.firstSeen() == null) {
            return false;
        }
        boolean seenEnough = device.sightings() >= settings.knownAfterSightings();
        Duration age = Duration.between(device.firstSeen(), now);
        return seenEnough && age.compareTo(settings.knownAfterAge()) >= 0;
    }
}
