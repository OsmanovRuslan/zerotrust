package ru.donstu.zerotrust.core.factor;

import ru.donstu.zerotrust.core.model.FactorId;
import ru.donstu.zerotrust.core.model.FactorValue;
import ru.donstu.zerotrust.core.model.Scores;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Фактор B — поведение. Три признака: обычный ли это час для пользователя,
 * обычная ли частота запросов и знакомая ли операция.
 *
 * <p>Достоверность c_B растёт вместе с профилем: пока успешных вызовов меньше
 * {@link BehaviorSettings#minSamples()}, фактор влияет на оценку доверия лишь частично.
 *
 * <p>Профиль обновляется только после разрешённых вызовов, иначе злоумышленник приучил бы
 * профиль к своему поведению. Исключение — счётчик текущей минуты: он увеличивается на каждом
 * вызове, иначе перебор не был бы виден.
 */
public class BehaviorModel {

    private final BehaviorSettings settings;

    public BehaviorModel(BehaviorSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("Настройки фактора поведения не заданы");
        }
        this.settings = settings;
    }

    /**
     * Считает фактор B.
     *
     * @param profile            профиль пользователя
     * @param operation          имя операции
     * @param hour               час вызова в часовом поясе модуля
     * @param requestsInMinute   сколько вызовов пользователь сделал в текущей минуте, включая этот
     * @param now                время вызова
     */
    public FactorValue evaluate(BehaviorProfile profile, String operation, int hour,
                                long requestsInMinute, Instant now) {
        requireProfile(profile);
        double time = timeScore(profile, hour);
        double rate = rateScore(profile, requestsInMinute);
        double resource = resourceScore(profile, operation, now);

        double value = settings.timeWeight() * time
                + settings.rateWeight() * rate
                + settings.resourceWeight() * resource;

        Map<String, Double> parts = new LinkedHashMap<>();
        parts.put("time", time);
        parts.put("rate", rate);
        parts.put("res", resource);
        return FactorValue.of(FactorId.B, value, confidence(profile), parts);
    }

    /** Достоверность фактора: доля накопленной истории от нужного числа успешных вызовов. */
    public double confidence(BehaviorProfile profile) {
        requireProfile(profile);
        return Scores.clamp01((double) profile.sampleCount() / settings.minSamples());
    }

    /**
     * Признак «час суток»: сглаженная по соседним часам доля активности, отнесённая к доле
     * равномерного распределения. Час, в котором пользователь активен не меньше, чем
     * {@code hourKappa} от равномерной доли, даёт единицу. Новый пользователь с равномерной
     * гистограммой получает единицу в любой час.
     */
    public double timeScore(BehaviorProfile profile, int hour) {
        requireProfile(profile);
        if (hour < 0 || hour >= BehaviorProfile.HOURS) {
            throw new IllegalArgumentException("Час " + hour + " вне диапазона [0, 23]");
        }
        int previous = (hour + BehaviorProfile.HOURS - 1) % BehaviorProfile.HOURS;
        int next = (hour + 1) % BehaviorProfile.HOURS;
        double smoothed = 0.25 * profile.hour(previous) + 0.5 * profile.hour(hour)
                + 0.25 * profile.hour(next);
        double uniformShare = settings.hourKappa() / BehaviorProfile.HOURS;
        return Scores.clamp01(smoothed / uniformShare);
    }

    /**
     * Признак «частота запросов»: насколько число вызовов в текущей минуте отклоняется от
     * обычного для этого пользователя. Отклонение до {@code rateZFull} сигм — норма,
     * от {@code rateZZero} — признак обнуляется.
     */
    public double rateScore(BehaviorProfile profile, long requestsInMinute) {
        requireProfile(profile);
        double z = zScore(profile, requestsInMinute);
        double range = settings.rateZZero() - settings.rateZFull();
        return Scores.clamp01(1.0 - (z - settings.rateZFull()) / range);
    }

    /** Отклонение числа запросов от среднего в сигмах. */
    public double zScore(BehaviorProfile profile, long requestsInMinute) {
        requireProfile(profile);
        double deviation = Math.max(profile.rateDeviation(), 1.0);
        return (requestsInMinute - profile.rateMean()) / deviation;
    }

    /** Признак «новизна операции»: знакомая операция даёт единицу, незнакомая — настройку. */
    public double resourceScore(BehaviorProfile profile, String operation, Instant now) {
        requireProfile(profile);
        if (operation == null || now == null) {
            return settings.newOperationScore();
        }
        Instant lastCall = profile.knownOperations().get(operation);
        if (lastCall == null) {
            return settings.newOperationScore();
        }
        Duration since = Duration.between(lastCall, now);
        boolean remembered = !since.isNegative() && since.compareTo(settings.operationMemory()) <= 0;
        return remembered ? 1.0 : settings.newOperationScore();
    }

    /**
     * Учитывает вызов в счётчике текущей минуты. При смене минуты закрывает прошлую: если в ней
     * были разрешённые вызовы, её итог обновляет среднее и дисперсию частоты.
     *
     * <p>Вызывается на каждом защищённом вызове, включая отклонённые.
     */
    public BehaviorProfile countRequest(BehaviorProfile profile, Instant now) {
        requireProfile(profile);
        if (now == null) {
            throw new IllegalArgumentException("Время вызова не задано");
        }
        Instant minute = now.truncatedTo(ChronoUnit.MINUTES);
        if (minute.equals(profile.currentMinute())) {
            return new BehaviorProfile(profile.sampleCount(), profile.hourHistogram(),
                    profile.rateMean(), profile.rateVariance(),
                    minute, profile.currentMinuteCount() + 1, profile.currentMinuteSuccesses(),
                    profile.knownOperations(), profile.firstSeen());
        }

        double mean = profile.rateMean();
        double variance = profile.rateVariance();
        if (profile.currentMinute() != null && profile.currentMinuteSuccesses() > 0) {
            double delta = profile.currentMinuteCount() - mean;
            mean = mean + settings.rateAlpha() * delta;
            variance = (1.0 - settings.rateAlpha())
                    * (variance + settings.rateAlpha() * delta * delta);
        }
        return new BehaviorProfile(profile.sampleCount(), profile.hourHistogram(), mean, variance,
                minute, 1, 0, profile.knownOperations(), profile.firstSeen());
    }

    /**
     * Обновляет профиль после разрешённого вызова: сдвигает гистограмму часов к текущему часу,
     * запоминает операцию и увеличивает счётчики.
     */
    public BehaviorProfile recordSuccess(BehaviorProfile profile, int hour, String operation, Instant now) {
        requireProfile(profile);
        if (hour < 0 || hour >= BehaviorProfile.HOURS) {
            throw new IllegalArgumentException("Час " + hour + " вне диапазона [0, 23]");
        }
        if (now == null) {
            throw new IllegalArgumentException("Время вызова не задано");
        }

        List<Double> histogram = new ArrayList<>(profile.hourHistogram());
        double alpha = settings.hourAlpha();
        for (int i = 0; i < BehaviorProfile.HOURS; i++) {
            double target = (i == hour) ? 1.0 : 0.0;
            histogram.set(i, (1.0 - alpha) * histogram.get(i) + alpha * target);
        }

        Map<String, Instant> operations = new HashMap<>(profile.knownOperations());
        if (operation != null) {
            operations.put(operation, now);
            evictOldest(operations);
        }

        return new BehaviorProfile(profile.sampleCount() + 1, histogram,
                profile.rateMean(), profile.rateVariance(),
                profile.currentMinute(), profile.currentMinuteCount(),
                profile.currentMinuteSuccesses() + 1,
                operations, (profile.firstSeen() == null) ? now : profile.firstSeen());
    }

    /** Держит словарь операций в заданном размере, вытесняя самую давнюю. */
    private void evictOldest(Map<String, Instant> operations) {
        while (operations.size() > settings.maxKnownOperations()) {
            String oldest = null;
            Instant oldestTime = null;
            for (Map.Entry<String, Instant> entry : operations.entrySet()) {
                if (oldestTime == null || entry.getValue().isBefore(oldestTime)) {
                    oldest = entry.getKey();
                    oldestTime = entry.getValue();
                }
            }
            operations.remove(oldest);
        }
    }

    private static void requireProfile(BehaviorProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("Профиль поведения не задан");
        }
    }
}
