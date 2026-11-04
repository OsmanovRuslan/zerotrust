package ru.donstu.zerotrust.core.model;

/** На кого выдано переопределение политики (таблица zt_override). */
public enum SubjectType {

    /** На конкретного пользователя: самое сильное переопределение. */
    USER,

    /** На роль: действует на всех её носителей. */
    ROLE
}
