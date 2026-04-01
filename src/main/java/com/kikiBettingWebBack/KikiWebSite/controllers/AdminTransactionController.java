package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.Config.ApiResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.TransactionResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.TransactionResponseDto;
import com.kikiBettingWebBack.KikiWebSite.entities.Transaction;
import com.kikiBettingWebBack.KikiWebSite.entities.TransactionStatus;
import com.kikiBettingWebBack.KikiWebSite.entities.TransactionType;
import com.kikiBettingWebBack.KikiWebSite.repos.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminTransactionController {

    private final TransactionRepository transactionRepository;

    // ---------------------------------------------------------------
    // GET /api/v1/admin/transactions
    // All transactions — optionally filtered by type, status, userId
    // ---------------------------------------------------------------
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<TransactionResponseDto>>> getAllTransactions(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) UUID userId) {

        List<Transaction> transactions = transactionRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                // Hide PENDING deposits — only show once Paystack confirms via webhook
                .filter(tx -> !(tx.getType() == TransactionType.DEPOSIT
                        && tx.getStatus() == TransactionStatus.PENDING))
                .collect(Collectors.toList());

        if (type != null) {
            transactions = transactions.stream()
                    .filter(tx -> tx.getType() == type)
                    .collect(Collectors.toList());
        }
        if (status != null) {
            transactions = transactions.stream()
                    .filter(tx -> tx.getStatus() == status)
                    .collect(Collectors.toList());
        }
        if (userId != null) {
            transactions = transactions.stream()
                    .filter(tx -> tx.getUser().getId().equals(userId))
                    .collect(Collectors.toList());
        }

        List<TransactionResponseDto> response = transactions.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(
                "Fetched " + response.size() + " transactions", response));
    }

    // ---------------------------------------------------------------
    // GET /api/v1/admin/transactions/stats
    // Summary totals for the 4 stat cards on the frontend
    // ---------------------------------------------------------------
    @GetMapping("/transactions/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {

        List<Transaction> all = transactionRepository.findAllByOrderByCreatedAtDesc();

        Map<String, Object> stats = Map.of(
                "totalDeposits",    sumSuccessful(all, TransactionType.DEPOSIT),
                "totalWithdrawals", sumSuccessful(all, TransactionType.WITHDRAWAL),
                "totalBetStakes",   sumSuccessful(all, TransactionType.BET_STAKE),
                "totalWinnings",    sumSuccessful(all, TransactionType.BET_WINNINGS),
                "totalRefunds",     sumSuccessful(all, TransactionType.BET_REFUND),
                "totalCount",       all.size()
        );

        return ResponseEntity.ok(ApiResponse.success("Stats fetched", stats));
    }

    // ---------------------------------------------------------------
    // GET /api/v1/admin/users/{userId}/transactions
    // Transactions for a specific user (linked from users page)
    // ---------------------------------------------------------------
    @GetMapping("/users/{userId}/transactions")
    public ResponseEntity<ApiResponse<List<TransactionResponseDto>>> getUserTransactions(
            @PathVariable UUID userId) {

        List<TransactionResponseDto> response =
                transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                        .stream()
                        .map(this::toResponse)
                        .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(
                "Fetched " + response.size() + " transactions for user", response));
    }

    // ---------------------------------------------------------------
    // PRIVATE HELPERS
    // ---------------------------------------------------------------

    private TransactionResponseDto toResponse(Transaction tx) {
        return TransactionResponseDto.builder()
                .id(tx.getId())
                .type(tx.getType())
                .status(tx.getStatus())
                .amount(tx.getAmount())
                .balanceAfter(tx.getBalanceAfter() != null ? tx.getBalanceAfter() : BigDecimal.ZERO)
                .description(tx.getDescription() != null ? tx.getDescription() : "")
                .paymentReference(tx.getPaymentReference() != null ? tx.getPaymentReference() : "")
                .createdAt(tx.getCreatedAt())
                .userId(tx.getUser().getId())
                // In toResponse() — replace the userFullName line with:
                .userFullName(
                        (tx.getUser().getFirstName() != null ? tx.getUser().getFirstName() : "") +
                                (tx.getUser().getLastName()  != null ? " " + tx.getUser().getLastName() : "")
                )
                .userEmail(tx.getUser().getEmail() != null ? tx.getUser().getEmail() : "")
                .build();
    }

    private BigDecimal sumSuccessful(List<Transaction> all, TransactionType type) {
        return all.stream()
                .filter(tx -> tx.getType() == type && tx.getStatus() == TransactionStatus.SUCCESS)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}