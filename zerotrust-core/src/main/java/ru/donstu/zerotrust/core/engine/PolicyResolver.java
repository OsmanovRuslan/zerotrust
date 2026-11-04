package ru.donstu.zerotrust.core.engine;

import ru.donstu.zerotrust.core.model.EffectivePolicy;
import ru.donstu.zerotrust.core.model.Enforcement;
import ru.donstu.zerotrust.core.model.FactorId;
import ru.donstu.zerotrust.core.model.PolicyOverride;
import ru.donstu.zerotrust.core.model.PolicyDefinition;
import ru.donstu.zerotrust.core.model.Subject;
import ru.donstu.zerotrust.core.model.SubjectType;
import ru.donstu.zerotrust.core.model.Thresholds;
import ru.donstu.zerotrust.core.model.Weights;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;

/**
 * Слияние политики и переопределений в действующую политику вызова.
 *
 * <p>Порядок: политика, затем переопределения ролей субъекта, затем переопределение
 * пользователя — самое сильное. Поле переопределения, равное {@code null}, значение не меняет.
 * Если в группе несколько переопределений с одинаковым приоритетом, берётся более строгое:
 * для порогов — максимум по каждому, для весов — переопределение, созданное раньше.
 * Отключённые факторы и правила объединяются: вернуть отключённое правило переопределением
 * нельзя.
 *
 * <p>Класс без состояния.
 */
public class PolicyResolver {

    /**
     * Считает действующую политику.
     *
     * @param policy    политика из хранилища
     * @param overrides переопределения-кандидаты: лишние отфильтруются по сроку, политике и субъекту
     * @param subject   субъект вызова: по его ролям выбираются переопределения ролей
     * @param now       текущее время
     * @throws IllegalArgumentException если после слияния пороги нарушают порядок
     *                                  0 &lt; low &lt; high ≤ critical ≤ 0,95
     */
    public EffectivePolicy resolve(PolicyDefinition policy, Collection<PolicyOverride> overrides,
                                   Subject subject, Instant now) {
        if (policy == null) {
            throw new IllegalArgumentException("Политика не задана");
        }
        if (now == null) {
            throw new IllegalArgumentException("Время вызова не задано");
        }

        Weights weights = policy.weights();
        double low = policy.thresholds().low();
        double high = policy.thresholds().high();
        double critical = policy.thresholds().critical();
        Enforcement enforcement = policy.enforcement();
        Set<FactorId> disabledFactors = new TreeSet<>();
        Set<String> disabledRules = new TreeSet<>();
        List<UUID> applied = new ArrayList<>();

        for (SubjectType type : List.of(SubjectType.ROLE, SubjectType.USER)) {
            List<PolicyOverride> group = strongest(overrides, type, policy.name(), subject, now);
            for (PolicyOverride active : group) {
                applied.add(active.id());
                disabledFactors.addAll(active.disabledFactors());
                disabledRules.addAll(active.disabledRules());
            }
            Weights groupWeights = firstWeights(group);
            if (groupWeights != null) {
                weights = groupWeights;
            }
            Double groupLow = strictestThreshold(group, PolicyOverride::low);
            if (groupLow != null) {
                low = groupLow;
            }
            Double groupHigh = strictestThreshold(group, PolicyOverride::high);
            if (groupHigh != null) {
                high = groupHigh;
            }
            Double groupCritical = strictestThreshold(group, PolicyOverride::critical);
            if (groupCritical != null) {
                critical = groupCritical;
            }
            Enforcement groupEnforcement = strictestEnforcement(group);
            if (groupEnforcement != null) {
                enforcement = groupEnforcement;
            }
        }

        Thresholds thresholds = thresholds(low, high, critical, policy.name(), applied);
        Weights effectiveWeights = disabledFactors.isEmpty()
                ? weights
                : weights.normalizeWithout(disabledFactors);

        return new EffectivePolicy(policy.name(), effectiveWeights, thresholds, enforcement,
                disabledFactors, disabledRules, policy.rules(), applied);
    }

    /**
     * Переопределения одной группы с наибольшим приоритетом, в порядке создания.
     * Остальные отбрасываются: при нескольких ролях выигрывает самый высокий приоритет.
     */
    private List<PolicyOverride> strongest(Collection<PolicyOverride> overrides, SubjectType type,
                                     String policyName, Subject subject, Instant now) {
        if (overrides == null || overrides.isEmpty()) {
            return List.of();
        }
        List<PolicyOverride> candidates = new ArrayList<>();
        int maxPriority = Integer.MIN_VALUE;
        for (PolicyOverride candidate : overrides) {
            if (candidate == null || candidate.subjectType() != type
                    || !candidate.isActiveAt(now)
                    || !candidate.appliesTo(policyName)
                    || !candidate.matches(subject)) {
                continue;
            }
            if (candidate.priority() > maxPriority) {
                maxPriority = candidate.priority();
                candidates.clear();
            }
            if (candidate.priority() == maxPriority) {
                candidates.add(candidate);
            }
        }
        candidates.sort(Comparator
                .comparing(PolicyOverride::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PolicyOverride::id));
        return candidates;
    }

    /** Веса из переопределения, созданного раньше всех в группе. */
    private Weights firstWeights(List<PolicyOverride> group) {
        for (PolicyOverride active : group) {
            if (active.weights() != null) {
                return active.weights();
            }
        }
        return null;
    }

    /** Самый строгий (наибольший) порог в группе. */
    private Double strictestThreshold(List<PolicyOverride> group, Function<PolicyOverride, Double> field) {
        Double strictest = null;
        for (PolicyOverride active : group) {
            Double value = field.apply(active);
            if (value != null && (strictest == null || value > strictest)) {
                strictest = value;
            }
        }
        return strictest;
    }

    /** Самый строгий режим применения: ENFORCE строже, чем MONITOR. */
    private Enforcement strictestEnforcement(List<PolicyOverride> group) {
        Enforcement strictest = null;
        for (PolicyOverride active : group) {
            if (active.enforcement() == Enforcement.ENFORCE) {
                return Enforcement.ENFORCE;
            }
            if (active.enforcement() != null) {
                strictest = active.enforcement();
            }
        }
        return strictest;
    }

    private Thresholds thresholds(double low, double high, double critical, String policyName,
                                  List<UUID> applied) {
        try {
            return new Thresholds(low, high, critical);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Переопределения " + applied + " ломают порядок порогов"
                    + " политики " + policyName + ": " + e.getMessage(), e);
        }
    }
}
