package com.kikiBettingWebBack.KikiWebSite.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    // ✅ No isActive — User entity has no such field
    private LocalDateTime createdAt;

    // Wallet fields
    private BigDecimal walletBalance;
    private BigDecimal totalDeposited;
    private BigDecimal totalWithdrawn;
    private boolean hasEverDeposited;
    private boolean canWithdraw;
}