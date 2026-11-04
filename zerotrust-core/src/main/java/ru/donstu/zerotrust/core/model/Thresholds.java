package ru.donstu.zerotrust.core.model;

/**
 * Пороги принятия решения из действующей политики.
 *
 * <p>Инвариант: {@code 0 < low < high ≤ critical ≤ 0,95}.
 *
 * <ul>
 *   <li>{@code low} (θ_L) — ниже него step-up уже не предлагается, сразу отказ
 *       (шаг 10 алгоритма решения): если доверия почти нет, предлагать ввести код опасно —
 *       злоумышленник с украденным токеном получил бы попытку подбора;</li>
 *   <li>{@code high} (θ_H) — базовый порог обычной операции;</li>
 *   <li>{@code critical} (θ_C) — базовый порог критической операции.</li>
 * </ul>
 *
 * <p>Ограничение сверху 0,95 — чтобы требование не стало недостижимым: даже образцовый
 * пользователь редко набирает единицу по всем факторам одновременно.
 *
 * <p>Значения по умолчанию — из главы 1 ВКР: 0,40 / 0,70 / 0,80.
 *
 * @param low      низкий порог θ_L
 * @param high     высокий порог θ_H
 * @param critical критический порог θ_C
 */
public record Thresholds(double low, double high, double critical) {

    /** Верхний предел любого порога и требуемого порога вызова. */
    public static final double MAX = 0.95;

    /** Пороги из главы 1 ВКР. */
    public static final Thresholds DEFAULT = new Thresholds(0.40, 0.70, 0.80);

    public Thresholds {
        requireFinite(low, "low");
        requireFinite(high, "high");
        requireFinite(critical, "critical");
        if (!(0 < low && low < high && high <= critical && critical <= MAX)) {
            throw new IllegalArgumentException(
                    "Нарушен порядок порогов: требуется 0 < low < high <= critical <= " + MAX
                            + ", получено low=" + low + ", high=" + high + ", critical=" + critical);
        }
    }

    /**
     * Базовый порог θ_level для уровня операции. Правила могут поднять его до
     * θ_req, но не опустить ниже.
     *
     * @param level уровень операции
     * @return θ_H для {@link Level#NORMAL}, θ_C для {@link Level#CRITICAL}
     * @throws IllegalArgumentException для {@link Level#AUTH_ONLY}: на этом уровне оценка
     *         доверия не вычисляется, поэтому порога у него нет (шаг 6 алгоритма решения).
     *         Исключение здесь — защита от тихой ошибки в конвейере, а не ожидаемая ситуация
     */
    public double baseThresholdFor(Level level) {
        return switch (level) {
            case NORMAL -> high;
            case CRITICAL -> critical;
            case AUTH_ONLY -> throw new IllegalArgumentException(
                    "Для уровня AUTH_ONLY оценка доверия не вычисляется, требуемый порог не определён");
        };
    }

    private static void requireFinite(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Порог " + name + " должен быть конечным числом, получено: " + value);
        }
    }

    @Override
    public String toString() {
        return "low=" + Scores.round3(low) + ", high=" + Scores.round3(high)
                + ", critical=" + Scores.round3(critical);
    }
}
