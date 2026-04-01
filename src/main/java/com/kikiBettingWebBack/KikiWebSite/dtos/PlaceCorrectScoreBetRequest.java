package com.kikiBettingWebBack.KikiWebSite.dtos;

import com.kikiBettingWebBack.KikiWebSite.entities.CorrectScoreMarketStatus;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlaceCorrectScoreBetRequest {
    private UUID gameId;
    private UUID correctScoreOptionId; // which score line the user picked
    private BigDecimal stake;
}