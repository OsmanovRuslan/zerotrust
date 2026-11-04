package ru.donstu.zerotrust.core.factor;

import ru.donstu.zerotrust.core.model.GeoPoint;

import java.time.Duration;

/**
 * Геометрия на сфере для фактора N: расстояние между точками и скорость перемещения.
 *
 * <p>Служебный класс, экземпляры не создаются.
 */
public final class GeoMath {

    /** Средний радиус Земли, км. */
    public static final double EARTH_RADIUS_KM = 6371.0;

    /** Минимальный интервал при расчёте скорости: два вызова в одну секунду не дают бесконечность. */
    public static final Duration MIN_TRAVEL_INTERVAL = Duration.ofMinutes(1);

    private GeoMath() {
        throw new AssertionError("Служебный класс, экземпляры не создаются");
    }

    /**
     * Расстояние по большому кругу (формула гаверсинуса), км.
     *
     * @throws IllegalArgumentException если одна из точек не задана
     */
    public static double distanceKm(GeoPoint from, GeoPoint to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Для расчёта расстояния нужны обе точки");
        }
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double halfDeltaLat = (lat2 - lat1) / 2.0;
        double halfDeltaLon = Math.toRadians(to.longitude() - from.longitude()) / 2.0;

        double h = square(Math.sin(halfDeltaLat))
                + Math.cos(lat1) * Math.cos(lat2) * square(Math.sin(halfDeltaLon));
        return 2.0 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /**
     * Скорость перемещения, км/ч. Интервал меньше {@link #MIN_TRAVEL_INTERVAL} считается равным
     * ему: иначе два вызова подряд давали бы запредельную скорость на любом расстоянии.
     */
    public static double speedKmh(double distanceKm, Duration elapsed) {
        if (elapsed == null) {
            throw new IllegalArgumentException("Интервал между вызовами не задан");
        }
        Duration interval = (elapsed.compareTo(MIN_TRAVEL_INTERVAL) < 0) ? MIN_TRAVEL_INTERVAL : elapsed;
        return distanceKm / (interval.toMillis() / 3_600_000.0);
    }

    private static double square(double x) {
        return x * x;
    }
}
