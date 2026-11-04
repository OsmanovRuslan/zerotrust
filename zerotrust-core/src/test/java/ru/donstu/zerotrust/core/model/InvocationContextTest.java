package ru.donstu.zerotrust.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Тесты контекста защищённого вызова: имя операции, заголовки, аргументы. */
class InvocationContextTest {

    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

    private InvocationContext.Builder minimal() {
        return InvocationContext.builder()
                .requestId("req-1")
                .subject(Subject.of("alice", Set.of("USER"), Set.of()))
                .operation("PaymentController.transfer")
                .timestamp(NOW);
    }

    @Test
    @DisplayName("минимальный контекст: обязательны запрос, субъект, операция, уровень и время")
    void minimalContext() {
        InvocationContext ctx = minimal().build();

        assertThat(ctx.requestId()).isEqualTo("req-1");
        assertThat(ctx.subject().id()).isEqualTo("alice");
        assertThat(ctx.operation()).isEqualTo("PaymentController.transfer");
        assertThat(ctx.level()).isEqualTo(Level.NORMAL);
        assertThat(ctx.timestamp()).isEqualTo(NOW);
        assertThat(ctx.arguments()).isEmpty();
        assertThat(ctx.headers()).isEmpty();
        assertThat(ctx.clientIpString()).isEmpty();
    }

    @ParameterizedTest(name = "имя операции «{0}» допустимо")
    @ValueSource(strings = {"PaymentController.transfer", "payments.transfer", "zt:admin", "a", "A-1_2.3:4"})
    @DisplayName("допустимые имена операций")
    void validOperationNames(String operation) {
        assertThat(InvocationContext.isValidOperation(operation)).isTrue();
        assertThat(minimal().operation(operation).build().operation()).isEqualTo(operation);
    }

    @ParameterizedTest(name = "имя операции «{0}» отвергается")
    @ValueSource(strings = {"payments transfer", "payments/transfer", "платежи.перевод", "payments#transfer"})
    @DisplayName("недопустимые имена операций отвергаются")
    void invalidOperationNames(String operation) {
        assertThat(InvocationContext.isValidOperation(operation)).isFalse();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> minimal().operation(operation).build())
                .withMessageContaining("Недопустимое имя операции");
    }

    @Test
    @DisplayName("имя операции длиннее 128 символов отвергается")
    void operationNameLengthIsLimited() {
        assertThat(InvocationContext.isValidOperation("a".repeat(128))).isTrue();
        assertThat(InvocationContext.isValidOperation("a".repeat(129))).isFalse();
    }

    @Test
    @DisplayName("пустой контекст без субъекта или времени — ошибка")
    void requiredFieldsAreChecked() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> minimal().subject(null).build())
                .withMessageContaining("Субъект");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> minimal().timestamp(null).build())
                .withMessageContaining("Clock");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> minimal().requestId(" ").build())
                .withMessageContaining("requestId");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> minimal().level(null).build())
                .withMessageContaining("Уровень");
    }

    @Test
    @DisplayName("заголовки читаются без учёта регистра — как в прототипе")
    void headerLookupIsCaseInsensitive() {
        InvocationContext ctx = minimal()
                .headers(Map.of("X-Device-Id", "d-1", "Accept-Language", "ru-RU"))
                .build();

        assertThat(ctx.header("x-device-id")).isEqualTo("d-1");
        assertThat(ctx.header("X-DEVICE-ID")).isEqualTo("d-1");
        assertThat(ctx.header("X-Request-Id")).isNull();
        assertThat(ctx.header(null)).isNull();
    }

    @Test
    @DisplayName("аргументы метода доступны по имени параметра — вход SpEL-правил")
    void argumentsAreAvailableByName() {
        InvocationContext ctx = minimal()
                .arguments(Map.of("amount", 150_000, "toAccountId", "16"))
                .build();

        assertThat(ctx.argument("amount")).contains(150_000);
        assertThat(ctx.argument("missing")).isEmpty();
    }

    @Test
    @DisplayName("аргументы и заголовки копируются и неизменяемы")
    void mapsAreImmutable() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("amount", 1);
        InvocationContext ctx = minimal().arguments(mutable).build();

        mutable.put("amount", 2);

        assertThat(ctx.argument("amount")).contains(1);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> ctx.headers().put("x", "y"));
    }

    @Test
    @DisplayName("полный контекст: адрес, страна, координаты, устройство, сессия")
    void fullContext() throws UnknownHostException {
        InvocationContext ctx = minimal()
                .level(Level.CRITICAL)
                .policyName("payments")
                .clientIp(InetAddress.getByName("203.0.113.5"))
                .country("RU")
                .geo(new GeoPoint(47.2357, 39.7015))
                .device("a".repeat(64), true)
                .sessionKey("b".repeat(64))
                .http("POST", "/api/payments/transfer")
                .build();

        assertThat(ctx.level()).isEqualTo(Level.CRITICAL);
        assertThat(ctx.policyName()).isEqualTo("payments");
        assertThat(ctx.clientIpString()).isEqualTo("203.0.113.5");
        assertThat(ctx.country()).isEqualTo("RU");
        assertThat(ctx.geo().latitude()).isEqualTo(47.2357);
        assertThat(ctx.deviceHasId()).isTrue();
        assertThat(ctx.httpMethod()).isEqualTo("POST");
        assertThat(ctx.path()).isEqualTo("/api/payments/transfer");
    }

    @Test
    @DisplayName("аргумент метода может быть null: вызов с незаполненным параметром тоже защищён")
    void nullArgumentIsAllowed() {
        java.util.Map<String, Object> arguments = new java.util.HashMap<>();
        arguments.put("from", null);
        arguments.put("amount", 150_000);

        InvocationContext ctx = minimal().arguments(arguments).build();

        assertThat(ctx.arguments()).containsEntry("amount", 150_000).containsKey("from");
        assertThat(ctx.arguments().get("from")).isNull();
    }

    @Test
    @DisplayName("координаты вне допустимых диапазонов отвергаются")
    void geoPointIsValidated() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GeoPoint(91.0, 0.0));
        assertThatIllegalArgumentException().isThrownBy(() -> new GeoPoint(0.0, 181.0));
    }
}
