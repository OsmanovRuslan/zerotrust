package ru.donstu.zerotrust.core.model;

import java.util.List;

/**
 * Правило политики, заданное администратором без изменения кода.
 *
 * <p>Выражение {@code when} вычисляется в урезанном контексте SpEL: только чтение свойств,
 * без вызова произвольных методов и без обращения к бинам. Разбор и вычисление выражения
 * делает слой autoconfigure, ядро хранит текст.
 *
 * @param id         адрес правила в журнале и в списке отключённых правил
 * @param when       условие на SpEL
 * @param action     что делать, если условие выполнено
 * @param raiseTo    до какого порога поднимать доверие, если действие RAISE
 * @param message    текст для журнала и ответа
 * @param operations маски операций, к которым правило применяется; пусто — ко всем.
 *                   Маска поддерживает {@code *} в конце: {@code payments.*}
 */
public record PolicyRule(String id, String when, Action action, Double raiseTo, String message,
                         List<String> operations) {

    public PolicyRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Идентификатор правила политики не задан");
        }
        if (when == null || when.isBlank()) {
            throw new IllegalArgumentException("Условие правила " + id + " не задано");
        }
        if (action == null) {
            throw new IllegalArgumentException("Действие правила " + id + " не задано");
        }
        if (action == Action.RAISE) {
            if (raiseTo == null) {
                throw new IllegalArgumentException("Правило " + id + " поднимает порог, но raiseTo не задан");
            }
            if (raiseTo <= 0 || raiseTo > Thresholds.MAX) {
                throw new IllegalArgumentException("raiseTo правила " + id + " должен лежать в (0, "
                        + Thresholds.MAX + "], а равен " + raiseTo);
            }
        }
        operations = (operations == null) ? List.of() : List.copyOf(operations);
    }

    /** Истина, если правило применяется к этой операции. */
    public boolean appliesTo(String operation) {
        if (operations.isEmpty()) {
            return true;
        }
        if (operation == null) {
            return false;
        }
        for (String mask : operations) {
            if (matches(mask, operation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String mask, String operation) {
        if (mask == null || mask.isBlank()) {
            return false;
        }
        if (mask.endsWith("*")) {
            return operation.startsWith(mask.substring(0, mask.length() - 1));
        }
        return mask.equals(operation);
    }
}
