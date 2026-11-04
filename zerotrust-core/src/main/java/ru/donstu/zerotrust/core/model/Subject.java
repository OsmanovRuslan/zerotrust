package ru.donstu.zerotrust.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Субъект вызова: тот, кто обращается к ресурсу (уровень 0 «личность»).
 *
 * <p>Главный принцип модуля — он <b>не владеет пользователями</b>. Отсюда и
 * состав этой записи: из приложения или из токена нужен только стабильный строковый
 * идентификатор, а всё остальное (роли, разрешения, атрибуты) модуль получает через SPI и
 * хранит у себя без внешних ключей на таблицы приложения. Поэтому модуль подключается
 * к системе, где пользователи уже лежат в своей PostgreSQL, в Mongo или в LDAP, без миграции
 * данных.
 *
 * @param id            стабильный идентификатор субъекта: claim {@code sub} токена или
 *                      {@code Authentication.getName()}; никогда не пустой
 * @param roles         роли субъекта без префикса {@code ROLE_}
 * @param permissions   разрешения: из сопоставления «роль → разрешения», из claim {@code scope}
 *                      или из обоих источников
 * @param attributes    произвольные атрибуты для правил ABAC: claims токена и данные от
 *                      {@code SubjectAttributesProvider}, например {@code department}
 * @param acr           уровень аутентификации из claim {@code acr}, если провайдер его выдаёт;
 *                      по нему проверяется, выполнен ли step-up методом OIDC
 * @param authTime      момент аутентификации из claim {@code auth_time}: вместе с {@code acr}
 *                      отвечает на вопрос «подтверждение ещё свежее?»
 * @param rawSessionId  идентификатор сессии в исходном виде (claim {@code sid} или
 *                      {@code HttpSession.getId()}). В хранилище пишется не он, а его SHA-256,
 * поэтому за пределы сборки контекста это значение не уходит
 */
public record Subject(String id,
                      Set<String> roles,
                      Set<String> permissions,
                      Map<String, Object> attributes,
                      String acr,
                      Instant authTime,
                      String rawSessionId) {

    public Subject {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Идентификатор субъекта не может быть пустым");
        }
        roles = (roles == null) ? Set.of() : Set.copyOf(roles);
        permissions = (permissions == null) ? Set.of() : Set.copyOf(permissions);
        attributes = (attributes == null) ? Map.of() : Map.copyOf(attributes);
    }

    /** Субъект без атрибутов и без данных о step-up — основной случай в тестах ядра. */
    public static Subject of(String id, Set<String> roles, Set<String> permissions) {
        return new Subject(id, roles, permissions, Map.of(), null, null, null);
    }

    /**
     * Проверка RBAC по ролям: нужна <b>хотя бы одна</b> из перечисленных ролей. Пустой список
     * требуемых ролей означает «роли не проверяются».
     */
    public boolean hasAnyRole(Set<String> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        for (String role : required) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    /** Проверка RBAC по разрешениям в режиме {@code ANY}: нужно хотя бы одно. */
    public boolean hasAnyPermission(Set<String> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        for (String permission : required) {
            if (permissions.contains(permission)) {
                return true;
            }
        }
        return false;
    }

    /** Проверка RBAC по разрешениям в режиме {@code ALL} (по умолчанию): нужны все. */
    public boolean hasAllPermissions(Set<String> required) {
        return required == null || required.isEmpty() || permissions.containsAll(required);
    }
}
