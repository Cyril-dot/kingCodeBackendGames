package com.kikiBettingWebBack.KikiWebSite.repos;

import com.kikiBettingWebBack.KikiWebSite.entities.Transaction;
import com.kikiBettingWebBack.KikiWebSite.entities.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Transaction> findByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, TransactionType type);
    Optional<Transaction> findByPaymentReference(String reference);
    boolean existsByPaymentReference(String reference);
}