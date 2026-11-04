package ru.donstu.zerotrust.core.factor;

import ru.donstu.zerotrust.core.model.Scores;

import java.time.Duration;

/**
 * Настройки фактора B ({@code zerotrust.factors.behavior}).
 *
 * @param timeWeight           вес признака «час суток» внутри фактора
 * @param rateWeight           вес признака «частота запросов»
 * @param resourceWeight       вес признака «новизна операции»
 * @param hourAlpha            скорость обучения гистограммы часов
 * @param hourKappa            доля равномерного распределения, с которой час считается обычным
 * @param rateAlpha            скорость обучения среднего и дисперсии частоты
 * @param rateInitialMean      начальное среднее число запросов в активную минуту
 * @param rateInitialVariance  начальная дисперсия
 * @param rateZFull            отклонение, до которого частота считается нормальной
 * @param rateZZero            отклонение, при котором признак обнуляется
 * @param minSamples           сколько успешных вызовов нужно для полной достоверности c_B
 * @param maxKnownOperations   сколько операций помнить в профиле
 * @param operationMemory      как долго операция считается знакомой
 * @param newOperationScore    оценка незнакомой операции
 */
public record BehaviorSettings(double timeWeight, double rateWeight, double resourceWeight,
                               double hourAlpha, double hourKappa,
                               double rateAlpha, double rateInitialMean, double rateInitialVariance,
                               double rateZFull, double rateZZero,
                               long minSamples, int maxKnownOperations, Duration operationMemory,
                               double newOperationScore) {

    /** Умолчания. */
    public static final BehaviorSettings DEFAULT = new BehaviorSettings(
            0.40, 0.35, 0.25,
            0.05, 0.5,
            0.10, 2.0, 4.0,
            2.0, 6.0,
            50, 500, Duration.ofDays(90), 0.5);

    public BehaviorSettings {
        double weightSum = timeWeight + rateWeight + resourceWeight;
        if (Math.abs(weightSum - 1.0) > Scores.WEIGHT_SUM_TOLERANCE) {
            throw new IllegalArgumentException(
                    "Внутренние веса фактора B должны давать в сумме 1, а дают " + weightSum);
        }
        if (timeWeight < 0 || rateWeight < 0 || resourceWeight < 0) {
            throw new IllegalArgumentException("Внутренние веса фактора B не могут быть отрицательными");
        }
        if (hourAlpha <= 0 || hourAlpha > 1) {
            throw new IllegalArgumentException("hourAlpha должно лежать в (0, 1], а не " + hourAlpha);
        }
        if (hourKappa <= 0) {
            throw new IllegalArgumentException("hourKappa должно быть положительным");
        }
        if (rateAlpha <= 0 || rateAlpha > 1) {
            throw new IllegalArgumentException("rateAlpha должно лежать в (0, 1], а не " + rateAlpha);
        }
        if (rateInitialMean < 0 || rateInitialVariance < 0) {
            throw new IllegalArgumentException("Начальные среднее и дисперсия не могут быть отрицательными");
        }
        if (rateZZero <= rateZFull) {
            throw new IllegalArgumentException(
                    "Нужно rateZFull < rateZZero, а задано " + rateZFull + " и " + rateZZero);
        }
        if (minSamples < 1) {
            throw new IllegalArgumentException("minSamples должно быть не меньше 1");
        }
        if (maxKnownOperations < 1) {
            throw new IllegalArgumentException("maxKnownOperations должно быть не меньше 1");
        }
        if (operationMemory == null || operationMemory.isNegative()) {
            throw new IllegalArgumentException("operationMemory должно быть задано и неотрицательно");
        }
        if (!Scores.isUnitValue(newOperationScore)) {
            throw new IllegalArgumentException(
                    "newOperationScore должно лежать в [0, 1], а не " + newOperationScore);
        }
    }
}
