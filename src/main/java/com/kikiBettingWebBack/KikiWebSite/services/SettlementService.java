package com.kikiBettingWebBack.KikiWebSite.services;

import com.kikiBettingWebBack.KikiWebSite.dtos.EnterResultRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.SettlementResponse;
import com.kikiBettingWebBack.KikiWebSite.entities.*;
import com.kikiBettingWebBack.KikiWebSite.exceptions.BadRequestException;
import com.kikiBettingWebBack.KikiWebSite.repos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final GameRepository gameRepository;
    private final BetSlipRepository betSlipRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final GameService gameService;

    // ---------------------------------------------------------------
    // ENTER RESULT + AUTO-SETTLE ALL PENDING SLIPS (1X2 markets only)
    // Correct score bets are settled separately via
    // CorrectScoreMarketService.revealAndSettle()
    // ---------------------------------------------------------------
    @Transactional
    public SettlementResponse enterResultAndSettle(UUID gameId, EnterResultRequest request) {

        log.info("=== SETTLEMENT START === gameId: {}", gameId);

        Game game = gameService.getGameOrThrow(gameId);

        log.info("Game found: {} vs {} | current status: {}",
                game.getHomeTeam(), game.getAwayTeam(), game.getStatus());

        if (game.getStatus() == GameStatus.FINISHED) {
            log.warn("Settlement rejected — game {} is already FINISHED", gameId);
            throw new BadRequestException("Result has already been entered for this game");
        }
        if (game.getStatus() == GameStatus.CANCELLED) {
            log.warn("Settlement rejected — game {} is CANCELLED", gameId);
            throw new BadRequestException("This game has been cancelled");
        }

        // Persist the result and mark game FINISHED
        game.setHomeScore(request.getHomeScore());
        game.setAwayScore(request.getAwayScore());
        game.setStatus(GameStatus.FINISHED);
        gameRepository.save(game);

        log.info("Result saved — {} vs {} | Final score: {}-{}",
                game.getHomeTeam(), game.getAwayTeam(),
                request.getHomeScore(), request.getAwayScore());

        // Load all PENDING slips that contain a 1X2 selection on this game
        // CORRECT_SCORE slips are excluded — they have their own settlement flow
        List<BetSlip> pendingSlips = betSlipRepository.findPendingSlipsByGameId(
                gameId, BetStatus.PENDING);

        log.info("Found {} PENDING slip(s) to evaluate for game {}", pendingSlips.size(), gameId);

        int winners          = 0;
        int losers           = 0;
        int skipped          = 0;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (BetSlip slip : pendingSlips) {

            // Skip slips that are purely correct score — those are handled elsewhere
            boolean isCorrectScoreOnlySlip = slip.getSelections().stream()
                    .allMatch(s -> s.getMarketType() == MarketType.CORRECT_SCORE);

            if (isCorrectScoreOnlySlip) {
                log.debug("SKIP — slip {} is a correct score slip, handled by CorrectScoreMarketService",
                        slip.getSlipReference());
                skipped++;
                continue;
            }

            log.debug("Evaluating slip: {} | selections: {}",
                    slip.getSlipReference(), slip.getSelections().size());

            SlipVerdict verdict = settleSlipSelections(slip, game, request);

            switch (verdict) {

                case WON -> {
                    Wallet wallet = walletRepository.findByUserId(slip.getUser().getId())
                            .orElse(null);

                    if (wallet == null) {
                        log.error("CRITICAL — Wallet not found for user {} | " +
                                        "slip {} WON but payout NOT credited. Manual intervention required.",
                                slip.getUser().getId(), slip.getSlipReference());
                    } else {
                        wallet.credit(slip.getPotentialPayout());
                        walletRepository.save(wallet);

                        transactionRepository.save(Transaction.builder()
                                .user(slip.getUser())
                                .type(TransactionType.BET_WINNINGS)
                                .status(TransactionStatus.SUCCESS)
                                .amount(slip.getPotentialPayout())
                                .balanceAfter(wallet.getBalance())
                                .betSlipId(slip.getId())
                                .description("Winnings — slip: " + slip.getSlipReference())
                                .build());

                        totalPaid = totalPaid.add(slip.getPotentialPayout());

                        log.info("WINNER — user: {} | slip: {} | payout: {} | new balance: {}",
                                slip.getUser().getEmail(),
                                slip.getSlipReference(),
                                slip.getPotentialPayout(),
                                wallet.getBalance());
                    }

                    slip.setStatus(BetStatus.WON);
                    slip.setActualPayout(slip.getPotentialPayout());
                    slip.setSettledAt(LocalDateTime.now());
                    betSlipRepository.save(slip);
                    winners++;
                }

                case LOST -> {
                    slip.setStatus(BetStatus.LOST);
                    slip.setActualPayout(BigDecimal.ZERO);
                    slip.setSettledAt(LocalDateTime.now());
                    betSlipRepository.save(slip);
                    losers++;

                    log.info("LOSER — user: {} | slip: {}",
                            slip.getUser().getEmail(), slip.getSlipReference());
                }

                case PENDING -> {
                    betSlipRepository.save(slip);
                    skipped++;

                    log.debug("SKIP — slip {} still has PENDING selections on other games",
                            slip.getSlipReference());
                }
            }
        }

        log.info("=== SETTLEMENT COMPLETE === game: {} | slips evaluated: {} | " +
                        "winners: {} | losers: {} | skipped: {} | total paid out: {}",
                gameId, pendingSlips.size(), winners, losers, skipped, totalPaid);

        return SettlementResponse.builder()
                .gameId(gameId.toString())
                .homeTeam(game.getHomeTeam())
                .awayTeam(game.getAwayTeam())
                .homeScore(request.getHomeScore())
                .awayScore(request.getAwayScore())
                .totalSlipsSettled(winners + losers)
                .totalWinners(winners)
                .totalLosers(losers)
                .totalPayoutCredited(totalPaid)
                .build();
    }

    // ---------------------------------------------------------------
    // CANCEL GAME — voids all pending bets and refunds stakes
    // Refunds both 1X2 and correct score bets — no distinction needed
    // ---------------------------------------------------------------
    @Transactional
    public SettlementResponse cancelGame(UUID gameId) {

        log.info("=== CANCELLATION START === gameId: {}", gameId);

        Game game = gameService.getGameOrThrow(gameId);

        log.info("Game found: {} vs {} | current status: {}",
                game.getHomeTeam(), game.getAwayTeam(), game.getStatus());

        if (game.getStatus() == GameStatus.FINISHED) {
            log.warn("Cancellation rejected — game {} is already FINISHED", gameId);
            throw new BadRequestException("Cannot cancel a game that has already been settled");
        }
        if (game.getStatus() == GameStatus.CANCELLED) {
            log.warn("Cancellation rejected — game {} is already CANCELLED", gameId);
            throw new BadRequestException("Game is already cancelled");
        }

        game.setStatus(GameStatus.CANCELLED);
        // Also mark correct score market as settled so it can't be locked/revealed
        game.setCorrectScoreMarketStatus(CorrectScoreMarketStatus.SETTLED);
        gameRepository.save(game);
        log.info("Game {} marked CANCELLED", gameId);

        List<BetSlip> pendingSlips = betSlipRepository.findPendingSlipsByGameId(
                gameId, BetStatus.PENDING);

        log.info("Found {} PENDING slip(s) to refund for cancelled game {}", pendingSlips.size(), gameId);

        BigDecimal totalRefunded = BigDecimal.ZERO;
        int refunded             = 0;
        int failedRefunds        = 0;

        for (BetSlip slip : pendingSlips) {

            Wallet wallet = walletRepository.findByUserId(slip.getUser().getId()).orElse(null);

            if (wallet == null) {
                log.error("CRITICAL — Wallet not found for user {} | " +
                                "slip {} stake of {} NOT refunded. Manual intervention required.",
                        slip.getUser().getId(), slip.getSlipReference(), slip.getStake());
                failedRefunds++;

                slip.setStatus(BetStatus.CANCELLED);
                slip.setSettledAt(LocalDateTime.now());
                betSlipRepository.save(slip);
                continue;
            }

            wallet.credit(slip.getStake());
            walletRepository.save(wallet);

            transactionRepository.save(Transaction.builder()
                    .user(slip.getUser())
                    .type(TransactionType.BET_REFUND)
                    .status(TransactionStatus.SUCCESS)
                    .amount(slip.getStake())
                    .balanceAfter(wallet.getBalance())
                    .betSlipId(slip.getId())
                    .description("Stake refund — cancelled game | slip: " + slip.getSlipReference())
                    .build());

            totalRefunded = totalRefunded.add(slip.getStake());
            refunded++;

            log.info("REFUND — user: {} | slip: {} | stake refunded: {} | new balance: {}",
                    slip.getUser().getEmail(),
                    slip.getSlipReference(),
                    slip.getStake(),
                    wallet.getBalance());

            slip.setStatus(BetStatus.CANCELLED);
            slip.setSettledAt(LocalDateTime.now());
            betSlipRepository.save(slip);
        }

        if (failedRefunds > 0) {
            log.error("=== CANCELLATION COMPLETE WITH ERRORS === game: {} | " +
                            "refunded: {} | FAILED refunds: {} | total refunded: {}",
                    gameId, refunded, failedRefunds, totalRefunded);
        } else {
            log.info("=== CANCELLATION COMPLETE === game: {} | slips refunded: {} | total refunded: {}",
                    gameId, refunded, totalRefunded);
        }

        return SettlementResponse.builder()
                .gameId(gameId.toString())
                .homeTeam(game.getHomeTeam())
                .awayTeam(game.getAwayTeam())
                .homeScore(0)
                .awayScore(0)
                .totalSlipsSettled(pendingSlips.size())
                .totalWinners(0)
                .totalLosers(0)
                .totalPayoutCredited(totalRefunded)
                .build();
    }

    // ---------------------------------------------------------------
    // PRIVATE — settle each 1X2 selection in a slip for the given game
    // CORRECT_SCORE selections are skipped — they don't belong here
    // ---------------------------------------------------------------
    private SlipVerdict settleSlipSelections(BetSlip slip, Game game, EnterResultRequest result) {

        int home  = result.getHomeScore();
        int away  = result.getAwayScore();

        log.debug("Settling selections for slip {} against game {} (score {}-{})",
                slip.getSlipReference(), game.getId(), home, away);

        for (BetSelection selection : slip.getSelections()) {

            if (!selection.getGame().getId().equals(game.getId())) continue;

            // Correct score selections on this game are settled via their own service
            if (selection.getMarketType() == MarketType.CORRECT_SCORE) {
                log.debug("Skipping CORRECT_SCORE selection {} on slip {} — handled separately",
                        selection.getId(), slip.getSlipReference());
                continue;
            }

            boolean won = evaluateSelection(selection.getMarketType(), home, away);
            selection.setSelectionStatus(won ? BetStatus.WON : BetStatus.LOST);

            log.debug("Selection {} | market: {} | result: {}",
                    selection.getId(),
                    selection.getMarketType(),
                    won ? "WON" : "LOST");
        }

        // Determine overall slip verdict across ALL selections
        boolean anyLost    = slip.getSelections().stream()
                .anyMatch(s -> s.getSelectionStatus() == BetStatus.LOST);

        boolean anyPending = slip.getSelections().stream()
                .anyMatch(s -> s.getSelectionStatus() == BetStatus.PENDING);

        if (anyLost) {
            log.debug("Slip {} verdict: LOST", slip.getSlipReference());
            return SlipVerdict.LOST;
        }
        if (anyPending) {
            log.debug("Slip {} verdict: PENDING (waiting on other games)", slip.getSlipReference());
            return SlipVerdict.PENDING;
        }

        log.debug("Slip {} verdict: WON", slip.getSlipReference());
        return SlipVerdict.WON;
    }

    // Pure 1X2 evaluation — over/under removed alongside MarketType cleanup
    private boolean evaluateSelection(MarketType market, int home, int away) {
        return switch (market) {
            case HOME_WIN -> home > away;
            case DRAW     -> home == away;
            case AWAY_WIN -> away > home;
            // CORRECT_SCORE never reaches here — filtered above
            default -> {
                log.warn("Unexpected market type {} in 1X2 settlement — skipping", market);
                yield false;
            }
        };
    }

    private enum SlipVerdict {
        WON,
        LOST,
        PENDING
    }
}