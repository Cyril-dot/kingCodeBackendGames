package com.kikiBettingWebBack.KikiWebSite.dtos;

import com.kikiBettingWebBack.KikiWebSite.entities.Admin;
import com.kikiBettingWebBack.KikiWebSite.entities.User;   // ✅ correct package
import com.kikiBettingWebBack.KikiWebSite.entities.Wallet;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ResponseMapper {

    private static final BigDecimal WITHDRAWAL_THRESHOLD = new BigDecimal("5000");

    // ---------------------------------------------------------------
    // USER → UserResponse
    // ---------------------------------------------------------------
    public UserResponse toUserResponse(User user, Wallet wallet) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                // ✅ Removed: user.isActive() — User entity has no isActive field
                .createdAt(user.getCreatedAt())
                .walletBalance(wallet.getBalance())
                .totalDeposited(wallet.getTotalDeposited())
                .totalWithdrawn(wallet.getTotalWithdrawn())
                .hasEverDeposited(wallet.isHasEverDeposited())
                .canWithdraw(wallet.getBalance().compareTo(WITHDRAWAL_THRESHOLD) > 0)
                .build();
    }

    // ---------------------------------------------------------------
    // ADMIN → AdminResponse
    // ---------------------------------------------------------------
    public AdminResponse toAdminResponse(Admin admin) {
        return AdminResponse.builder()
                .id(admin.getId())
                .fullName(admin.getFullName())   // ✅ Admin entity has fullName, not firstName + lastName
                .email(admin.getEmail())
                // ✅ Removed: admin.isActive() — Admin entity has no isActive field
                // ✅ Removed: admin.getFirstName() / admin.getLastName() — Admin entity has no such fields
                .createdAt(admin.getCreatedAt())
                .build();
    }
}