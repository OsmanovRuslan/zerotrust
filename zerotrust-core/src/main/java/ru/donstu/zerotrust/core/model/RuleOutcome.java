package ru.donstu.zerotrust.core.model;

/**
 * Исход одного правила условий.
 *
 * <p>Исходы всех правил вызова попадают в поле {@code rule_outcomes} журнала решений — именно
 * по ним потом видно, что доступ закрыло не «низкое доверие вообще», а конкретное правило
 * {@code amount-limit} на сумме 150 000.
 *
 * @param id       идентификатор правила: {@code amount-limit}, {@code time-window} и т. п.. Полный адрес правила для переопределений — {@code id@операция}
 * @param action   предписанное действие или {@code null}, если правило не сработало
 * @param raiseTo  порог ρ для действия {@link Action#RAISE}; для остальных действий {@code null}
 * @param message  пояснение для журнала и, при {@code expose-details}, для ответа клиенту
 */
public record RuleOutcome(String id, Action action, Double raiseTo, String message) {

    public RuleOutcome {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Идентификатор правила не может быть пустым");
        }
        if (action == Action.RAISE) {
            if (raiseTo == null) {
                throw new IllegalArgumentException("Правило " + id + " с действием RAISE должно задавать raiseTo");
            }
            if (!(raiseTo > 0.0 && raiseTo <= Thresholds.MAX)) {
                throw new IllegalArgumentException(
                        "Правило " + id + ": raiseTo должно лежать в (0, " + Thresholds.MAX + "], получено " + raiseTo);
            }
        }
    }

    /** Правило проверено и не сработало. */
    public static RuleOutcome pass(String id) {
        return new RuleOutcome(id, null, null, null);
    }

    /** Правило сработало с действием, не требующим порога: DENY, STEP_UP или LOG. */
    public static RuleOutcome fired(String id, Action action, String message) {
        return new RuleOutcome(id, action, null, message);
    }

    /** Правило подняло требуемый порог до {@code raiseTo} — динамическая критичность. */
    public static RuleOutcome raise(String id, double raiseTo, String message) {
        return new RuleOutcome(id, Action.RAISE, raiseTo, message);
    }

    /** Истина, если правило сработало (независимо от действия). */
    public boolean isFired() {
        return action != null;
    }

    /** Истина, если правило запрещает вызов. */
    public boolean isDeny() {
        return action == Action.DENY;
    }

    /** Истина, если правило требует подтверждения вторым фактором. */
    public boolean isStepUp() {
        return action == Action.STEP_UP;
    }

    /** Истина, если правило поднимает требуемый порог. */
    public boolean isRaise() {
        return action == Action.RAISE;
    }
}
