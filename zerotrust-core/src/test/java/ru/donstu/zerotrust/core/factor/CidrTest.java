package ru.donstu.zerotrust.core.factor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Тесты диапазона адресов: доверенные сети, прокси и правило AllowedNetworks. */
class CidrTest {

    private static InetAddress ip(String value) {
        return IpAddresses.parse(value);
    }

    @ParameterizedTest(name = "{1} входит в {0}: {2}")
    @CsvSource({
            "10.0.0.0/8,       10.1.2.3,     true",
            "10.0.0.0/8,       11.0.0.1,     false",
            "192.168.1.0/24,   192.168.1.42, true",
            "192.168.1.0/24,   192.168.2.42, false",
            "203.0.113.5/32,   203.0.113.5,  true",
            "203.0.113.5/32,   203.0.113.6,  false",
            "100.64.0.0/10,    100.70.1.1,   true",
            "100.64.0.0/10,    100.128.1.1,  false",
            "0.0.0.0/0,        8.8.8.8,      true",
            "2001:db8::/32,    2001:db8:1::1, true",
            "2001:db8::/32,    2001:db9::1,  false"
    })
    @DisplayName("вхождение адреса в диапазон")
    void contains(String cidr, String address, boolean expected) {
        assertThat(Cidr.parse(cidr).contains(ip(address))).isEqualTo(expected);
    }

    @Test
    @DisplayName("адреса разных версий не сравниваются")
    void differentFamiliesDoNotMatch() {
        assertThat(Cidr.parse("10.0.0.0/8").contains(ip("::1"))).isFalse();
        assertThat(Cidr.parse("2001:db8::/32").contains(ip("10.0.0.1"))).isFalse();
        assertThat(Cidr.parse("10.0.0.0/8").contains(null)).isFalse();
    }

    @Test
    @DisplayName("запись без длины префикса — один адрес")
    void singleAddress() {
        Cidr cidr = Cidr.parse("203.0.113.5");

        assertThat(cidr.prefixLength()).isEqualTo(32);
        assertThat(cidr.contains(ip("203.0.113.5"))).isTrue();
        assertThat(cidr.contains(ip("203.0.113.4"))).isFalse();
    }

    @Test
    @DisplayName("хвост адреса обнуляется: 10.1.2.3/8 — это сеть 10.0.0.0/8")
    void hostBitsAreCleared() {
        Cidr cidr = Cidr.parse("10.1.2.3/8");

        assertThat(cidr.network().getHostAddress()).isEqualTo("10.0.0.0");
        assertThat(cidr).hasToString("10.0.0.0/8");
        assertThat(cidr).isEqualTo(Cidr.parse("10.0.0.0/8"));
    }

    @ParameterizedTest(name = "запись «{0}» отвергается")
    @ValueSource(strings = {"не адрес", "10.0.0.0/33", "10.0.0.0/-1", "10.0.0.0/abc",
            "2001:db8::/129", "300.1.1.1/24"})
    @DisplayName("недопустимые записи отвергаются с понятной ошибкой")
    void invalidRecords(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> Cidr.parse(value));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("пустая запись отвергается")
    void emptyRecord(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> Cidr.parse(value))
                .withMessageContaining("Пустая запись");
    }

    @Test
    @DisplayName("длина префикса проверяется и в конструкторе")
    void constructorChecksPrefixLength() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Cidr(ip("10.0.0.0"), 33))
                .withMessageContaining("вне диапазона");
        assertThatIllegalArgumentException().isThrownBy(() -> new Cidr(null, 8));
    }
}
