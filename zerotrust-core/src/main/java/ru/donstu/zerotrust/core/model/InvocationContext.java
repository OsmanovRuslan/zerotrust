package ru.donstu.zerotrust.core.model;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Контекст защищённого вызова: всё, что PEP собрал о запросе, прежде чем принимать решение.
 *
 * <p>Прямой наследник {@code AccessContext} из прототипа, но теперь это неизменяемая запись:
 * один и тот же контекст читают провайдеры факторов, правила и асинхронный аудит, возможно
 * в разных нитях, и ни один из них не должен иметь возможности его изменить.
 *
 * <p>Контекст — это вход PDP, поэтому состав полей определяется тем, что нужно формулам
 * оценки доверия и правилам условий: время (час суток для фактора B и окно {@code @TimeWindow}),
 * адрес и координаты (фактор N), ключ устройства (фактор D), ключ сессии (дрейф и срок действия
 * step-up), аргументы метода (динамическая критичность по сумме операции).
 *
 * <p>Заголовка {@code Authorization} в {@link #headers()} нет и быть не должно: токен не попадает
 * ни в журнал, ни в правила.
 *
 * @param requestId   идентификатор запроса: заголовок {@code X-Request-Id}, если он пришёл в
 *                    допустимом формате, иначе UUID. Связывает журналы соседних сервисов
 *                    в одну цепочку
 * @param subject     субъект вызова
 * @param operation   имя операции: {@code ПростоеИмяКласса.имяМетода} или значение
 *                    атрибута {@code operation}. Используется в журнале, в профиле поведения
 *                    (новизна операции), в ключах {@code @RateLimit} и при адресации правил
 * @param policyName  имя действующей политики
 * @param level       уровень проверки
 * @param clientIp    адрес клиента, определённый с учётом доверенных прокси;
 *                    {@code null}, если адрес определить не удалось
 * @param country     код страны ISO 3166-1 alpha-2 или {@code null}, если геолокация выключена
 *                    либо адрес частный
 * @param geo         координаты адреса или {@code null}
 * @param deviceKey   ключ устройства: SHA-256 в hex
 * @param deviceHasId истина, если ключ построен по заголовку {@code X-Device-Id}, а не по
 *                    {@code User-Agent}: устройство «без идентификатора» получает фактор D = 0,3
 * @param sessionKey  SHA-256 от идентификатора сессии в hex
 * @param timestamp   время вызова, полученное из {@link java.time.Clock}
 * @param arguments   аргументы метода по именам параметров — вход SpEL-выражений правил
 * @param httpMethod  метод HTTP или {@code null} для вызова вне веб-запроса
 * @param path        путь запроса или {@code null}
 * @param headers     заголовки, нужные правилам; ключи приведены к нижнему регистру
 */
public record InvocationContext(String requestId,
                                Subject subject,
                                String operation,
                                String policyName,
                                Level level,
                                InetAddress clientIp,
                                String country,
                                GeoPoint geo,
                                String deviceKey,
                                boolean deviceHasId,
                                String sessionKey,
                                Instant timestamp,
                                Map<String, Object> arguments,
                                String httpMethod,
                                String path,
                                Map<String, String> headers) {

    /** Допустимый формат имени операции. */
    private static final Pattern OPERATION = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    public InvocationContext {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId не может быть пустым");
        }
        if (subject == null) {
            throw new IllegalArgumentException("Субъект вызова не может быть null: "
                    + "случай «нет аутентифицированного субъекта» обрабатывается до сборки контекста "
                    + "(шаг 1 алгоритма решения)");
        }
        if (!isValidOperation(operation)) {
            throw new IllegalArgumentException(
                    "Недопустимое имя операции: '" + operation + "'. "
                            + "Ожидаются символы [A-Za-z0-9._:-], длина 1..128");
        }
        if (level == null) {
            throw new IllegalArgumentException("Уровень проверки не может быть null");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Время вызова не может быть null: оно берётся из Clock");
        }
        arguments = (arguments == null)
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        headers = normalizeHeaders(headers);
    }

    /**
     * Проверка имени операции по правилу 5.1. Выделена отдельно, потому что те же имена
     * проверяются при старте по всем защищённым методам.
     */
    public static boolean isValidOperation(String operation) {
        return operation != null && OPERATION.matcher(operation).matches();
    }

    /** Регистронезависимое чтение заголовка — так же, как в прототипе. */
    public String header(String name) {
        return (name == null) ? null : headers.get(name.toLowerCase());
    }

    /** Значение аргумента метода по имени параметра. */
    public Optional<Object> argument(String name) {
        return Optional.ofNullable(arguments.get(name));
    }

    /** Строковое представление адреса клиента для журнала; пустая строка, если адрес неизвестен. */
    public String clientIpString() {
        return (clientIp == null) ? "" : clientIp.getHostAddress();
    }

    private static Map<String, String> normalizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            if (name != null && value != null) {
                normalized.put(name.toLowerCase(), value);
            }
        });
        return Map.copyOf(normalized);
    }

    /** Новый построитель. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Построитель контекста.
     *
     * <p>У записи шестнадцать компонентов, и большинство из них необязательны: вызов может идти
     * вне HTTP-запроса, геолокация может быть выключена, устройство — не передано. Прямой вызов
     * конструктора с десятком {@code null} невозможно прочитать ни в коде сборки контекста,
     * ни в тестах, поэтому здесь построитель — он единственная уступка краткости в этом пакете.
     */
    public static final class Builder {

        private String requestId;
        private Subject subject;
        private String operation;
        private String policyName;
        private Level level = Level.NORMAL;
        private InetAddress clientIp;
        private String country;
        private GeoPoint geo;
        private String deviceKey;
        private boolean deviceHasId;
        private String sessionKey;
        private Instant timestamp;
        private Map<String, Object> arguments = Map.of();
        private String httpMethod;
        private String path;
        private Map<String, String> headers = Map.of();

        private Builder() {
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder subject(Subject subject) {
            this.subject = subject;
            return this;
        }

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder policyName(String policyName) {
            this.policyName = policyName;
            return this;
        }

        public Builder level(Level level) {
            this.level = level;
            return this;
        }

        public Builder clientIp(InetAddress clientIp) {
            this.clientIp = clientIp;
            return this;
        }

        public Builder country(String country) {
            this.country = country;
            return this;
        }

        public Builder geo(GeoPoint geo) {
            this.geo = geo;
            return this;
        }

        public Builder device(String deviceKey, boolean deviceHasId) {
            this.deviceKey = deviceKey;
            this.deviceHasId = deviceHasId;
            return this;
        }

        public Builder sessionKey(String sessionKey) {
            this.sessionKey = sessionKey;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments;
            return this;
        }

        public Builder http(String httpMethod, String path) {
            this.httpMethod = httpMethod;
            this.path = path;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public InvocationContext build() {
            return new InvocationContext(requestId, subject, operation, policyName, level, clientIp, country, geo,
                    deviceKey, deviceHasId, sessionKey, timestamp, arguments, httpMethod, path, headers);
        }
    }
}
