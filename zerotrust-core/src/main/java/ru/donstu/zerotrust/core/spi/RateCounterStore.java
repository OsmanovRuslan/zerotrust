package ru.donstu.zerotrust.core.spi;

import java.time.Duration;
import java.time.Instant;

/**
 * Счётчики частоты вызовов в фиксированном окне.
 *
 * <p>Инкремент должен быть атомарным: иначе при одновременных запросах ограничение можно
 * обойти.
 */
public interface RateCounterStore {

    /**
     * Увеличивает счётчик окна и возвращает новое значение.
     *
     * @param key         ключ счётчика, собранный из области видимости правила
     * @param windowStart начало окна
     * @param ttl         через сколько запись можно удалить
     */
    long incrementAndGet(String key, Instant windowStart, Duration ttl);

    /**
     * Удаляет истёкшие окна: сами они не исчезают, а строка на каждое
     * окно и каждого субъекта быстро накапливается.
     *
     * @param now текущий момент
     * @return сколько записей удалено
     */
    int deleteExpired(Instant now);
}
