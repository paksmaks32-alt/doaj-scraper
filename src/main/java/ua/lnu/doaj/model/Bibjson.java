package ua.lnu.doaj.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Бібліографічні дані журналу (поле bibjson). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Bibjson {
    public String title;

    @JsonProperty("alternative_title")
    public String alternativeTitle;

    /** Електронний ISSN. Є не в усіх журналів. */
    public String eissn;
    /** Друкований ISSN. */
    public String pissn;

    public Organization publisher;
    public Organization institution;

    public List<String> keywords;
    /** Коди мов, наприклад "EN", "UK". */
    public List<String> language;
    public List<Subject> subject;
    public List<License> license;
    public Apc apc;
    public Ref ref;

    @JsonProperty("oa_start")
    public Integer oaStart;

    @JsonProperty("publication_time_weeks")
    public Integer publicationTimeWeeks;

    /** Повертає ISSN для адреси сторінки журналу: спочатку eissn, якщо нема — pissn. */
    public String preferredIssn() {
        if (eissn != null && !eissn.isBlank()) return eissn;
        if (pissn != null && !pissn.isBlank()) return pissn;
        return null;
    }
}
