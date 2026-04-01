package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.dtos.*;
import com.kikiBettingWebBack.KikiWebSite.services.CorrectScoreMarketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/correct-score")
@RequiredArgsConstructor
public class CorrectScoreMarketController {

    private final CorrectScoreMarketService correctScoreMarketService;

    // GET /api/correct-score/{gameId}
    // Returns all score options + market status for a game
    // Final score is hidden until market is SETTLED
    @GetMapping("/{gameId}")
    public ResponseEntity<CorrectScoreMarketResponse> getMarket(@PathVariable UUID gameId) {
        return ResponseEntity.ok(correctScoreMarketService.getMarket(gameId));
    }

    // POST /api/correct-score/bet
    // User places a correct score bet
    @PostMapping("/bet")
    public ResponseEntity<BetSlipResponse> placeBet(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody PlaceCorrectScoreBetRequest request) {
        return ResponseEntity.ok(correctScoreMarketService.placeCorrectScoreBet(userId, request));
    }
}