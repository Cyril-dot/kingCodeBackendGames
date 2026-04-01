package com.kikiBettingWebBack.KikiWebSite.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "home_team", nullable = false)
    private String homeTeam;

    @Column(name = "away_team", nullable = false)
    private String awayTeam;

    @Column(name = "match_date", nullable = false)
    private LocalDateTime matchDate;

    // ---- Match result odds ----
    @Column(name = "odds_home_win", nullable = false, precision = 10, scale = 2)
    private BigDecimal oddsHomeWin;

    @Column(name = "odds_draw", nullable = false, precision = 10, scale = 2)
    private BigDecimal oddsDraw;

    @Column(name = "odds_away_win", nullable = false, precision = 10, scale = 2)
    private BigDecimal oddsAwayWin;

    // ---- Booking code ----
    @Column(name = "booking_code", unique = true)
    private String bookingCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private GameStatus status = GameStatus.UPCOMING;

    // ---- Correct score market state ----
    @Enumerated(EnumType.STRING)
    @Column(name = "correct_score_market_status")
    @Builder.Default
    private CorrectScoreMarketStatus correctScoreMarketStatus = CorrectScoreMarketStatus.OPEN;

    // Set by admin after match ends
    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}