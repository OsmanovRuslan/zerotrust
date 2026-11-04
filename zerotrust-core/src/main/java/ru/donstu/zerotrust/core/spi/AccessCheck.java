package ru.donstu.zerotrust.core.spi;

/** Ответ приложения на вопрос «доступен ли этот объект этому субъекту». */
public enum AccessCheck {

    /** Доступен: вызов продолжается. */
    ALLOWED,

    /** Не доступен: отказ с причиной DENY_OWNERSHIP и событием OWNERSHIP_VIOLATION. */
    DENIED,

    /**
     * Объекта нет. При {@code hide-existence: true} отвечает так же, как DENIED (404), чтобы
     * перебором идентификаторов нельзя было узнать, какие объекты существуют; при {@code false}
     * вызов продолжается и метод сам возвращает 404.
     */
    NOT_FOUND
}
