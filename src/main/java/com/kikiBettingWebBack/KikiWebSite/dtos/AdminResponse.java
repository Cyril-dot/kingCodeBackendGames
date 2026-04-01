package com.kikiBettingWebBack.KikiWebSite.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AdminResponse {
    private UUID id;
    private String fullName;
    private String email;
    private boolean isActive;
    private LocalDateTime createdAt;
}