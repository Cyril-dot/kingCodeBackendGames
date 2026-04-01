package com.kikiBettingWebBack.KikiWebSite.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class WithdrawResponse {
    private String reference;
    private BigDecimal requestedAmount;
    private BigDecimal feeDeducted;         // 10% of requested amount
    private BigDecimal amountReceived;      // 90% — what user actually gets
    private BigDecimal balanceAfter;
    private String currency;
    private String status;
    private String message;
}