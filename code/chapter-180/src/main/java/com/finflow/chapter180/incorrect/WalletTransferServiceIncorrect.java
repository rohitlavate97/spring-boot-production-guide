package com.finflow.chapter180.incorrect;

import com.finflow.chapter180.domain.MerchantWallet;
import com.finflow.chapter180.domain.UnversionedWallet;
import com.finflow.chapter180.repository.MerchantWalletRepository;
import com.finflow.chapter180.repository.UnversionedWalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Lost Update Problem: Mutates unversioned entity under concurrency.
 * 2. Database Deadlock: Acquires pessimistic locks in non-deterministic order.
 */
@Service
public class WalletTransferServiceIncorrect {

    private final UnversionedWalletRepository unversionedWalletRepository;
    private final MerchantWalletRepository merchantWalletRepository;

    public WalletTransferServiceIncorrect(UnversionedWalletRepository unversionedWalletRepository,
                                         MerchantWalletRepository merchantWalletRepository) {
        this.unversionedWalletRepository = unversionedWalletRepository;
        this.merchantWalletRepository = merchantWalletRepository;
    }

    /**
     * Anti-Pattern 1: Lost Update on unversioned entity.
     * Two concurrent transactions read balance = $1000.
     * Tx1 debits $200 -> writes $800.
     * Tx2 debits $300 -> overwrites with $700!
     * Expected balance: $500. Actual balance: $700 (Lost Update of $200!).
     */
    @Transactional
    public void debitUnversioned(String merchantId, BigDecimal amount) {
        UnversionedWallet wallet = unversionedWalletRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + merchantId));

        wallet.debit(amount);
        unversionedWalletRepository.save(wallet);
    }

    /**
     * Anti-Pattern 2: Deadlock Hazard via Inconsistent Lock Acquisition Order.
     * Thread 1: transfers from A to B (Locks A, then attempts to lock B).
     * Thread 2: transfers from B to A (Locks B, then attempts to lock A).
     * PostgreSQL detects cycle in wait-for graph and aborts one transaction with SQLState 40P01!
     */
    @Transactional
    public void transferWithDeadlockRisk(String fromMerchantId, String toMerchantId, BigDecimal amount) {
        // Lock 1: Lock source wallet
        MerchantWallet fromWallet = merchantWalletRepository.findByMerchantIdWithPessimisticWriteLock(fromMerchantId)
                .orElseThrow(() -> new IllegalArgumentException("Source wallet not found: " + fromMerchantId));

        // Artificial brief delay simulating intermediate processing / external validation
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Lock 2: Lock target wallet
        MerchantWallet toWallet = merchantWalletRepository.findByMerchantIdWithPessimisticWriteLock(toMerchantId)
                .orElseThrow(() -> new IllegalArgumentException("Target wallet not found: " + toMerchantId));

        fromWallet.debit(amount);
        toWallet.credit(amount);
    }
}
