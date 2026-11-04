package ru.donstu.zerotrust.core.totp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * Одноразовые коды по времени: HOTP (RFC 4226) на счётчике шагов времени, то есть TOTP
 * (RFC 6238). Используется встроенным методом step-up.
 *
 * <p>Класс без состояния, один экземпляр обслуживает все проверки.
 */
public class TotpGenerator {

    private static final String ALGORITHM = "HmacSHA1";

    private final TotpSettings settings;

    /** Генератор с умолчаниями: 6 цифр, шаг 30 секунд, допуск ±1 шаг. */
    public TotpGenerator() {
        this(TotpSettings.DEFAULT);
    }

    public TotpGenerator(TotpSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("Настройки TOTP не заданы");
        }
        this.settings = settings;
    }

    /** Номер шага времени, которому принадлежит момент: T = floor(секунды / шаг). */
    public long timeStep(Instant time) {
        if (time == null) {
            throw new IllegalArgumentException("Время не задано");
        }
        return Math.floorDiv(time.getEpochSecond(), settings.period().toSeconds());
    }

    /** Код, действующий в указанный момент. */
    public String generate(byte[] secret, Instant time) {
        return generateForStep(secret, timeStep(time));
    }

    /** Код для заданного шага времени (он же счётчик HOTP). */
    public String generateForStep(byte[] secret, long step) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("Секрет не задан");
        }
        byte[] hash = hmac(secret, ByteBuffer.allocate(8).putLong(step).array());

        // Усечение по RFC 4226: младший полубайт последнего байта задаёт смещение
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);

        int modulo = (int) Math.pow(10, settings.digits());
        return String.format("%0" + settings.digits() + "d", binary % modulo);
    }

    /**
     * Проверяет код с допуском по шагам времени.
     *
     * @return шаг, на котором код совпал, или пустое значение, если код не подошёл. Шаг нужен
     *         хранилищу: код того же шага нельзя использовать повторно
     */
    public OptionalLong verify(byte[] secret, String code, Instant time) {
        if (code == null || code.length() != settings.digits()) {
            return OptionalLong.empty();
        }
        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) < '0' || code.charAt(i) > '9') {
                return OptionalLong.empty();
            }
        }
        long current = timeStep(time);
        for (long step = current - settings.window(); step <= current + settings.window(); step++) {
            if (equalsConstantTime(generateForStep(secret, step), code)) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    private static byte[] hmac(byte[] secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Не удалось вычислить HMAC-SHA1 для кода TOTP", e);
        }
    }

    /** Сравнение за постоянное время: по времени ответа нельзя подбирать код посимвольно. */
    private static boolean equalsConstantTime(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }
}
