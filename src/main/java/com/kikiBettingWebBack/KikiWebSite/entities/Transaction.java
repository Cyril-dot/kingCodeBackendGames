package com.kikiBettingWebBack.KikiWebSite.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // Balance snapshot after this transaction — useful for audit trail
    @Column(name = "balance_after", precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    // Paystack reference for deposits / withdrawals
    @Column(name = "payment_reference", unique = true)
    private String paymentReference;

    // Human-readable note — e.g. "Bet stake for slip #BET-20240330-001"
    @Column(name = "description")
    private String description;

    // Links back to bet slip if this is a bet-related transaction
    @Column(name = "bet_slip_id")
    private UUID betSlipId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}