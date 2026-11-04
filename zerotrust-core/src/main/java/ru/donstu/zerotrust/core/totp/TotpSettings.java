package ru.donstu.zerotrust.core.totp;

import java.time.Duration;

/**
 * Настройки одноразовых кодов ({@code zerotrust.step-up.totp}).
 *
 * @param digits длина кода
 * @param period шаг времени
 * @param window допуск в шагах в каждую сторону: компенсирует расхождение часов
 */
public record TotpSettings(int digits, Duration period, int window) {

    /** Умолчания и RFC 6238: 6 цифр, 30 секунд, допуск ±1 шаг. */
    public static final TotpSettings DEFAULT = new TotpSettings(6, Duration.ofSeconds(30), 1);

    public TotpSettings {
        if (digits < 6 || digits > 8) {
            throw new IllegalArgumentException("Длина кода должна быть от 6 до 8 цифр, а не " + digits);
        }
        if (period == null || period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("Шаг времени должен быть положительным");
        }
        if (window < 0 || window > 10) {
            throw new IllegalArgumentException("Допуск должен лежать в [0, 10] шагов, а не " + window);
        }
    }
}
