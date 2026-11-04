package ru.donstu.zerotrust.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты причин решения: соответствие таблице ответов 7.2 и таблице событий 4.8.
 *
 * <p>Эти проверки нужны, чтобы код ответа и тип события нельзя было случайно рассогласовать
 * при добавлении новой причины: перечисление — единственный источник обеих таблиц.
 */
class DecisionReasonTest {

    @ParameterizedTest
    @EnumSource(DecisionReason.class)
    @DisplayName("статус ответа согласован с решением")
    void httpStatusMatchesDecision(DecisionReason reason) {
        switch (reason.decision()) {
            case ALLOW -> assertThat(reason.httpStatus()).isEqualTo(200);
            case STEP_UP -> assertThat(reason.httpStatus())
                    .as("step-up всегда отдаётся как 401 с заголовком WWW-Authenticate")
                    .isEqualTo(401);
            case DENY -> assertThat(reason.httpStatus())
                    .as("отказ: 401 для неаутентифицированного, 403, 429 для частоты, 503 для fail-closed")
                    .isIn(401, 403, 429, 503);
        }
    }

    @ParameterizedTest
    @EnumSource(DecisionReason.class)
    @DisplayName("тип problem+json задан для всех причин, кроме разрешающих")
    void problemTypeIsSetForFailures(DecisionReason reason) {
        if (reason.isAllow()) {
            assertThat(reason.problemType()).isNull();
        } else {
            assertThat(reason.problemType()).startsWith("urn:zerotrust:");
        }
    }

    @Test
    @DisplayName("типы problem+json для основных отказов")
    void problemTypesMatchSpecification() {
        assertThat(DecisionReason.DENY_UNAUTHENTICATED.problemType()).isEqualTo("urn:zerotrust:unauthenticated");
        assertThat(DecisionReason.STEP_UP_TRUST.problemType()).isEqualTo("urn:zerotrust:step-up-required");
        assertThat(DecisionReason.DENY_BLOCKED.problemType()).isEqualTo("urn:zerotrust:blocked");
        assertThat(DecisionReason.DENY_RBAC.problemType()).isEqualTo("urn:zerotrust:access-denied");
        assertThat(DecisionReason.RATE_LIMITED.problemType()).isEqualTo("urn:zerotrust:rate-limited");
        assertThat(DecisionReason.ERROR_FAIL_CLOSED.problemType()).isEqualTo("urn:zerotrust:unavailable");
    }

    @Test
    @DisplayName("особые статусы — 429 для частоты и 503 при отказе хранилища")
    void specialStatuses() {
        assertThat(DecisionReason.RATE_LIMITED.httpStatus()).isEqualTo(429);
        assertThat(DecisionReason.ERROR_FAIL_CLOSED.httpStatus()).isEqualTo(503);
        assertThat(DecisionReason.DENY_UNAUTHENTICATED.httpStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("причина отказа порождает событие безопасности")
    void failureReasonsProduceSecurityEvents() {
        assertThat(DecisionReason.DENY_OWNERSHIP.securityEvent()).isEqualTo(SecurityEventType.OWNERSHIP_VIOLATION);
        assertThat(DecisionReason.DENY_RULE.securityEvent()).isEqualTo(SecurityEventType.RULE_DENIED);
        assertThat(DecisionReason.RATE_LIMITED.securityEvent()).isEqualTo(SecurityEventType.RATE_LIMIT_EXCEEDED);
        assertThat(DecisionReason.DENY_LOW_TRUST.securityEvent()).isEqualTo(SecurityEventType.LOW_TRUST_DENIED);
        assertThat(DecisionReason.DENY_RBAC.securityEvent()).isEqualTo(SecurityEventType.RBAC_DENIED);
        assertThat(DecisionReason.DENY_DEVICE_REVOKED.securityEvent()).isEqualTo(SecurityEventType.REVOKED_DEVICE_USED);
        assertThat(DecisionReason.STEP_UP_DRIFT.securityEvent()).isEqualTo(SecurityEventType.SESSION_DRIFT);
    }

    @Test
    @DisplayName("разрешающие причины событий безопасности не порождают")
    void allowReasonsHaveNoSecurityEvent() {
        for (DecisionReason reason : DecisionReason.values()) {
            if (reason.isAllow()) {
                assertThat(reason.securityEvent()).as("причина %s", reason).isNull();
            }
        }
    }

    @Test
    @DisplayName("step-up по превышению порога сам события не порождает: его порождает ввод кода")
    void trustStepUpHasNoEventOfItsOwn() {
        assertThat(DecisionReason.STEP_UP_TRUST.securityEvent()).isNull();
        assertThat(DecisionReason.STEP_UP_RULE.securityEvent()).isNull();
    }

    @Test
    @DisplayName("тяжести событий по умолчанию")
    void defaultSeveritiesAreFixed() {
        assertThat(SecurityEventType.STEP_UP_FAILED.defaultSeverity()).isEqualTo(0.10);
        assertThat(SecurityEventType.STEP_UP_SUCCEEDED.defaultSeverity())
                .as("успешный step-up частично восстанавливает репутацию")
                .isEqualTo(-0.10);
        assertThat(SecurityEventType.OWNERSHIP_VIOLATION.defaultSeverity()).isEqualTo(0.25);
        assertThat(SecurityEventType.RULE_DENIED.defaultSeverity()).isEqualTo(0.15);
        assertThat(SecurityEventType.RATE_LIMIT_EXCEEDED.defaultSeverity()).isEqualTo(0.15);
        assertThat(SecurityEventType.LOW_TRUST_DENIED.defaultSeverity()).isEqualTo(0.20);
        assertThat(SecurityEventType.IMPOSSIBLE_TRAVEL.defaultSeverity()).isEqualTo(0.30);
        assertThat(SecurityEventType.SESSION_DRIFT.defaultSeverity()).isEqualTo(0.10);
        assertThat(SecurityEventType.RBAC_DENIED.defaultSeverity()).isEqualTo(0.05);
        assertThat(SecurityEventType.REVOKED_DEVICE_USED.defaultSeverity()).isEqualTo(0.30);
        assertThat(SecurityEventType.BLOCKED_ACCESS_ATTEMPT.defaultSeverity())
                .as("попытка доступа заблокированного пользователя только пишется в журнал")
                .isZero();
    }
}
