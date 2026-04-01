package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.Config.ApiResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.AddGameRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.EnterResultRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.UpdateGameRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.GameResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.SettlementResponse;
import com.kikiBettingWebBack.KikiWebSite.services.GameService;
import com.kikiBettingWebBack.KikiWebSite.services.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final SettlementService settlementService;

    // ==============================================================
    // PUBLIC ENDPOINTS — no token needed
    // ==============================================================

    // GET /api/v1/games/public — all upcoming games for bettors
    @GetMapping("/api/v1/games/public")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getUpcomingGames() {
        List<GameResponse> games = gameService.getUpcomingGames();
        return ResponseEntity.ok(ApiResponse.success(
                games.size() + " upcoming games available", games));
    }

    // GET /api/v1/games/public/{gameId} — single game detail
    @GetMapping("/api/v1/games/public/{gameId}")
    public ResponseEntity<ApiResponse<GameResponse>> getGameById(
            @PathVariable UUID gameId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Game details", gameService.getGameById(gameId)));
    }

    // GET /api/v1/games/public/booking/{code} — load game by booking code
    @GetMapping("/api/v1/games/public/booking/{code}")
    public ResponseEntity<ApiResponse<GameResponse>> getByBookingCode(
            @PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(
                "Game found", gameService.getGameByBookingCode(code)));
    }

    // ==============================================================
    // ADMIN ENDPOINTS — ROLE_ADMIN required
    // ==============================================================

    // GET /api/v1/admin/games — admin sees all games (all statuses)
    @GetMapping("/api/v1/admin/games")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getAllGames() {
        List<GameResponse> games = gameService.getAllGames();
        return ResponseEntity.ok(ApiResponse.success(
                "Fetched " + games.size() + " games", games));
    }

    // POST /api/v1/admin/games — add a new game
    @PostMapping("/api/v1/admin/games")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<GameResponse>> addGame(
            @Valid @RequestBody AddGameRequest request) {
        GameResponse game = gameService.addGame(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Game added successfully", game));
    }

    // PATCH /api/v1/admin/games/{gameId} — update game before it starts
    @PatchMapping("/api/v1/admin/games/{gameId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<GameResponse>> updateGame(
            @PathVariable UUID gameId,
            @Valid @RequestBody UpdateGameRequest request) {
        GameResponse game = gameService.updateGame(gameId, request);
        return ResponseEntity.ok(ApiResponse.success("Game updated successfully", game));
    }

    // DELETE /api/v1/admin/games/{gameId} — remove upcoming game
    @DeleteMapping("/api/v1/admin/games/{gameId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeGame(
            @PathVariable UUID gameId) {
        gameService.removeGame(gameId);
        return ResponseEntity.ok(ApiResponse.success("Game removed successfully"));
    }

    // POST /api/v1/admin/games/{gameId}/result — enter score and auto-settle all bets
    @PostMapping("/api/v1/admin/games/{gameId}/result")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SettlementResponse>> enterResult(
            @PathVariable UUID gameId,
            @Valid @RequestBody EnterResultRequest request) {
        SettlementResponse result = settlementService.enterResultAndSettle(gameId, request);
        return ResponseEntity.ok(ApiResponse.success(
                "Result entered and all bets settled", result));
    }

    // POST /api/v1/admin/games/{gameId}/cancel — cancel game and refund all stakes
    @PostMapping("/api/v1/admin/games/{gameId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SettlementResponse>> cancelGame(
            @PathVariable UUID gameId) {
        SettlementResponse result = settlementService.cancelGame(gameId);
        return ResponseEntity.ok(ApiResponse.success(
                "Game cancelled and all stakes refunded", result));
    }
}