package ru.donstu.zerotrust.core.factor;

import ru.donstu.zerotrust.core.model.FactorId;
import ru.donstu.zerotrust.core.model.FactorValue;
import ru.donstu.zerotrust.core.model.Scores;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Фактор R — репутация.
 *
 * <p>На пользователя хранится одно число — накопленный штраф P за события безопасности.
 * Он затухает с периодом полураспада H, поэтому пересчёт при чтении стоит O(1):
 * P(t) = max(0, P(t₀)·2^(−(t−t₀)/H) + s), R = clamp(1 − P).
 */
public class ReputationModel {

    private final ReputationSettings settings;

    public ReputationModel(ReputationSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("Настройки фактора репутации не заданы");
        }
        this.settings = settings;
    }

    /**
     * Штраф на момент {@code now} без нового события — только затухание.
     *
     * @param penalty   штраф, записанный в хранилище
     * @param updatedAt когда он был записан
     */
    public double decayedPenalty(double penalty, Instant updatedAt, Instant now) {
        return applyEvent(penalty, updatedAt, 0.0, now);
    }

    /**
     * Штраф после события тяжестью {@code severity}: сначала затухание, потом событие.
     * Отрицательная тяжесть (успешный step-up) частично восстанавливает репутацию,
     * но ниже нуля штраф не уходит.
     */
    public double applyEvent(double penalty, Instant updatedAt, double severity, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Время вызова не задано");
        }
        double current = Math.max(0.0, penalty);
        if (updatedAt != null && current > 0.0) {
            Duration elapsed = Duration.between(updatedAt, now);
            if (elapsed.isPositive()) {
                double halfLives = (double) elapsed.toMillis() / settings.halfLife().toMillis();
                current = current * Math.pow(2.0, -halfLives);
            }
        }
        return Math.max(0.0, current + severity);
    }

    /**
     * Считает фактор R.
     *
     * @param penalty   штраф из хранилища
     * @param updatedAt когда штраф был записан; {@code null}, если событий ещё не было
     * @param firstSeen первый вызов пользователя; {@code null}, если истории нет
     * @param now       текущее время
     */
    public FactorValue evaluate(double penalty, Instant updatedAt, Instant firstSeen, Instant now) {
        double current = decayedPenalty(penalty, updatedAt, now);
        double value = Scores.clamp01(1.0 - current);
        double confidence = confidence(current, firstSeen, now);
        return new FactorValue(FactorId.R, value, confidence,
                Map.of("penalty", Scores.round3(current)), false);
    }

    /**
     * Достоверность фактора: при заметном штрафе — единица (наказание учитывается сразу
     * и в полную силу), иначе она растёт вместе с историей пользователя. Новый пользователь
     * не получает «бесплатную» высокую репутацию.
     */
    public double confidence(double penalty, Instant firstSeen, Instant now) {
        if (penalty >= settings.penaltyFullTrust()) {
            return 1.0;
        }
        if (firstSeen == null) {
            return 0.0;
        }
        Duration history = Duration.between(firstSeen, now);
        if (history.isNegative()) {
            return 0.0;
        }
        double days = (double) history.toMillis() / Duration.ofDays(1).toMillis();
        return Scores.clamp01(days / settings.confidenceDays());
    }
}
