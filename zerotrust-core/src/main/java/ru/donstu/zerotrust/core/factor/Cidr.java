package ru.donstu.zerotrust.core.factor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Диапазон адресов в записи CIDR: 10.0.0.0/8, 2001:db8::/32.
 *
 * <p>Нужен доверенным сетям таблицы zt_network, списку доверенных прокси
 * и правилу AllowedNetworks.
 *
 * @param network      адрес сети с обнулённым хвостом
 * @param prefixLength длина префикса в битах
 */
public record Cidr(InetAddress network, int prefixLength) {

    public Cidr {
        if (network == null) {
            throw new IllegalArgumentException("Адрес сети не задан");
        }
        int bits = network.getAddress().length * 8;
        if (prefixLength < 0 || prefixLength > bits) {
            throw new IllegalArgumentException(
                    "Длина префикса " + prefixLength + " вне диапазона [0, " + bits + "]");
        }
    }

    /**
     * Разбирает запись вида 192.168.0.0/16. Адрес без длины префикса считается одиночным:
     * /32 для IPv4, /128 для IPv6.
     *
     * @throws IllegalArgumentException если запись не разбирается
     */
    public static Cidr parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Пустая запись CIDR");
        }
        String text = value.trim();
        int slash = text.indexOf('/');
        String addressPart = (slash < 0) ? text : text.substring(0, slash);
        InetAddress address;
        try {
            address = IpAddresses.parse(addressPart);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Недопустимый адрес в записи CIDR: " + value, e);
        }
        int prefixLength = address.getAddress().length * 8;
        if (slash >= 0) {
            try {
                prefixLength = Integer.parseInt(text.substring(slash + 1).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Недопустимая длина префикса в записи CIDR: " + value, e);
            }
        }
        return new Cidr(networkAddress(address, prefixLength), prefixLength);
    }

    /** Истина, если адрес входит в диапазон. Адреса разных версий не сравниваются. */
    public boolean contains(InetAddress address) {
        if (address == null) {
            return false;
        }
        byte[] candidate = address.getAddress();
        byte[] base = network.getAddress();
        if (candidate.length != base.length) {
            return false;
        }
        int fullBytes = prefixLength / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (candidate[i] != base[i]) {
                return false;
            }
        }
        int remainingBits = prefixLength % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits);
        return (candidate[fullBytes] & mask) == (base[fullBytes] & mask);
    }

    @Override
    public String toString() {
        return network.getHostAddress() + "/" + prefixLength;
    }

    /** Обнуляет биты за префиксом: 10.1.2.3/8 превращается в адрес сети 10.0.0.0. */
    private static InetAddress networkAddress(InetAddress address, int prefixLength) {
        byte[] bytes = address.getAddress();
        int bits = bytes.length * 8;
        if (prefixLength < 0 || prefixLength > bits) {
            throw new IllegalArgumentException(
                    "Длина префикса " + prefixLength + " вне диапазона [0, " + bits + "]");
        }
        byte[] masked = Arrays.copyOf(bytes, bytes.length);
        for (int bit = prefixLength; bit < bits; bit++) {
            masked[bit / 8] &= (byte) ~(1 << (7 - bit % 8));
        }
        try {
            return InetAddress.getByAddress(masked);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Недопустимый адрес сети: " + address, e);
        }
    }
}
