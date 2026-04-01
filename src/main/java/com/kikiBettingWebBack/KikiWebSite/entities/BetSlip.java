package com.kikiBettingWebBack.KikiWebSite.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bet_slips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BetSlip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The amount the user staked on this slip
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal stake;

    // Combined odds = product of all selection odds
    @Column(name = "total_odds", nullable = false, precision = 10, scale = 4)
    private BigDecimal totalOdds;

    // Potential payout = stake × totalOdds (shown before confirmation)
    @Column(name = "potential_payout", nullable = false, precision = 19, scale = 2)
    private BigDecimal potentialPayout;

    // Actual payout — null until settled
    @Column(name = "actual_payout", precision = 19, scale = 2)
    private BigDecimal actualPayout;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BetStatus status = BetStatus.PENDING;

    // Slip reference code — e.g. SLIP-20240330-0001
    @Column(name = "slip_reference", unique = true, nullable = false)
    private String slipReference;

    @OneToMany(mappedBy = "betSlip", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BetSelection> selections = new ArrayList<>();

    @Column(name = "placed_at", nullable = false, updatable = false)
    private LocalDateTime placedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @PrePersist
    protected void onCreate() {
        placedAt = LocalDateTime.now();
    }
}