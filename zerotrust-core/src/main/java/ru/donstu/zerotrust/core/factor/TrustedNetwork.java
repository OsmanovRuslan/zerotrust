package ru.donstu.zerotrust.core.factor;

import ru.donstu.zerotrust.core.model.Scores;

/**
 * Доверенная сеть из таблицы zt_network: офис, VPN, сеть соседнего сервиса.
 *
 * @param cidr       диапазон адресов
 * @param group      группа записей, по умолчанию trusted
 * @param trustValue оценка адресов из этой сети
 */
public record TrustedNetwork(Cidr cidr, String group, double trustValue) {

    /** Группа по умолчанию. */
    public static final String DEFAULT_GROUP = "trusted";

    public TrustedNetwork {
        if (cidr == null) {
            throw new IllegalArgumentException("Диапазон доверенной сети не задан");
        }
        group = (group == null || group.isBlank()) ? DEFAULT_GROUP : group.trim();
        if (!Scores.isUnitValue(trustValue)) {
            throw new IllegalArgumentException("trustValue должно лежать в [0, 1], а не " + trustValue);
        }
    }

    /** Полностью доверенная сеть из записи вида 10.0.0.0/8. */
    public static TrustedNetwork of(String cidr) {
        return new TrustedNetwork(Cidr.parse(cidr), DEFAULT_GROUP, 1.0);
    }

    /** Доверенная сеть с заданной оценкой. */
    public static TrustedNetwork of(String cidr, double trustValue) {
        return new TrustedNetwork(Cidr.parse(cidr), DEFAULT_GROUP, trustValue);
    }
}
