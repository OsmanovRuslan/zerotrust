package ru.donstu.zerotrust.core.ahp;

import java.util.List;

/**
 * Результат метода анализа иерархий.
 *
 * @param weights          веса в порядке строк матрицы, сумма равна 1
 * @param lambdaMax        наибольшее собственное значение матрицы
 * @param consistencyIndex индекс согласованности CI = (λ_max − n) / (n − 1)
 * @param consistencyRatio отношение согласованности CR = CI / RI
 * @param consistent       ложь, если CR ≥ 0,10: такие веса принимать нельзя
 */
public record AhpResult(List<Double> weights, double lambdaMax, double consistencyIndex,
                        double consistencyRatio, boolean consistent) {

    public AhpResult {
        weights = (weights == null) ? List.of() : List.copyOf(weights);
    }
}
