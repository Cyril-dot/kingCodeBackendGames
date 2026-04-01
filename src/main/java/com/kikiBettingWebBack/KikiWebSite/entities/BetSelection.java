package com.kikiBettingWebBack.KikiWebSite.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bet_selections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BetSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bet_slip_id", nullable = false)
    private BetSlip betSlip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    // Which market the user picked
    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", nullable = false)
    private MarketType marketType;

    // The odds at the time of placing (locked in)
    @Column(name = "odds_at_placement", nullable = false, precision = 10, scale = 2)
    private BigDecimal oddsAtPlacement;

    // Outcome of this individual selection — null until game is settled
    @Enumerated(EnumType.STRING)
    @Column(name = "selection_status")
    private BetStatus selectionStatus;

    // For CORRECT_SCORE bets — stores which score option the user picked
    // Used during settlement to match against the final score
    @Column(name = "correct_score_option_id")
    private UUID correctScoreOptionId;
}