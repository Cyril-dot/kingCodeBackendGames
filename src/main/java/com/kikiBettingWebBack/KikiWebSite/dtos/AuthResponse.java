package com.kikiBettingWebBack.KikiWebSite.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private String tokenType;   // Always "Bearer"
    private String role;        // "USER" or "ADMIN"
    private String email;
    private String firstName;
    private String lastName;
}