package ru.donstu.zerotrust.core.spi;

/**
 * Проверка принадлежности объекта субъекту — её реализует приложение.
 *
 * <p>Модуль не знает предметной области, поэтому вопрос «её ли это счёт» задаёт приложению.
 * Такой вид проверки закрывает BOLA: решение выдаётся на доступ к конкретному объекту,
 * а не к эндпоинту.
 *
 * @param <ID> тип идентификатора объекта
 */
public interface AccessResolver<ID> {

    /**
     * Доступен ли объект субъекту.
     *
     * @param subjectId идентификатор субъекта
     * @param resourceId идентификатор объекта, вычисленный из аргументов вызова
     */
    AccessCheck check(String subjectId, ID resourceId);

    /** Заглушка для значения по умолчанию в аннотации: проверка не задана. */
    final class None implements AccessResolver<Object> {

        @Override
        public AccessCheck check(String subjectId, Object resourceId) {
            throw new UnsupportedOperationException(
                    "Проверка владельца не настроена: задайте resolver или ownerExpression");
        }
    }
}
