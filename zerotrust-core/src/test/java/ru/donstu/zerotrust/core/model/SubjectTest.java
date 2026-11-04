package ru.donstu.zerotrust.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Тесты субъекта вызова и проверок RBAC. */
class SubjectTest {

    private final Subject alice = Subject.of("alice", Set.of("USER"), Set.of("data:read", "account:read"));

    @Test
    @DisplayName("идентификатор субъекта обязателен: без него решение принимать нельзя")
    void idIsRequired() {
        assertThatIllegalArgumentException().isThrownBy(() -> Subject.of(null, Set.of(), Set.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> Subject.of("  ", Set.of(), Set.of()));
    }

    @Test
    @DisplayName("отсутствующие роли, разрешения и атрибуты превращаются в пустые наборы")
    void nullCollectionsBecomeEmpty() {
        Subject subject = new Subject("bob", null, null, null, null, null, null);

        assertThat(subject.roles()).isEmpty();
        assertThat(subject.permissions()).isEmpty();
        assertThat(subject.attributes()).isEmpty();
    }

    @Test
    @DisplayName("нужна хотя бы одна из перечисленных ролей")
    void anyRoleIsEnough() {
        assertThat(alice.hasAnyRole(Set.of("USER"))).isTrue();
        assertThat(alice.hasAnyRole(Set.of("ADMIN", "USER"))).isTrue();
        assertThat(alice.hasAnyRole(Set.of("ADMIN"))).isFalse();
    }

    @Test
    @DisplayName("пустой список требуемых ролей означает «роли не проверяются»")
    void emptyRequirementPasses() {
        assertThat(alice.hasAnyRole(Set.of())).isTrue();
        assertThat(alice.hasAnyRole(null)).isTrue();
        assertThat(alice.hasAllPermissions(Set.of())).isTrue();
        assertThat(alice.hasAnyPermission(null)).isTrue();
    }

    @Test
    @DisplayName("режим ALL требует все разрешения, режим ANY — хотя бы одно")
    void permissionModes() {
        assertThat(alice.hasAllPermissions(Set.of("data:read", "account:read"))).isTrue();
        assertThat(alice.hasAllPermissions(Set.of("data:read", "payment:transfer"))).isFalse();
        assertThat(alice.hasAnyPermission(Set.of("data:read", "payment:transfer"))).isTrue();
        assertThat(alice.hasAnyPermission(Set.of("payment:transfer"))).isFalse();
    }

    @Test
    @DisplayName("атрибуты доступны правилам ABAC: раздел субъекта из токена или из приложения")
    void attributesAreAvailable() {
        Subject carol = new Subject("carol", Set.of("ACCOUNTANT"), Set.of(),
                Map.of("department", "finance"), "mfa", null, "sid-1");

        assertThat(carol.attributes()).containsEntry("department", "finance");
        assertThat(carol.acr()).isEqualTo("mfa");
        assertThat(carol.rawSessionId()).isEqualTo("sid-1");
    }

    @Test
    @DisplayName("наборы копируются и неизменяемы: один субъект читают несколько нитей")
    void collectionsAreImmutable() {
        Set<String> mutableRoles = new HashSet<>();
        mutableRoles.add("USER");
        Subject subject = Subject.of("bob", mutableRoles, Set.of());

        mutableRoles.add("ADMIN");

        assertThat(subject.roles()).containsExactly("USER");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> subject.roles().add("ADMIN"));
    }
}
