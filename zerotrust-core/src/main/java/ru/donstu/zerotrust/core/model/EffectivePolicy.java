package ru.donstu.zerotrust.core.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Действующая политика вызова: результат слияния политики и переопределений.
 *
 * <p>Веса здесь уже перенормированы без отключённых факторов, поэтому их можно читать как доли.
 *
 * @param name             имя исходной политики
 * @param weights          веса факторов
 * @param thresholds       пороги
 * @param enforcement      режим применения
 * @param disabledFactors  отключённые факторы: их вес равен нулю
 * @param disabledRules    адреса отключённых правил
 * @param rules            правила политики
 * @param appliedOverrides идентификаторы применённых переопределений в порядке применения:
 *                         пишутся в журнал решения
 */
public record EffectivePolicy(String name, Weights weights, Thresholds thresholds, Enforcement enforcement,
                              Set<FactorId> disabledFactors, Set<String> disabledRules,
                              List<PolicyRule> rules, List<UUID> appliedOverrides) {

    public EffectivePolicy {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Имя политики не задано");
        }
        if (weights == null) {
            throw new IllegalArgumentException("Веса действующей политики не заданы");
        }
        if (thresholds == null) {
            throw new IllegalArgumentException("Пороги действующей политики не заданы");
        }
        if (enforcement == null) {
            enforcement = Enforcement.ENFORCE;
        }
        disabledFactors = (disabledFactors == null) ? Set.of() : Set.copyOf(disabledFactors);
        disabledRules = (disabledRules == null) ? Set.of() : Set.copyOf(disabledRules);
        rules = (rules == null) ? List.of() : List.copyOf(rules);
        appliedOverrides = (appliedOverrides == null) ? List.of() : List.copyOf(appliedOverrides);
    }

    /** Истина, если решение применяется, а не только записывается в журнал. */
    public boolean isEnforced() {
        return enforcement == Enforcement.ENFORCE;
    }

    /**
     * Истина, если правило отключено для этой операции. Поддерживаются формы адреса:
     * {@code time-window}, {@code time-window@payments.transfer}, {@code *@payments.transfer}
     * и {@code *} (то же самое, что {@code *@*}) — все правила везде.
     *
     * <p>Полное отключение нужно, когда правила надо выключить целиком, например чтобы сравнить
     * поведение модуля с правилами и без них: перечислять для этого все операции неудобно.
     */
    public boolean isRuleDisabled(String ruleId, String operation) {
        if (ruleId == null) {
            return false;
        }
        if (disabledRules.contains("*") || disabledRules.contains("*@*")) {
            return true;
        }
        if (disabledRules.contains(ruleId)) {
            return true;
        }
        if (operation == null) {
            return false;
        }
        return disabledRules.contains(ruleId + "@" + operation)
                || disabledRules.contains("*@" + operation);
    }

    /** Базовый порог уровня операции без учёта поднятий правилами. */
    public double baseThresholdFor(Level level) {
        return thresholds.baseThresholdFor(level);
    }
}
