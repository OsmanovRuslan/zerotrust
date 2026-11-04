package ru.donstu.zerotrust.core.factor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.donstu.zerotrust.core.model.FactorId;
import ru.donstu.zerotrust.core.model.FactorValue;
import ru.donstu.zerotrust.core.model.GeoPoint;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/** Тесты фактора N — сеть: оценка адреса, страны и перемещения. */
class NetworkModelTest {

    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
    private static final GeoPoint ROSTOV = new GeoPoint(47.2357, 39.7015);
    private static final GeoPoint MOSCOW = new GeoPoint(55.7558, 37.6173);

    private final NetworkModel model = new NetworkModel(NetworkSettings.DEFAULT);

    private static InetAddress ip(String value) {
        return IpAddresses.parse(value);
    }

    private static NetworkObservation from(String address) {
        return NetworkObservation.of(ip(address), NOW);
    }

    @Nested
    @DisplayName("Оценка адреса")
    class IpScore {

        @Test
        @DisplayName("адрес из списка анонимайзеров — 0,1, даже если он в доверенной сети")
        void anonymizerWins() {
            NetworkObservation tor = new NetworkObservation(ip("203.0.113.5"), null, null, true,
                    List.of(TrustedNetwork.of("203.0.113.0/24")), NOW);

            assertThat(model.ipScore(tor, NetworkProfile.EMPTY)).isEqualTo(0.1);
        }

        @Test
        @DisplayName("доверенная сеть даёт свою оценку, при нескольких совпадениях — наибольшую")
        void trustedNetwork() {
            NetworkObservation office = new NetworkObservation(ip("203.0.113.5"), null, null, false,
                    List.of(TrustedNetwork.of("203.0.113.0/24", 0.7),
                            TrustedNetwork.of("203.0.113.0/25", 0.95),
                            TrustedNetwork.of("198.51.100.0/24", 1.0)), NOW);

            assertThat(model.ipScore(office, NetworkProfile.EMPTY)).isEqualTo(0.95);
        }

        @Test
        @DisplayName("вызов из офисной сети — частный адрес, 0,9")
        void privateAddress() {
            assertThat(model.ipScore(from("10.1.2.3"), NetworkProfile.EMPTY)).isEqualTo(0.9);
        }

        @Test
        @DisplayName("знакомая пользователю подсеть — 0,8")
        void knownPrefix() {
            NetworkProfile profile = new NetworkProfile(List.of("203.0.113.0/24"), List.of(), null, null);

            assertThat(model.ipScore(from("203.0.113.90"), profile)).isEqualTo(0.8);
            assertThat(model.ipScore(from("203.0.114.90"), profile)).isEqualTo(0.5);
        }

        @Test
        @DisplayName("новый публичный адрес — 0,5")
        void newPublicAddress() {
            assertThat(model.ipScore(from("8.8.8.8"), NetworkProfile.EMPTY)).isEqualTo(0.5);
            assertThat(model.ipScore(from("8.8.8.8"), null)).isEqualTo(0.5);
        }
    }

    @Nested
    @DisplayName("Оценка страны")
    class CountryScore {

        @Test
        @DisplayName("знакомая страна — 1,0, новая — 0,6")
        void knownAndNewCountry() {
            NetworkProfile profile = new NetworkProfile(List.of(), List.of("RU"), null, null);
            NetworkObservation fromRussia = new NetworkObservation(ip("203.0.113.5"), "ru", null, false,
                    List.of(), NOW);
            NetworkObservation fromGermany = new NetworkObservation(ip("203.0.113.5"), "DE", null, false,
                    List.of(), NOW);

            assertThat(model.countryScore(fromRussia, profile)).isEqualTo(1.0);
            assertThat(model.countryScore(fromGermany, profile)).isEqualTo(0.6);
        }

