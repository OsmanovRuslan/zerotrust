package ru.donstu.zerotrust.core.spi;

import ru.donstu.zerotrust.core.model.PolicyDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Хранилище политик — PAP в терминах NIST SP 800-207.
 *
 * <p>Реализация на PostgreSQL ведёт политики, которые меняет администратор; реализация
 * на настройках приложения работает, пока базы нет, и изменения не принимает.
 */
public interface PolicyStore {

    /** Политика по имени или пустое значение, если такой нет. */
    Optional<PolicyDefinition> find(String name);

    /** Все известные политики — для админского API и проверок при старте. */
    List<PolicyDefinition> findAll();

    /**
     * Сохраняет политику с оптимистической блокировкой.
     *
     * @param expectedVersion версия, которую видел администратор; при расхождении изменение
     *                        отклоняется, чтобы правка не затёрла чужую
     * @param updatedBy       кто меняет: идентификатор администратора или {@code system}
     * @return сохранённая политика с новой версией
     * @throws UnsupportedOperationException если хранилище изменений не принимает
     */
    PolicyDefinition save(PolicyDefinition policy, long expectedVersion, String updatedBy);

    /**
     * Удаляет политику.
     *
     * @return истина, если политика была и удалена
     * @throws UnsupportedOperationException если хранилище изменений не принимает
     */
    boolean delete(String name);
}
