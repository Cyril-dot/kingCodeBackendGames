package com.kikiBettingWebBack.KikiWebSite.services;

import com.kikiBettingWebBack.KikiWebSite.dtos.LoginRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.RegisterRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.UpdateUserRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.UserAuthResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.UserResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.ResponseMapper;
import com.kikiBettingWebBack.KikiWebSite.entities.User;
import com.kikiBettingWebBack.KikiWebSite.entities.Wallet;
import com.kikiBettingWebBack.KikiWebSite.exceptions.BadRequestException;
import com.kikiBettingWebBack.KikiWebSite.exceptions.ResourceNotFoundException;
import com.kikiBettingWebBack.KikiWebSite.exceptions.UnauthorizedException;
import com.kikiBettingWebBack.KikiWebSite.repos.UserRepository;
import com.kikiBettingWebBack.KikiWebSite.repos.WalletRepository;
import com.kikiBettingWebBack.KikiWebSite.Config.Security.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final ResponseMapper responseMapper;
    private final GeoLocationService geoLocationService; // ✅ injected for IP detection

    // ---------------------------------------------------------------
    // REGISTER
    // ✅ clientIp is passed in from the controller — detected there
    //    from HttpServletRequest so the service stays testable
    // ---------------------------------------------------------------
    @Transactional
    public UserAuthResponse register(RegisterRequest request, String clientIp) {

        if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new BadRequestException("An account with this email already exists");
        }
        if (StringUtils.hasText(request.getPhoneNumber()) &&
                userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new BadRequestException("An account with this phone number already exists");
        }

        // ✅ Detect currency from IP — user never sees or touches this
        String currency = geoLocationService.detectCurrency(clientIp);
        log.info("Detected currency '{}' for registration IP: {}", currency, clientIp);

        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(request.getEmail().toLowerCase().trim())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .currency(currency)   // ✅ stored automatically
                .build();

        user = userRepository.save(user);

        // Auto-create wallet
        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .totalDeposited(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .hasEverDeposited(false)
                .build();

        walletRepository.save(wallet);
        log.info("New user registered: {} | currency: {}", user.getEmail(), currency);

        String token = tokenService.generateAccessToken(user);

        return UserAuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .role("USER")
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }

    // ---------------------------------------------------------------
    // LOGIN
    // ---------------------------------------------------------------
    public UserAuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        log.info("User logged in: {}", user.getEmail());

        String token = tokenService.generateAccessToken(user);

        return UserAuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .role("USER")
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }

    // ---------------------------------------------------------------
    // VIEW ACCOUNT
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public UserResponse getMyAccount(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        return responseMapper.toUserResponse(user, wallet);
    }

    // ---------------------------------------------------------------
    // UPDATE PROFILE
    // ---------------------------------------------------------------
    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateUserRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (StringUtils.hasText(request.getFirstName())) {
            user.setFirstName(request.getFirstName().trim());
        }
        if (StringUtils.hasText(request.getLastName())) {
            user.setLastName(request.getLastName().trim());
        }

        if (StringUtils.hasText(request.getPhoneNumber())) {
            boolean phoneTakenByOther = userRepository.findByPhoneNumber(request.getPhoneNumber())
                    .filter(found -> !found.getId().equals(userId))
                    .isPresent();
            if (phoneTakenByOther) {
                throw new BadRequestException("Phone number already in use by another account");
            }
            user.setPhoneNumber(request.getPhoneNumber());
        }

        if (StringUtils.hasText(request.getNewPassword())) {
            if (!StringUtils.hasText(request.getCurrentPassword())) {
                throw new BadRequestException("Current password is required to set a new password");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new BadRequestException("Current password is incorrect");
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        user = userRepository.save(user);
        log.info("User profile updated: {}", user.getEmail());

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        return responseMapper.toUserResponse(user, wallet);
    }
}