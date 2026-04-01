package com.kikiBettingWebBack.KikiWebSite.services;

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
import java.util.HexFormat;
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

    @Value("${app.paystack.secret-key}")          // ← was webhook-secret, now secret-key
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
        int amountInSmallestUnit = request.getAmount().multiply(BigDecimal.valueOf(100)).intValue();

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

        if (!payload.contains("\"charge.success\"")) {
            log.info("Paystack webhook received — event not charge.success, ignoring");
            return;
        }

        String reference = extractFromJson(payload, "reference");
        if (reference == null) {
            log.warn("Webhook payload missing reference — skipping");
            return;
        }

        // Idempotency check — same pattern as PaystackService
        Transaction tx = transactionRepository.findByPaymentReference(reference).orElse(null);
        if (tx == null) {
            log.warn("Webhook received for unknown reference: {} — skipping", reference);
            return;
        }

        if (tx.getStatus() == TransactionStatus.SUCCESS) {
            log.info("Webhook for ref {} already processed — skipping (idempotent)", reference);
            return;
        }

        Wallet wallet = walletRepository.findByUserId(tx.getUser().getId())
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

        Wallet wallet = walletRepository.findByUserId(userId)
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

        Transaction tx = Transaction.builder()
                .user(user)
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.SUCCESS)
                .amount(requestedAmount)
                .balanceAfter(wallet.getBalance())
                .paymentReference(reference)
                .description(String.format("Withdrawal — fee: %s %.2f | received: %s %.2f",
                        currency, fee, currency, amountReceived))
                .build();

        transactionRepository.save(tx);
        initiatePaystackTransfer(amountReceived, currency, request, reference, user.getEmail());

        log.info("Withdrawal processed — user: {} | amount: {} | fee: {} | received: {}",
                user.getEmail(), requestedAmount, fee, amountReceived);

        return WithdrawResponse.builder()
                .reference(reference)
                .requestedAmount(requestedAmount)
                .feeDeducted(fee)
                .amountReceived(amountReceived)
                .balanceAfter(wallet.getBalance())
                .currency(currency)
                .status("SUCCESS")
                .message(String.format("Withdrawal successful. %s %.2f will be sent to your account.",
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

    private void initiatePaystackTransfer(BigDecimal amount, String currency,
                                          WithdrawRequest request, String reference, String email) {
        try {
            int amountInSmallestUnit = amount.multiply(BigDecimal.valueOf(100)).intValue();

            Map<String, Object> body = Map.of(
                    "source",    "balance",
                    "amount",    amountInSmallestUnit,
                    "currency",  currency,
                    "reference", reference,
                    "recipient", Map.of(
                            "type",           "mobile_money",
                            "name",           request.getAccountName(),
                            "account_number", request.getAccountNumber(),
                            "bank_code",      request.getBankCode(),
                            "currency",       currency
                    ),
                    "reason", "Betting platform withdrawal"
            );

            paystackWebClient.post()
                    .uri("/transfer")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .subscribe(
                            response -> log.info("Paystack transfer initiated for {}", email),
                            error    -> log.error("Paystack transfer failed for {} — ref: {}", email, reference)
                    );

        } catch (Exception e) {
            log.error("Paystack transfer initiation failed for ref: {} — manual review needed", reference, e);
        }
    }

    private boolean isValidSignature(String payload, String paystackSignature) {
        try {
            // ← Now uses paystackSecretKey directly, matching PaystackService exactly
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA512");
            mac.init(new javax.crypto.spec.SecretKeySpec(
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

    private String extractFromJson(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
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