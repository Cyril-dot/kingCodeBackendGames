package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.dtos.*;
import com.kikiBettingWebBack.KikiWebSite.services.CorrectScoreMarketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/correct-score")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCorrectScoreController {

    private final CorrectScoreMarketService correctScoreMarketService;

    // POST /api/admin/correct-score/{gameId}/generate
    // Generate (or regenerate) random score options for a game
    @PostMapping("/{gameId}/generate")
    public ResponseEntity<List<CorrectScoreOptionResponse>> generateOptions(
            @PathVariable UUID gameId) {
        return ResponseEntity.ok(correctScoreMarketService.generateOptions(gameId));
    }

    // POST /api/admin/correct-score/{gameId}/lock
    // Step 1 — lock the market, no new bets accepted, score still hidden
    @PostMapping("/{gameId}/lock")
    public ResponseEntity<GameResponse> lockMarket(@PathVariable UUID gameId) {
        return ResponseEntity.ok(correctScoreMarketService.lockMarket(gameId));
    }

    // POST /api/admin/correct-score/{gameId}/reveal
    // Step 2 — reveal the final score and settle all bets
    @PostMapping("/{gameId}/reveal")
    public ResponseEntity<GameResponse> revealAndSettle(
            @PathVariable UUID gameId,
            @Valid @RequestBody RevealCorrectScoreRequest request) {
        return ResponseEntity.ok(correctScoreMarketService.revealAndSettle(gameId, request));
    }
}