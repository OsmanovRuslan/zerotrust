package ru.donstu.zerotrust.core.ahp;

import ru.donstu.zerotrust.core.model.FactorId;
import ru.donstu.zerotrust.core.model.Weights;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обоснование весов факторов методом анализа иерархий.
 *
 * <p>На вход — матрица попарных сравнений по шкале Саати: a_ij показывает, во сколько раз
 * фактор i важнее фактора j, a_ji = 1/a_ij, a_ii = 1. На выход — веса (нормированный главный
 * собственный вектор), наибольшее собственное значение и отношение согласованности CR.
 * При CR ≥ 0,10 суждения эксперта противоречивы и веса принимать нельзя.
 *
 * <p>Собственный вектор считается степенным методом: w ← A·w с нормировкой на каждом шаге.
 *
 * <p>Класс без состояния.
 */
public class AhpCalculator {

    /** Предельное отношение согласованности: при нём и выше матрица считается несогласованной. */
    public static final double MAX_CONSISTENCY_RATIO = 0.10;

    /** Шкала Саати: сравнения лежат между 1/9 и 9. */
    public static final double MAX_SAATY = 9.0;

    /** Допуск при проверке обратной симметричности a_ij·a_ji = 1. */
    public static final double RECIPROCAL_TOLERANCE = 1e-6;

    private static final double CONVERGENCE = 1e-10;
    private static final int MAX_ITERATIONS = 1000;

    /**
     * Случайные индексы согласованности RI по Саати для размерностей 1..7.
     * Для n ≤ 2 матрица согласована всегда, поэтому RI не используется.
     */
    private static final double[] RANDOM_INDEX = {0.0, 0.0, 0.58, 0.90, 1.12, 1.24, 1.32};

    /**
     * Считает веса и согласованность матрицы.
     *
     * @param matrix матрица попарных сравнений n×n, n от 2 до 7
     * @throws IllegalArgumentException если матрица не квадратная, не обратно симметричная,
     *                                  содержит значения вне шкалы Саати или имеет
     *                                  неподдерживаемую размерность
     */
    public AhpResult calculate(double[][] matrix) {
        int n = validate(matrix);

        double[] weights = new double[n];
        for (int i = 0; i < n; i++) {
            weights[i] = 1.0 / n;
        }
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            double[] next = multiply(matrix, weights);
            normalize(next);
            if (distance(weights, next) < CONVERGENCE) {
                weights = next;
                break;
            }
            weights = next;
        }

        double lambdaMax = lambdaMax(matrix, weights);
        double consistencyIndex = (n > 2) ? (lambdaMax - n) / (n - 1) : 0.0;
        double consistencyRatio = (n > 2) ? consistencyIndex / RANDOM_INDEX[n - 1] : 0.0;

        List<Double> result = new ArrayList<>(n);
        for (double weight : weights) {
            result.add(weight);
        }
        return new AhpResult(result, lambdaMax, consistencyIndex, consistencyRatio,
                consistencyRatio < MAX_CONSISTENCY_RATIO);
    }

    /**
     * Веса политики из матрицы сравнений: порядок факторов должен совпадать с порядком строк.
     *
     * @throws IllegalArgumentException если матрица несогласована (CR ≥ 0,10) или число
     *                                  факторов не совпадает с размером матрицы
     */
    public Weights weights(double[][] matrix, List<FactorId> factors) {
        if (factors == null || factors.isEmpty()) {
            throw new IllegalArgumentException("Список факторов не задан");
        }
        AhpResult result = calculate(matrix);
        if (factors.size() != result.weights().size()) {
            throw new IllegalArgumentException("Факторов " + factors.size()
                    + ", а строк в матрице " + result.weights().size());
        }
        if (!result.consistent()) {
            throw new IllegalArgumentException("Матрица несогласована: CR = "
                    + String.format("%.3f", result.consistencyRatio())
                    + " при пределе " + MAX_CONSISTENCY_RATIO + ", веса не принимаются");
        }
        Map<FactorId, Double> values = new LinkedHashMap<>();
        for (int i = 0; i < factors.size(); i++) {
            values.put(factors.get(i), result.weights().get(i));
        }
        return new Weights(values);
    }

    private int validate(double[][] matrix) {
        if (matrix == null || matrix.length < 2) {
            throw new IllegalArgumentException("Матрица сравнений должна содержать не меньше двух строк");
        }
        int n = matrix.length;
        if (n > RANDOM_INDEX.length) {
            throw new IllegalArgumentException("Поддерживаются матрицы до " + RANDOM_INDEX.length
                    + " факторов, а задано " + n);
        }
        for (int i = 0; i < n; i++) {
            if (matrix[i] == null || matrix[i].length != n) {
                throw new IllegalArgumentException("Матрица сравнений должна быть квадратной");
            }
            for (int j = 0; j < n; j++) {
                double value = matrix[i][j];
                if (Double.isNaN(value) || value <= 0) {
                    throw new IllegalArgumentException("Сравнение a[" + i + "][" + j
                            + "] должно быть положительным, а равно " + value);
                }
                if (value > MAX_SAATY || value < 1.0 / MAX_SAATY) {
                    throw new IllegalArgumentException("Сравнение a[" + i + "][" + j
                            + "] вне шкалы Саати [1/9, 9]: " + value);
                }
            }
            if (Math.abs(matrix[i][i] - 1.0) > RECIPROCAL_TOLERANCE) {
                throw new IllegalArgumentException("На диагонали должна быть единица, а a[" + i + "]["
                        + i + "] = " + matrix[i][i]);
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(matrix[i][j] * matrix[j][i] - 1.0) > RECIPROCAL_TOLERANCE) {
                    throw new IllegalArgumentException("Матрица должна быть обратно симметричной: a["
                            + i + "][" + j + "] = " + matrix[i][j] + ", a[" + j + "][" + i + "] = "
                            + matrix[j][i]);
                }
            }
        }
        return n;
    }

    private static double[] multiply(double[][] matrix, double[] vector) {
        double[] result = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            double sum = 0.0;
            for (int j = 0; j < vector.length; j++) {
                sum += matrix[i][j] * vector[j];
            }
            result[i] = sum;
        }
        return result;
    }

    private static void normalize(double[] vector) {
        double sum = 0.0;
        for (double value : vector) {
            sum += value;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= sum;
        }
    }

    private static double distance(double[] first, double[] second) {
        double max = 0.0;
        for (int i = 0; i < first.length; i++) {
            max = Math.max(max, Math.abs(first[i] - second[i]));
        }
        return max;
    }

    /** λ_max как среднее отношение (A·w)_i / w_i. */
    private static double lambdaMax(double[][] matrix, double[] weights) {
        double[] product = multiply(matrix, weights);
        double sum = 0.0;
        for (int i = 0; i < weights.length; i++) {
            sum += product[i] / weights[i];
        }
        return sum / weights.length;
    }
}
