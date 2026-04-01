package com.kikiBettingWebBack.KikiWebSite.dtos;

import com.kikiBettingWebBack.KikiWebSite.entities.CorrectScoreMarketStatus;
import lombok.*;
import java.util.List;
import java.util.UUID;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RevealCorrectScoreRequest {
    private Integer homeScore;
    private Integer awayScore;
}