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

    private final Random random = new Random();

    // ================================================================
    // ADMIN — Generate / regenerate score options for a game
    // ================================================================
    @Transactional
    public List<CorrectScoreOptionResponse> generateOptions(UUID gameId) {

        Game game = getGameOrThrow(gameId);

        if (game.getCorrectScoreMarketStatus() != CorrectScoreMarketStatus.OPEN) {
            throw new BadRequestException(
                    "Score options can only be regenerated while the market is OPEN");
        }

        correctScoreOptionRepository.deleteByGameId(gameId);
        List<CorrectScoreOption> options = buildRandomOptions(game);
        correctScoreOptionRepository.saveAll(options);

        log.info("Generated {} correct score options for game {}", options.size(), gameId);
        return toResponseList(options);
    }

    // ================================================================
    // ADMIN — Step 1 (optional): Lock market — no new bets
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
    // ADMIN — Reveal final score and settle all correct score bets.
    // FIX: now works from OPEN or LOCKED — no longer requires LOCKED first.
    // This means the admin's single "Enter Result & Settle" action works
    // without a separate lock step.
    // ================================================================
    @Transactional
    public GameResponse revealAndSettle(UUID gameId, RevealCorrectScoreRequest request) {

        Game game = getGameOrThrow(gameId);

        // Only block if already settled — allow OPEN or LOCKED
        if (game.getCorrectScoreMarketStatus() == CorrectScoreMarketStatus.SETTLED) {
            throw new BadRequestException("This correct score market has already been settled");
        }

        int finalHome = request.getHomeScore();
        int finalAway = request.getAwayScore();

        game.setHomeScore(finalHome);
        game.setAwayScore(finalAway);
        game.setCorrectScoreMarketStatus(CorrectScoreMarketStatus.SETTLED);
        gameRepository.save(game);

        List<BetSelection> selections = betSelectionRepository
                .findByGameIdAndMarketType(gameId, MarketType.CORRECT_SCORE);

        log.info("Found {} correct score selections to settle for game {}", selections.size(), gameId);

        for (BetSelection sel : selections) {

            boolean won = isWinningSelection(sel, finalHome, finalAway);

            sel.setSelectionStatus(won ? BetStatus.WON : BetStatus.LOST);
            betSelectionRepository.save(sel);

            BetSlip slip = sel.getBetSlip();

            if (won) {
                BigDecimal payout = slip.getStake()
                        .multiply(sel.getOddsAtPlacement())
                        .setScale(2, RoundingMode.HALF_UP);

                slip.setActualPayout(payout);
                slip.setStatus(BetStatus.WON);
                slip.setSettledAt(LocalDateTime.now());
                betSlipRepository.save(slip);

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
                gameId, finalHome, finalAway, selections.size());

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

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    /**
     * FIX: Uses .equals() not == for Integer comparison.
     * No winningOption gate — win is determined purely by the stored option.
     */
    private boolean isWinningSelection(BetSelection sel, int finalHome, int finalAway) {
        if (sel.getCorrectScoreOptionId() == null) return false;
        return correctScoreOptionRepository.findById(sel.getCorrectScoreOptionId())
                .map(opt -> opt.getHomeScore().equals(finalHome) && opt.getAwayScore().equals(finalAway))
                .orElse(false);
    }

    private List<CorrectScoreOption> buildRandomOptions(Game game) {
        List<CorrectScoreOption> options = new ArrayList<>();
        Set<String> used = new HashSet<>();

        for (int h = 0; h <= 4; h++) {
            for (int a = 0; a <= 4; a++) {
                addOption(game, h, a, options, used);
            }
        }

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

    private BigDecimal randomOdds(int h, int a) {
        int total = h + a;
        double base;
        if (total == 0)       base = 6.0;
        else if (total == 1)  base = 4.5;
        else if (total == 2)  base = 7.0;
        else if (total == 3)  base = 12.0;
        else if (total <= 5)  base = 22.0;
        else                  base = 45.0;

        double jitter = 0.8 + (random.nextDouble() * 0.4);
        double raw = base * jitter;
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }

   /* private Game getGameOrThrow(UUID gameId) {
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
}*/