package com.kikiBettingWebBack.KikiWebSite.dtos;

import lombok.Data;

@Data
public class LiveGameResponse {
    private Long fixtureId;
    private String homeTeam;
    private String awayTeam;
    private Integer homeScore;
    private Integer awayScore;
    private String statusShort;   // "1H", "HT", "2H", etc.
    private String statusLong;
    private Integer elapsed;      // minutes played
    private String league;
    private String country;
    private String round;
    private String kickoff;       // ISO date string
}