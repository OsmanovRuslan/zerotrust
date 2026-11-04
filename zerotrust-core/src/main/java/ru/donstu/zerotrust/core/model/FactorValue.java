package ru.donstu.zerotrust.core.model;

import java.util.Map;

/**
 * Значение фактора вместе с достоверностью — вход формулы оценки доверия.
 *
 * <p>Достоверность c_k отвечает на вопрос «хватает ли данных, чтобы этому числу верить».
 * У нового пользователя профиля поведения ещё нет, и фактор B с c = 0 не тянет оценку
 * ни вверх, ни вниз: решение принимается по известным факторам.
 *
 * <p>Погрешность вычислений за границами отрезка [0, 1] (до {@link Scores#UNIT_TOLERANCE})
 * обрезается молча. {@code NaN}, бесконечность и значения заметно вне отрезка — это отказ
 * провайдера: такой фактор получает c = 0 и пометку {@code degraded}, в оценке не участвует,
 * а в лог пишется ERROR.
 *
 * @param id         идентификатор фактора
 * @param value      значение x_k ∈ [0, 1]
 * @param confidence достоверность c_k ∈ [0, 1]; 0 означает «фактор не участвует в оценке»
 * @param parts      разложение значения на составляющие: у фактора B это
 *                   {@code {"time": …, "rate": …, "res": …}}. Пишется в журнал, чтобы решение
 *                   можно было объяснить, а не только воспроизвести
 * @param degraded   истина, если фактор не удалось вычислить: провайдер упал, не ответил
 *                   за 50 мс или вернул недопустимое значение
 */
public record FactorValue(FactorId id, double value, double confidence, Map<String, Double> parts, boolean degraded) {

    private static final System.Logger LOG = System.getLogger(FactorValue.class.getName());

    public FactorValue {
        if (id == null) {
            throw new IllegalArgumentException("Идентификатор фактора не может быть null");
        }
        parts = (parts == null) ? Map.of() : Map.copyOf(parts);
        if (Scores.isUnitValue(value) && Scores.isUnitValue(confidence)) {
            value = Scores.clamp01(value);
            confidence = Scores.clamp01(confidence);
        } else {
            double badValue = value;
            double badConfidence = confidence;
            FactorId badId = id;
            LOG.log(System.Logger.Level.ERROR, () -> "Фактор " + badId + ": недопустимое значение провайдера"
                    + " (x=" + badValue + ", c=" + badConfidence + "), фактор исключён из оценки доверия");
            value = 0.0;
            confidence = 0.0;
            degraded = true;
        }
    }

    /** Фактор с полной достоверностью и без разложения: случай факторов N и D, а также симуляции. */
    public static FactorValue of(FactorId id, double value) {
        return new FactorValue(id, value, 1.0, Map.of(), false);
    }

    /** Фактор с полной достоверностью и разложением на составляющие. */
    public static FactorValue of(FactorId id, double value, Map<String, Double> parts) {
        return new FactorValue(id, value, 1.0, parts, false);
    }

    /** Фактор с частичной достоверностью: обучающийся профиль (B) или молодая история (R). */
    public static FactorValue of(FactorId id, double value, double confidence, Map<String, Double> parts) {
        return new FactorValue(id, value, confidence, parts, false);
    }

    /** Фактор, данных по которому нет: c = 0, в формуле оценки доверия не влияет ни на числитель, ни на знаменатель. */
    public static FactorValue unknown(FactorId id) {
        return new FactorValue(id, 0.0, 0.0, Map.of(), false);
    }

    /** Фактор, который не удалось вычислить из-за отказа провайдера: c = 0 и пометка degraded. */
    public static FactorValue degraded(FactorId id) {
        return new FactorValue(id, 0.0, 0.0, Map.of(), true);
    }

    /** Истина, если фактор несёт данные и участвует в оценке. */
    public boolean hasEvidence() {
        return confidence > 0.0;
    }
}
