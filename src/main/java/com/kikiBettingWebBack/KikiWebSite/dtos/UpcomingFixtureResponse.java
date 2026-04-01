package com.kikiBettingWebBack.KikiWebSite.dtos;

import lombok.Data;

/** Returned for upcoming fixtures */
@Data
public class UpcomingFixtureResponse {
    private Long fixtureId;
    private String homeTeam;
    private String homeLogo;
    private String awayTeam;
    private String awayLogo;
    private String kickoff;       // ISO date string
    private String league;
    private String country;
    private String round;
    private String statusShort;   // always "NS" (Not Started)
}