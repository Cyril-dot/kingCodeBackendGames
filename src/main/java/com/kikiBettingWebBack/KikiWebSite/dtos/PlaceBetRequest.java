package com.kikiBettingWebBack.KikiWebSite.dtos;

import com.kikiBettingWebBack.KikiWebSite.entities.MarketType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class PlaceBetRequest {

    @NotNull(message = "Stake amount is required")
    @DecimalMin(value = "5.00", message = "Minimum stake is 5")
    private BigDecimal stake;

    @NotEmpty(message = "At least one selection is required")
    private List<BetSelectionRequest> selections;

    @Data
    public static class BetSelectionRequest {

        @NotNull(message = "Game ID is required")
        private UUID gameId;

        @NotNull(message = "Market type is required")
        private MarketType marketType;
    }
}