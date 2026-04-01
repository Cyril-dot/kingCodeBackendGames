package com.kikiBettingWebBack.KikiWebSite.dtos;

import com.kikiBettingWebBack.KikiWebSite.entities.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {
    private UUID id;
    private TransactionType type;
    private TransactionStatus status;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String description;
    private String paymentReference;
    private LocalDateTime createdAt;
}