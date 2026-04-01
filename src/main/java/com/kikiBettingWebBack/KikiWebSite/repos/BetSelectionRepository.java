package com.kikiBettingWebBack.KikiWebSite.repos;

import com.kikiBettingWebBack.KikiWebSite.entities.BetSelection;
import com.kikiBettingWebBack.KikiWebSite.entities.MarketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BetSelectionRepository extends JpaRepository<BetSelection, UUID> {

    List<BetSelection> findByBetSlipId(UUID betSlipId);

    List<BetSelection> findByGameId(UUID gameId);

    boolean existsByGameId(UUID gameId);

    void deleteByGameId(UUID gameId);
    // Used by CorrectScoreMarketService.revealAndSettle() to fetch all
    // correct score selections for a game when settling bets
    List<BetSelection> findByGameIdAndMarketType(UUID gameId, MarketType marketType);
}