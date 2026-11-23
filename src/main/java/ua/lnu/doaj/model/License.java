package ua.lnu.doaj.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Ліцензія Creative Commons, наприклад "CC BY". */
@JsonIgnoreProperties(ignoreUnknown = true)
public class License {
    public String type;
    public String url;
}
