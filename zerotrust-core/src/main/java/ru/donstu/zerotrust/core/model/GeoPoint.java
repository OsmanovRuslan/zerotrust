package ru.donstu.zerotrust.core.model;

/**
 * Географические координаты IP-адреса клиента, полученные из базы геолокации.
 *
 * <p>Нужны только для признака перемещения в факторе N: по расстоянию между
 * координатами двух последовательных успешных вызовов и времени между ними считается скорость,
 * и при v ≥ 900 км/ч возникает событие {@code IMPOSSIBLE_TRAVEL}.
 *
 * @param latitude  широта в градусах, [-90, 90]
 * @param longitude долгота в градусах, [-180, 180]
 */
public record GeoPoint(double latitude, double longitude) {

    public GeoPoint {
        if (Double.isNaN(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Широта должна лежать в [-90, 90], получено: " + latitude);
        }
        if (Double.isNaN(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Долгота должна лежать в [-180, 180], получено: " + longitude);
        }
    }
}
