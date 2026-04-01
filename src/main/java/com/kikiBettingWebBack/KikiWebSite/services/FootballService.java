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

    @Qualifier("footballWebClient")
    private final WebClient footballWebClient;

    @Qualifier("genericWebClient")
    private final WebClient genericWebClient;

    private final ObjectMapper objectMapper;

    @Value("${worldcup.json.url}")
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

    // ── Public API ───────────────────────────────────────────────────────

    public Mono<List<MatchDTO>> getAllFixtures() {
        log.info("📡 Fetching all fixtures (club + WC 2026)...");
        return Mono.zip(fetchClubFixtures(), fetchWorldCupFixtures())
                .map(tuple -> {
                    List<MatchDTO> all = new ArrayList<>();
                    all.addAll(tuple.getT1());
                    all.addAll(tuple.getT2());
                    log.info("✅ Total fixtures returned: {} ({} club, {} WC)",
                            all.size(), tuple.getT1().size(), tuple.getT2().size());
                    return all;
                })
                .doOnError(e -> log.error("❌ getAllFixtures failed: {}", e.getMessage(), e))
                .onErrorReturn(Collections.emptyList());
    }

    public Mono<List<MatchDTO>> getLiveFixtures() {
        log.info("📡 Fetching live fixtures...");
        return footballWebClient.get()
                .uri("/matches?competitions={ids}&status=IN_PLAY,PAUSED", COMP_IDS)
                .retrieve()
                .bodyToMono(String.class)
                .map(raw -> parseClubMatches(parseJson(raw), true))
                .doOnSuccess(list -> log.info("✅ Live fixtures fetched: {}", list.size()))
                .doOnError(e -> logHttpError("getLiveFixtures", e))
                .onErrorReturn(Collections.emptyList());
    }

    // ── Club fixtures ────────────────────────────────────────────────────

    private Mono<List<MatchDTO>> fetchClubFixtures() {
        Instant now = Instant.now();
        String dateFrom = now.toString().substring(0, 10);
        String dateTo   = now.plus(7, ChronoUnit.DAYS).toString().substring(0, 10);

        log.info("📅 Fetching club fixtures from {} to {}", dateFrom, dateTo);
        log.info("🏆 Competition IDs: {}", COMP_IDS);

        Mono<List<MatchDTO>> upcoming = footballWebClient.get()
                .uri("/matches?competitions={ids}&dateFrom={from}&dateTo={to}", COMP_IDS, dateFrom, dateTo)
                .retrieve()
                .bodyToMono(String.class)
                .map(raw -> parseClubMatches(parseJson(raw), false))
                .doOnSuccess(list -> log.info("✅ Upcoming club fixtures fetched: {}", list.size()))
                .doOnError(e -> logHttpError("Club upcoming", e))
                .onErrorReturn(Collections.emptyList());

        Mono<List<MatchDTO>> live = footballWebClient.get()
                .uri("/matches?competitions={ids}&status=IN_PLAY,PAUSED", COMP_IDS)
                .retrieve()
                .bodyToMono(String.class)
                .map(raw -> parseClubMatches(parseJson(raw), true))
                .doOnSuccess(list -> log.info("✅ Live club fixtures fetched: {}", list.size()))
                .doOnError(e -> logHttpError("Club live", e))
                .onErrorReturn(Collections.emptyList());

        return Mono.zip(upcoming, live).map(t -> {
            Map<String, MatchDTO> seen = new LinkedHashMap<>();
            t.getT2().forEach(m -> seen.put(m.getUid(), m));
            t.getT1().forEach(m -> seen.putIfAbsent(m.getUid(), m));
            log.info("🔀 Merged club fixtures (deduped): {}", seen.size());
            return new ArrayList<>(seen.values());
        });
    }

    private List<MatchDTO> parseClubMatches(JsonNode json, boolean forceLive) {
        List<MatchDTO> result = new ArrayList<>();
        if (json == null) return result;

        JsonNode matches = json.path("matches");
        if (!matches.isArray()) {
            log.warn("⚠️ 'matches' not an array. Response preview: {}",
                    json.toString().substring(0, Math.min(300, json.toString().length())));
            return result;
        }

        log.debug("🔍 Parsing {} raw club match nodes (forceLive={})", matches.size(), forceLive);

        for (JsonNode m : matches) {
            int compId = m.path("competition").path("id").asInt();
            String[] compInfo = COMP_MAP.get(compId);
            if (compInfo == null) continue;

            String status = m.path("status").asText("SCHEDULED");
            boolean isLive = forceLive || status.equals("IN_PLAY") || status.equals("PAUSED");
            String uid = "fd-" + m.path("id").asText();

            result.add(MatchDTO.builder()
                    .uid(uid)
                    .source("fd")
                    .competition(m.path("competition").path("name").asText(compInfo[0]))
                    .competitionCode(compInfo[1])
                    .category("club")
                    .live(isLive)
                    .status(status)
                    .minute(m.path("minute").asText(null))
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

        log.info("📊 Parsed {} valid club matches from {} nodes", result.size(), matches.size());
        return result;
    }

    // ── World Cup fixtures ───────────────────────────────────────────────

    private Mono<List<MatchDTO>> fetchWorldCupFixtures() {
        log.info("🌍 Fetching WC fixtures from: {}", worldCupUrl);
        return genericWebClient.get()
                .uri(worldCupUrl)
                .retrieve()
                .bodyToMono(String.class)
                .map(raw -> parseWorldCupMatches(parseJson(raw)))
                .doOnSuccess(list -> log.info("✅ WC fixtures fetched: {}", list.size()))
                .doOnError(e -> logHttpError("WC", e))
                .onErrorReturn(Collections.emptyList());
    }

    private List<MatchDTO> parseWorldCupMatches(JsonNode json) {
        List<MatchDTO> result = new ArrayList<>();
        if (json == null) return result;

        JsonNode matches = json.path("matches");
        if (!matches.isArray()) {
            log.warn("⚠️ WC 'matches' not an array. Preview: {}",
                    json.toString().substring(0, Math.min(300, json.toString().length())));
            return result;
        }

        Instant now = Instant.now();
        Instant horizon = now.plus(90, ChronoUnit.DAYS);
        int idx = 0, skipped = 0;

        for (JsonNode m : matches) {
            String dateStr = m.path("date").asText("2026-06-11") + "T12:00:00Z";
            Instant dt;
            try { dt = Instant.parse(dateStr); } catch (Exception e) { idx++; skipped++; continue; }
            if (dt.isBefore(now) || dt.isAfter(horizon)) { idx++; skipped++; continue; }

            String uid = "wc-" + idx;
            result.add(MatchDTO.builder()
                    .uid(uid)
                    .source("wc")
                    .competition("FIFA World Cup 2026")
                    .competitionCode("WC2026")
                    .category("wc")
                    .live(false)
                    .status("SCHEDULED")
                    .utcDate(dateStr)
                    .round(m.path("round").asText(null))
                    .venue(m.path("ground").asText(null))
                    .homeTeam(m.path("team1").asText("TBD"))
                    .awayTeam(m.path("team2").asText("TBD"))
                    .homeScore(m.hasNonNull("score1") ? m.path("score1").asInt() : null)
                    .awayScore(m.hasNonNull("score2") ? m.path("score2").asInt() : null)
                    .odds(computeOdds(uid))
                    .build());
            idx++;
        }

        log.info("📊 Parsed {} WC matches ({} skipped)", result.size(), skipped);
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private JsonNode parseJson(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            log.error("❌ Failed to parse JSON: {} | Preview: {}",
                    e.getMessage(), raw.substring(0, Math.min(200, raw.length())));
            return null;
        }
    }

    private void logHttpError(String label, Throwable e) {
        if (e instanceof WebClientResponseException ex) {
            log.error("❌ {} failed — HTTP {}: {}", label, ex.getStatusCode(), ex.getResponseBodyAsString());
        } else {
            log.error("❌ {} failed: {} - {}", label, e.getClass().getSimpleName(), e.getMessage());
        }
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
        return s != null && !s.isBlank() ? s : t.path("name").asText("TBD");
    }

    private Integer score(JsonNode match, String side) {
        JsonNode ft = match.path("score").path("fullTime").path(side);
        if (!ft.isNull() && ft.isNumber()) return ft.asInt();
        JsonNode ht = match.path("score").path("halfTime").path(side);
        if (!ht.isNull() && ht.isNumber()) return ht.asInt();
        return null;
    }
}