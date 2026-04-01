package com.kikiBettingWebBack.KikiWebSite.repos;

import com.kikiBettingWebBack.KikiWebSite.entities.Transaction;
import com.kikiBettingWebBack.KikiWebSite.entities.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Query("SELECT t FROM Transaction t JOIN FETCH t.user WHERE t.user.id = :userId ORDER BY t.createdAt DESC")
    List<Transaction> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    List<Transaction> findByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, TransactionType type);

    Optional<Transaction> findByPaymentReference(String reference);

    boolean existsByPaymentReference(String reference);

    @Query("SELECT t FROM Transaction t JOIN FETCH t.user ORDER BY t.createdAt DESC")
    List<Transaction> findAllByOrderByCreatedAtDesc();
}