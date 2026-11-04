package ru.donstu.zerotrust.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Тесты ссылки на ресурс: она связывает решение с конкретным объектом. */
class ResourceRefTest {

    @Test
    @DisplayName("ссылка пишется в журнал в виде тип:идентификатор")
    void readableForm() {
        ResourceRef account = new ResourceRef("account", "16");

        assertThat(account.type()).isEqualTo("account");
        assertThat(account.id()).isEqualTo("16");
        assertThat(account).hasToString("account:16");
    }

    @Test
    @DisplayName("ссылки на один объект равны, на разные — нет")
    void equality() {
        assertThat(new ResourceRef("account", "16")).isEqualTo(new ResourceRef("account", "16"));
        assertThat(new ResourceRef("account", "16")).isNotEqualTo(new ResourceRef("account", "15"));
        assertThat(new ResourceRef("account", "16")).isNotEqualTo(new ResourceRef("card", "16"));
    }

    @ParameterizedTest(name = "тип «{0}» отвергается")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("без типа ресурса ссылка бессмысленна")
    void typeIsRequired(String type) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ResourceRef(type, "16"))
                .withMessageContaining("Тип ресурса");
    }

    @ParameterizedTest(name = "идентификатор «{0}» отвергается")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("без идентификатора объекта ссылка бессмысленна")
    void idIsRequired(String id) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ResourceRef("account", id))
                .withMessageContaining("Идентификатор ресурса");
    }
}
