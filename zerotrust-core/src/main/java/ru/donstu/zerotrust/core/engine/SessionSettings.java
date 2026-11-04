package ru.donstu.zerotrust.core.engine;

import java.time.Duration;

/**
 * Настройки непрерывной оценки доверия в сессии ({@code zerotrust.session}).
 *
 * @param driftDelta   насколько оценка должна упасть относительно базовой линии, чтобы это
 *                     считалось дрейфом
 * @param baselineBeta скорость, с которой базовая линия подтягивается к текущей оценке
 * @param idleTimeout  через сколько неактивности сессия удаляется задачей очистки
 */
public record SessionSettings(double driftDelta, double baselineBeta, Duration idleTimeout) {

    /** Умолчания: падение на 0,25, β = 0,2, 12 часов. */
    public static final SessionSettings DEFAULT = new SessionSettings(0.25, 0.2, Duration.ofHours(12));

    public SessionSettings {
        if (driftDelta <= 0 || driftDelta > 1) {
            throw new IllegalArgumentException("driftDelta должно лежать в (0, 1], а не " + driftDelta);
        }
        if (baselineBeta <= 0 || baselineBeta > 1) {
            throw new IllegalArgumentException("baselineBeta должно лежать в (0, 1], а не " + baselineBeta);
        }
        if (idleTimeout == null || idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idleTimeout должен быть положительным");
        }
    }

    /**
     * Новая базовая линия сессии после разрешённого вызова:
     * TS₀ ← (1 − β)·TS₀ + β·TS.
     */
    public double updateBaseline(double baseline, double trustScore) {
        return (1.0 - baselineBeta) * baseline + baselineBeta * trustScore;
    }

    /** Истина, если оценка упала относительно базовой линии настолько, что это дрейф. */
    public boolean isDrift(Double baseline, double trustScore) {
        return baseline != null && trustScore < baseline - driftDelta;
    }
}
