package com.kikiBettingWebBack.KikiWebSite.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamDTO {
    private Long id;
    private String name;
    private String shortName;
    private String tla;
    private String crest;
}