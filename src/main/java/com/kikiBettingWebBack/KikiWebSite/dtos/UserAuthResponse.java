package com.kikiBettingWebBack.KikiWebSite.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserAuthResponse {
    private String token;
    private String tokenType;   // Always "Bearer"
    private String role;        // Always "USER"
    private String email;
    private String firstName;
    private String lastName;
}