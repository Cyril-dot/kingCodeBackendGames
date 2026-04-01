package com.kikiBettingWebBack.KikiWebSite.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SettlementResponse {
    private String gameId;
    private String homeTeam;
    private String awayTeam;
    private int homeScore;
    private int awayScore;
    private int totalSlipsSettled;
    private int totalWinners;
    private int totalLosers;
    private BigDecimal totalPayoutCredited;   // Total winnings sent to user wallets
}