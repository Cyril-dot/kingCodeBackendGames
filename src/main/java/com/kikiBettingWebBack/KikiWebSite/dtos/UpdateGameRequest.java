package com.kikiBettingWebBack.KikiWebSite.dtos;

import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateGameRequest {

    private String homeTeam;
    private String awayTeam;
    private LocalDateTime matchDate;

    @DecimalMin(value = "1.01", message = "Odds must be at least 1.01")
    private BigDecimal oddsHomeWin;

    @DecimalMin(value = "1.01", message = "Odds must be at least 1.01")
    private BigDecimal oddsDraw;

    @DecimalMin(value = "1.01", message = "Odds must be at least 1.01")
    private BigDecimal oddsAwayWin;

    private String bookingCode;
}