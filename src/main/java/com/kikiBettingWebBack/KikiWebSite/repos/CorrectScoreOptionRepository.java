package com.kikiBettingWebBack.KikiWebSite.repos;

import com.kikiBettingWebBack.KikiWebSite.entities.CorrectScoreOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CorrectScoreOptionRepository extends JpaRepository<CorrectScoreOption, UUID> {

    List<CorrectScoreOption> findByGameIdOrderByHomeScoreAscAwayScoreAsc(UUID gameId);

    void deleteByGameId(UUID gameId);

    Optional<CorrectScoreOption> findByGameIdAndHomeScoreAndAwayScore(
            UUID gameId, Integer homeScore, Integer awayScore);
}