package ua.lnu.doaj.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Один результат пошуку: службові поля Elasticsearch + сам журнал (_source).
 * _source зберігаємо двічі: як типізований об'єкт Journal (для окремих колонок у БД)
 * і як "сирий" JsonNode (щоб у БД лежав повний JSON без втрат).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Hit {
    @JsonProperty("_id")
    public String id;

    @JsonProperty("_source")
    public JsonNode rawSource;

    /** Значення сортування: [дата додавання в мс, id]. Потрібні для search_after. */
    public List<Object> sort;
}
