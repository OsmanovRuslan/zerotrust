package ru.donstu.zerotrust.core.factor;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Разбор адреса клиента для фактора N: частный он или публичный и какой у него префикс.
 *
 * <p>Служебный класс, экземпляры не создаются.
 */
public final class IpAddresses {

    /** Длина префикса, которым адреса пользователя запоминаются в профиле: /24 для IPv4. */
    public static final int IPV4_PREFIX_BITS = 24;

    /** Длина префикса для IPv6: /48. */
    public static final int IPV6_PREFIX_BITS = 48;

    private IpAddresses() {
        throw new AssertionError("Служебный класс, экземпляры не создаются");
    }

    /**
     * Разбирает адрес, записанный числами: 203.0.113.5 или 2001:db8::1. Имена не разрешаются,
     * обращений к DNS нет.
     *
     * @throws IllegalArgumentException если это не адрес
     */
    public static InetAddress parse(String literal) {
        if (literal == null || literal.isBlank()) {
            throw new IllegalArgumentException("Адрес не задан");
        }
        String text = literal.trim();
        if (text.indexOf(':') >= 0) {
            try {
                // строку с двоеточием InetAddress разбирает только как литерал IPv6, без DNS
                return InetAddress.getByName(text);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Недопустимый адрес IPv6: " + literal, e);
            }
        }
        try {
            return InetAddress.getByAddress(parseIpv4(text, literal));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Недопустимый адрес IPv4: " + literal, e);
        }
    }

    /**
     * Истина для адресов, которые не маршрутизируются в интернете: RFC 1918, 127.0.0.0/8,
     * 169.254.0.0/16, ::1, fc00::/7 и fe80::/10. Геолокация для них бессмысленна, а сам адрес
     * считается относительно доверенным.
     */
    public static boolean isPrivateOrLocal(InetAddress address) {
        if (address == null) {
            return false;
        }
        if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isAnyLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        // fc00::/7 — уникальные локальные адреса IPv6, отдельного метода в InetAddress для них нет
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    /**
     * Префикс адреса строкой для хранения в профиле: 203.0.113.0/24 или 2001:db8:1234::/48.
     */
    public static String prefixOf(InetAddress address) {
        if (address == null) {
            throw new IllegalArgumentException("Адрес не задан");
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return (bytes[0] & 0xFF) + "." + (bytes[1] & 0xFF) + "." + (bytes[2] & 0xFF)
                    + ".0/" + IPV4_PREFIX_BITS;
        }
        return String.format("%x:%x:%x::/%d",
                group(bytes, 0), group(bytes, 1), group(bytes, 2), IPV6_PREFIX_BITS);
    }

    private static int group(byte[] bytes, int index) {
        return ((bytes[index * 2] & 0xFF) << 8) | (bytes[index * 2 + 1] & 0xFF);
    }

    private static byte[] parseIpv4(String text, String original) {
        String[] parts = text.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Недопустимый адрес IPv4: " + original);
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) {
                throw new IllegalArgumentException("Недопустимый адрес IPv4: " + original);
            }
            for (int c = 0; c < part.length(); c++) {
                if (part.charAt(c) < '0' || part.charAt(c) > '9') {
                    throw new IllegalArgumentException("Недопустимый адрес IPv4: " + original);
                }
            }
            int octet = Integer.parseInt(part);
            if (octet > 255) {
                throw new IllegalArgumentException("Октет " + octet + " больше 255: " + original);
            }
            bytes[i] = (byte) octet;
        }
        return bytes;
    }
}
