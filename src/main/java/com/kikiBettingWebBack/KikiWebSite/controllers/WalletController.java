package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.Config.ApiResponse;
import com.kikiBettingWebBack.KikiWebSite.Config.Security.UserPrincipal;
import com.kikiBettingWebBack.KikiWebSite.dtos.DepositInitiateRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.DepositInitiateResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.TransactionResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.WithdrawRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.WithdrawResponse;
import com.kikiBettingWebBack.KikiWebSite.entities.User;
import com.kikiBettingWebBack.KikiWebSite.entities.Wallet;
import com.kikiBettingWebBack.KikiWebSite.exceptions.ResourceNotFoundException;
import com.kikiBettingWebBack.KikiWebSite.repos.UserRepository;
import com.kikiBettingWebBack.KikiWebSite.repos.WalletRepository;
import com.kikiBettingWebBack.KikiWebSite.services.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    // ---------------------------------------------------------------
    // POST /api/v1/wallet/deposit
    // Initiates a Paystack payment — returns URL for user to complete payment
    // ---------------------------------------------------------------
    @PostMapping("/deposit")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<DepositInitiateResponse>> initiateDeposit(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody DepositInitiateRequest request) {

        UUID userId = userPrincipal.getUserId();
        DepositInitiateResponse response = walletService.initiateDeposit(userId, request);

        return ResponseEntity.ok(ApiResponse.success(
                "Payment initiated. Redirect user to the payment URL.", response));
    }

    // ---------------------------------------------------------------
    // POST /api/v1/wallet/paystack/webhook
    // Called by Paystack server after payment — do NOT require auth
    // Paystack signature verified inside the service
    // ---------------------------------------------------------------
    @PostMapping("/paystack/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("x-paystack-signature") String signature) {

        walletService.handlePaystackWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }

    // ---------------------------------------------------------------
    // POST /api/v1/wallet/withdraw
    // Auto-approved if balance rules pass
    // ---------------------------------------------------------------
    @PostMapping("/withdraw")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<WithdrawResponse>> withdraw(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody WithdrawRequest request) {

        UUID userId = userPrincipal.getUserId();
        WithdrawResponse response = walletService.withdraw(userId, request);

        return ResponseEntity.ok(ApiResponse.success("Withdrawal processed successfully", response));
    }

    // ---------------------------------------------------------------
    // GET /api/v1/wallet/transactions
    // Full transaction history for logged-in user
    // ---------------------------------------------------------------
    @GetMapping("/transactions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getTransactionHistory(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        UUID userId = userPrincipal.getUserId();
        List<TransactionResponse> history = walletService.getTransactionHistory(userId);

        return ResponseEntity.ok(ApiResponse.success(
                "Fetched " + history.size() + " transactions", history));
    }

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<?>> getBalance(
            @AuthenticationPrincipal UserPrincipal userDetails) {

        User user = userRepository
                .findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Wallet wallet = walletRepository
                .findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        return ResponseEntity.ok(ApiResponse.success("Balance fetched",
                Map.of(
                        "balance",  wallet.getBalance(),
                        "currency", user.getCurrency()
                )
        ));
    }
}