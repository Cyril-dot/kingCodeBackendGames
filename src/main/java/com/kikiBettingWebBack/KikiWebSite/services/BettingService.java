package com.kikiBettingWebBack.KikiWebSite.services;

import com.kikiBettingWebBack.KikiWebSite.dtos.*;
import com.kikiBettingWebBack.KikiWebSite.entities.*;
import com.kikiBettingWebBack.KikiWebSite.exceptions.*;
import com.kikiBettingWebBack.KikiWebSite.repos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BettingService {

    private final BetSlipRepository betSlipRepository;
    private final BetSelectionRepository betSelectionRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    // ---------------------------------------------------------------
    // PLACE BET — 1X2 accumulator flow only
    // Correct score bets go through CorrectScoreMarketService
    // ---------------------------------------------------------------
    @Transactional
    public BetSlipResponse placeBet(UUID userId, PlaceBetRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        if (!wallet.isHasEverDeposited()) {
            throw new BadRequestException("You must deposit funds before placing a bet");
        }

        if (wallet.getBalance().compareTo(request.getStake()) < 0) {
            throw new BadRequestException(
                    String.format("Insufficient balance. Available: %s %.2f | Required: %.2f",
                            user.getCurrency(),
                            wallet.getBalance(),
                            request.getStake()));
        }

        List<UUID> gameIds = request.getSelections().stream()
                .map(PlaceBetRequest.BetSelectionRequest::getGameId)
                .toList();

        if (gameIds.size() != new HashSet<>(gameIds).size()) {
            throw new BadRequestException("You cannot add the same game more than once on a slip");
        }

        List<BetSelection> selections = new ArrayList<>();
        BigDecimal totalOdds = BigDecimal.ONE;

        for (PlaceBetRequest.BetSelectionRequest sel : request.getSelections()) {

            // Correct score bets are not allowed through this endpoint
            if (sel.getMarketType() == MarketType.CORRECT_SCORE) {
                throw new BadRequestException(
                        "Correct score bets must be placed via /api/correct-score/bet");
            }

            Game game = gameRepository.findById(sel.getGameId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Game not found: " + sel.getGameId()));

            if (game.getStatus() != GameStatus.UPCOMING) {
                throw new BadRequestException(
                        String.format("Game '%s vs %s' is no longer open for betting",
                                game.getHomeTeam(), game.getAwayTeam()));
            }

            BigDecimal odds = resolveOdds(game, sel.getMarketType());

            selections.add(BetSelection.builder()
                    .game(game)
                    .marketType(sel.getMarketType())
                    .oddsAtPlacement(odds)
                    .selectionStatus(BetStatus.PENDING)
                    .build());

            totalOdds = totalOdds.multiply(odds).setScale(4, RoundingMode.HALF_UP);
        }

        BigDecimal potentialPayout = request.getStake()
                .multiply(totalOdds)
                .setScale(2, RoundingMode.HALF_UP);

        String slipRef = generateSlipReference();

        BetSlip slip = BetSlip.builder()
                .user(user)
                .stake(request.getStake())
                .totalOdds(totalOdds)
                .potentialPayout(potentialPayout)
                .status(BetStatus.PENDING)
                .slipReference(slipRef)
                .selections(new ArrayList<>())
                .build();

        slip = betSlipRepository.save(slip);

        final BetSlip savedSlip = slip;
        selections.forEach(s -> s.setBetSlip(savedSlip));
        betSelectionRepository.saveAll(selections);
        slip.setSelections(selections);

        wallet.debit(request.getStake());
        walletRepository.save(wallet);

        transactionRepository.save(Transaction.builder()
                .user(user)
                .type(TransactionType.BET_STAKE)
                .status(TransactionStatus.SUCCESS)
                .amount(request.getStake())
                .balanceAfter(wallet.getBalance())
                .betSlipId(slip.getId())
                .description("Bet stake — slip: " + slipRef)
                .build());

        log.info("Bet placed — user: {} | slip: {} | stake: {} | odds: {} | payout: {}",
                user.getEmail(), slipRef, request.getStake(), totalOdds, potentialPayout);

        return toBetSlipResponse(slip);
    }

    // ---------------------------------------------------------------
    // MY BETS
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<BetSlipResponse> getMyBets(UUID userId) {
        return betSlipRepository.findByUserIdOrderByPlacedAtDesc(userId)
                .stream()
                .map(this::toBetSlipResponse)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // GET SINGLE SLIP BY REFERENCE
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public BetSlipResponse getSlipByReference(String reference) {
        BetSlip slip = betSlipRepository.findBySlipReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Bet slip not found: " + reference));
        return toBetSlipResponse(slip);
    }

    // ---------------------------------------------------------------
    // PRIVATE HELPERS
    // ---------------------------------------------------------------

    private BigDecimal resolveOdds(Game game, MarketType market) {
        return switch (market) {
            case HOME_WIN -> nullCheck(game.getOddsHomeWin(), "Home Win", game);
            case DRAW     -> nullCheck(game.getOddsDraw(),    "Draw",     game);
            case AWAY_WIN -> nullCheck(game.getOddsAwayWin(), "Away Win", game);
            default       -> throw new BadRequestException("Unsupported market: " + market);
        };
    }

    private BigDecimal nullCheck(BigDecimal odds, String marketName, Game game) {
        if (odds == null) {
            throw new BadRequestException(
                    String.format("Market '%s' is not available for game '%s vs %s'",
                            marketName, game.getHomeTeam(), game.getAwayTeam()));
        }
        return odds;
    }

    private String generateSlipReference() {
        String datePart = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "SLIP-" + datePart + "-" + rand;
    }

    BetSlipResponse toBetSlipResponse(BetSlip slip) {
        List<BetSlipResponse.SelectionDetail> selectionDetails = slip.getSelections().stream()
                .map(sel -> BetSlipResponse.SelectionDetail.builder()
                        .gameId(sel.getGame().getId())
                        .homeTeam(sel.getGame().getHomeTeam())
                        .awayTeam(sel.getGame().getAwayTeam())
                        .matchDate(sel.getGame().getMatchDate())
                        .marketType(sel.getMarketType())
                        .oddsAtPlacement(sel.getOddsAtPlacement())
                        .selectionStatus(sel.getSelectionStatus())
                        .build())
                .collect(Collectors.toList());

        return BetSlipResponse.builder()
                .id(slip.getId())
                .slipReference(slip.getSlipReference())
                .stake(slip.getStake())
                .totalOdds(slip.getTotalOdds())
                .potentialPayout(slip.getPotentialPayout())
                .actualPayout(slip.getActualPayout())
                .status(slip.getStatus())
                .placedAt(slip.getPlacedAt())
                .settledAt(slip.getSettledAt())
                .selections(selectionDetails)
                .build();
    }
}