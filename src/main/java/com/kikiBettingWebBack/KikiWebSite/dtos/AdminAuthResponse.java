package com.kikiBettingWebBack.KikiWebSite.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAuthResponse {
    private String token;
    private String tokenType;   // Always "Bearer"
    private String role;        // Always "ADMIN"
    private String email;
    private String fullName;
}