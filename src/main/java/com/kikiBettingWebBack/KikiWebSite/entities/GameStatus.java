package com.kikiBettingWebBack.KikiWebSite.entities;

public enum GameStatus {
    UPCOMING,   // Not started yet — bets open
    LIVE,       // Match in progress — bets closed
    FINISHED,   // Result entered — bets settled
    CANCELLED   // Voided — stakes refunded
}