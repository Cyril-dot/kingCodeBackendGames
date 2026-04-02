package com.kikiBettingWebBack.KikiWebSite.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kikiBettingWebBack.KikiWebSite.dtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FootballService {

    // ── API credentials ───────────────────────────────────────────────────────
    private static final String FD_API_KEY  = "be1005d63c744335b70addc178dfce37";
    private static final String FD_BASE_URL = "https://api.football-data.org/v4";

    @Qualifier("genericWebClient")
    private final WebClient genericWebClient;

    private final ObjectMapper objectMapper;

    @Value("${worldcup.json.url:https://raw.githubusercontent.com/openfootball/worldcup.json/master/2022/worldcup.json}")
    private String worldCupUrl;

    private static final Map<Integer, String[]> COMP_MAP = Map.of(
            2021, new String[]{"Premier League",   "PL"},
            2001, new String[]{"Champions League", "UCL"},
            2014, new String[]{"La Liga",          "PD"},
            2002, new String[]{"Bundesliga",        "BL1"},
            2019, new String[]{"Serie A",           "SA"},
            2015, new String[]{"Ligue 1",           "FL1"}
    );
    private static final String COMP_IDS = COMP_MAP.keySet()
            .stream().map(Object::toString).collect(Collectors.joining(","));

    // ── Manual in-memory cache (safe with Mono — no @Cacheable needed) ────────
    private volatile List<MatchDTO> fixturesCache    = null;
    private volatile Instant        fixturesCachedAt = Instant.EPOCH;
    private static final long       FIXTURES_TTL_MINS = 30;

    private volatile List<MatchDTO> liveCache    = null;
    private volatile Instant        liveCachedAt = Instant.EPOCH;
    private static final long       LIVE_TTL_SECS = 60;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * All fixtures (club + WC). Cached for 30 minutes.
     * On error returns stale cache if available, otherwise empty list.
     */
    public Mono<List<MatchDTO>> getAllFixtures() {
        if (fixturesCache != null &&
                Instant.now().isBefore(fixturesCachedAt.plus(FIXTURES_TTL_MINS, ChronoUnit.MINUTES))) {
            log.info("✅ [CACHE HIT] Returning {} cached fixtures", fixturesCache.size());
            return Mono.just(fixturesCache);
        }

        log.info("📡 [CACHE MISS] Fetching all fixtures (club + WC)...");
        return Mono.zip(fetchClubFixtures(), fetchWorldCupFixtures())
                .map(tuple -> {
                    List<MatchDTO> all = new ArrayList<>();
                    all.addAll(tuple.getT1());
                    all.addAll(tuple.getT2());
                    log.info("✅ Total fixtures fetched: {} ({} club, {} WC)",
                            all.size(), tuple.getT1().size(), tuple.getT2().size());
                    // Store in cache
                    fixturesCache    = all;
                    fixturesCachedAt = Instant.now();
                    return all;
                })
                .onErrorResume(e -> {
                    log.error("❌ getAllFixtures failed: {}", e.getMessage(), e);
                    // Return stale cache if available, else empty list
                    return Mono.just(fixturesCache != null ? fixturesCache : Collections.emptyList());
                });
    }

    /**
     * Live / in-play fixtures only. Cached for 60 seconds.
     * On error returns stale cache if available, otherwise empty list.
     */
    public Mono<List<MatchDTO>> getLiveFixtures() {
        if (liveCache != null &&
                Instant.now().isBefore(liveCachedAt.plus(LIVE_TTL_SECS, ChronoUnit.SECONDS))) {
            log.info("✅ [CACHE HIT] Returning {} cached live fixtures", liveCache.size());
            return Mono.just(liveCache);
        }

        log.info("📡 [CACHE MISS] Fetching live fixtures...");
        return fdGet("/matches?competitions=" + COMP_IDS + "&status=IN_PLAY,PAUSED")
                .map(raw -> parseClubMatches(parseJson(raw), true))
                .doOnSuccess(list -> {
                    log.info("✅ Live fixtures fetched: {}", list.size());
                    liveCache    = list;
                    liveCachedAt = Instant.now();
                })
                .onErrorResume(e -> {
                    logHttpError("getLiveFixtures", e);
                    return Mono.just(liveCache != null ? liveCache : Collections.emptyList());
                });
    }

    // ── Club fixtures ─────────────────────────────────────────────────────────

    private Mono<List<MatchDTO>> fetchClubFixtures() {
        Instant now     = Instant.now();
        String dateFrom = now.toString().substring(0, 10);
        String dateTo   = now.plus(7, ChronoUnit.DAYS).toString().substring(0, 10);

        log.info("📅 Fetching club fixtures {} → {}", dateFrom, dateTo);

        Mono<List<MatchDTO>> upcoming = fdGet(
                "/matches?competitions=" + COMP_IDS + "&dateFrom=" + dateFrom + "&dateTo=" + dateTo)
                .map(raw -> parseClubMatches(parseJson(raw), false))
                .doOnSuccess(l -> log.info("✅ Upcoming club fixtures: {}", l.size()))
                .doOnError(e -> logHttpError("Club upcoming", e))
                .onErrorReturn(Collections.emptyList());

        Mono<List<MatchDTO>> live = fdGet(
                "/matches?competitions=" + COMP_IDS + "&status=IN_PLAY,PAUSED")
                .map(raw -> parseClubMatches(parseJson(raw), true))
                .doOnSuccess(l -> log.info("✅ Live club fixtures: {}", l.size()))
                .doOnError(e -> logHttpError("Club live", e))
                .onErrorReturn(Collections.emptyList());

        Mono<List<MatchDTO>> finished = fdGet(
                "/matches?competitions=" + COMP_IDS
                        + "&dateFrom=" + dateFrom + "&dateTo=" + dateFrom + "&status=FINISHED")
                .map(raw -> parseClubMatches(parseJson(raw), false))
                .doOnSuccess(l -> log.info("✅ Finished today: {}", l.size()))
                .doOnError(e -> logHttpError("Club finished", e))
                .onErrorReturn(Collections.emptyList());

        return Mono.zip(upcoming, live, finished).map(t -> {
            Map<String, MatchDTO> seen = new LinkedHashMap<>();
            // Live takes priority, then upcoming, then finished
            t.getT2().forEach(m -> seen.put(m.getUid(), m));
            t.getT1().forEach(m -> seen.putIfAbsent(m.getUid(), m));
            t.getT3().forEach(m -> seen.putIfAbsent(m.getUid(), m));
            log.info("🔀 Merged club fixtures (deduped): {}", seen.size());
            return new ArrayList<>(seen.values());
        });
    }

    // ── football-data.org HTTP ────────────────────────────────────────────────

    private Mono<String> fdGet(String path) {
        return genericWebClient.get()
                .uri(FD_BASE_URL + path)
                .header("X-Auth-Token", FD_API_KEY)
                .retrieve()
                .bodyToMono(String.class);
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    private List<MatchDTO> parseClubMatches(JsonNode json, boolean forceLive) {
        List<MatchDTO> result = new ArrayList<>();
        if (json == null) return result;

        JsonNode matches = json.path("matches");
        if (!matches.isArray()) {
            log.warn("⚠️ 'matches' not an array. Preview: {}",
                    json.toString().substring(0, Math.min(300, json.toString().length())));
            return result;
        }

        for (JsonNode m : matches) {
            int compId = m.path("competition").path("id").asInt();
            String[] compInfo = COMP_MAP.get(compId);
            if (compInfo == null) continue;

            String status  = m.path("status").asText("SCHEDULED");
            boolean isLive = forceLive || status.equals("IN_PLAY") || status.equals("PAUSED");
            String uid     = "fd-" + m.path("id").asText();

            result.add(MatchDTO.builder()
                    .uid(uid)
                    .source("fd")
                    .competition(m.path("competition").path("name").asText(compInfo[0]))
                    .competitionCode(compInfo[1])
                    .category("club")
                    .live(isLive)
                    .status(status)
                    .minute(isLive ? m.path("minute").asText(null) : null)
                    .utcDate(m.path("utcDate").asText())
                    .homeTeam(teamName(m, "homeTeam"))
                    .awayTeam(teamName(m, "awayTeam"))
                    .homeCrest(m.path("homeTeam").path("crest").asText(null))
                    .awayCrest(m.path("awayTeam").path("crest").asText(null))
                    .homeScore(score(m, "home"))
                    .awayScore(score(m, "away"))
                    .odds(computeOdds(uid))
                    .build());
        }

        log.info("📊 Parsed {} club matches", result.size());
        return result;
    }

    // ── World Cup fixtures ────────────────────────────────────────────────────

    private Mono<List<MatchDTO>> fetchWorldCupFixtures() {
        log.info("🌍 Fetching WC fixtures from: {}", worldCupUrl);
        return genericWebClient.get()
                .uri(worldCupUrl)
                .retrieve()
                .bodyToMono(String.class)
                .map(raw -> parseWorldCupMatches(parseJson(raw)))
                .doOnSuccess(l -> log.info("✅ WC fixtures fetched: {}", l.size()))
                .doOnError(e -> logHttpError("WC", e))
                .onErrorReturn(Collections.emptyList());
    }

    private List<MatchDTO> parseWorldCupMatches(JsonNode json) {
        List<MatchDTO> result = new ArrayList<>();
        if (json == null) return result;

        JsonNode rounds = json.path("rounds");
        if (!rounds.isArray()) {
            JsonNode matches = json.path("matches");
            if (matches.isArray()) return parseWcMatchesFlat(matches);
            log.warn("⚠️ WC JSON has neither 'rounds' nor 'matches' array");
            return result;
        }

        Instant now     = Instant.now();
        Instant horizon = now.plus(180, ChronoUnit.DAYS);
        int idx = 0, skipped = 0;

        for (JsonNode round : rounds) {
            String roundName = round.path("name").asText("Round");
            JsonNode matches = round.path("matches");
            if (!matches.isArray()) continue;

            for (JsonNode m : matches) {
                String dateStr = m.path("date").asText("") + "T15:00:00Z";
                Instant dt;
                try { dt = Instant.parse(dateStr); }
                catch (Exception e) { idx++; skipped++; continue; }
                if (dt.isBefore(now) || dt.isAfter(horizon)) { idx++; skipped++; continue; }

                String uid   = "wc-" + idx;
                String team1 = m.path("team1").path("name").asText(m.path("team1").asText("TBD"));
                String team2 = m.path("team2").path("name").asText(m.path("team2").asText("TBD"));

                result.add(MatchDTO.builder()
                        .uid(uid).source("wc")
                        .competition("FIFA World Cup 2026").competitionCode("WC2026")
                        .category("wc").live(false).status("SCHEDULED")
                        .utcDate(dateStr).round(roundName)
                        .homeTeam(team1).awayTeam(team2)
                        .homeScore(m.hasNonNull("score1") ? m.path("score1").asInt() : null)
                        .awayScore(m.hasNonNull("score2") ? m.path("score2").asInt() : null)
                        .odds(computeOdds(uid))
                        .build());
                idx++;
            }
        }

        log.info("📊 Parsed {} WC matches ({} skipped)", result.size(), skipped);
        return result;
    }

    private List<MatchDTO> parseWcMatchesFlat(JsonNode matches) {
        List<MatchDTO> result = new ArrayList<>();
        Instant now     = Instant.now();
        Instant horizon = now.plus(180, ChronoUnit.DAYS);
        int idx = 0;
        for (JsonNode m : matches) {
            String dateStr = m.path("date").asText("") + "T15:00:00Z";
            Instant dt;
            try { dt = Instant.parse(dateStr); }
            catch (Exception e) { idx++; continue; }
            if (dt.isBefore(now) || dt.isAfter(horizon)) { idx++; continue; }
            String uid = "wc-" + idx;
            result.add(MatchDTO.builder()
                    .uid(uid).source("wc")
                    .competition("FIFA World Cup 2026").competitionCode("WC2026")
                    .category("wc").live(false).status("SCHEDULED")
                    .utcDate(dateStr)
                    .homeTeam(m.path("team1").asText("TBD"))
                    .awayTeam(m.path("team2").asText("TBD"))
                    .odds(computeOdds(uid))
                    .build());
            idx++;
        }
        return result;
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private JsonNode parseJson(String raw) {
        try { return objectMapper.readTree(raw); }
        catch (Exception e) {
            log.error("❌ JSON parse failed: {}", e.getMessage());
            return null;
        }
    }

    private void logHttpError(String label, Throwable e) {
        if (e instanceof WebClientResponseException ex)
            log.error("❌ {} failed — HTTP {}: {}", label, ex.getStatusCode(), ex.getResponseBodyAsString());
        else
            log.error("❌ {} failed: {}", label, e.getMessage());
    }

    private OddsDTO computeOdds(String uid) {
        long seed = hashString(uid) % 900000L + 100000L;
        return new OddsDTO(
                round2(1.35 + sr(seed)         * 2.5),
                round2(2.60 + sr(seed * 7919L) * 1.5),
                round2(1.35 + sr(seed * 6271L) * 2.5),
                round2(1.12 + sr(seed + 1)     * 0.4),
                round2(1.55 + sr(seed + 2)     * 0.7),
                round2(2.10 + sr(seed + 3)     * 1.4)
        );
    }

    private double sr(long n) {
        n ^= (n << 13); n ^= (n >> 17); n ^= (n << 5);
        return (n & 0xFFFFFFFFL) / 4294967296.0;
    }

    private long hashString(String s) {
        long h = 0;
        for (char c : s.toCharArray()) h = (31 * h + c) & 0xFFFFFFFFL;
        return h;
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private String teamName(JsonNode match, String side) {
        JsonNode t = match.path(side);
        String s = t.path("shortName").asText(null);
        return (s != null && !s.isBlank()) ? s : t.path("name").asText("TBD");
    }

    private Integer score(JsonNode match, String side) {
        JsonNode ft = match.path("score").path("fullTime").path(side);
        if (!ft.isNull() && ft.isNumber()) return ft.asInt();
        JsonNode ht = match.path("score").path("halfTime").path(side);
        if (!ht.isNull() && ht.isNumber()) return ht.asInt();
        return null;
    }
}