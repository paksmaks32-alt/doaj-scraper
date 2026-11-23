package ua.lnu.doaj.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Запис про журнал (вміст поля _source). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Journal {
    public String id;
    public Bibjson bibjson;

    @JsonProperty("created_date")
    public String createdDate;

    @JsonProperty("last_updated")
    public String lastUpdated;
}
