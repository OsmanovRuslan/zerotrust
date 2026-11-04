package ru.donstu.zerotrust.core.model;

/**
 * Причина решения: единственный код, который объясняет, что именно произошло.
 *
 * <p>Перечисление намеренно сделано «толстым» — каждая причина сама знает своё решение,
 * HTTP-статус, тип для {@code application/problem+json} (RFC 9457) и тип события безопасности.
 * Так таблица ответов 7.2 и таблица событий 4.8 существуют в одном месте, а не расползаются
 * по обработчику исключений, веб-слою и сервису событий, где рано или поздно разойдутся.
 *
 * <p>Два случая обрабатываются вне перечисления:
 * <ul>
 *   <li>{@link #DENY_OWNERSHIP} объявлен как 403 {@code access-denied}, но при включённом
 *       {@code zerotrust.ownership.hide-existence} (по умолчанию) веб-слой отдаёт 404
 *       {@code urn:zerotrust:not-found} — чтобы перебором идентификаторов нельзя было узнать,
 *       какие объекты существуют. Статус зависит от настройки, поэтому он
 *       не может быть константой причины;</li>
 *   <li>признак {@code NO_EVIDENCE} здесь не объявлен: это не причина решения,
 *       а пометка в поле {@code factors} журнала о том, что данных для оценки не было.
 *       Само решение в этом случае — обычный {@link #STEP_UP_TRUST}.</li>
 * </ul>
 */
public enum DecisionReason {

    // --- Разрешения -------------------------------------------------------------------------

    /** Уровень {@link Level#AUTH_ONLY}: личность, роли, владелец и правила в порядке. */
    ALLOW_AUTHENTICATED(Decision.ALLOW, 200, null, null),

    /** Оценка доверия не ниже требуемого порога. */
    ALLOW_TRUST(Decision.ALLOW, 200, null, null),

    /** Порог не достигнут (или step-up потребовало правило), но подтверждение ещё действует. */
    ALLOW_STEP_UP_SATISFIED(Decision.ALLOW, 200, null, null),

    /** Метод помечен {@code @PublicEndpoint}: проверки модуля отключены. */
    ALLOW_PUBLIC(Decision.ALLOW, 200, null, null),

    // --- Требуется подтверждение ------------------------------------------------------------

    /** Step-up потребовало правило условий (например, {@code @AmountLimit.stepUpAbove}). */
    STEP_UP_RULE(Decision.STEP_UP, 401, "urn:zerotrust:step-up-required", null),

    /** Оценка доверия ниже требуемого порога θ_req. */
    STEP_UP_TRUST(Decision.STEP_UP, 401, "urn:zerotrust:step-up-required", null),

    /** Доверие резко упало относительно базовой линии сессии. */
    STEP_UP_DRIFT(Decision.STEP_UP, 401, "urn:zerotrust:step-up-required", SecurityEventType.SESSION_DRIFT),

    // --- Отказы -----------------------------------------------------------------------------

    /** В контексте безопасности нет аутентифицированного субъекта. */
    DENY_UNAUTHENTICATED(Decision.DENY, 401, "urn:zerotrust:unauthenticated", null),

    /** Субъект заблокирован вручную или автоблокировкой. */
    DENY_BLOCKED(Decision.DENY, 403, "urn:zerotrust:blocked", SecurityEventType.BLOCKED_ACCESS_ATTEMPT),

    /** Вызов пришёл с устройства в состоянии {@code REVOKED}. */
    DENY_DEVICE_REVOKED(Decision.DENY, 403, "urn:zerotrust:access-denied", SecurityEventType.REVOKED_DEVICE_USED),

    /** Нет требуемой роли или разрешения. */
    DENY_RBAC(Decision.DENY, 403, "urn:zerotrust:access-denied", SecurityEventType.RBAC_DENIED),

    /**
     * Объект принадлежит другому субъекту. Базовый статус — 403; при {@code hide-existence}
     * веб-слой подменяет его на 404 {@code urn:zerotrust:not-found}.
     */
    DENY_OWNERSHIP(Decision.DENY, 403, "urn:zerotrust:access-denied", SecurityEventType.OWNERSHIP_VIOLATION),

    /** Сработало правило с действием {@link Action#DENY}. */
    DENY_RULE(Decision.DENY, 403, "urn:zerotrust:access-denied", SecurityEventType.RULE_DENIED),

    /** Превышено ограничение частоты: единственный отказ со статусом 429 и {@code Retry-After}. */
    RATE_LIMITED(Decision.DENY, 429, "urn:zerotrust:rate-limited", SecurityEventType.RATE_LIMIT_EXCEEDED),

    /** Оценка доверия ниже низкого порога θ_L: step-up не предлагается (шаг 10 алгоритма решения). */
    DENY_LOW_TRUST(Decision.DENY, 403, "urn:zerotrust:access-denied", SecurityEventType.LOW_TRUST_DENIED),

    /** Нужен step-up, но метод подтверждения не настроен ({@code step-up.method: NONE}). */
    DENY_STEP_UP_UNAVAILABLE(Decision.DENY, 403, "urn:zerotrust:access-denied", null),

    /** Нужен step-up, но у пользователя не подключён TOTP и выбран режим {@code when-not-enrolled: DENY}. */
    DENY_STEP_UP_NOT_ENROLLED(Decision.DENY, 403, "urn:zerotrust:access-denied", null),

    // --- Ошибки: отказ закрывает доступ (fail-closed) ------------------------

    /** Хранилище недоступно или ответило дольше {@code resilience.store-timeout}. */
    ERROR_FAIL_CLOSED(Decision.DENY, 503, "urn:zerotrust:unavailable", null),

    /** Политика, указанная в аннотации, отсутствует в хранилище. */
    ERROR_POLICY_NOT_FOUND(Decision.DENY, 403, "urn:zerotrust:access-denied", null),

    /** Ошибка вычисления SpEL-выражения правила: исход правила считается {@link Action#DENY}. */
    ERROR_RULE_EVALUATION(Decision.DENY, 403, "urn:zerotrust:access-denied", null);

    private final Decision decision;
    private final int httpStatus;
    private final String problemType;
    private final SecurityEventType securityEvent;

    DecisionReason(Decision decision, int httpStatus, String problemType, SecurityEventType securityEvent) {
        this.decision = decision;
        this.httpStatus = httpStatus;
        this.problemType = problemType;
        this.securityEvent = securityEvent;
    }

    /** Решение, которому соответствует причина. */
    public Decision decision() {
        return decision;
    }

    /** HTTP-статус ответа (для {@link Decision#ALLOW} — 200). */
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * Значение поля {@code type} в {@code application/problem+json}.
     *
     * @return URN вида {@code urn:zerotrust:...}; {@code null} для разрешающих причин
     */
    public String problemType() {
        return problemType;
    }

    /**
     * Событие безопасности, которое нужно записать и учесть в репутации.
     *
     * @return тип события или {@code null}, если причина сама по себе события не порождает
     */
    public SecurityEventType securityEvent() {
        return securityEvent;
    }

    /** Истина, если причина разрешает вызов. */
    public boolean isAllow() {
        return decision == Decision.ALLOW;
    }
}
