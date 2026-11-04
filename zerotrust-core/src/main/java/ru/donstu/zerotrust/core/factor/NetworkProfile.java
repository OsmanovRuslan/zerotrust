package ru.donstu.zerotrust.core.factor;

import ru.donstu.zerotrust.core.model.GeoPoint;

import java.time.Instant;
import java.util.List;

/**
 * Сетевая часть профиля пользователя (таблица zt_behavior_profile): откуда он обычно работает.
 * Пополняется только после разрешённых вызовов.
 *
 * @param knownPrefixes  знакомые подсети в порядке появления, самая давняя первой
 * @param knownCountries знакомые страны, двухбуквенные коды в верхнем регистре
 * @param lastPoint      координаты последнего разрешённого вызова или {@code null}
 * @param lastSuccessAt  время последнего разрешённого вызова или {@code null}
 */
public record NetworkProfile(List<String> knownPrefixes, List<String> knownCountries,
                             GeoPoint lastPoint, Instant lastSuccessAt) {

    /** Профиль нового пользователя: ничего не известно. */
    public static final NetworkProfile EMPTY = new NetworkProfile(List.of(), List.of(), null, null);

    public NetworkProfile {
        knownPrefixes = (knownPrefixes == null) ? List.of() : List.copyOf(knownPrefixes);
        knownCountries = (knownCountries == null) ? List.of() : List.copyOf(knownCountries);
    }

    /** Истина, если подсеть адреса уже встречалась у пользователя. */
    public boolean knowsPrefix(String prefix) {
        return prefix != null && knownPrefixes.contains(prefix);
    }

    /** Истина, если страна уже встречалась у пользователя. */
    public boolean knowsCountry(String country) {
        return country != null && knownCountries.contains(country.trim().toUpperCase());
    }

    /** Истина, если есть и прошлые координаты, и время прошлого вызова: перемещение можно оценить. */
    public boolean hasPosition() {
        return lastPoint != null && lastSuccessAt != null;
    }
}
