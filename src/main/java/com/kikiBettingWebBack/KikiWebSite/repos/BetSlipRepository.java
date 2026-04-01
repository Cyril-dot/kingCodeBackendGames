package com.kikiBettingWebBack.KikiWebSite.repos;

import com.kikiBettingWebBack.KikiWebSite.entities.BetSlip;
import com.kikiBettingWebBack.KikiWebSite.entities.BetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BetSlipRepository extends JpaRepository<BetSlip, UUID> {

    List<BetSlip> findByUserIdOrderByPlacedAtDesc(UUID userId);

    List<BetSlip> findByUserIdAndStatusOrderByPlacedAtDesc(UUID userId, BetStatus status);

    Optional<BetSlip> findBySlipReference(String slipReference);

    // Find all slips with a given status that contain a selection for a given game.
    // Used by SettlementService to locate slips to settle or cancel when a game finishes.
    @Query("""
            SELECT DISTINCT bs FROM BetSlip bs
            JOIN bs.selections sel
            WHERE sel.game.id = :gameId
            AND bs.status = :status
            """)
    List<BetSlip> findPendingSlipsByGameId(
            @Param("gameId") UUID gameId,
            @Param("status") BetStatus status
    );
}