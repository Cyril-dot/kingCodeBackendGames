package com.kikiBettingWebBack.KikiWebSite.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EnterResultRequest {

    @NotNull(message = "Home score is required")
    @Min(value = 0, message = "Score cannot be negative")
    private Integer homeScore;

    @NotNull(message = "Away score is required")
    @Min(value = 0, message = "Score cannot be negative")
    private Integer awayScore;
}