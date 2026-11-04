package ru.donstu.zerotrust.core.engine;

import ru.donstu.zerotrust.core.model.FactorId;
import ru.donstu.zerotrust.core.model.FactorValue;
import ru.donstu.zerotrust.core.model.Scores;
import ru.donstu.zerotrust.core.model.Thresholds;
import ru.donstu.zerotrust.core.model.TrustScore;
import ru.donstu.zerotrust.core.model.Weights;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Расчёт интегральной оценки доверия по формуле оценки доверия:
 * TS = Σ w_k·c_k·x_k / Σ w_k·c_k.
 *
 * <p>Знаменатель — это и есть обобщение формулы главы 1: фактор, по которому нет данных
 * (c = 0), выпадает и из числителя, и из знаменателя, поэтому не притягивает оценку к нулю.
 * При c = 1 у всех факторов знаменатель равен единице и формула совпадает с формулой
 * главы 1 — это проверяется регрессионным тестом сценариев статьи.
 *
 * <p>Класс без состояния, один экземпляр обслуживает все запросы.
 */
public class TrustScoreCalculator {

    /**
     * Считает оценку доверия.
     *
     * @param weights    веса действующей политики; факторы с весом 0 (отключённые) игнорируются
     * @param factors    значения факторов; фактор без веса в политике в расчёт не попадает
     * @param thresholds пороги политики: из них берётся θ_L для случая «данных нет»
     * @return оценка; при Σ w_k·c_k &lt; {@link Scores#EVIDENCE_EPSILON} — θ_L с признаком
     *         {@code noEvidence}, что по алгоритму решения даёт step-up
     * @throws IllegalArgumentException если один и тот же фактор передан дважды: у каждого
     *                                  фактора должен быть ровно один провайдер
     */
    public TrustScore compute(Weights weights, Collection<FactorValue> factors, Thresholds thresholds) {
        if (weights == null) {
            throw new IllegalArgumentException("Веса политики не заданы");
        }
        if (thresholds == null) {
            throw new IllegalArgumentException("Пороги политики не заданы");
        }
        if (factors == null) {
            throw new IllegalArgumentException("Набор факторов не может быть null");
        }

        Set<FactorId> seen = new HashSet<>();
        Map<FactorId, Double> contributions = new TreeMap<>();
        double numerator = 0.0;
        double evidenceMass = 0.0;

        for (FactorValue factor : factors) {
            if (factor == null) {
                throw new IllegalArgumentException("Значение фактора не может быть null");
            }
            if (!seen.add(factor.id())) {
                throw new IllegalArgumentException("Фактор " + factor.id() + " передан в расчёт дважды: "
                        + "у каждого фактора должен быть ровно один провайдер");
            }
            double contribution = weights.weightOf(factor.id()) * factor.confidence();
            if (contribution <= 0.0) {
                continue;
            }
            numerator += contribution * factor.value();
            evidenceMass += contribution;
            contributions.put(factor.id(), contribution);
        }

        if (evidenceMass < Scores.EVIDENCE_EPSILON) {
            return new TrustScore(thresholds.low(), evidenceMass, true);
        }

        Map<FactorId, Double> effectiveWeights = new TreeMap<>();
        for (Map.Entry<FactorId, Double> entry : contributions.entrySet()) {
            effectiveWeights.put(entry.getKey(), entry.getValue() / evidenceMass);
        }
        return new TrustScore(numerator / evidenceMass, evidenceMass, false, effectiveWeights);
    }
}
