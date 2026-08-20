package com.finflow.chapter180.correct;

import com.finflow.chapter180.domain.MerchantWallet;
import com.finflow.chapter180.repository.MerchantWalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PessimisticWalletService {

    private final MerchantWalletRepository walletRepository;

    public PessimisticWalletService(MerchantWalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    /**
     * Single-wallet debit using Pessimistic Write Lock (SELECT FOR UPDATE).
     */
    @Transactional
    public MerchantWallet debitPessimistic(String merchantId, BigDecimal amount) {
        MerchantWallet wallet = walletRepository.findByMerchantIdWithPessimisticWriteLock(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + merchantId));

        wallet.debit(amount);
        return walletRepository.save(wallet);
    }

    /**
     * Deadlock-Free Multi-Wallet Transfer using CANONICAL RESOURCE ORDERING.
     * Always acquires locks in lexicographical order (firstId < secondId)
     * regardless of whether it is a debit or credit direction.
     */
    @Transactional
    public void transferDeadlockFree(String fromMerchantId, String toMerchantId, BigDecimal amount) {
        if (fromMerchantId.equals(toMerchantId)) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet: " + fromMerchantId);
        }

        // Canonical ordering: determine which ID comes first alphabetically
        boolean fromIsFirst = fromMerchantId.compareTo(toMerchantId) < 0;
        String firstId = fromIsFirst ? fromMerchantId : toMerchantId;
        String secondId = fromIsFirst ? toMerchantId : fromMerchantId;

        // Step 1: Acquire lock on the 1st wallet in global order
        MerchantWallet firstWallet = walletRepository.findByMerchantIdWithPessimisticWriteLock(firstId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + firstId));

        // Step 2: Acquire lock on the 2nd wallet in global order
        MerchantWallet secondWallet = walletRepository.findByMerchantIdWithPessimisticWriteLock(secondId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + secondId));

        MerchantWallet fromWallet = fromIsFirst ? firstWallet : secondWallet;
        MerchantWallet toWallet = fromIsFirst ? secondWallet : firstWallet;

        fromWallet.debit(amount);
        toWallet.credit(amount);

        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);
    }
}
