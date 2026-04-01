package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.Config.ApiResponse;
import com.kikiBettingWebBack.KikiWebSite.Config.Security.UserPrincipal;
import com.kikiBettingWebBack.KikiWebSite.dtos.BetSlipResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.PlaceBetRequest;
import com.kikiBettingWebBack.KikiWebSite.services.BettingService;
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
@RequestMapping("/api/v1/bets")
@RequiredArgsConstructor
public class BettingController {

    private final BettingService bettingService;

    // ---------------------------------------------------------------
    // POST /api/v1/bets — place an accumulator bet slip
    // Stake deducted immediately. Potential payout shown in response.
    // ---------------------------------------------------------------
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<BetSlipResponse>> placeBet(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody PlaceBetRequest request) {

        UUID userId = userPrincipal.getUserId();
        BetSlipResponse slip = bettingService.placeBet(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        String.format("Bet placed! Potential payout: %.2f", slip.getPotentialPayout()),
                        slip));
    }

    // ---------------------------------------------------------------
    // GET /api/v1/bets/my — user's full bet history
    // ---------------------------------------------------------------
    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<BetSlipResponse>>> getMyBets(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        UUID userId = userPrincipal.getUserId();
        List<BetSlipResponse> bets = bettingService.getMyBets(userId);

        return ResponseEntity.ok(ApiResponse.success(
                "Fetched " + bets.size() + " bet slips", bets));
    }

    // ---------------------------------------------------------------
    // GET /api/v1/bets/slip/{reference} — load a slip by reference code
    // Useful for sharing / checking a specific slip
    // ---------------------------------------------------------------
    @GetMapping("/slip/{reference}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<BetSlipResponse>> getSlipByReference(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String reference) {

        BetSlipResponse slip = bettingService.getSlipByReference(reference);

        return ResponseEntity.ok(ApiResponse.success("Slip details fetched", slip));
    }
}