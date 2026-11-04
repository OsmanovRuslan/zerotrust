package ru.donstu.zerotrust.core.totp;

/**
 * Кодирование base32 (RFC 4648) — в этом виде секрет TOTP показывается пользователю и
 * передаётся в приложение-аутентификатор.
 *
 * <p>Кодирование без выравнивания символом «=»: приложения его не требуют. При разборе
 * выравнивание, пробелы и строчные буквы допускаются.
 *
 * <p>Служебный класс, экземпляры не создаются.
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {
        throw new AssertionError("Служебный класс, экземпляры не создаются");
    }

    /** Кодирует байты в base32 без выравнивания. */
    public static String encode(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Нечего кодировать");
        }
        StringBuilder encoded = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xFF);
            bits += 8;
            while (bits >= 5) {
                encoded.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            encoded.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return encoded.toString();
    }

    /**
     * Разбирает строку base32.
     *
     * @throws IllegalArgumentException если строка содержит символы вне алфавита
     */
    public static byte[] decode(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("Нечего разбирать");
        }
        String text = encoded.replace("=", "").replace(" ", "").replace("-", "").toUpperCase();
        byte[] decoded = new byte[text.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int position = 0;
        for (int i = 0; i < text.length(); i++) {
            int index = ALPHABET.indexOf(text.charAt(i));
            if (index < 0) {
                throw new IllegalArgumentException("Недопустимый символ base32: " + text.charAt(i));
            }
            buffer = (buffer << 5) | index;
            bits += 5;
            if (bits >= 8) {
                decoded[position++] = (byte) ((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return decoded;
    }
}
