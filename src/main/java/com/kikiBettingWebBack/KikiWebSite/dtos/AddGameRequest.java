package com.kikiBettingWebBack.KikiWebSite.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ── Add Game ──────────────────────────────────────────────────────
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddGameRequest {

    @NotBlank(message = "Home team is required")
    private String homeTeam;

    @NotBlank(message = "Away team is required")
    private String awayTeam;

    @NotNull(message = "Match date is required")
    private LocalDateTime matchDate;

    @NotNull(message = "Home win odds are required")
    @DecimalMin(value = "1.01", message = "Odds must be at least 1.01")
    private BigDecimal oddsHomeWin;

    @NotNull(message = "Draw odds are required")
    @DecimalMin(value = "1.01", message = "Odds must be at least 1.01")
    private BigDecimal oddsDraw;

    @NotNull(message = "Away win odds are required")
    @DecimalMin(value = "1.01", message = "Odds must be at least 1.01")
    private BigDecimal oddsAwayWin;

    // Optional — auto-generated if blank
    private String bookingCode;
}

// ── Update Game ───────────────────────────────────────────────────
