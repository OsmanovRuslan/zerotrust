package ru.donstu.zerotrust.core.factor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.donstu.zerotrust.core.model.FactorId;
import ru.donstu.zerotrust.core.model.FactorValue;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Тесты фактора D — устройство. */
class DeviceModelTest {

    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

    private final DeviceModel model = new DeviceModel(DeviceSettings.DEFAULT);

    private static DeviceSnapshot device(DeviceStatus status, long sightings, Duration age) {
        return new DeviceSnapshot("a".repeat(64), status, true, sightings, NOW.minus(age), NOW);
    }

    @Test
    @DisplayName("доверенное устройство (подтверждено step-up) даёт 1,0")
    void trustedDevice() {
        assertThat(model.score(device(DeviceStatus.TRUSTED, 1, Duration.ofMinutes(5)), true, NOW))
                .isEqualTo(1.0);
    }

    @ParameterizedTest(name = "встреч {0}, возраст {1} ч -> D = {2}")
    @CsvSource({
            "3,  24, 0.8",
            "10, 72, 0.8",
            "2,  72, 0.6",
            "3,  23, 0.6",
            "1,  1,  0.6"
    })
    @DisplayName("знакомым устройство становится после 3 встреч и 24 часов")
    void knownThresholds(long sightings, long ageHours, double expected) {
        DeviceSnapshot snapshot = device(DeviceStatus.KNOWN, sightings, Duration.ofHours(ageHours));

        assertThat(model.score(snapshot, true, NOW)).isEqualTo(expected);
    }

    @Test
    @DisplayName("новое устройство (записи ещё нет) даёт 0,4")
    void newDevice() {
        assertThat(model.score(null, true, NOW)).isEqualTo(0.4);
    }

    @Test
    @DisplayName("ключ по User-Agent даёт 0,3: такой ключ не опознаёт устройство")
    void deviceWithoutHeader() {
        assertThat(model.score(null, false, NOW)).isEqualTo(0.3);
        assertThat(model.score(device(DeviceStatus.KNOWN, 10, Duration.ofDays(30)), false, NOW))
                .isEqualTo(0.3);
        assertThat(model.score(device(DeviceStatus.TRUSTED, 10, Duration.ofDays(30)), false, NOW))
                .as("даже подтверждённое доверие не поднимает оценку ключа по User-Agent")
                .isEqualTo(0.3);
    }

    @Test
    @DisplayName("устройство без X-Device-Id нельзя сделать доверенным")
    void deviceWithoutHeaderCannotBecomeTrusted() {
        DeviceSnapshot withHeader = device(DeviceStatus.KNOWN, 5, Duration.ofDays(2));
        DeviceSnapshot withoutHeader = new DeviceSnapshot("c".repeat(64), DeviceStatus.KNOWN, false,
                5, NOW.minus(Duration.ofDays(2)), NOW);

        assertThat(model.canBecomeTrusted(withHeader))
                .as("успешный step-up на таком устройстве переводит его в TRUSTED")
                .isTrue();
        assertThat(model.canBecomeTrusted(withoutHeader))
                .as("иначе в zt_device появится TRUSTED-запись, которая всё равно даёт 0,3")
                .isFalse();
        assertThat(model.canBecomeTrusted(null)).isFalse();
    }

    @Test
    @DisplayName("отозванное устройство нельзя сделать доверенным, минуя возврат в обычный статус")
    void revokedDeviceCannotBecomeTrusted() {
        assertThat(model.canBecomeTrusted(device(DeviceStatus.REVOKED, 10, Duration.ofDays(30))))
                .isFalse();
    }

    @Test
    @DisplayName("отозванное устройство в оценку не попадает: его отклоняют раньше")
    void revokedDevice() {
        DeviceSnapshot revoked = device(DeviceStatus.REVOKED, 10, Duration.ofDays(30));

        assertThat(revoked.isRevoked()).isTrue();
        assertThat(model.score(revoked, true, NOW)).isZero();
    }

    @Test
    @DisplayName("достоверность фактора всегда 1, в разложение попадает счётчик встреч")
    void confidenceAndParts() {
        FactorValue value = model.evaluate(device(DeviceStatus.KNOWN, 5, Duration.ofDays(2)), true, NOW);

        assertThat(value.id()).isEqualTo(FactorId.D);
        assertThat(value.value()).isEqualTo(0.8);
        assertThat(value.confidence()).isEqualTo(1.0);
        assertThat(value.parts()).containsEntry("sightings", 5.0);
    }

    @Test
    @DisplayName("первая встреча устройства: статус NEW и один просмотр")
    void firstSighting() {
        DeviceSnapshot snapshot = DeviceSnapshot.firstSighting("b".repeat(64), true, NOW);

        assertThat(snapshot.status()).isEqualTo(DeviceStatus.NEW);
        assertThat(snapshot.sightings()).isEqualTo(1);
        assertThat(model.isKnown(snapshot, NOW)).isFalse();
    }

    @Test
    @DisplayName("не заданные настройки, время и ключ устройства — ошибка")
    void argumentsAreChecked() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DeviceModel(null));
        assertThatIllegalArgumentException().isThrownBy(() -> model.evaluate(null, true, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DeviceSnapshot(" ", DeviceStatus.NEW, true, 1, NOW, NOW));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DeviceSnapshot("key", null, true, 1, NOW, NOW));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DeviceSnapshot("key", DeviceStatus.NEW, true, -1, NOW, NOW));
    }

    @Test
    @DisplayName("настройки проверяются: значения вне [0, 1] и нулевые пороги отвергаются")
    void settingsAreValidated() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DeviceSettings(1.5, 0.8, 0.6, 0.4, 0.3, 3, Duration.ofHours(24)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DeviceSettings(1.0, 0.8, 0.6, 0.4, 0.3, 0, Duration.ofHours(24)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DeviceSettings(1.0, 0.8, 0.6, 0.4, 0.3, 3, null));
    }
}
