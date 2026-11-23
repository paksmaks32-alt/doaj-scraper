package ua.lnu.doaj.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Посилання журналу; нам потрібна головна сторінка журналу. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Ref {
    public String journal;
}