        @Test
        @DisplayName("без геолокации и для частных адресов страна не снижает оценку")
        void unknownCountryIsNeutral() {
            NetworkObservation noGeo = from("203.0.113.5");
            NetworkObservation privateIp = new NetworkObservation(ip("10.0.0.1"), "DE", null, false,
                    List.of(), NOW);

            assertThat(model.countryScore(noGeo, NetworkProfile.EMPTY)).isEqualTo(1.0);
            assertThat(model.countryScore(privateIp, NetworkProfile.EMPTY)).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Перемещение")
    class Travel {

        private NetworkObservation at(GeoPoint point, Instant now) {
            return new NetworkObservation(ip("203.0.113.5"), "RU", point, false, List.of(), now);
        }

        private NetworkProfile lastSeen(GeoPoint point, Duration ago) {
            return new NetworkProfile(List.of(), List.of("RU"), point, NOW.minus(ago));
        }

        @ParameterizedTest(name = "1000 км за {0} мин -> s_travel = {1}")
        @CsvSource({
                "120, 1.0",
                "86,  0.555",
                "67,  0.110",
                "60,  0.1",
                "30,  0.1"
        })
        @DisplayName("оценка линейно падает между 500 и 900 км/ч")
        void travelScoreBySpeed(long minutes, double expected) {
            GeoPoint start = new GeoPoint(0, 0);
            GeoPoint end = new GeoPoint(0, 1000.0 / 111.19492664455873);

            TravelAssessment assessment = model.assessTravel(
                    at(end, NOW), lastSeen(start, Duration.ofMinutes(minutes)));

            assertThat(assessment.distanceKm()).isEqualTo(1000.0, within(0.5));
            assertThat(assessment.score()).isEqualTo(expected, within(0.005));
            assertThat(assessment.assessed()).isTrue();
        }

        @Test
        @DisplayName("957 км за 10 минут — невозможное перемещение, нужно событие")
        void impossibleTravel() {
            TravelAssessment assessment = model.assessTravel(
                    at(MOSCOW, NOW), lastSeen(ROSTOV, Duration.ofMinutes(10)));

            assertThat(assessment.speedKmh()).isGreaterThan(900);
            assertThat(assessment.score()).isEqualTo(0.1);
            assertThat(assessment.impossible()).isTrue();
        }

        @Test
        @DisplayName("перемещение меньше 50 км не оценивается: это погрешность геолокации")
        void shortDistanceIsIgnored() {
            GeoPoint nearby = new GeoPoint(ROSTOV.latitude() + 0.1, ROSTOV.longitude());

            TravelAssessment assessment = model.assessTravel(
                    at(nearby, NOW), lastSeen(ROSTOV, Duration.ofMinutes(1)));

            assertThat(assessment.distanceKm()).isLessThan(50);
            assertThat(assessment.score()).isEqualTo(1.0);
            assertThat(assessment.impossible()).isFalse();
        }

        @Test
        @DisplayName("скорость самолёта (500 км/ч) ещё не подозрительна")
        void flightIsAllowed() {
            TravelAssessment assessment = model.assessTravel(
                    at(MOSCOW, NOW), lastSeen(ROSTOV, Duration.ofMinutes(115)));

            assertThat(assessment.speedKmh()).isLessThan(500);
            assertThat(assessment.score()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("без прошлых координат, без геолокации и для частных адресов не оценивается")
        void notAssessed() {
            assertThat(model.assessTravel(at(MOSCOW, NOW), NetworkProfile.EMPTY))
                    .isEqualTo(TravelAssessment.NOT_ASSESSED);
            assertThat(model.assessTravel(at(null, NOW), lastSeen(ROSTOV, Duration.ofMinutes(10))))
                    .isEqualTo(TravelAssessment.NOT_ASSESSED);
            NetworkObservation privateIp = new NetworkObservation(ip("10.0.0.1"), "RU", MOSCOW, false,
                    List.of(), NOW);
            assertThat(model.assessTravel(privateIp, lastSeen(ROSTOV, Duration.ofMinutes(10))))
                    .isEqualTo(TravelAssessment.NOT_ASSESSED);
            assertThat(TravelAssessment.NOT_ASSESSED.score()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("время в профиле из будущего не оценивается: часы разъехались")
        void negativeIntervalIsIgnored() {
            NetworkProfile future = new NetworkProfile(List.of(), List.of(), ROSTOV, NOW.plusSeconds(60));

            assertThat(model.assessTravel(at(MOSCOW, NOW), future))
                    .isEqualTo(TravelAssessment.NOT_ASSESSED);
        }
    }

    @Nested
    @DisplayName("Итог фактора: минимум по трём признакам")
    class FactorValueCases {

        @Test
        @DisplayName("офисная сеть даёт N = 0,9")
        void officeCall() {
            FactorValue n = model.evaluate(from("10.1.2.3"), NetworkProfile.EMPTY);

            assertThat(n.id()).isEqualTo(FactorId.N);
            assertThat(n.value()).isEqualTo(0.9);
            assertThat(n.confidence()).isEqualTo(1.0);
            assertThat(n.parts()).containsEntry("ip", 0.9).containsEntry("country", 1.0);
            assertThat(n.parts()).doesNotContainKey("travel");
        }

        @Test
        @DisplayName("украденный токен даёт N = 0,1 — минимум из 0,5, 0,6 и 0,1")
        void stolenToken() {
            NetworkObservation observation = new NetworkObservation(ip("198.51.100.7"), "DE", MOSCOW,
                    false, List.of(), NOW);
            NetworkProfile profile = new NetworkProfile(List.of("203.0.113.0/24"), List.of("RU"),
                    ROSTOV, NOW.minus(Duration.ofMinutes(10)));

            FactorValue n = model.evaluate(observation, profile);

            assertThat(n.parts()).containsEntry("ip", 0.5).containsEntry("country", 0.6)
                    .containsEntry("travel", 0.1);
            assertThat(n.value()).isEqualTo(0.1);
        }

        @Test
        @DisplayName("новая страна при знакомой подсети: минимум берёт 0,6")
        void weakestLinkWins() {
            NetworkObservation observation = new NetworkObservation(ip("203.0.113.5"), "DE", null,
                    false, List.of(), NOW);
            NetworkProfile profile = new NetworkProfile(List.of("203.0.113.0/24"), List.of("RU"),
                    null, null);

            assertThat(model.evaluate(observation, profile).value()).isEqualTo(0.6);
        }
    }

    @Nested
    @DisplayName("Пополнение профиля после разрешённого вызова")
    class ProfileUpdate {

        @Test
        @DisplayName("запоминаются подсеть, страна и точка последнего вызова")
        void recordsAddress() {
            NetworkObservation observation = new NetworkObservation(ip("203.0.113.5"), "RU", ROSTOV,
                    false, List.of(), NOW);

            NetworkProfile profile = model.recordSuccess(observation, NetworkProfile.EMPTY);

            assertThat(profile.knownPrefixes()).containsExactly("203.0.113.0/24");
            assertThat(profile.knownCountries()).containsExactly("RU");
            assertThat(profile.lastPoint()).isEqualTo(ROSTOV);
            assertThat(profile.lastSuccessAt()).isEqualTo(NOW);
            assertThat(profile.hasPosition()).isTrue();
        }

        @Test
        @DisplayName("частный адрес не попадает в список подсетей, но время обновляется")
        void privateAddressIsNotRemembered() {
            NetworkProfile profile = model.recordSuccess(from("10.0.0.1"), NetworkProfile.EMPTY);

            assertThat(profile.knownPrefixes()).isEmpty();
            assertThat(profile.lastSuccessAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("повторный вызов из той же подсети не создаёт дубль, а поднимает её в конец")
        void duplicatePrefixIsMovedToEnd() {
            NetworkProfile start = new NetworkProfile(List.of("8.8.8.0/24", "203.0.113.0/24"),
                    List.of(), null, null);

            NetworkProfile profile = model.recordSuccess(from("8.8.8.8"), start);

            assertThat(profile.knownPrefixes()).containsExactly("203.0.113.0/24", "8.8.8.0/24");
        }

        @Test
        @DisplayName("список подсетей ограничен настройкой: вытесняется самая давняя")
        void oldestPrefixIsEvicted() {
            NetworkModel small = new NetworkModel(new NetworkSettings(0.1, 0.9, 0.8, 0.5, 0.6, 2,
                    TravelSettings.DEFAULT));
            NetworkProfile start = new NetworkProfile(List.of("1.1.1.0/24", "2.2.2.0/24"),
                    List.of(), null, null);

            NetworkProfile profile = small.recordSuccess(from("3.3.3.3"), start);

            assertThat(profile.knownPrefixes()).containsExactly("2.2.2.0/24", "3.3.3.0/24");
        }

        @Test
        @DisplayName("прошлая точка сохраняется, если геолокации у нового вызова нет")
        void lastPointIsKept() {
            NetworkProfile start = new NetworkProfile(List.of(), List.of(), ROSTOV,
                    NOW.minus(Duration.ofDays(1)));

            NetworkProfile profile = model.recordSuccess(from("203.0.113.5"), start);

            assertThat(profile.lastPoint()).isEqualTo(ROSTOV);
            assertThat(profile.lastSuccessAt()).isEqualTo(NOW);
        }
    }

    @Test
    @DisplayName("не заданные настройки, адрес и время — ошибка")
    void argumentsAreChecked() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NetworkModel(null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> model.evaluate(null, NetworkProfile.EMPTY));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NetworkObservation.of(null, NOW));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NetworkObservation.of(ip("10.0.0.1"), null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new NetworkSettings(0.1, 0.9, 0.8, 0.5, 0.6, 0, TravelSettings.DEFAULT));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TravelSettings(50, 900, 500, 0.1));
    }
}
