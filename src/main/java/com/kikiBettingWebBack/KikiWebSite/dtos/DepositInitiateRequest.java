package com.kikiBettingWebBack.KikiWebSite.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class DepositInitiateRequest {

    private BigDecimal amount;
}