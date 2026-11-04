package ru.donstu.zerotrust.core.rule;

import ru.donstu.zerotrust.core.model.InvocationContext;
import ru.donstu.zerotrust.core.spi.RateCounterStore;

import java.time.Clock;
import java.time.ZonedDateTime;

/**
 * Что доступно правилу при вычислении: контекст вызова, аргументы метода, время
 * и счётчики частоты.
 *
 * <p>Время — только отсюда: ни одно правило не вызывает {@code Instant.now()} само, иначе его
 * нельзя было бы проверить тестом с управляемыми часами и прогнать с ускоренным
 * временем.
 */
public interface RuleContext {

    /** Контекст защищённого вызова. */
    InvocationContext invocation();

    /**
     * Значение аргумента метода по выражению: имя параметра, {@code #req.amount}, {@code #p1}.
     *
     * @return значение или {@code null}, если выражение ничего не дало
     */
    Object argument(String expression);

    /** Часы модуля. */
    Clock clock();

    /** Время вызова в часовом поясе модуля ({@code zerotrust.time-zone}). */
    ZonedDateTime localTime();

    /** Счётчики частоты вызовов — нужны правилу {@code @RateLimit}. */
    RateCounterStore rateCounters();
}
