package ru.donstu.zerotrust.core.totp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Тесты base32 по тестовым векторам RFC 4648. */
class Base32Test {

    @ParameterizedTest(name = "«{0}» -> «{1}»")
    @CsvSource({
            "f,      MY",
            "fo,     MZXQ",
            "foo,    MZXW6",
            "foob,   MZXW6YQ",
            "fooba,  MZXW6YTB",
            "foobar, MZXW6YTBOI"
    })
    @DisplayName("тестовые векторы RFC 4648, без выравнивания")
    void rfcVectors(String text, String expected) {
        byte[] data = text.getBytes(StandardCharsets.US_ASCII);

        assertThat(Base32.encode(data)).isEqualTo(expected);
        assertThat(Base32.decode(expected)).isEqualTo(data);
    }

    @Test
    @DisplayName("пустые данные кодируются в пустую строку")
    void emptyData() {
        assertThat(Base32.encode(new byte[0])).isEmpty();
        assertThat(Base32.decode("")).isEmpty();
    }

    @Test
    @DisplayName("при разборе допускаются выравнивание, пробелы, дефисы и строчные буквы")
    void decodeIsForgiving() {
        byte[] expected = "foobar".getBytes(StandardCharsets.US_ASCII);

        assertThat(Base32.decode("MZXW6YTBOI")).isEqualTo(expected);
        assertThat(Base32.decode("mzxw6ytboi")).isEqualTo(expected);
        assertThat(Base32.decode("MZXW 6YTB OI")).isEqualTo(expected);
        assertThat(Base32.decode("MZXW-6YTB-OI")).isEqualTo(expected);
        assertThat(Base32.decode("MY======")).isEqualTo("f".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("секрет из 20 случайных байт переживает кодирование и разбор")
    void secretRoundTrip() {
        byte[] secret = new byte[20];
        new SecureRandom().nextBytes(secret);

        String encoded = Base32.encode(secret);

        assertThat(encoded).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Base32.decode(encoded)).isEqualTo(secret);
    }

    @Test
    @DisplayName("недопустимые символы и отсутствие данных отвергаются")
    void invalidInput() {
        assertThatIllegalArgumentException().isThrownBy(() -> Base32.decode("MZXW6YTB01"))
                .withMessageContaining("Недопустимый символ");
        assertThatIllegalArgumentException().isThrownBy(() -> Base32.decode(null));
        assertThatIllegalArgumentException().isThrownBy(() -> Base32.encode(null));
    }
}
