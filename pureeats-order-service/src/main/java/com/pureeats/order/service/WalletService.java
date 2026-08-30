package com.pureeats.order.service;

import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.Transaction;
import com.pureeats.domain.entity.Wallet;
import com.pureeats.order.dto.AdminTransactionResponse;
import com.pureeats.order.dto.AdminWalletResponse;
import com.pureeats.order.dto.AdminWalletTransactionResponse;
import com.pureeats.order.dto.WalletAdjustRequest;
import com.pureeats.order.dto.WalletBalanceResponse;
import com.pureeats.order.dto.WalletTransactionResponse;
import com.pureeats.order.repository.TransactionRepository;
import com.pureeats.order.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A minimal wallet ledger (User-holder only - restaurant settlement uses
 * {@code RestaurantEarning}/{@code RestaurantPayout} directly, see {@link RestaurantPayoutService}).
 * Balances are stored as integer minor units (paise), matching the legacy bavix/laravel-wallet convention.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private static final String USER_HOLDER_TYPE = "App\\User";
    private static final BigDecimal MINOR_UNIT_FACTOR = BigDecimal.valueOf(100);

    public static final String TX_TYPE_DEPOSIT = "deposit";
    public static final String TX_TYPE_WITHDRAW = "withdraw";

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public WalletBalanceResponse getBalance(Long userId) {
        return new WalletBalanceResponse(toDecimal(getOrCreateWallet(userId).getBalance()));
    }

    @Transactional(readOnly = true)
    public List<WalletTransactionResponse> getTransactions(Long userId) {
        Wallet wallet = getOrCreateWallet(userId);
        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream()
                .map(t -> new WalletTransactionResponse(t.getId(), t.getType(), toDecimal(t.getAmount()), t.getMeta(), t.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void credit(Long userId, BigDecimal amount, String meta) {
        Wallet wallet = getOrCreateWallet(userId);
        wallet.setBalance(wallet.getBalance() + toMinorUnits(amount));
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);
        recordTransaction(wallet, TX_TYPE_DEPOSIT, amount, meta);
    }

    @Transactional
    public void debit(Long userId, BigDecimal amount, String meta) {
        Wallet wallet = getOrCreateWallet(userId);
        wallet.setBalance(wallet.getBalance() - toMinorUnits(amount));
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);
        recordTransaction(wallet, TX_TYPE_WITHDRAW, amount, meta);
    }

    /** Admin-facing lookup (or lazy-create) of any user's wallet - unlike {@link #getBalance}, returns the full record. */
    @Transactional
    public AdminWalletResponse getWalletForHolder(Long userId) {
        return toAdminResponse(getOrCreateWallet(userId));
    }

    @Transactional(readOnly = true)
    public List<AdminWalletTransactionResponse> getTransactionsForWallet(Long walletId) {
        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId).stream()
                .map(this::toAdminTransactionResponse).toList();
    }

    /** Admin-initiated credit/debit against a wallet the admin is viewing (identified by walletId, not userId). */
    @Transactional
    public AdminWalletResponse adjust(Long walletId, WalletAdjustRequest request) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
        if ("debit".equalsIgnoreCase(request.type())) {
            debit(wallet.getHolderId(), request.amount(), request.message());
        } else {
            credit(wallet.getHolderId(), request.amount(), request.message());
        }
        return toAdminResponse(getOrCreateWallet(wallet.getHolderId()));
    }

    /** Platform-wide ledger across every wallet - resolves the holder's wallet name per row for display. */
    @Transactional(readOnly = true)
    public PageResponse<AdminTransactionResponse> listAllTransactions(Pageable pageable) {
        Page<Transaction> page = transactionRepository.findAllByOrderByCreatedAtDesc(pageable);
        Map<Long, Wallet> walletsById = new HashMap<>();
        List<AdminTransactionResponse> content = page.getContent().stream().map(t -> {
            Wallet wallet = t.getWalletId() != null
                    ? walletsById.computeIfAbsent(t.getWalletId(), id -> walletRepository.findById(id).orElse(null))
                    : null;
            String type = TX_TYPE_DEPOSIT.equals(t.getType()) ? "credit" : "debit";
            Map<String, Object> meta = t.getMeta() != null ? Map.of("reason", t.getMeta()) : null;
            return new AdminTransactionResponse(t.getId(), t.getPayableType(), t.getPayableId(), t.getWalletId(),
                    type, toDecimal(t.getAmount()), Boolean.TRUE.equals(t.getConfirmed()), meta, t.getUuid(),
                    t.getCreatedAt(), t.getUpdatedAt(), wallet != null ? wallet.getName() : "Unknown wallet",
                    wallet != null ? wallet.getHolderType() : "", wallet != null ? wallet.getHolderId() : null);
        }).toList();
        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private AdminWalletResponse toAdminResponse(Wallet w) {
        return new AdminWalletResponse(w.getId(), w.getHolderType(), w.getHolderId(), w.getName(), w.getSlug(),
                w.getDescription(), toDecimal(w.getBalance()), w.getDecimalPlaces() != null ? w.getDecimalPlaces().intValue() : 2,
                w.getCreatedAt(), w.getUpdatedAt());
    }

    private AdminWalletTransactionResponse toAdminTransactionResponse(Transaction t) {
        String type = TX_TYPE_DEPOSIT.equals(t.getType()) ? "credit" : "debit";
        Map<String, Object> meta = t.getMeta() != null ? Map.of("reason", t.getMeta()) : null;
        return new AdminWalletTransactionResponse(t.getId(), t.getPayableType(), t.getPayableId(), t.getWalletId(),
                type, toDecimal(t.getAmount()), Boolean.TRUE.equals(t.getConfirmed()), meta, t.getUuid(),
                t.getCreatedAt(), t.getUpdatedAt());
    }

    private Wallet getOrCreateWallet(Long userId) {
        return walletRepository.findByHolderTypeAndHolderId(USER_HOLDER_TYPE, userId)
                .orElseGet(() -> {
                    Wallet wallet = new Wallet();
                    wallet.setHolderType(USER_HOLDER_TYPE);
                    wallet.setHolderId(userId);
                    wallet.setName("default");
                    wallet.setSlug("default");
                    wallet.setBalance(0L);
                    wallet.setDecimalPlaces((short) 2);
                    wallet.setCreatedAt(LocalDateTime.now());
                    wallet.setUpdatedAt(LocalDateTime.now());
                    return walletRepository.save(wallet);
                });
    }

    private void recordTransaction(Wallet wallet, String type, BigDecimal amount, String meta) {
        Transaction transaction = new Transaction();
        transaction.setPayableType(USER_HOLDER_TYPE);
        transaction.setPayableId(wallet.getHolderId());
        transaction.setWalletId(wallet.getId());
        transaction.setType(type);
        transaction.setAmount(toMinorUnits(amount));
        transaction.setConfirmed(true);
        transaction.setMeta(meta);
        transaction.setUuid(UUID.randomUUID().toString());
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
    }

    private static long toMinorUnits(BigDecimal amount) {
        return amount.multiply(MINOR_UNIT_FACTOR).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal toDecimal(long minorUnits) {
        return BigDecimal.valueOf(minorUnits).divide(MINOR_UNIT_FACTOR, 2, RoundingMode.HALF_UP);
    }
}
