package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.Config.ApiResponse;
import com.kikiBettingWebBack.KikiWebSite.Config.Security.UserPrincipal;
import com.kikiBettingWebBack.KikiWebSite.dtos.LoginRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.RegisterRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.UpdateUserRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.UserAuthResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.UserResponse;
import com.kikiBettingWebBack.KikiWebSite.services.GeoLocationService;
import com.kikiBettingWebBack.KikiWebSite.services.UserAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAuthService userAuthService;
    private final GeoLocationService geoLocationService; // ✅ used to extract IP cleanly

    // ---------------------------------------------------------------
    // POST /api/v1/users/register
    // Public — HttpServletRequest injected by Spring to read client IP
    // ---------------------------------------------------------------
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserAuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {               // ✅ Spring injects this automatically

        // Extract real client IP (handles proxies / Render / Cloudflare)
        String clientIp = geoLocationService.extractClientIp(httpRequest);

        UserAuthResponse authResponse = userAuthService.register(request, clientIp);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful. Welcome!", authResponse));
    }

    // ---------------------------------------------------------------
    // POST /api/v1/users/login
    // Public — no token needed
    // ---------------------------------------------------------------
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserAuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        UserAuthResponse authResponse = userAuthService.login(request);

        return ResponseEntity.ok(ApiResponse.success("Login successful", authResponse));
    }

    // ---------------------------------------------------------------
    // GET /api/v1/users/me
    // Protected — user token required
    // ---------------------------------------------------------------
    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<UserResponse>> getMyAccount(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        UUID userId = userPrincipal.getUserId();
        UserResponse userResponse = userAuthService.getMyAccount(userId);

        return ResponseEntity.ok(ApiResponse.success("Account details fetched", userResponse));
    }

    // ---------------------------------------------------------------
    // PATCH /api/v1/users/me
    // Protected — user token required
    // ---------------------------------------------------------------
    @PatchMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UpdateUserRequest request) {

        UUID userId = userPrincipal.getUserId();
        UserResponse userResponse = userAuthService.updateProfile(userId, request);

        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", userResponse));
    }
}