package ua.lnu.doaj.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Видавець або установа: назва і код країни (наприклад "UA"). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Organization {
    public String name;
    public String country;
}
