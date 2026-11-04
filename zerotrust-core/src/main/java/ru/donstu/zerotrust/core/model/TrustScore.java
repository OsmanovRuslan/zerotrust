package ru.donstu.zerotrust.core.model;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Интегральная оценка доверия TS — результат формулы оценки доверия.
 *
 * <p>Вместе со значением запись несёт данные, по которым оценка получена: оценка 0,40 может
 * означать «субъект подозрителен» и «про субъекта ничего не известно», и в журнале это должны
 * быть разные записи.
 *
 * @param value            значение TS ∈ [0, 1] без округления; округление до трёх знаков
 *                         ({@link #rounded()}) делается при записи в журнал и в ответ
 * @param evidenceMass     знаменатель формулы оценки доверия, Σ w_k·c_k. Единица означает, что все
 * факторы
 *                         политики известны с полной достоверностью и формула совпадает
 *                         с формулой главы 1
 * @param noEvidence       истина, если Σ w_k·c_k меньше {@link Scores#EVIDENCE_EPSILON}: данных
 *                         нет совсем, значение приравнено к низкому порогу θ_L, а в журнал
 *                         решения пишется признак {@code NO_EVIDENCE}
 * @param effectiveWeights итоговые веса факторов с учётом достоверности,
 *                         w̃_k = w_k·c_k / Σ w_k·c_k (сумма равна 1; при отсутствии данных карта
 *                         пуста). Это доля влияния фактора на оценку: TS = Σ w̃_k·x_k. Пишется
 *                         в журнал решения, потому что номинальный вес политики без достоверности
 *                         не объясняет, откуда взялась оценка
 */
public record TrustScore(double value, double evidenceMass, boolean noEvidence,
                         Map<FactorId, Double> effectiveWeights) {

    public TrustScore {
        value = Scores.clamp01(value);
        effectiveWeights = (effectiveWeights == null)
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(effectiveWeights));
    }

    /** Оценка без разложения по факторам — для тестов и для режима симуляции. */
    public TrustScore(double value, double evidenceMass, boolean noEvidence) {
        this(value, evidenceMass, noEvidence, Map.of());
    }

    /** Значение, округлённое до трёх знаков: так оценка попадает в журнал, ответы и таблицы ВКР. */
    public double rounded() {
        return Scores.round3(value);
    }

    /**
     * Истина, если все факторы политики известны с полной достоверностью, то есть формула оценки
     * доверия
     * выродилась в формулу главы 1: TS = wB·B + wN·N + wD·D + wR·R.
     */
    public boolean isFullEvidence() {
        return Math.abs(evidenceMass - 1.0) <= Scores.EVIDENCE_EPSILON;
    }

    /** Итоговый вес фактора с учётом достоверности; 0, если фактор в оценке не участвовал. */
    public double effectiveWeightOf(FactorId id) {
        return effectiveWeights.getOrDefault(id, 0.0);
    }

    @Override
    public String toString() {
        return "TS=" + rounded() + (noEvidence ? " (нет данных)" : "");
    }
}
