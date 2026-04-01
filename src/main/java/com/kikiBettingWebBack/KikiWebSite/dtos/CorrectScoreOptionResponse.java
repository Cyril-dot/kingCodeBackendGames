package com.kikiBettingWebBack.KikiWebSite.dtos;

import com.kikiBettingWebBack.KikiWebSite.entities.CorrectScoreMarketStatus;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// ── Response: one score option shown to user ──────────────────────
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrectScoreOptionResponse {
    private UUID id;
    private Integer homeScore;
    private Integer awayScore;
    private BigDecimal odds;
}

// ── Response: full market for a game ─────────────────────────────


// ── Request: user places a correct score bet ──────────────────────


