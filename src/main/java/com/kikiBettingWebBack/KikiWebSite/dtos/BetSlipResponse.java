package com.kikiBettingWebBack.KikiWebSite.dtos;


import com.kikiBettingWebBack.KikiWebSite.entities.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BetSlipResponse {
    private UUID id;
    private String slipReference;
    private BigDecimal stake;
    private BigDecimal totalOdds;
    private BigDecimal potentialPayout;
    private BigDecimal actualPayout;
    private BetStatus status;
    private LocalDateTime placedAt;
    private LocalDateTime settledAt;
    private List<SelectionDetail> selections;

    @Data
    @Builder
    public static class SelectionDetail {
        private UUID gameId;
        private String homeTeam;
        private String awayTeam;
        private LocalDateTime matchDate;
        private MarketType marketType;
        private BigDecimal oddsAtPlacement;
        private BetStatus selectionStatus;
    }
}