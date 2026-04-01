package com.kikiBettingWebBack.KikiWebSite.services;

import com.kikiBettingWebBack.KikiWebSite.dtos.AdminAuthResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.AdminRegisterRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.AdminResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.LoginRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.ResponseMapper;
import com.kikiBettingWebBack.KikiWebSite.dtos.UserResponse;
import com.kikiBettingWebBack.KikiWebSite.entities.Admin;
import com.kikiBettingWebBack.KikiWebSite.entities.User;
import com.kikiBettingWebBack.KikiWebSite.entities.Wallet;
import com.kikiBettingWebBack.KikiWebSite.exceptions.BadRequestException;
import com.kikiBettingWebBack.KikiWebSite.exceptions.ResourceNotFoundException;
import com.kikiBettingWebBack.KikiWebSite.exceptions.UnauthorizedException;
import com.kikiBettingWebBack.KikiWebSite.repos.AdminRepository;
import com.kikiBettingWebBack.KikiWebSite.repos.UserRepository;
import com.kikiBettingWebBack.KikiWebSite.repos.WalletRepository;
import com.kikiBettingWebBack.KikiWebSite.Config.Security.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final ResponseMapper responseMapper;

    // ---------------------------------------------------------------
    // REGISTER ADMIN
    // ---------------------------------------------------------------
    @Transactional
    public AdminAuthResponse register(AdminRegisterRequest request) {

        if (adminRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new BadRequestException("An admin account with this email already exists");
        }

        Admin admin = Admin.builder()
                .fullName(request.getFullName().trim())
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        admin = adminRepository.save(admin);
        log.info("New admin registered: {}", admin.getEmail());

        String token = tokenService.generateOwnerAccessToken(admin);

        return AdminAuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .role("ADMIN")
                .email(admin.getEmail())
                .fullName(admin.getFullName())
                .build();
    }

    // ---------------------------------------------------------------
    // LOGIN ADMIN
    // ---------------------------------------------------------------
    public AdminAuthResponse login(LoginRequest request) {

        Admin admin = adminRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        log.info("Admin logged in: {}", admin.getEmail());

        String token = tokenService.generateOwnerAccessToken(admin);

        return AdminAuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .role("ADMIN")
                .email(admin.getEmail())
                .fullName(admin.getFullName())
                .build();
    }

    // ---------------------------------------------------------------
    // VIEW OWN ADMIN PROFILE
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public AdminResponse getMyProfile(UUID adminId) {

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        return responseMapper.toAdminResponse(admin);
    }

    // ---------------------------------------------------------------
    // VIEW ALL USERS
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {

        return userRepository.findAll().stream()
                .map(user -> {
                    Wallet wallet = walletRepository.findByUserId(user.getId())
                            .orElse(buildEmptyWallet(user));
                    return responseMapper.toUserResponse(user, wallet);
                })
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // VIEW SINGLE USER DETAIL
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElse(buildEmptyWallet(user));

        return responseMapper.toUserResponse(user, wallet);
    }

    // ---------------------------------------------------------------
    // PRIVATE HELPERS
    // ---------------------------------------------------------------
    private Wallet buildEmptyWallet(User user) {
        log.warn("Wallet not found for user {} — returning zero wallet", user.getId());
        return Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .totalDeposited(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .hasEverDeposited(false)
                .build();
    }
}