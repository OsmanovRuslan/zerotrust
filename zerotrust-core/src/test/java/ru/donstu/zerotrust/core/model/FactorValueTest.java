package ru.donstu.zerotrust.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Тесты значения фактора: проверка отрезка [0, 1], отказ провайдера и признаки наличия данных. */
class FactorValueTest {

    @Test
    @DisplayName("фабрика of даёт полную достоверность — случай факторов N и D")
    void ofGivesFullConfidence() {
        FactorValue n = FactorValue.of(FactorId.N, 0.9);

        assertThat(n.value()).isEqualTo(0.9);
        assertThat(n.confidence()).isEqualTo(1.0);
        assertThat(n.hasEvidence()).isTrue();
        assertThat(n.degraded()).isFalse();
        assertThat(n.parts()).isEmpty();
    }

    @Test
    @DisplayName("разложение на составляющие сохраняется: по нему решение объясняется в журнале")
    void partsArePreserved() {
        FactorValue b = FactorValue.of(FactorId.B, 0.68, Map.of("time", 0.2, "rate", 1.0, "res", 1.0));

        assertThat(b.parts()).containsEntry("time", 0.2).containsEntry("rate", 1.0).containsEntry("res", 1.0);
    }

    @Test
    @DisplayName("фактор без данных: достоверность нуль, в оценке не участвует")
    void unknownFactorHasNoEvidence() {
        FactorValue unknown = FactorValue.unknown(FactorId.B);

        assertThat(unknown.confidence()).isZero();
        assertThat(unknown.hasEvidence()).isFalse();
        assertThat(unknown.degraded()).isFalse();
    }

    @Test
    @DisplayName("фактор после отказа провайдера помечается degraded")
    void degradedFactorIsMarked() {
        FactorValue degraded = FactorValue.degraded(FactorId.N);

        assertThat(degraded.degraded()).isTrue();
        assertThat(degraded.hasEvidence()).isFalse();
    }

    @ParameterizedTest(name = "x={0}, c={1} — отказ провайдера")
    @CsvSource({
            "1.5, 1.0",
            "-0.5, 1.0",
            "0.5, 2.0",
            "0.5, -1.0",
            "NaN, 1.0",
            "0.5, NaN",
            "Infinity, 1.0",
            "0.5, -Infinity"
    })
    @DisplayName("недопустимое значение — отказ провайдера: c = 0 и пометка degraded")
    void invalidValueBecomesProviderFailure(double value, double confidence) {
        FactorValue factor = new FactorValue(FactorId.B, value, confidence, Map.of("time", 0.2), false);

        assertThat(factor.value()).isZero();
        assertThat(factor.confidence()).isZero();
        assertThat(factor.degraded()).isTrue();
        assertThat(factor.hasEvidence()).isFalse();
        assertThat(factor.parts()).as("разложение сохраняется: по нему видно, что считал провайдер")
                .containsEntry("time", 0.2);
    }

    @ParameterizedTest(name = "x={0} -> {1}")
    @CsvSource({
            "1.000000000001, 1.0",
            "-0.000000000001, 0.0"
    })
    @DisplayName("погрешность вычислений до 1e-9 за границами отрезка обрезается молча")
    void roundingErrorIsTrimmed(double value, double expected) {
        FactorValue factor = new FactorValue(FactorId.N, value, 1.0, Map.of(), false);

        assertThat(factor.value()).isEqualTo(expected);
        assertThat(factor.confidence()).isEqualTo(1.0);
        assertThat(factor.degraded()).isFalse();
    }

    @Test
    @DisplayName("границы отрезка — допустимые значения")
    void boundsAreValid() {
        assertThat(new FactorValue(FactorId.D, 0.0, 1.0, Map.of(), false).degraded()).isFalse();
        assertThat(new FactorValue(FactorId.D, 1.0, 0.0, Map.of(), false).degraded()).isFalse();
    }

    @Test
    @DisplayName("фактор без идентификатора — ошибка")
    void idIsRequired() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FactorValue(null, 0.5, 1.0, Map.of(), false));
    }

    @Test
    @DisplayName("разложение копируется и неизменяемо: значение фактора читают несколько нитей")
    void partsAreImmutable() {
        Map<String, Double> mutable = new HashMap<>();
        mutable.put("time", 0.2);
        FactorValue b = FactorValue.of(FactorId.B, 0.5, mutable);

        mutable.put("rate", 1.0);

        assertThat(b.parts()).containsOnlyKeys("time");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> b.parts().put("rate", 1.0));
    }

    @Test
    @DisplayName("отсутствующее разложение превращается в пустую карту, а не в null")
    void nullPartsBecomeEmptyMap() {
        assertThat(new FactorValue(FactorId.D, 1.0, 1.0, null, false).parts()).isEmpty();
    }
}
