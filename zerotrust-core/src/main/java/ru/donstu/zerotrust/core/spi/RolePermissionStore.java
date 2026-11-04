package ru.donstu.zerotrust.core.spi;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Сопоставление ролей и разрешений: роли приходят из токена или из Spring Security,
 * а разрешения ведёт администратор модуля.
 *
 * <p>Так приложению не нужно менять формат ролей: новое разрешение добавляется в хранилище
 * модуля, а не в провайдера идентичности.
 */
public interface RolePermissionStore {

    /** Разрешения, которые дают перечисленные роли. */
    Set<String> permissionsOf(Collection<String> roles);

    /**
     * Выдаёт роли разрешение.
     *
     * @throws UnsupportedOperationException если хранилище изменений не принимает
     */
    void grant(String role, String permission, String createdBy);

    /**
     * Отбирает у роли разрешение.
     *
     * @return истина, если разрешение было выдано и теперь отобрано
     * @throws UnsupportedOperationException если хранилище изменений не принимает
     */
    boolean revoke(String role, String permission);

    /** Все сопоставления: роль -> её разрешения. */
    Map<String, Set<String>> list();
}
