package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.services.FootballService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/football")
@RequiredArgsConstructor
public class FootballController {

    private final FootballService footballService;

    // ── GET /api/v1/football/fixtures ─────────────────────────────────
    // Returns all upcoming + live club fixtures (next 7 days) + WC 2026
    @GetMapping("/fixtures")
    public Mono<ResponseEntity<Map<String, Object>>> getFixtures() {
        log.info("GET /fixtures called");
        return footballService.getAllFixtures()
                .map(matches -> {
                    long club = matches.stream().filter(m -> "club".equals(m.getCategory())).count();
                    long wc   = matches.stream().filter(m -> "wc".equals(m.getCategory())).count();
                    return ResponseEntity.ok(Map.of(
                            "total",   matches.size(),
                            "club",    club,
                            "wc",      wc,
                            "matches", matches
                    ));
                });
    }

    // ── GET /api/v1/football/live ──────────────────────────────────────
    // Returns only in-play / paused matches
    @GetMapping("/live")
    public Mono<ResponseEntity<Map<String, Object>>> getLive() {
        log.info("GET /live called");
        return footballService.getLiveFixtures()
                .map(matches -> ResponseEntity.ok(Map.of(
                        "total",   matches.size(),
                        "matches", matches
                )));
    }

    // ── GET /api/v1/football/health ────────────────────────────────────
    // Quick health-check endpoint (useful for Railway / Render uptime monitors)
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "gstake-backend"));
    }
}