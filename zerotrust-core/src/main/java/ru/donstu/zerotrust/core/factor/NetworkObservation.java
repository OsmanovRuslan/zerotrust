package ru.donstu.zerotrust.core.factor;

import ru.donstu.zerotrust.core.model.GeoPoint;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;

/**
 * Что известно о сети текущего вызова (вход фактора N).
 *
 * @param ip               адрес клиента, определённый с учётом доверенных прокси
 * @param country          код страны по геолокации или {@code null}, если её нет
 * @param geo              координаты по геолокации или {@code null}
 * @param anonymizer       истина, если адрес есть в списке анонимайзеров (узлы Tor)
 * @param trustedNetworks  доверенные сети из хранилища
 * @param now              время вызова
 */
public record NetworkObservation(InetAddress ip, String country, GeoPoint geo, boolean anonymizer,
                                 List<TrustedNetwork> trustedNetworks, Instant now) {

    public NetworkObservation {
        if (ip == null) {
            throw new IllegalArgumentException("Адрес клиента не задан");
        }
        if (now == null) {
            throw new IllegalArgumentException("Время вызова не задано");
        }
        country = (country == null || country.isBlank()) ? null : country.trim().toUpperCase();
        trustedNetworks = (trustedNetworks == null) ? List.of() : List.copyOf(trustedNetworks);
    }

    /** Наблюдение без геолокации и доверенных сетей — минимальный случай. */
    public static NetworkObservation of(InetAddress ip, Instant now) {
        return new NetworkObservation(ip, null, null, false, List.of(), now);
    }

    /** Истина, если адрес частный или локальный: геолокация и перемещение для него не считаются. */
    public boolean isPrivate() {
        return IpAddresses.isPrivateOrLocal(ip);
    }
}
