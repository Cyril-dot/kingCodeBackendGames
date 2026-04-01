package com.kikiBettingWebBack.KikiWebSite.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

// ─── Root wrapper returned by api-football ───────────────────────────────────
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FootballApiResponse {
    private List<FixtureItem> response;
}

// ─── One fixture (live or upcoming) ──────────────────────────────────────────
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class FixtureItem {
    private FixtureInfo fixture;
    private League league;
    private Teams teams;
    private Goals goals;
    private Score score;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class FixtureInfo {
    private Long id;
    private String referee;
    private String timezone;
    private String date;          // ISO-8601
    private Long timestamp;
    private Status status;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class Status {
    @JsonProperty("long")
    private String longStatus;    // "Match Finished", "First Half", etc.
    @JsonProperty("short")
    private String shortStatus;   // "FT", "1H", "NS", etc.
    private Integer elapsed;      // minutes played (null if not started)
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class League {
    private Long id;
    private String name;
    private String country;
    private String logo;
    private String flag;
    private Integer season;
    private String round;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class Teams {
    private TeamInfo home;
    private TeamInfo away;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class TeamInfo {
    private Long id;
    private String name;
    private String logo;
    private Boolean winner;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class Goals {
    private Integer home;
    private Integer away;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class Score {
    private HalfScore halftime;
    private HalfScore fulltime;
    private HalfScore extratime;
    private HalfScore penalty;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class HalfScore {
    private Integer home;
    private Integer away;
}

// ─── Clean response DTOs returned to your frontend ───────────────────────────

/** Returned for live games */


