package com.kikiBettingWebBack.KikiWebSite.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DepositInitiateResponse {
    private String paymentUrl;          // Redirect user here to complete payment
    private String reference;           // Internal transaction reference
    private String paystackReference;   // Paystack's own reference
    private String currency;
    private String amountToDeposit;     // Formatted: "GHS 800.00"
}