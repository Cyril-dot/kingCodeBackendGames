package com.kikiBettingWebBack.KikiWebSite.dtos;

import com.kikiBettingWebBack.KikiWebSite.entities.CorrectScoreMarketStatus;
import com.kikiBettingWebBack.KikiWebSite.entities.GameStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameResponse {

    private UUID id;
    private String homeTeam;
    private String awayTeam;
    private LocalDateTime matchDate;
    private GameStatus status;
    private CorrectScoreMarketStatus correctScoreMarketStatus;
    private String bookingCode;

    // 1X2 odds
    private BigDecimal oddsHomeWin;
    private BigDecimal oddsDraw;
    private BigDecimal oddsAwayWin;

    // Final score — null until admin reveals
    private Integer homeScore;
    private Integer awayScore;

    private LocalDateTime createdAt;
}