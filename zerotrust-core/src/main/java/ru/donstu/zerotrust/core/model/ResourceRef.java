package ru.donstu.zerotrust.core.model;

/**
 * Ссылка на конкретный объект, к которому обращается вызов.
 *
 * <p>Первый принцип NIST SP 800-207 — «все данные и сервисы являются ресурсами», и решение
 * выдаётся на доступ к <b>конкретному</b> ресурсу, а не к эндпоинту. Эта ссылка связывает
 * решение с объектом: она попадает в поле {@code resource_id} журнала решений и в событие
 * {@code OWNERSHIP_VIOLATION}, поэтому по журналу видно не только «alice пыталась читать счета»,
 * но и «alice пыталась читать счёт 16, который ей не принадлежит».
 *
 * @param type тип ресурса в терминах приложения, например {@code "account"}
 * @param id   идентификатор объекта, приведённый к строке
 */
public record ResourceRef(String type, String id) {

    public ResourceRef {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Тип ресурса не может быть пустым");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Идентификатор ресурса не может быть пустым");
        }
    }

    @Override
    public String toString() {
        return type + ":" + id;
    }
}
