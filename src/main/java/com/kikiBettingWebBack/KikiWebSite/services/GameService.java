package com.kikiBettingWebBack.KikiWebSite.services;

import com.kikiBettingWebBack.KikiWebSite.dtos.*;
import com.kikiBettingWebBack.KikiWebSite.entities.Game;
import com.kikiBettingWebBack.KikiWebSite.entities.GameStatus;
import com.kikiBettingWebBack.KikiWebSite.exceptions.BadRequestException;
import com.kikiBettingWebBack.KikiWebSite.exceptions.ResourceNotFoundException;
import com.kikiBettingWebBack.KikiWebSite.repos.BetSelectionRepository;
import com.kikiBettingWebBack.KikiWebSite.repos.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final BetSelectionRepository betSelectionRepository;

    // ---------------------------------------------------------------
    // ADD GAME
    // ---------------------------------------------------------------
    @Transactional
    public GameResponse addGame(AddGameRequest request) {

        String bookingCode = resolveBookingCode(request.getBookingCode(), null);

        Game game = Game.builder()
                .homeTeam(request.getHomeTeam().trim())
                .awayTeam(request.getAwayTeam().trim())
                .matchDate(request.getMatchDate())
                .oddsHomeWin(request.getOddsHomeWin())
                .oddsDraw(request.getOddsDraw())
                .oddsAwayWin(request.getOddsAwayWin())
                .bookingCode(bookingCode)
                .build();

        game = gameRepository.save(game);
        log.info("Game added: {} vs {} on {} | code: {}",
                game.getHomeTeam(), game.getAwayTeam(), game.getMatchDate(), bookingCode);

        return toGameResponse(game);
    }

    // ---------------------------------------------------------------
    // UPDATE GAME — only allowed if still UPCOMING
    // ---------------------------------------------------------------
    @Transactional
    public GameResponse updateGame(UUID gameId, UpdateGameRequest request) {

        Game game = getGameOrThrow(gameId);

        if (game.getStatus() != GameStatus.UPCOMING) {
            throw new BadRequestException("Only UPCOMING games can be edited");
        }

        if (StringUtils.hasText(request.getHomeTeam()))  game.setHomeTeam(request.getHomeTeam().trim());
        if (StringUtils.hasText(request.getAwayTeam()))  game.setAwayTeam(request.getAwayTeam().trim());
        if (request.getMatchDate()   != null) game.setMatchDate(request.getMatchDate());
        if (request.getOddsHomeWin() != null) game.setOddsHomeWin(request.getOddsHomeWin());
        if (request.getOddsDraw()    != null) game.setOddsDraw(request.getOddsDraw());
        if (request.getOddsAwayWin() != null) game.setOddsAwayWin(request.getOddsAwayWin());

        if (StringUtils.hasText(request.getBookingCode())) {
            game.setBookingCode(resolveBookingCode(request.getBookingCode(), gameId));
        }

        game = gameRepository.save(game);
        log.info("Game updated: {}", gameId);
        return toGameResponse(game);
    }

    // ---------------------------------------------------------------
    // REMOVE GAME — only if UPCOMING and no bet selections exist
    // ---------------------------------------------------------------
    @Transactional
    public void removeGame(UUID gameId) {

        Game game = getGameOrThrow(gameId);

        if (game.getStatus() != GameStatus.UPCOMING) {
            throw new BadRequestException(
                    "Only UPCOMING games can be removed. Use cancel to void a live game.");
        }

        if (betSelectionRepository.existsByGameId(gameId)) {
            throw new BadRequestException(
                    "Cannot remove this game — it has active bet selections. Cancel it instead.");
        }

        gameRepository.delete(game);
        log.info("Game removed: {}", gameId);
    }

    // ---------------------------------------------------------------
    // GET ALL GAMES (public) — all statuses, ordered by match date
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<GameResponse> getUpcomingGames() {
        return gameRepository.findAllByOrderByMatchDateAsc()
                .stream()
                .map(this::toGameResponse)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // GET ALL GAMES — admin
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<GameResponse> getAllGames() {
        return gameRepository.findAllByOrderByMatchDateAsc()
                .stream()
                .map(this::toGameResponse)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // GET GAME BY ID
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public GameResponse getGameById(UUID gameId) {
        return toGameResponse(getGameOrThrow(gameId));
    }

    // ---------------------------------------------------------------
    // GET GAME BY BOOKING CODE
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public GameResponse getGameByBookingCode(String bookingCode) {
        Game game = gameRepository.findByBookingCode(bookingCode.toUpperCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No game found with booking code: " + bookingCode));
        return toGameResponse(game);
    }

    // ---------------------------------------------------------------
    // PRIVATE HELPERS
    // ---------------------------------------------------------------

    private String resolveBookingCode(String requested, UUID excludeGameId) {
        if (StringUtils.hasText(requested)) {
            String code = requested.toUpperCase().trim();
            boolean takenByAnother = excludeGameId != null
                    ? gameRepository.existsByBookingCodeAndIdNot(code, excludeGameId)
                    : gameRepository.existsByBookingCode(code);
            if (takenByAnother) {
                throw new BadRequestException("Booking code '" + code + "' is already in use");
            }
            return code;
        }
        String datePart = java.time.LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = UUID.randomUUID().toString()
                .replace("-", "").substring(0, 5).toUpperCase();
        return "BET-" + datePart + "-" + randomPart;
    }

    Game getGameOrThrow(UUID gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new ResourceNotFoundException("Game not found: " + gameId));
    }

    GameResponse toGameResponse(Game game) {
        return GameResponse.builder()
                .id(game.getId())
                .homeTeam(game.getHomeTeam())
                .awayTeam(game.getAwayTeam())
                .matchDate(game.getMatchDate())
                .status(game.getStatus())
                .correctScoreMarketStatus(game.getCorrectScoreMarketStatus())
                .bookingCode(game.getBookingCode())
                .oddsHomeWin(game.getOddsHomeWin())
                .oddsDraw(game.getOddsDraw())
                .oddsAwayWin(game.getOddsAwayWin())
                .homeScore(game.getHomeScore())
                .awayScore(game.getAwayScore())
                .createdAt(game.getCreatedAt())
                .build();
    }
}