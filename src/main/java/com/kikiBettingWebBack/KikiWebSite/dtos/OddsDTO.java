package com.kikiBettingWebBack.KikiWebSite.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OddsDTO {
    private double home;
    private double draw;
    private double away;
    private double over15;
    private double over25;
    private double over35;
}