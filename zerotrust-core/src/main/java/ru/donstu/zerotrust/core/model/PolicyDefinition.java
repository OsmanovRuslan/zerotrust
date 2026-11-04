package ru.donstu.zerotrust.core.model;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Политика, как она хранится в таблице zt_policy: веса, пороги, режим применения
 * и правила без кода.
 *
 * @param name        имя политики, на которое ссылаются аннотации
 * @param description пояснение для администратора
 * @param weights     веса факторов
 * @param thresholds  пороги
 * @param enforcement режим применения
 * @param rules       правила политики
 * @param version     версия для оптимистической блокировки при изменении через API
 */
public record PolicyDefinition(String name, String description, Weights weights, Thresholds thresholds,
                               Enforcement enforcement, List<PolicyRule> rules, long version) {

    /** Имя политики по умолчанию. */
    public static final String DEFAULT_NAME = "default";

    /** Допустимое имя политики: то же ограничение, что и в CHECK таблицы zt_policy. */
    public static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");

    public PolicyDefinition {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Недопустимое имя политики: " + name);
        }
        if (weights == null) {
            throw new IllegalArgumentException("Веса политики " + name + " не заданы");
        }
        if (thresholds == null) {
            throw new IllegalArgumentException("Пороги политики " + name + " не заданы");
        }
        if (enforcement == null) {
            enforcement = Enforcement.ENFORCE;
        }
        if (version < 0) {
            throw new IllegalArgumentException("Версия политики не может быть отрицательной");
        }
        rules = (rules == null) ? List.of() : List.copyOf(rules);
    }

    /** Политика с умолчаниями главы 1: веса 0,30 / 0,25 / 0,20 / 0,25 и пороги 0,40 / 0,70 / 0,80. */
    public static PolicyDefinition defaults() {
        return new PolicyDefinition(DEFAULT_NAME, null, Weights.DEFAULT, Thresholds.DEFAULT,
                Enforcement.ENFORCE, List.of(), 1);
    }

    /** Политика с заданными весами и порогами. */
    public static PolicyDefinition of(String name, Weights weights, Thresholds thresholds) {
        return new PolicyDefinition(name, null, weights, thresholds, Enforcement.ENFORCE, List.of(), 1);
    }
}
