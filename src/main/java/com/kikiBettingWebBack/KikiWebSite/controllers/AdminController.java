package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.Config.ApiResponse;
import com.kikiBettingWebBack.KikiWebSite.Config.Security.AdminPrincipal;
import com.kikiBettingWebBack.KikiWebSite.dtos.AdminAuthResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.AdminRegisterRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.AdminResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.LoginRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.UserResponse;
import com.kikiBettingWebBack.KikiWebSite.services.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAuthService adminAuthService;

    // ---------------------------------------------------------------
    // POST /api/v1/admin/register
    // Public — lock down via secret header or IP allowlist in production
    // ---------------------------------------------------------------
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AdminAuthResponse>> register(
            @Valid @RequestBody AdminRegisterRequest request) {

        AdminAuthResponse authResponse = adminAuthService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Admin account created successfully", authResponse));
    }

    // ---------------------------------------------------------------
    // POST /api/v1/admin/login
    // Public — no token needed
    // ---------------------------------------------------------------
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminAuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AdminAuthResponse authResponse = adminAuthService.login(request);

        return ResponseEntity.ok(ApiResponse.success("Admin login successful", authResponse));
    }

    // ---------------------------------------------------------------
    // GET /api/v1/admin/me
    // Protected — admin token required
    // ✅ Uses @AuthenticationPrincipal AdminPrincipal to extract adminId
    //    directly from the security context — no SecurityUtils needed
    // ---------------------------------------------------------------
    @GetMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminResponse>> getMyProfile(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {

        UUID adminId = adminPrincipal.getId();
        AdminResponse profile = adminAuthService.getMyProfile(adminId);

        return ResponseEntity.ok(ApiResponse.success("Admin profile fetched", profile));
    }

    // ---------------------------------------------------------------
    // GET /api/v1/admin/users
    // Protected — admin token required
    // ---------------------------------------------------------------
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {

        List<UserResponse> users = adminAuthService.getAllUsers();

        return ResponseEntity.ok(
                ApiResponse.success("Fetched " + users.size() + " users", users));
    }

    // ---------------------------------------------------------------
    // GET /api/v1/admin/users/{userId}
    // Protected — admin token required
    // ---------------------------------------------------------------
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable UUID userId) {

        UserResponse user = adminAuthService.getUserById(userId);

        return ResponseEntity.ok(ApiResponse.success("User details fetched", user));
    }
}