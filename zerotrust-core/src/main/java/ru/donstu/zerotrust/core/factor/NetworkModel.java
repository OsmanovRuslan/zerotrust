package ru.donstu.zerotrust.core.factor;

import ru.donstu.zerotrust.core.model.FactorId;
import ru.donstu.zerotrust.core.model.FactorValue;
import ru.donstu.zerotrust.core.model.Scores;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Фактор N — сеть. Достоверность всегда равна 1: адрес вызова известен точно.
 *
 * <p>Итог — минимум из трёх оценок: адреса, страны и перемещения. Минимум, а не среднее:
 * одного тревожного признака сетевого контекста достаточно, чтобы потребовать подтверждение
 * («слабое звено»).
 */
public class NetworkModel {

    private final NetworkSettings settings;

    public NetworkModel(NetworkSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("Настройки фактора сети не заданы");
        }
        this.settings = settings;
    }

    /** Считает фактор N и складывает составляющие в разложение для журнала. */
    public FactorValue evaluate(NetworkObservation observation, NetworkProfile profile) {
        NetworkProfile known = (profile == null) ? NetworkProfile.EMPTY : profile;
        double ip = ipScore(observation, known);
        double country = countryScore(observation, known);
        TravelAssessment travel = assessTravel(observation, known);

        Map<String, Double> parts = new LinkedHashMap<>();
        parts.put("ip", ip);
        parts.put("country", country);
        if (travel.assessed()) {
            parts.put("travel", travel.score());
            parts.put("speed", Scores.round3(travel.speedKmh()));
        }
        double value = Math.min(ip, Math.min(country, travel.score()));
        return FactorValue.of(FactorId.N, value, parts);
    }

    /**
     * Оценка адреса: берётся первое подходящее условие сверху вниз — анонимайзер, доверенная
     * сеть, частный адрес, знакомая подсеть, новый публичный адрес.
     */
    public double ipScore(NetworkObservation observation, NetworkProfile profile) {
        requireObservation(observation);
        NetworkProfile known = (profile == null) ? NetworkProfile.EMPTY : profile;
        if (observation.anonymizer()) {
            return settings.anonymizer();
        }
        double trusted = -1.0;
        for (TrustedNetwork network : observation.trustedNetworks()) {
            if (network.cidr().contains(observation.ip())) {
                trusted = Math.max(trusted, network.trustValue());
            }
        }
        if (trusted >= 0.0) {
            return trusted;
        }
        if (observation.isPrivate()) {
            return settings.privateAddress();
        }
        if (known.knowsPrefix(IpAddresses.prefixOf(observation.ip()))) {
            return settings.knownPrefix();
        }
        return settings.newPublic();
    }

    /**
     * Оценка страны. Для частных адресов и при выключенной геолокации возвращает 1:
     * отсутствие данных не должно выглядеть как тревожный признак.
     */
    public double countryScore(NetworkObservation observation, NetworkProfile profile) {
        requireObservation(observation);
        NetworkProfile known = (profile == null) ? NetworkProfile.EMPTY : profile;
        if (observation.country() == null || observation.isPrivate()) {
            return 1.0;
        }
        return known.knowsCountry(observation.country()) ? 1.0 : settings.newCountry();
    }

    /**
     * Оценка перемещения с прошлого разрешённого вызова: расстояние по гаверсинусу, требуемая
     * скорость и линейное снижение оценки между «подозрительной» и «невозможной» скоростью.
     *
     * <p>Если проверять нечем (нет прошлых координат, нет геолокации, частный адрес),
     * возвращается {@link TravelAssessment#NOT_ASSESSED} с оценкой 1.
     */
    public TravelAssessment assessTravel(NetworkObservation observation, NetworkProfile profile) {
        requireObservation(observation);
        NetworkProfile known = (profile == null) ? NetworkProfile.EMPTY : profile;
        if (observation.geo() == null || !known.hasPosition() || observation.isPrivate()) {
            return TravelAssessment.NOT_ASSESSED;
        }
        TravelSettings travel = settings.travel();
        double distance = GeoMath.distanceKm(known.lastPoint(), observation.geo());
        Duration elapsed = Duration.between(known.lastSuccessAt(), observation.now());
        if (elapsed.isNegative()) {
            return TravelAssessment.NOT_ASSESSED;
        }
        double speed = GeoMath.speedKmh(distance, elapsed);
        if (distance < travel.minDistanceKm() || speed <= travel.suspiciousKmh()) {
            return new TravelAssessment(1.0, distance, speed, false, true);
        }
        if (speed >= travel.impossibleKmh()) {
            return new TravelAssessment(travel.minScore(), distance, speed, true, true);
        }
        double range = travel.impossibleKmh() - travel.suspiciousKmh();
        double score = 1.0 - (1.0 - travel.minScore()) * (speed - travel.suspiciousKmh()) / range;
        return new TravelAssessment(Scores.clamp01(score), distance, speed, false, true);
    }

    /**
     * Пополняет сетевой профиль после разрешённого вызова: запоминает подсеть, страну и точку.
     * Список подсетей ограничен настройкой, вытесняется самая давняя.
     */
    public NetworkProfile recordSuccess(NetworkObservation observation, NetworkProfile profile) {
        requireObservation(observation);
        NetworkProfile known = (profile == null) ? NetworkProfile.EMPTY : profile;

        List<String> prefixes = new ArrayList<>(known.knownPrefixes());
        if (!observation.isPrivate()) {
            String prefix = IpAddresses.prefixOf(observation.ip());
            prefixes.remove(prefix);
            prefixes.add(prefix);
            while (prefixes.size() > settings.maxKnownPrefixes()) {
                prefixes.removeFirst();
            }
        }

        List<String> countries = new ArrayList<>(known.knownCountries());
        if (observation.country() != null && !observation.isPrivate()
                && !countries.contains(observation.country())) {
            countries.add(observation.country());
        }

        boolean positionKnown = observation.geo() != null && !observation.isPrivate();
        return new NetworkProfile(prefixes, countries,
                positionKnown ? observation.geo() : known.lastPoint(),
                observation.now());
    }

    private static void requireObservation(NetworkObservation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("Наблюдение сети не задано");
        }
    }
}
