package ru.donstu.zerotrust.core.model;

import java.util.regex.Pattern;

/**
 * Идентификатор фактора доверия.
 *
 * <p>Четыре фактора модели — {@link #B} поведение, {@link #N} сеть, {@link #D} устройство,
 * {@link #R} репутация. Пятым и далее может быть пользовательский фактор, например
 * {@code FRAUD} — риск-скор антифрода: приложение объявляет бин
 * {@code TrustFactorProvider} со своим идентификатором, а вес задаётся в политике наравне
 * с B/N/D/R.
 *
 * <p>Это {@code record}, а не {@code enum}, именно из-за пользовательских факторов: набор
 * факторов открыт и не известен на момент компиляции модуля. Формат значения
 * ({@code [A-Z][A-Z0-9_]{0,15}}) проверяется в конструкторе, потому что идентификатор
 * попадает в ключи JSON весов в базе и в теги метрик — опечатка вроде {@code "b"} или
 * {@code "fraud score"} должна обнаруживаться сразу, а не превращаться в молча
 * «потерянный» фактор с нулевым весом.
 *
 * @param value код фактора в верхнем регистре
 */
public record FactorId(String value) implements Comparable<FactorId> {

    /** Допустимый формат кода фактора. */
    private static final Pattern VALID = Pattern.compile("[A-Z][A-Z0-9_]{0,15}");

    /** Поведение субъекта: час суток, частота запросов, новизна операции. */
    public static final FactorId B = new FactorId("B");

    /** Сетевое окружение: адрес, страна, перемещение. */
    public static final FactorId N = new FactorId("N");

    /** Состояние устройства. */
    public static final FactorId D = new FactorId("D");

    /** Репутация: затухающий штраф за события безопасности. */
    public static final FactorId R = new FactorId("R");

    public FactorId {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Недопустимый идентификатор фактора: '" + value + "'. "
                            + "Ожидается код в верхнем регистре по шаблону [A-Z][A-Z0-9_]{0,15}, например B, N, D, R, FRAUD");
        }
    }

    /**
     * Фабрика для разбора кода из конфигурации или JSON весов.
     *
     * @param value код фактора
     * @return идентификатор (для B/N/D/R возвращаются объявленные константы)
     */
    public static FactorId of(String value) {
        if (B.value.equals(value)) {
            return B;
        }
        if (N.value.equals(value)) {
            return N;
        }
        if (D.value.equals(value)) {
            return D;
        }
        if (R.value.equals(value)) {
            return R;
        }
        return new FactorId(value);
    }

    /** Истина для четырёх факторов математической модели главы 1 (не пользовательского). */
    public boolean isBuiltIn() {
        return equals(B) || equals(N) || equals(D) || equals(R);
    }

    /** Сортировка по коду: нужна, чтобы веса и факторы в журнале шли в одном порядке. */
    @Override
    public int compareTo(FactorId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
