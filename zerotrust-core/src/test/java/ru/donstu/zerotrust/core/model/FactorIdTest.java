package ru.donstu.zerotrust.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Тесты идентификатора фактора: формат кода и открытый набор факторов. */
class FactorIdTest {

    @Test
    @DisplayName("четыре фактора модели объявлены константами и считаются встроенными")
    void builtInFactors() {
        assertThat(FactorId.B.value()).isEqualTo("B");
        assertThat(FactorId.N.value()).isEqualTo("N");
        assertThat(FactorId.D.value()).isEqualTo("D");
        assertThat(FactorId.R.value()).isEqualTo("R");
        assertThat(FactorId.B.isBuiltIn()).isTrue();
        assertThat(FactorId.of("FRAUD").isBuiltIn()).isFalse();
    }

    @Test
    @DisplayName("фабрика возвращает те же экземпляры для встроенных факторов")
    void factoryReusesConstants() {
        assertThat(FactorId.of("B")).isSameAs(FactorId.B);
        assertThat(FactorId.of("R")).isSameAs(FactorId.R);
    }

    @Test
    @DisplayName("пользовательский фактор создаётся и равен такому же по значению")
    void customFactorEquality() {
        assertThat(FactorId.of("FRAUD")).isEqualTo(new FactorId("FRAUD"));
    }

    @ParameterizedTest(name = "код «{0}» допустим")
    @ValueSource(strings = {"B", "FRAUD", "RISK_SCORE", "X1", "A_1_2", "ABCDEFGHIJKLMNOP"})
    @DisplayName("допустимые коды факторов")
    void validCodes(String code) {
        assertThat(FactorId.of(code).value()).isEqualTo(code);
    }

    @ParameterizedTest(name = "код «{0}» отвергается")
    @ValueSource(strings = {
            "b",                    // нижний регистр
            "fraud",                // нижний регистр
            "1B",                   // начинается с цифры
            "_B",                   // начинается с подчёркивания
            "FRAUD SCORE",          // пробел
            "FRAUD-SCORE",          // дефис
            "ABCDEFGHIJKLMNOPQ"     // 17 символов
    })
    @DisplayName("недопустимые коды факторов отвергаются на месте")
    void invalidCodes(String code) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FactorId.of(code))
                .withMessageContaining("Недопустимый идентификатор фактора");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("пустой и отсутствующий код отвергаются")
    void nullAndEmptyCodes(String code) {
        assertThatIllegalArgumentException().isThrownBy(() -> FactorId.of(code));
    }

    @Test
    @DisplayName("сортировка по коду: веса и факторы в журнале идут в одном порядке")
    void naturalOrderIsByCode() {
        assertThat(new TreeSet<>(java.util.List.of(FactorId.R, FactorId.B, FactorId.of("FRAUD"), FactorId.N)))
                .extracting(FactorId::value)
                .containsExactly("B", "FRAUD", "N", "R");
    }

    @Test
    @DisplayName("строковое представление — сам код: так он выглядит в журнале и в метриках")
    void toStringIsTheCode() {
        assertThat(FactorId.B).hasToString("B");
    }
}
