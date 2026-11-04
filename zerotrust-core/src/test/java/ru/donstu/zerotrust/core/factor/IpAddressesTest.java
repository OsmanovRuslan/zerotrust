package ru.donstu.zerotrust.core.factor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Тесты разбора адреса клиента: частный или публичный и префикс для профиля. */
class IpAddressesTest {

    @ParameterizedTest(name = "{0} — частный или локальный")
    @ValueSource(strings = {"10.0.0.1", "172.16.0.1", "192.168.1.1", "127.0.0.1", "169.254.1.1",
            "0.0.0.0", "::1", "fc00::1", "fd12:3456::1", "fe80::1"})
    @DisplayName("частные и локальные адреса: в Docker и офисной сети все вызовы приходят с них")
    void privateAddresses(String address) {
        assertThat(IpAddresses.isPrivateOrLocal(IpAddresses.parse(address))).isTrue();
    }

    @ParameterizedTest(name = "{0} — публичный")
    @ValueSource(strings = {"8.8.8.8", "203.0.113.5", "172.32.0.1", "2001:db8::1",
            "2001:4860:4860::8888"})
    @DisplayName("публичные адреса")
    void publicAddresses(String address) {
        assertThat(IpAddresses.isPrivateOrLocal(IpAddresses.parse(address))).isFalse();
    }

    @Test
    @DisplayName("адрес не задан — не частный")
    void nullIsNotPrivate() {
        assertThat(IpAddresses.isPrivateOrLocal(null)).isFalse();
    }

    @ParameterizedTest(name = "префикс {0} = {1}")
    @CsvSource({
            "203.0.113.5,            203.0.113.0/24",
            "203.0.113.255,          203.0.113.0/24",
            "10.1.2.3,               10.1.2.0/24",
            "2001:db8:1234:5678::1,  2001:db8:1234::/48",
            "2001:db8:1234:9999::ff, 2001:db8:1234::/48"
    })
    @DisplayName("префикс адреса: /24 для IPv4 и /48 для IPv6")
    void prefixOf(String address, String expected) {
        assertThat(IpAddresses.prefixOf(IpAddresses.parse(address))).isEqualTo(expected);
    }

    @Test
    @DisplayName("адреса из одной подсети дают один префикс, из разных — разные")
    void prefixIdentifiesSubnet() {
        String first = IpAddresses.prefixOf(IpAddresses.parse("203.0.113.5"));
        String sameSubnet = IpAddresses.prefixOf(IpAddresses.parse("203.0.113.90"));
        String otherSubnet = IpAddresses.prefixOf(IpAddresses.parse("203.0.114.5"));

        assertThat(first).isEqualTo(sameSubnet).isNotEqualTo(otherSubnet);
    }

    @Test
    @DisplayName("префикс без адреса не считается")
    void prefixRequiresAddress() {
        assertThatIllegalArgumentException().isThrownBy(() -> IpAddresses.prefixOf(null));
    }

    @ParameterizedTest(name = "«{0}» — не адрес")
    @ValueSource(strings = {"не адрес", "example.com", "300.1.1.1", "1.2.3", "1.2.3.4.5",
            "1.2.3.", "1.2.3.-1", "2001:db8:::1", " ", "01.02.03.0004"})
    @DisplayName("имена и мусор не разбираются: обращений к DNS нет")
    void invalidLiterals(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> IpAddresses.parse(value));
    }

    @Test
    @DisplayName("адрес с пробелами по краям разбирается")
    void trimmedLiteral() {
        assertThat(IpAddresses.parse("  10.0.0.1 ").getHostAddress()).isEqualTo("10.0.0.1");
    }
}
