package ru.donstu.zerotrust.core.totp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Тесты одноразовых кодов по тестовым векторам RFC 4226 и RFC 6238. */
class TotpGeneratorTest {

    /** Секрет из тестовых векторов RFC: ASCII «12345678901234567890». */
    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private final TotpGenerator generator = new TotpGenerator();

    @ParameterizedTest(name = "RFC 4226: счётчик {0} -> код {1}")
    @CsvSource({
            "0, 755224",
            "1, 287082",
            "2, 359152",
            "3, 969429",
            "4, 338314",
            "5, 254676",
            "6, 287922",
            "7, 162583",
            "8, 399871",
            "9, 520489"
    })
    @DisplayName("тестовые векторы HOTP из приложения D к RFC 4226")
    void hotpVectors(long counter, String expected) {
        assertThat(generator.generateForStep(SECRET, counter)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "RFC 6238: время {0} с -> код {1}")
    @CsvSource({
            "59,          94287082",
            "1111111109,  07081804",
            "1111111111,  14050471",
            "1234567890,  89005924",
            "2000000000,  69279037",
            "20000000000, 65353130"
    })
    @DisplayName("тестовые векторы TOTP (SHA-1) из приложения B к RFC 6238")
    void totpVectors(long epochSecond, String expected) {
        TotpGenerator eightDigits = new TotpGenerator(new TotpSettings(8, Duration.ofSeconds(30), 1));

        assertThat(eightDigits.generate(SECRET, Instant.ofEpochSecond(epochSecond)))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("шаг времени считается от начала эпохи с шагом 30 секунд")
    void timeStep() {
        assertThat(generator.timeStep(Instant.ofEpochSecond(0))).isZero();
        assertThat(generator.timeStep(Instant.ofEpochSecond(29))).isZero();
        assertThat(generator.timeStep(Instant.ofEpochSecond(30))).isEqualTo(1);
        assertThat(generator.timeStep(Instant.ofEpochSecond(59))).isEqualTo(1);
        assertThat(generator.timeStep(Instant.ofEpochSecond(1111111109))).isEqualTo(37037036);
    }

    @Test
    @DisplayName("код действует в своём шаге и в соседних: часы клиента могут разойтись")
    void windowAllowsNeighbourSteps() {
        Instant now = Instant.ofEpochSecond(1_793_462_662L);
        String code = generator.generate(SECRET, now);
        long step = generator.timeStep(now);

        assertThat(generator.verify(SECRET, code, now)).hasValue(step);
        assertThat(generator.verify(SECRET, code, now.plusSeconds(30))).hasValue(step);
        assertThat(generator.verify(SECRET, code, now.minusSeconds(30))).hasValue(step);
        assertThat(generator.verify(SECRET, code, now.plusSeconds(90)))
                .as("через три шага код уже не подходит")
                .isEmpty();
    }

    @Test
    @DisplayName("без допуска код действует только в своём шаге")
    void zeroWindow() {
        TotpGenerator strict = new TotpGenerator(new TotpSettings(6, Duration.ofSeconds(30), 0));
        Instant now = Instant.ofEpochSecond(1_793_462_662L);
        String code = strict.generate(SECRET, now);

        assertThat(strict.verify(SECRET, code, now)).isPresent();
        assertThat(strict.verify(SECRET, code, now.plusSeconds(30))).isEmpty();
    }

    @Test
    @DisplayName("проверка возвращает шаг: по нему хранилище не даёт использовать код дважды")
    void verifyReturnsStep() {
        Instant now = Instant.ofEpochSecond(1_793_462_662L);

        OptionalLong step = generator.verify(SECRET, generator.generate(SECRET, now), now);

        assertThat(step).hasValue(generator.timeStep(now));
    }

    @Test
    @DisplayName("неверный, пустой и нечисловой код не проходят")
    void wrongCodesAreRejected() {
        Instant now = Instant.ofEpochSecond(1_793_462_662L);

        assertThat(generator.verify(SECRET, "000000", now.plusSeconds(0))).isEmpty();
        assertThat(generator.verify(SECRET, "", now)).isEmpty();
        assertThat(generator.verify(SECRET, null, now)).isEmpty();
        assertThat(generator.verify(SECRET, "12345", now)).as("слишком короткий").isEmpty();
        assertThat(generator.verify(SECRET, "1234567", now)).as("слишком длинный").isEmpty();
        assertThat(generator.verify(SECRET, "12345a", now)).as("не цифры").isEmpty();
    }

    @Test
    @DisplayName("код всегда нужной длины, даже когда число короче")
    void codeIsPadded() {
        for (long step = 0; step < 200; step++) {
            assertThat(generator.generateForStep(SECRET, step)).hasSize(6).containsOnlyDigits();
        }
    }

    @Test
    @DisplayName("разные секреты дают разные коды")
    void differentSecretsDifferentCodes() {
        byte[] another = "09876543210987654321".getBytes(StandardCharsets.US_ASCII);

        assertThat(generator.generateForStep(SECRET, 1))
                .isNotEqualTo(generator.generateForStep(another, 1));
    }

    @Test
    @DisplayName("без секрета, времени и настроек код не считается")
    void argumentsAreChecked() {
        assertThatIllegalArgumentException().isThrownBy(() -> generator.generateForStep(null, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> generator.generateForStep(new byte[0], 1));
        assertThatIllegalArgumentException().isThrownBy(() -> generator.timeStep(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new TotpGenerator(null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TotpSettings(4, Duration.ofSeconds(30), 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TotpSettings(6, Duration.ZERO, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TotpSettings(6, Duration.ofSeconds(30), -1));
    }
}
