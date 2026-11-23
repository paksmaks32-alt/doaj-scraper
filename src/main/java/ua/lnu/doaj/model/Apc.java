package ua.lnu.doaj.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** APC (Article Processing Charge) — плата за публікацію статті. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Apc {
    @JsonProperty("has_apc")
    public Boolean hasApc;

    public List<Price> max;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Price {
        public String currency;
        public Double price;
    }
}
