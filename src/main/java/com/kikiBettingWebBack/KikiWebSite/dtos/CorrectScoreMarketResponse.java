package com.kikiBettingWebBack.KikiWebSite.dtos;
import com.kikiBettingWebBack.KikiWebSite.entities.CorrectScoreMarketStatus;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrectScoreMarketResponse {
    private UUID gameId;
    private String homeTeam;
    private String awayTeam;
    private CorrectScoreMarketStatus marketStatus;
    // Only populated after admin reveals
    private Integer finalHomeScore;
    private Integer finalAwayScore;
    private List<CorrectScoreOptionResponse> options;
}