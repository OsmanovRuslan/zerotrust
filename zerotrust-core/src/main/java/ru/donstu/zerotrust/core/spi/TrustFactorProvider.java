package ru.donstu.zerotrust.core.spi;

import ru.donstu.zerotrust.core.model.FactorId;
import ru.donstu.zerotrust.core.model.FactorValue;
import ru.donstu.zerotrust.core.model.InvocationContext;

/**
 * Источник значения одного фактора доверия — PIP в терминах NIST SP 800-207.
 *
 * <p>Встроенные провайдеры считают факторы B, N, D и R. Приложение может добавить свой
 * (например, риск-скор антифрода) — достаточно объявить бин с собственным
 * {@link FactorId} и задать его вес в политике.
 *
 * <p>Требования к реализации: не дольше 50 мс, без побочных эффектов, не бросать исключения
 * наружу без необходимости. Отказ провайдера не отклоняет вызов: фактор получает
 * достоверность 0 и пометку degraded.
 */
public interface TrustFactorProvider {

    /** Идентификатор фактора: у каждого фактора ровно один провайдер. */
    FactorId id();

    /** Значение фактора для этого вызова. */
    FactorValue evaluate(InvocationContext context);

    /**
     * Вызывается после разрешённого вызова: здесь провайдер обновляет профиль, чтобы
     * злоумышленник не мог обучить профиль отклонёнными запросами.
     */
    default void onAllowed(InvocationContext context) {
        // по умолчанию фактор ничего не запоминает
    }
}
