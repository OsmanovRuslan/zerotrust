package ru.donstu.zerotrust.core.spi;

import java.util.Optional;

/**
 * Упрощённый вариант {@link AccessResolver} для случая «у объекта один владелец»:
 * приложению достаточно вернуть его идентификатор.
 *
 * @param <ID> тип идентификатора объекта
 */
public interface OwnerResolver<ID> extends AccessResolver<ID> {

    /** Владелец объекта или пустое значение, если объекта нет. */
    Optional<String> ownerOf(ID resourceId);

    @Override
    default AccessCheck check(String subjectId, ID resourceId) {
        return ownerOf(resourceId)
                .map(owner -> owner.equals(subjectId) ? AccessCheck.ALLOWED : AccessCheck.DENIED)
                .orElse(AccessCheck.NOT_FOUND);
    }
}
