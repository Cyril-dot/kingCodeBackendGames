package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.dtos.LiveGameResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.UpcomingFixtureResponse;
import com.kikiBettingWebBack.KikiWebSite.services.FootballApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public endpoints to expose live scores and upcoming fixtures.
 *
 * Base path: /api/football
 */
@RestController
@RequestMapping("/api/football")
@RequiredArgsConstructor
public class FootballApiController {

    private final FootballApiService footballApiService;

    // ─────────────────────────────────────────────────────────────────────────
    // LIVE GAMES
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/football/live
     *
     * Returns all in-play fixtures across every league.
     * Uses one API request — safe within the 100/day free limit.
     *
     * Example response:
     * [
     *   {
     *     "fixtureId": 867946,
     *     "homeTeam": "Arsenal",
     *     "awayTeam": "Chelsea",
     *     "homeScore": 1,
     *     "awayScore": 0,
     *     "statusShort": "2H",
     *     "statusLong": "Second Half",
     *     "elapsed": 67,
     *     "league": "Premier League",
     *     "country": "England",
     *     "round": "Regular Season - 30",
     *     "kickoff": "2025-04-01T19:45:00+00:00"
     *   }
     * ]
     */
    @GetMapping("/live")
    public ResponseEntity<List<LiveGameResponse>> getLiveGames() {
        return ResponseEntity.ok(footballApiService.getLiveGames());
    }

    /**
     * GET /api/football/live?leagues=39,140,135
     *
     * Returns live fixtures filtered to specific league IDs.
     * Comma-separated league IDs (e.g. 39 = Premier League, 140 = La Liga).
     * Still counts as ONE api-football request regardless of how many leagues.
     */
    @GetMapping("/live/leagues")
    public ResponseEntity<List<LiveGameResponse>> getLiveGamesByLeagues(
            @RequestParam String leagues) {
        return ResponseEntity.ok(footballApiService.getLiveGamesByLeagues(leagues));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPCOMING FIXTURES
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/football/upcoming
     *
     * Returns today's not-yet-started fixtures for all default leagues
     * configured in application.properties (football.api.default-leagues).
     *
     * NOTE: Makes one API call per league — keep the list short (5 leagues = 5 requests).
     */
    @GetMapping("/upcoming")
    public ResponseEntity<List<UpcomingFixtureResponse>> getUpcomingGames() {
        return ResponseEntity.ok(footballApiService.getTodayUpcomingFixtures());
    }

    /**
     * GET /api/football/upcoming/{leagueId}?season=2024
     *
     * Returns today's upcoming fixtures for a specific league.
     * Season defaults to the current football season if not supplied.
     */
    @GetMapping("/upcoming/{leagueId}")
    public ResponseEntity<List<UpcomingFixtureResponse>> getUpcomingByLeague(
            @PathVariable int leagueId,
            @RequestParam(required = false) Integer season) {

        int resolvedSeason = (season != null) ? season : currentSeason();
        return ResponseEntity.ok(footballApiService.getUpcomingByLeague(leagueId, resolvedSeason));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────────────────

    private int currentSeason() {
        int month = java.time.LocalDate.now().getMonthValue();
        int year  = java.time.LocalDate.now().getYear();
        return month >= 8 ? year : year - 1;
    }
}