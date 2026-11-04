package ru.donstu.zerotrust.core.factor;

import java.time.Duration;

/**
 * Настройки фактора R ({@code zerotrust.factors.reputation}).
 *
 * @param halfLife        период полураспада накопленного штрафа
 * @param confidenceDays  за сколько дней истории достоверность дорастает до единицы
 * @param penaltyFullTrust штраф, начиная с которого достоверность сразу равна единице:
 *                        наказание учитывается в полную силу даже у нового пользователя
 */
public record ReputationSettings(Duration halfLife, int confidenceDays, double penaltyFullTrust) {

    /** Умолчания: полураспад 72 часа, 7 дней истории, порог штрафа 0,05. */
    public static final ReputationSettings DEFAULT =
            new ReputationSettings(Duration.ofHours(72), 7, 0.05);

    public ReputationSettings {
        if (halfLife == null || halfLife.isZero() || halfLife.isNegative()) {
            throw new IllegalArgumentException("Период полураспада должен быть положительным");
        }
        if (confidenceDays < 1) {
            throw new IllegalArgumentException("confidenceDays должно быть не меньше 1");
        }
        if (penaltyFullTrust < 0) {
            throw new IllegalArgumentException("penaltyFullTrust не может быть отрицательным");
        }
    }
}
