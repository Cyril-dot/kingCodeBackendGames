package com.kikiBettingWebBack.KikiWebSite.entities;

public enum BetStatus {
    PENDING,    // Game not yet settled
    WON,        // All selections correct
    LOST,       // One or more selections wrong
    CANCELLED   // Game cancelled / admin voided
}