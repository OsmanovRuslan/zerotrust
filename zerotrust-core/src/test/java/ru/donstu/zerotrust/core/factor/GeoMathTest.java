package ru.donstu.zerotrust.core.factor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.donstu.zerotrust.core.model.GeoPoint;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/** Тесты расстояния и скорости перемещения. */
class GeoMathTest {

    private static final GeoPoint ROSTOV = new GeoPoint(47.2357, 39.7015);
    private static final GeoPoint MOSCOW = new GeoPoint(55.7558, 37.6173);

    @Test
    @DisplayName("расстояние Ростов — Москва около 957 км")
    void realDistance() {
        assertThat(GeoMath.distanceKm(ROSTOV, MOSCOW)).isEqualTo(957.0, within(5.0));
    }

    @Test
    @DisplayName("расстояние симметрично, до самой себя — нуль")
    void symmetryAndZero() {
        assertThat(GeoMath.distanceKm(ROSTOV, ROSTOV)).isZero();
        assertThat(GeoMath.distanceKm(MOSCOW, ROSTOV))
                .isEqualTo(GeoMath.distanceKm(ROSTOV, MOSCOW), within(1e-9));
    }

    @Test
    @DisplayName("градус по меридиану и по экватору — около 111,2 км")
    void oneDegree() {
        assertThat(GeoMath.distanceKm(new GeoPoint(0, 0), new GeoPoint(1, 0)))
                .isEqualTo(111.19, within(0.05));
        assertThat(GeoMath.distanceKm(new GeoPoint(0, 0), new GeoPoint(0, 1)))
                .isEqualTo(111.19, within(0.05));
    }

    @Test
    @DisplayName("противоположные точки — половина окружности Земли")
    void antipodalPoints() {
        assertThat(GeoMath.distanceKm(new GeoPoint(0, 0), new GeoPoint(0, 180)))
                .isEqualTo(Math.PI * GeoMath.EARTH_RADIUS_KM, within(1e-6));
    }

    @Test
    @DisplayName("без одной из точек расстояние не считается")
    void pointsAreRequired() {
        assertThatIllegalArgumentException().isThrownBy(() -> GeoMath.distanceKm(null, MOSCOW));
        assertThatIllegalArgumentException().isThrownBy(() -> GeoMath.distanceKm(ROSTOV, null));
    }

    @Test
    @DisplayName("1500 км за 10 минут — это 9000 км/ч, заведомо невозможное перемещение")
    void stolenTokenSpeed() {
        assertThat(GeoMath.speedKmh(1500, Duration.ofMinutes(10))).isEqualTo(9000.0, within(1e-9));
    }

    @Test
    @DisplayName("1500 км за 3 часа — 500 км/ч, предел для самолёта")
    void flightSpeed() {
        assertThat(GeoMath.speedKmh(1500, Duration.ofHours(3))).isEqualTo(500.0, within(1e-9));
    }

    @Test
    @DisplayName("интервал меньше минуты считается минутой: иначе скорость уходит в бесконечность")
    void shortIntervalIsRaisedToOneMinute() {
        double speed = GeoMath.speedKmh(100, Duration.ofSeconds(1));

        assertThat(speed).isEqualTo(6000.0, within(1e-9));
        assertThat(speed).isEqualTo(GeoMath.speedKmh(100, Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("без интервала скорость не считается")
    void intervalIsRequired() {
        assertThatIllegalArgumentException().isThrownBy(() -> GeoMath.speedKmh(10, null));
    }
}
