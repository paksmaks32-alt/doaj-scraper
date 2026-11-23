package ua.lnu.doaj.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Тематика журналу за класифікацією Бібліотеки Конгресу (LCC). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Subject {
    public String scheme;
    public String code;
    public String term;
}
