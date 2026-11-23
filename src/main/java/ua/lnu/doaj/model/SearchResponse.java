package ua.lnu.doaj.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Верхній рівень відповіді Elasticsearch на запит /query/journal/_search.
 * Поля took, _shards, aggregations нам не потрібні, тому ігноруються.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResponse {
    public Hits hits;
}
