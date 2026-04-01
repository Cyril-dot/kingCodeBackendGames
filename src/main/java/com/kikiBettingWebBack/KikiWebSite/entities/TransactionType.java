package com.kikiBettingWebBack.KikiWebSite.entities;

public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    BET_STAKE,       // Deducted when bet is placed
    BET_WINNINGS,    // Credited when bet is won
    BET_REFUND       // Credited when bet is cancelled
}