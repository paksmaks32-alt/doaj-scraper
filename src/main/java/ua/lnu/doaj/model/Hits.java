package ua.lnu.doaj.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Блок "hits": загальна кількість знайдених журналів і сам список. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Hits {
    public Total total;
    public List<Hit> hits;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Total {
        public long value;
    }
}
