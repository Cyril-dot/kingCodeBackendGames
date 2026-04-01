package com.kikiBettingWebBack.KikiWebSite.repos;

import com.kikiBettingWebBack.KikiWebSite.entities.Game;
import com.kikiBettingWebBack.KikiWebSite.entities.GameStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GameRepository extends JpaRepository<Game, UUID> {

    List<Game> findByStatusOrderByMatchDateAsc(GameStatus status);

    List<Game> findAllByOrderByMatchDateAsc();

    Optional<Game> findByBookingCode(String bookingCode);

    boolean existsByBookingCode(String bookingCode);

    // ✅ Added — used by GameService.getUpcomingGames() to filter out
    //    past-dated games that were never settled by the admin
    List<Game> findByStatusAndMatchDateAfterOrderByMatchDateAsc(
            GameStatus status, LocalDateTime after);

    // ✅ Added — used by GameService.resolveBookingCode() on update
    //    to check if another game (not this one) already holds the code
    boolean existsByBookingCodeAndIdNot(String bookingCode, UUID id);
}