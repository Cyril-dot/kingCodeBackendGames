package com.kikiBettingWebBack.KikiWebSite.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MatchDTO {

    // ── Identity ─────────────────────────────────────────────
    private String uid;          // e.g. "fd-123456" or "wc-0"
    private String source;       // "fd" | "wc"
    private String competition;  // "Premier League" / "FIFA World Cup 2026"
    private String competitionCode; // "PL", "UCL", "WC2026", etc.
    private String category;     // "club" | "wc"

    // ── Status ───────────────────────────────────────────────
    private boolean live;
    private String status;       // SCHEDULED | IN_PLAY | PAUSED | FINISHED
    private String minute;       // e.g. "67'" when live

    // ── Timing ───────────────────────────────────────────────
    private String utcDate;      // ISO-8601
    private String round;        // WC round label
    private String venue;        // WC ground

    // ── Teams ────────────────────────────────────────────────
    private String homeTeam;
    private String awayTeam;
    private String homeCrest;
    private String awayCrest;

    // ── Score (null if not started) ───────────────────────────
    private Integer homeScore;
    private Integer awayScore;

    // ── Odds (simulated) ─────────────────────────────────────
    private OddsDTO odds;
}