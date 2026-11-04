package ru.donstu.zerotrust.core.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Операции над величинами математической модели: проверка отрезка [0, 1] и округление.
 *
 * <p>Служебный класс, экземпляры не создаются.
 */
public final class Scores {

    /** Допуск при проверке суммы весов политики: сумма должна равняться единице с точностью 0,001. */
    public static final double WEIGHT_SUM_TOLERANCE = 1e-3;

    /** Порог, ниже которого знаменатель формулы оценки доверия Σ w_k·c_k считается нулевым: данных нет. */
    public static final double EVIDENCE_EPSILON = 1e-9;

    /** Допустимая погрешность вычислений за границами отрезка [0, 1]: обрезается молча. */
    public static final double UNIT_TOLERANCE = 1e-9;

    private Scores() {
        throw new AssertionError("Служебный класс, экземпляры не создаются");
    }

    /**
     * Проверяет, что значение годится как величина модели: конечное и лежит в [0, 1]
     * с точностью до {@link #UNIT_TOLERANCE}.
     *
     * @return ложь для {@code NaN}, бесконечности и значений заметно вне отрезка
     */
    public static boolean isUnitValue(double value) {
        return value >= -UNIT_TOLERANCE && value <= 1.0 + UNIT_TOLERANCE;
    }

    /**
     * Ограничивает значение отрезком [0, 1].
     *
     * @param value произвольное вещественное значение; {@code NaN} приводится к 0
     */
    public static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return clamp(value, 0.0, 1.0);
    }

    /**
     * Ограничивает значение отрезком [{@code min}, {@code max}] — для формул, где диапазон
     * задан не единицей (например, s_travel с минимумом 0,1).
     */
    public static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Округляет до трёх знаков после запятой по правилу «половина вверх»: в таком виде оценки
     * попадают в журнал решений, в ответы API и в таблицы ВКР.
     *
     * <p>Через {@link BigDecimal}, а не {@code Math.round(x * 1000) / 1000.0}: второй способ
     * на границах вроде 0,0005 даёт результат, зависящий от двоичного представления.
     */
    public static double round3(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
