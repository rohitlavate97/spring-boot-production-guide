package com.finflow.chapter210.correct;

import com.finflow.chapter210.domain.MerchantPayoutProfile;
import com.finflow.chapter210.repository.MerchantPayoutProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Expand-Contract (Parallel Run) Service Pattern:
 * Phase 1 & 2: Supports dual-write to new and legacy columns,
 * with resilient read-fallback to legacy data during rolling deployments.
 */
@Service
public class ExpandContractPayoutService {

    private final MerchantPayoutProfileRepository repository;

    public ExpandContractPayoutService(MerchantPayoutProfileRepository repository) {
        this.repository = repository;
    }

    /**
     * Dual-Write: Writes to both new column (iban) and legacy column (legacy_bank_account)
     * so older pods running concurrently during rolling updates do not see nulls or break.
     */
    @Transactional
    public MerchantPayoutProfile registerPayoutProfile(String merchantId, String currency, String accountNumber, String swiftCode) {
        MerchantPayoutProfile profile = new MerchantPayoutProfile(
                UUID.randomUUID().toString(),
                merchantId,
                currency,
                accountNumber, // Dual-write legacy column
                accountNumber, // Dual-write new column
                swiftCode,
                "ACTIVE",
                Instant.now()
        );
        return repository.save(profile);
    }

    /**
     * Read Fallback: Read new column (iban); if null (record created before migration), fall back to legacy_bank_account.
     */
    @Transactional(readOnly = true)
    public String resolveEffectiveAccountNumber(String merchantId) {
        MerchantPayoutProfile profile = repository.findByMerchantId(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + merchantId));

        if (profile.getIban() != null && !profile.getIban().isBlank()) {
            return profile.getIban();
        }
        return profile.getLegacyBankAccount();
    }
}
