package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.Config.ApiResponse;
import com.kikiBettingWebBack.KikiWebSite.Config.Security.UserPrincipal;
import com.kikiBettingWebBack.KikiWebSite.dtos.*;
import com.kikiBettingWebBack.KikiWebSite.services.CorrectScoreMarketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/correct-score")
@RequiredArgsConstructor
public class CorrectScoreMarketController {

    private final CorrectScoreMarketService correctScoreMarketService;

    // GET /api/correct-score/{gameId}
    @GetMapping("/{gameId}")
    public ResponseEntity<CorrectScoreMarketResponse> getMarket(@PathVariable UUID gameId) {
        return ResponseEntity.ok(correctScoreMarketService.getMarket(gameId));
    }

    // POST /api/correct-score/bet
    @PostMapping("/bet")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<BetSlipResponse>> placeBet(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody PlaceCorrectScoreBetRequest request) {

        BetSlipResponse slip = correctScoreMarketService
                .placeCorrectScoreBet(userPrincipal.getUserId(), request);

        return ResponseEntity.ok(ApiResponse.success(
                String.format("Bet placed! Potential payout: %.2f", slip.getPotentialPayout()),
                slip));
    }

    // GET /api/correct-score/bets/my
    @GetMapping("/bets/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<BetSlipResponse>>> getMyBets(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        List<BetSlipResponse> bets = correctScoreMarketService
                .getMyCorrectScoreBets(userPrincipal.getUserId());

        return ResponseEntity.ok(ApiResponse.success(
                "Fetched " + bets.size() + " correct score bets", bets));
    }
}