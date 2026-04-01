package com.kikiBettingWebBack.KikiWebSite.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kikiBettingWebBack.KikiWebSite.dtos.LiveGameResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.UpcomingFixtureResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps the api-football (v3.football.api-sports.io) REST API.
 *
 * Injected via constructor — ready to be called from GameService
 * or directly from FootballController.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FootballApiService {

    // ── Pulled from application.properties / application.yml ─────────────────
    @Value("${football.api.key-base}")
    private String apiKey;

    @Value("${football.api.base-url-base:https://v3.football.api-sports.io}")
    private String baseUrl;

    // Default league IDs to fetch when none is specified.
    // 39 = Premier League, 140 = La Liga, 135 = Serie A, 78 = Bundesliga, 61 = Ligue 1
    @Value("${football.api.default-leagues:39,140,135,78,61}")
    private String defaultLeagues;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all fixtures that are currently in-play across all leagues.
     * Uses the "live=all" parameter — counts as ONE request.
     */
    public List<LiveGameResponse> getLiveGames() {
        String url = baseUrl + "/fixtures?live=all";
        JsonNode root = fetch(url);
        return parseLiveGames(root);
    }

    /**
     * Returns live games filtered to specific league IDs, e.g. "39-140-135".
     * Pass comma-separated league IDs; they are converted to the dash-separated
     * format that api-football expects.
     */
    public List<LiveGameResponse> getLiveGamesByLeagues(String commaSeparatedLeagueIds) {
        String dashSeparated = commaSeparatedLeagueIds.replace(",", "-");
        String url = baseUrl + "/fixtures?live=" + dashSeparated;
        JsonNode root = fetch(url);
        return parseLiveGames(root);
    }

    /**
     * Returns today's upcoming fixtures (status = NS) for the configured default leagues.
     * Each league costs one request, so this method batches by issuing one call
     * per league — keep defaultLeagues small to stay inside the free 100 req/day cap.
     */
    public List<UpcomingFixtureResponse> getTodayUpcomingFixtures() {
        List<UpcomingFixtureResponse> all = new ArrayList<>();
        String today = java.time.LocalDate.now().toString(); // yyyy-MM-dd

        for (String leagueId : defaultLeagues.split(",")) {
            String url = baseUrl + "/fixtures?date=" + today
                    + "&league=" + leagueId.trim()
                    + "&season=" + currentSeason()
                    + "&status=NS";
            try {
                JsonNode root = fetch(url);
                all.addAll(parseUpcomingFixtures(root));
            } catch (Exception e) {
                log.warn("Failed to fetch upcoming for league {}: {}", leagueId, e.getMessage());
            }
        }
        return all;
    }

    /**
     * Returns upcoming fixtures for a specific league and season.
     */
    public List<UpcomingFixtureResponse> getUpcomingByLeague(int leagueId, int season) {
        String today = java.time.LocalDate.now().toString();
        String url = baseUrl + "/fixtures?date=" + today
                + "&league=" + leagueId
                + "&season=" + season
                + "&status=NS";
        JsonNode root = fetch(url);
        return parseUpcomingFixtures(root);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /** Executes a GET request and returns the parsed JSON root node. */
    private JsonNode fetch(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-apisports-key", apiKey);   // api-football auth header
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        log.debug("Calling api-football: {}", url);
        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class);

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            // api-football wraps results in a "response" array
            if (!root.has("response")) {
                log.error("Unexpected api-football response: {}", response.getBody());
                throw new RuntimeException("Invalid response from football API");
            }
            return root;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse football API response: " + e.getMessage(), e);
        }
    }

    private List<LiveGameResponse> parseLiveGames(JsonNode root) {
        List<LiveGameResponse> results = new ArrayList<>();
        for (JsonNode item : root.get("response")) {
            try {
                LiveGameResponse dto = new LiveGameResponse();

                JsonNode fixture  = item.get("fixture");
                JsonNode league   = item.get("league");
                JsonNode teams    = item.get("teams");
                JsonNode goals    = item.get("goals");
                JsonNode status   = fixture.get("status");

                dto.setFixtureId(fixture.get("id").asLong());
                dto.setKickoff(fixture.get("date").asText());

                dto.setHomeTeam(teams.get("home").get("name").asText());
                dto.setAwayTeam(teams.get("away").get("name").asText());

                dto.setHomeScore(goals.get("home").isNull() ? 0 : goals.get("home").asInt());
                dto.setAwayScore(goals.get("away").isNull() ? 0 : goals.get("away").asInt());

                dto.setStatusShort(status.get("short").asText());
                dto.setStatusLong(status.get("long").asText());
                dto.setElapsed(status.get("elapsed").isNull() ? null : status.get("elapsed").asInt());

                dto.setLeague(league.get("name").asText());
                dto.setCountry(league.get("country").asText());
                dto.setRound(league.get("round").asText());

                results.add(dto);
            } catch (Exception e) {
                log.warn("Skipping malformed fixture: {}", e.getMessage());
            }
        }
        return results;
    }

    private List<UpcomingFixtureResponse> parseUpcomingFixtures(JsonNode root) {
        List<UpcomingFixtureResponse> results = new ArrayList<>();
        for (JsonNode item : root.get("response")) {
            try {
                UpcomingFixtureResponse dto = new UpcomingFixtureResponse();

                JsonNode fixture = item.get("fixture");
                JsonNode league  = item.get("league");
                JsonNode teams   = item.get("teams");
                JsonNode status  = fixture.get("status");

                dto.setFixtureId(fixture.get("id").asLong());
                dto.setKickoff(fixture.get("date").asText());

                dto.setHomeTeam(teams.get("home").get("name").asText());
                dto.setHomeLogo(teams.get("home").get("logo").asText());
                dto.setAwayTeam(teams.get("away").get("name").asText());
                dto.setAwayLogo(teams.get("away").get("logo").asText());

                dto.setStatusShort(status.get("short").asText());

                dto.setLeague(league.get("name").asText());
                dto.setCountry(league.get("country").asText());
                dto.setRound(league.get("round").asText());

                results.add(dto);
            } catch (Exception e) {
                log.warn("Skipping malformed upcoming fixture: {}", e.getMessage());
            }
        }
        return results;
    }

    /** Returns the current football season year (switches to new year after July). */
    private int currentSeason() {
        int month = java.time.LocalDate.now().getMonthValue();
        int year  = java.time.LocalDate.now().getYear();
        return month >= 8 ? year : year - 1;
    }
}