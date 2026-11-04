package ru.donstu.zerotrust.core.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Веса факторов w_k из действующей политики.
 *
 * <p>Инвариант: сумма весов равна 1 с точностью до
 * {@link Scores#WEIGHT_SUM_TOLERANCE}, веса неотрицательны.
 * Нормировка нужна, чтобы оценка доверия сохраняла смысл «доля доверия» и чтобы веса,
 * полученные методом анализа иерархий или калибровкой,
 * подставлялись в модель без пересчёта порогов.
 *
 * <p>Веса по умолчанию — из главы 1 ВКР: {@code B 0,30 / N 0,25 / D 0,20 / R 0,25}.
 *
 * @param values вес каждого фактора; карта неизменяема и упорядочена по коду фактора,
 *               чтобы веса в журнале и в ответах API всегда шли в одном порядке
 */
public record Weights(Map<FactorId, Double> values) {

    /** Веса из главы 1 ВКР: экспертная оценка, принятая до проведения МАИ и калибровки. */
    public static final Weights DEFAULT = of(0.30, 0.25, 0.20, 0.25);

    public Weights {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Набор весов факторов не может быть пустым");
        }
        Map<FactorId, Double> sorted = new TreeMap<>();
        double sum = 0.0;
        for (Map.Entry<FactorId, Double> e : values.entrySet()) {
            FactorId id = e.getKey();
            Double w = e.getValue();
            if (id == null) {
                throw new IllegalArgumentException("Идентификатор фактора в весах не может быть null");
            }
            if (w == null || Double.isNaN(w) || Double.isInfinite(w)) {
                throw new IllegalArgumentException("Вес фактора " + id + " должен быть конечным числом, получено: " + w);
            }
            if (w < 0.0) {
                throw new IllegalArgumentException("Вес фактора " + id + " не может быть отрицательным: " + w);
            }
            sorted.put(id, w);
            sum += w;
        }
        if (Math.abs(sum - 1.0) > Scores.WEIGHT_SUM_TOLERANCE) {
            throw new IllegalArgumentException(
                    "Сумма весов факторов должна равняться 1 с точностью до "
                            + Scores.WEIGHT_SUM_TOLERANCE + ", получено " + sum + " для весов " + sorted);
        }
        values = Map.copyOf(sorted);
    }

    /** Веса четырёх факторов модели в порядке B, N, D, R. */
    public static Weights of(double b, double n, double d, double r) {
        Map<FactorId, Double> map = new LinkedHashMap<>();
        map.put(FactorId.B, b);
        map.put(FactorId.N, n);
        map.put(FactorId.D, d);
        map.put(FactorId.R, r);
        return new Weights(map);
    }

    /**
     * Вес фактора.
     *
     * @param id идентификатор фактора
     * @return вес; 0, если фактора нет в политике — такой фактор в оценке не участвует,
     *         даже если его провайдер прислал значение
     */
    public double weightOf(FactorId id) {
        return values.getOrDefault(id, 0.0);
    }

    /** Факторы, которые участвуют в оценке (вес больше нуля). */
    public Set<FactorId> activeFactors() {
        Map<FactorId, Double> active = new TreeMap<>();
        values.forEach((id, w) -> {
            if (w > 0.0) {
                active.put(id, w);
            }
        });
        return Set.copyOf(active.keySet());
    }

    /**
     * Обнуляет веса отключённых факторов и перенормирует остальные к сумме 1
     * ({@code disabled_factors} применяется <b>после</b> выбора весов).
     *
     * <p>Перенормировка на результат оценки не влияет: в формуле оценки доверия общий множитель
     * весов
     * сокращается в числителе и знаменателе. Она нужна для сохранения инварианта «сумма = 1»,
     * чтобы веса, попавшие в журнал решения, читались как доли.
     *
     * @param disabled отключённые факторы; {@code null} или пустое множество возвращают те же веса
     * @return новые веса без отключённых факторов
     * @throws IllegalArgumentException если отключены все факторы с ненулевым весом — оценивать
     *         доверие стало бы нечем (такое переопределение запрещено)
     */
    public Weights normalizeWithout(Set<FactorId> disabled) {
        if (disabled == null || disabled.isEmpty()) {
            return this;
        }
        Map<FactorId, Double> kept = new TreeMap<>();
        double sum = 0.0;
        for (Map.Entry<FactorId, Double> e : values.entrySet()) {
            if (!disabled.contains(e.getKey())) {
                kept.put(e.getKey(), e.getValue());
                sum += e.getValue();
            }
        }
        if (sum <= Scores.EVIDENCE_EPSILON) {
            throw new IllegalArgumentException(
                    "Нельзя отключить все факторы: после исключения " + disabled
                            + " не осталось ни одного фактора с положительным весом");
        }
        Map<FactorId, Double> normalized = new TreeMap<>();
        for (Map.Entry<FactorId, Double> e : kept.entrySet()) {
            normalized.put(e.getKey(), e.getValue() / sum);
        }
        return new Weights(normalized);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        values.forEach((id, w) -> {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(id).append('=').append(Scores.round3(w));
        });
        return sb.append('}').toString();
    }
}
