package ru.donstu.zerotrust.core.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Переопределение политики для пользователя или роли (таблица zt_override).
 *
 * <p>Поле, равное {@code null}, значение политики не меняет. Веса переопределяются целиком:
 * частичный набор весов запрещён, иначе их сумма перестала бы быть равной единице.
 *
 * @param id               идентификатор: попадает в журнал решения, чтобы было видно, почему
 *                         решение отличается от обычного
 * @param subjectType      на пользователя или на роль
 * @param subjectId        идентификатор пользователя или имя роли
 * @param policyName       политика, к которой относится; {@code null} — ко всем
 * @param weights          веса целиком или {@code null}
 * @param low              низкий порог или {@code null}
 * @param high             высокий порог или {@code null}
 * @param critical         критический порог или {@code null}
 * @param disabledFactors  факторы, которые отключаются
 * @param disabledRules    адреса правил, которые отключаются: {@code time-window},
 *                         {@code time-window@payments.transfer}, {@code *@payments.transfer}
 * @param enforcement      режим применения или {@code null}
 * @param priority         приоритет: при нескольких подходящих ролях выигрывает больший
 * @param reason           обоснование: обязательно, попадает в журнал администраторов
 * @param validFrom        с какого момента действует
 * @param validUntil       до какого момента действует
 * @param createdAt        когда создано: разрешает ничью при равном приоритете
 * @param revokedAt        когда отозвано или {@code null}
 */
public record PolicyOverride(UUID id, SubjectType subjectType, String subjectId, String policyName,
                       Weights weights, Double low, Double high, Double critical,
                       Set<FactorId> disabledFactors, Set<String> disabledRules,
                       Enforcement enforcement, int priority, String reason,
                       Instant validFrom, Instant validUntil, Instant createdAt, Instant revokedAt) {

    public PolicyOverride {
        if (id == null) {
            throw new IllegalArgumentException("Идентификатор переопределения не задан");
        }
        if (subjectType == null) {
            throw new IllegalArgumentException("Тип субъекта переопределения не задан");
        }
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("Субъект переопределения не задан");
        }
        if (reason == null || reason.trim().length() < 5) {
            throw new IllegalArgumentException("Обоснование переопределения должно быть не короче 5 символов");
        }
        if (validFrom == null || validUntil == null) {
            throw new IllegalArgumentException("Срок действия переопределения не задан");
        }
        if (!validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("Переопределение должно заканчиваться позже, чем начинается");
        }
        requireThreshold(low, "low");
        requireThreshold(high, "high");
        requireThreshold(critical, "critical");
        disabledFactors = (disabledFactors == null) ? Set.of() : Set.copyOf(disabledFactors);
        disabledRules = (disabledRules == null) ? Set.of() : Set.copyOf(disabledRules);
    }

    /** Истина, если переопределение действует в указанный момент и не отозвано. */
    public boolean isActiveAt(Instant moment) {
        if (moment == null || revokedAt != null) {
            return false;
        }
        return !moment.isBefore(validFrom) && moment.isBefore(validUntil);
    }

    /** Истина, если переопределение относится к этой политике. */
    public boolean appliesTo(String policy) {
        return policyName == null || policyName.equals(policy);
    }

    /** Истина, если переопределение выдано этому субъекту с учётом его ролей. */
    public boolean matches(Subject subject) {
        if (subject == null) {
            return false;
        }
        return switch (subjectType) {
            case USER -> subjectId.equals(subject.id());
            case ROLE -> subject.roles().contains(subjectId);
        };
    }

    private static void requireThreshold(Double value, String name) {
        if (value != null && (value <= 0 || value > Thresholds.MAX)) {
            throw new IllegalArgumentException("Порог " + name + " переопределения должен лежать в (0, "
                    + Thresholds.MAX + "], а равен " + value);
        }
    }
}
