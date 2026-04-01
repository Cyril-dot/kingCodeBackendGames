package com.kikiBettingWebBack.KikiWebSite.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WithdrawRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    // Mobile Money / Bank details
    @NotBlank(message = "Account number / mobile number is required")
    private String accountNumber;

    @NotBlank(message = "Bank code or network is required — e.g. MTN, 058")
    private String bankCode;

    @NotBlank(message = "Account name is required")
    private String accountName;
}