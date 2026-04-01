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
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CorrectScoreMarketService {

    private final GameRepository gameRepository;
    private final CorrectScoreOptionRepository correctScoreOptionRepository;
    private final BetSlipRepository betSlipRepository;
    private final BetSelectionRepository betSelectionRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    // ── Seeded random for reproducible test scenarios ─────────────
    private final Random random = new Random();

    // ================================================================
    // ADMIN — Generate / regenerate score options for a game
    // Can only be done while market is OPEN
    // ================================================================
    @Transactional
    public List<CorrectScoreOptionResponse> generateOptions(UUID gameId) {

        Game game = getGameOrThrow(gameId);

        if (game.getCorrectScoreMarketStatus() != CorrectScoreMarketStatus.OPEN) {
            throw new BadRequestException(
                    "Score options can only be regenerated while the market is OPEN");
        }

        // Wipe any previously generated options
        correctScoreOptionRepository.deleteByGameId(gameId);

        List<CorrectScoreOption> options = buildRandomOptions(game);
        correctScoreOptionRepository.saveAll(options);

        log.info("Generated {} correct score options for game {}", options.size(), gameId);
        return toResponseList(options);
    }

    // ================================================================
    // ADMIN — Step 1: Lock market (score hidden, no new bets)
    // ================================================================
    @Transactional
    public GameResponse lockMarket(UUID gameId) {

        Game game = getGameOrThrow(gameId);

        if (game.getCorrectScoreMarketStatus() != CorrectScoreMarketStatus.OPEN) {
            throw new BadRequestException("Market is not OPEN — cannot lock");
        }

        game.setCorrectScoreMarketStatus(CorrectScoreMarketStatus.LOCKED);
        gameRepository.save(game);

        log.info("Correct score market LOCKED for game {}", gameId);
        return toGameResponse(game);
    }

    // ================================================================
    // ADMIN — Step 2: Reveal final score and settle all bets
    // ================================================================
    @Transactional
    public GameResponse revealAndSettle(UUID gameId, RevealCorrectScoreRequest request) {

        Game game = getGameOrThrow(gameId);

        if (game.getCorrectScoreMarketStatus() != CorrectScoreMarketStatus.LOCKED) {
            throw new BadRequestException("Market must be LOCKED before revealing the score");
        }

        // Persist the final score
        game.setHomeScore(request.getHomeScore());
        game.setAwayScore(request.getAwayScore());
        game.setCorrectScoreMarketStatus(CorrectScoreMarketStatus.SETTLED);
        gameRepository.save(game);

        // Find the winning option (if it exists in the generated list)
        Optional<CorrectScoreOption> winningOption =
                correctScoreOptionRepository.findByGameIdAndHomeScoreAndAwayScore(
                        gameId, request.getHomeScore(), request.getAwayScore());

        // Settle all CORRECT_SCORE bet selections for this game
        List<BetSelection> selections = betSelectionRepository
                .findByGameIdAndMarketType(gameId, MarketType.CORRECT_SCORE);

        for (BetSelection sel : selections) {
            boolean won = winningOption.isPresent()
                    && sel.getOddsAtPlacement() != null
                    && isWinningSelection(sel, request.getHomeScore(), request.getAwayScore(), gameId);

            sel.setSelectionStatus(won ? BetStatus.WON : BetStatus.LOST);
            betSelectionRepository.save(sel);

            // Settle the parent slip
            BetSlip slip = sel.getBetSlip();
            if (won) {
                BigDecimal payout = slip.getStake()
                        .multiply(sel.getOddsAtPlacement())
                        .setScale(2, RoundingMode.HALF_UP);

                slip.setActualPayout(payout);
                slip.setStatus(BetStatus.WON);
                slip.setSettledAt(LocalDateTime.now());
                betSlipRepository.save(slip);

                // Credit winnings to wallet
                Wallet wallet = walletRepository.findByUserId(slip.getUser().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
                wallet.credit(payout);
                walletRepository.save(wallet);

                transactionRepository.save(Transaction.builder()
                        .user(slip.getUser())
                        .type(TransactionType.BET_WINNINGS)
                        .status(TransactionStatus.SUCCESS)
                        .amount(payout)
                        .balanceAfter(wallet.getBalance())
                        .betSlipId(slip.getId())
                        .description("Correct score win — slip: " + slip.getSlipReference())
                        .build());

                log.info("Settled WON — slip: {} | payout: {}", slip.getSlipReference(), payout);
            } else {
                slip.setActualPayout(BigDecimal.ZERO);
                slip.setStatus(BetStatus.LOST);
                slip.setSettledAt(LocalDateTime.now());
                betSlipRepository.save(slip);
                log.info("Settled LOST — slip: {}", slip.getSlipReference());
            }
        }

        log.info("Correct score market SETTLED for game {} | final: {}:{} | {} bets settled",
                gameId, request.getHomeScore(), request.getAwayScore(), selections.size());

        return toGameResponse(game);
    }

    // ================================================================
    // USER — View available score options for a game
    // ================================================================
    @Transactional(readOnly = true)
    public CorrectScoreMarketResponse getMarket(UUID gameId) {

        Game game = getGameOrThrow(gameId);
        List<CorrectScoreOption> options =
                correctScoreOptionRepository.findByGameIdOrderByHomeScoreAscAwayScoreAsc(gameId);

        // Only expose the final score after the market is settled
        Integer finalHome = game.getCorrectScoreMarketStatus() == CorrectScoreMarketStatus.SETTLED
                ? game.getHomeScore() : null;
        Integer finalAway = game.getCorrectScoreMarketStatus() == CorrectScoreMarketStatus.SETTLED
                ? game.getAwayScore() : null;

        return CorrectScoreMarketResponse.builder()
                .gameId(game.getId())
                .homeTeam(game.getHomeTeam())
                .awayTeam(game.getAwayTeam())
                .marketStatus(game.getCorrectScoreMarketStatus())
                .finalHomeScore(finalHome)
                .finalAwayScore(finalAway)
                .options(toResponseList(options))
                .build();
    }

    // ================================================================
    // USER — Place a correct score bet
    // ================================================================
    @Transactional
    public BetSlipResponse placeCorrectScoreBet(UUID userId, PlaceCorrectScoreBetRequest request) {

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

        Game game = getGameOrThrow(request.getGameId());

        if (game.getCorrectScoreMarketStatus() != CorrectScoreMarketStatus.OPEN) {
            throw new BadRequestException("This correct score market is no longer open for betting");
        }

        CorrectScoreOption option = correctScoreOptionRepository
                .findById(request.getCorrectScoreOptionId())
                .orElseThrow(() -> new ResourceNotFoundException("Score option not found"));

        if (!option.getGame().getId().equals(game.getId())) {
            throw new BadRequestException("Score option does not belong to this game");
        }

        BigDecimal odds = option.getOdds();
        BigDecimal potentialPayout = request.getStake()
                .multiply(odds)
                .setScale(2, RoundingMode.HALF_UP);

        String slipRef = generateSlipReference();

        BetSlip slip = BetSlip.builder()
                .user(user)
                .stake(request.getStake())
                .totalOdds(odds)
                .potentialPayout(potentialPayout)
                .status(BetStatus.PENDING)
                .slipReference(slipRef)
                .selections(new ArrayList<>())
                .build();

        slip = betSlipRepository.save(slip);

        BetSelection selection = BetSelection.builder()
                .betSlip(slip)
                .game(game)
                .marketType(MarketType.CORRECT_SCORE)
                .oddsAtPlacement(odds)
                .selectionStatus(BetStatus.PENDING)
                // Store chosen score on the selection via a transient label
                // The actual score chosen is traced via correctScoreOptionId below
                .correctScoreOptionId(option.getId())
                .build();

        betSelectionRepository.save(selection);
        slip.getSelections().add(selection);

        wallet.debit(request.getStake());
        walletRepository.save(wallet);

        transactionRepository.save(Transaction.builder()
                .user(user)
                .type(TransactionType.BET_STAKE)
                .status(TransactionStatus.SUCCESS)
                .amount(request.getStake())
                .balanceAfter(wallet.getBalance())
                .betSlipId(slip.getId())
                .description("Correct score bet — slip: " + slipRef
                        + " | pick: " + option.getHomeScore() + ":" + option.getAwayScore())
                .build());

        log.info("Correct score bet placed — user: {} | slip: {} | pick: {}:{} | odds: {}",
                user.getEmail(), slipRef, option.getHomeScore(), option.getAwayScore(), odds);

        return toBetSlipResponse(slip);
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    /**
     * Generates randomised correct score options covering:
     *  0:0 → 4:0 and reverses → mixed combos → wild scores
     * Odds scale inversely with likelihood (low-scoring = lower odds).
     */
    private List<CorrectScoreOption> buildRandomOptions(Game game) {
        List<CorrectScoreOption> options = new ArrayList<>();
        Set<String> used = new HashSet<>();

        // Standard range 0-4 each side
        for (int h = 0; h <= 4; h++) {
            for (int a = 0; a <= 4; a++) {
                addOption(game, h, a, options, used);
            }
        }

        // Wild scores
        int[][] wilds = {{5,0},{0,5},{5,1},{1,5},{5,2},{2,5},{4,3},{3,4},{5,3},{3,5},{6,0},{0,6}};
        for (int[] w : wilds) {
            addOption(game, w[0], w[1], options, used);
        }

        Collections.shuffle(options, random);
        return options;
    }

    private void addOption(Game game, int h, int a,
                           List<CorrectScoreOption> list, Set<String> used) {
        String key = h + ":" + a;
        if (used.contains(key)) return;
        used.add(key);
        list.add(CorrectScoreOption.builder()
                .game(game)
                .homeScore(h)
                .awayScore(a)
                .odds(randomOdds(h, a))
                .build());
    }

    /**
     * Odds logic — lower scoring = lower odds (more likely),
     * high-scoring / unusual = higher odds.
     */
    private BigDecimal randomOdds(int h, int a) {
        int total = h + a;
        double base;
        if (total == 0)       base = 6.0;
        else if (total == 1)  base = 4.5;
        else if (total == 2)  base = 7.0;
        else if (total == 3)  base = 12.0;
        else if (total <= 5)  base = 22.0;
        else                  base = 45.0;

        double jitter = 0.8 + (random.nextDouble() * 0.4); // ±20%
        double raw = base * jitter;
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * A selection wins if the option the user picked matches the final score.
     * We look up the option by its stored ID on the selection.
     */
    private boolean isWinningSelection(BetSelection sel, int finalHome, int finalAway, UUID gameId) {
        if (sel.getCorrectScoreOptionId() == null) return false;
        return correctScoreOptionRepository.findById(sel.getCorrectScoreOptionId())
                .map(opt -> opt.getHomeScore() == finalHome && opt.getAwayScore() == finalAway)
                .orElse(false);
    }

    private Game getGameOrThrow(UUID gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new ResourceNotFoundException("Game not found: " + gameId));
    }

    private List<CorrectScoreOptionResponse> toResponseList(List<CorrectScoreOption> options) {
        return options.stream()
                .map(o -> CorrectScoreOptionResponse.builder()
                        .id(o.getId())
                        .homeScore(o.getHomeScore())
                        .awayScore(o.getAwayScore())
                        .odds(o.getOdds())
                        .build())
                .collect(Collectors.toList());
    }

    private String generateSlipReference() {
        String datePart = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "CS-" + datePart + "-" + rand;
    }

    private GameResponse toGameResponse(Game game) {
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

    BetSlipResponse toBetSlipResponse(BetSlip slip) {
        List<BetSlipResponse.SelectionDetail> details = slip.getSelections().stream()
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
                .selections(details)
                .build();
    }


    // ================================================================
// USER — Get my correct score bet history
// ================================================================
    @Transactional(readOnly = true)
    public List<BetSlipResponse> getMyCorrectScoreBets(UUID userId) {
        return betSlipRepository.findByUserIdOrderByPlacedAtDesc(userId)
                .stream()
                .filter(slip -> slip.getSelections().stream()
                        .anyMatch(sel -> sel.getMarketType() == MarketType.CORRECT_SCORE))
                .map(this::toBetSlipResponse)
                .collect(Collectors.toList());
    }
}