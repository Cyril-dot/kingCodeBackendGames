package com.kikiBettingWebBack.KikiWebSite.entities;

public enum CorrectScoreMarketStatus {
    OPEN,      // Bets accepted
    LOCKED,    // Admin locked — no new bets, score still hidden
    SETTLED    // Admin revealed final score — bets paid out
}