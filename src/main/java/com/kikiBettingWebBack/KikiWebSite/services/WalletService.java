package com.kikiBettingWebBack.KikiWebSite.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kikiBettingWebBack.KikiWebSite.dtos.DepositInitiateRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.DepositInitiateResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.TransactionResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.WithdrawRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.WithdrawResponse;
import com.kikiBettingWebBack.KikiWebSite.entities.Transaction;
import com.kikiBettingWebBack.KikiWebSite.entities.TransactionStatus;
import com.kikiBettingWebBack.KikiWebSite.entities.TransactionType;
import com.kikiBettingWebBack.KikiWebSite.entities.User;
import com.kikiBettingWebBack.KikiWebSite.entities.Wallet;
import com.kikiBettingWebBack.KikiWebSite.exceptions.BadRequestException;
import com.kikiBettingWebBack.KikiWebSite.exceptions.ResourceNotFoundException;
import com.kikiBettingWebBack.KikiWebSite.repos.TransactionRepository;
import com.kikiBettingWebBack.KikiWebSite.repos.UserRepository;
import com.kikiBettingWebBack.KikiWebSite.repos.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final WebClient paystackWebClient;
    // FIX #1: inject Jackson ObjectMapper instead of hand-rolling JSON parsing
    private final ObjectMapper objectMapper;

    @Value("${app.paystack.secret-key}")
    private String paystackSecretKey;

    @Value("${app.paystack.callback-url}")
    private String callbackUrl;

    @Value("${app.rules.withdrawal-fee-percent}")
    private BigDecimal withdrawalFeePercent;

    @Value("${app.rules.min-deposit}")
    private BigDecimal minDeposit;

    @Value("${app.rules.min-withdraw-balance}")
    private BigDecimal minWithdrawBalance;

    // ---------------------------------------------------------------
    // INITIATE DEPOSIT — creates Paystack payment link
    // ---------------------------------------------------------------
    @Transactional
    public DepositInitiateResponse initiateDeposit(UUID userId, DepositInitiateRequest request) {

        User user = getUser(userId);
        String currency = user.getCurrency();

        if (request.getAmount().compareTo(minDeposit) < 0) {
            throw new BadRequestException(
                    String.format("Minimum deposit is %s %.2f", currency, minDeposit));
        }

        String internalRef = "DEP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        // FIX #5: use Math.toIntExact() to catch overflow instead of silent intValue()
        int amountInSmallestUnit = Math.toIntExact(
                request.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact());

        Map<String, Object> paystackBody = Map.of(
                "email",        user.getEmail(),
                "amount",       amountInSmallestUnit,
                "currency",     currency,
                "reference",    internalRef,
                "callback_url", callbackUrl,
                "metadata", Map.of(
                        "userId",      userId.toString(),
                        "currency",    currency,
                        "internalRef", internalRef
                )
        );

        Map<?, ?> paystackResponse = paystackWebClient.post()
                .uri("/transaction/initialize")
                .bodyValue(paystackBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (paystackResponse == null || !(Boolean) paystackResponse.get("status")) {
            throw new BadRequestException("Failed to initiate payment. Please try again.");
        }

        Map<?, ?> data = (Map<?, ?>) paystackResponse.get("data");
        String authorizationUrl = (String) data.get("authorization_url");
        String paystackRef      = (String) data.get("reference");

        Transaction pendingTx = Transaction.builder()
                .user(user)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .amount(request.getAmount())
                .paymentReference(internalRef)
                .description("Deposit via Paystack — " + currency)
                .build();

        transactionRepository.save(pendingTx);
        log.info("Deposit initiated for user {} — ref: {}", user.getEmail(), internalRef);

        return DepositInitiateResponse.builder()
                .paymentUrl(authorizationUrl)
                .reference(internalRef)
                .paystackReference(paystackRef)
                .currency(currency)
                .amountToDeposit(currency + " " + request.getAmount().setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    // ---------------------------------------------------------------
    // HANDLE PAYSTACK WEBHOOK — verifies signature and credits wallet
    // ---------------------------------------------------------------
    @Transactional
    public void handlePaystackWebhook(String payload, String paystackSignature) {

        if (!isValidSignature(payload, paystackSignature)) {
            log.warn("Invalid Paystack webhook signature — rejected");
            throw new BadRequestException("Invalid webhook signature");
        }

        // FIX #1: parse webhook payload with Jackson instead of fragile string search
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.warn("Webhook payload is not valid JSON — skipping");
            return;
        }

        String event = root.path("event").asText(null);
        if (!"charge.success".equals(event)) {
            log.info("Paystack webhook received — event '{}' ignored", event);
            return;
        }

        String reference = root.path("data").path("reference").asText(null);
        if (reference == null || reference.isBlank()) {
            log.warn("Webhook payload missing reference — skipping");
            return;
        }

        // Idempotency check
        Transaction tx = transactionRepository.findByPaymentReference(reference).orElse(null);
        if (tx == null) {
            log.warn("Webhook received for unknown reference: {} — skipping", reference);
            return;
        }

        if (tx.getStatus() == TransactionStatus.SUCCESS) {
            log.info("Webhook for ref {} already processed — skipping (idempotent)", reference);
            return;
        }

        // FIX #2: use pessimistic lock to prevent concurrent double-credit race condition
        Wallet wallet = walletRepository.findByUserIdWithLock(tx.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        wallet.credit(tx.getAmount());
        wallet.setTotalDeposited(wallet.getTotalDeposited().add(tx.getAmount()));
        wallet.setHasEverDeposited(true);
        walletRepository.save(wallet);

        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setBalanceAfter(wallet.getBalance());
        transactionRepository.save(tx);

        log.info("Wallet credited — user: {} | amount: {} | balance: {}",
                tx.getUser().getEmail(), tx.getAmount(), wallet.getBalance());
    }

    // ---------------------------------------------------------------
    // WITHDRAW
    // ---------------------------------------------------------------
    @Transactional
    public WithdrawResponse withdraw(UUID userId, WithdrawRequest request) {

        User user = getUser(userId);
        String currency = user.getCurrency();

        // FIX #2: acquire pessimistic write lock before reading balance to prevent
        // concurrent withdrawals from racing past the balance check simultaneously
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        if (!wallet.isHasEverDeposited()) {
            throw new BadRequestException("You must make a deposit before withdrawing");
        }

        if (wallet.getBalance().compareTo(minWithdrawBalance) <= 0) {
            throw new BadRequestException(
                    String.format("Your balance must exceed %s %.2f to withdraw", currency, minWithdrawBalance));
        }

        BigDecimal requestedAmount = request.getAmount();
        BigDecimal fee = requestedAmount
                .multiply(withdrawalFeePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal amountReceived = requestedAmount.subtract(fee);

        BigDecimal balanceAfter = wallet.getBalance().subtract(requestedAmount);
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Insufficient balance for this withdrawal amount");
        }

        wallet.debit(requestedAmount);
        wallet.setTotalWithdrawn(wallet.getTotalWithdrawn().add(requestedAmount));
        walletRepository.save(wallet);

        String reference = "WDR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        // FIX #3: save transaction as PENDING — only mark SUCCESS after Paystack confirms.
        // Previously the tx was saved as SUCCESS before Paystack accepted the transfer,
        // so a silent failure left the user debited with no money sent.
        Transaction tx = Transaction.builder()
                .user(user)
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.PENDING)           // ← was SUCCESS
                .amount(requestedAmount)
                .balanceAfter(wallet.getBalance())
                .paymentReference(reference)
                .description(String.format("Withdrawal — fee: %s %.2f | received: %s %.2f",
                        currency, fee, currency, amountReceived))
                .build();

        transactionRepository.save(tx);

        // FIX #4: create recipient first, then transfer using the returned recipient_code.
        // Previously the code sent inline recipient details directly to /transfer, which
        // Paystack does not support — /transfer requires a pre-created recipient_code.
        initiatePaystackTransfer(amountReceived, currency, request, reference, user.getEmail(), tx);

        log.info("Withdrawal processed — user: {} | amount: {} | fee: {} | received: {}",
                user.getEmail(), requestedAmount, fee, amountReceived);

        return WithdrawResponse.builder()
                .reference(reference)
                .requestedAmount(requestedAmount)
                .feeDeducted(fee)
                .amountReceived(amountReceived)
                .balanceAfter(wallet.getBalance())
                .currency(currency)
                .status("PENDING")                           // ← was SUCCESS; reflects actual state
                .message(String.format("Withdrawal initiated. %s %.2f will be sent to your account once confirmed.",
                        currency, amountReceived))
                .build();
    }

    // ---------------------------------------------------------------
    // GET TRANSACTION HISTORY
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionHistory(UUID userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toTransactionResponse)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // PRIVATE HELPERS
    // ---------------------------------------------------------------

    /**
     * FIX #4: two-step Paystack transfer.
     *  Step 1 — POST /transferrecipient  → get recipient_code
     *  Step 2 — POST /transfer           → send money using that code
     * On success, mark the transaction SUCCESS. On failure, log for manual review.
     */
    private void initiatePaystackTransfer(BigDecimal amount, String currency,
                                          WithdrawRequest request, String reference,
                                          String email, Transaction tx) {
        try {
            // FIX #5: safe conversion — throws ArithmeticException on overflow
            int amountInSmallestUnit = Math.toIntExact(
                    amount.multiply(BigDecimal.valueOf(100)).longValueExact());

            // Step 1: create recipient
            Map<String, Object> recipientBody = Map.of(
                    "type",           "mobile_money",
                    "name",           request.getAccountName(),
                    "account_number", request.getAccountNumber(),
                    "bank_code",      request.getBankCode(),
                    "currency",       currency
            );

            Map<?, ?> recipientResponse = paystackWebClient.post()
                    .uri("/transferrecipient")
                    .bodyValue(recipientBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (recipientResponse == null || !(Boolean) recipientResponse.get("status")) {
                log.error("Recipient creation failed for {} — ref: {} — manual review needed", email, reference);
                return;
            }

            Map<?, ?> recipientData = (Map<?, ?>) recipientResponse.get("data");
            String recipientCode = (String) recipientData.get("recipient_code");

            // Step 2: initiate transfer using recipient_code
            Map<String, Object> transferBody = Map.of(
                    "source",    "balance",
                    "amount",    amountInSmallestUnit,
                    "currency",  currency,
                    "reference", reference,
                    "recipient", recipientCode,           // ← correct field: just the code string
                    "reason",    "Betting platform withdrawal"
            );

            paystackWebClient.post()
                    .uri("/transfer")
                    .bodyValue(transferBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .subscribe(
                            response -> {
                                boolean ok = response != null && Boolean.TRUE.equals(response.get("status"));
                                if (ok) {
                                    // FIX #3: only mark SUCCESS once Paystack confirms acceptance
                                    tx.setStatus(TransactionStatus.SUCCESS);
                                    transactionRepository.save(tx);
                                    log.info("Paystack transfer accepted for {} — ref: {}", email, reference);
                                } else {
                                    log.error("Paystack transfer rejected for {} — ref: {} — manual review needed",
                                            email, reference);
                                }
                            },
                            error -> log.error("Paystack transfer error for {} — ref: {} — manual review needed",
                                    email, reference, error)
                    );

        } catch (Exception e) {
            log.error("Paystack transfer initiation failed for ref: {} — manual review needed", reference, e);
        }
    }

    private boolean isValidSignature(String payload, String paystackSignature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(
                    paystackSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));

            byte[] hashBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexHash = new StringBuilder();
            for (byte b : hashBytes) hexHash.append(String.format("%02x", b));

            boolean valid = hexHash.toString().equals(paystackSignature);
            if (!valid) {
                log.warn("HMAC mismatch — computed: {}, received: {}", hexHash, paystackSignature);
            }
            return valid;
        } catch (Exception e) {
            log.error("Signature validation error: {}", e.getMessage());
            return false;
        }
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private TransactionResponse toTransactionResponse(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .type(tx.getType())
                .status(tx.getStatus())
                .amount(tx.getAmount())
                .balanceAfter(tx.getBalanceAfter())
                .description(tx.getDescription())
                .paymentReference(tx.getPaymentReference())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}