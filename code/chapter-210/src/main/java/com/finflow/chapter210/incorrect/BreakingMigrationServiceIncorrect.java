package com.finflow.chapter210.incorrect;

import com.finflow.chapter210.domain.MerchantPayoutProfile;
import com.finflow.chapter210.repository.MerchantPayoutProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Hard assumption that old/new columns are immediately consistent without Expand-Contract.
 * 2. Unsafe reads failing immediately with NullPointerException or SQL exceptions during rolling deployments.
 */
@Service
public class BreakingMigrationServiceIncorrect {

    private final MerchantPayoutProfileRepository repository;

    public BreakingMigrationServiceIncorrect(MerchantPayoutProfileRepository repository) {
        this.repository = repository;
    }

    /**
     * Anti-Pattern: Hard assumption that new column is always populated immediately after DDL migration.
     * Breaks with NullPointerException on un-backfilled legacy rows during rolling deployment!
     */
    @Transactional(readOnly = true)
    public String getIbanUnsafe(String merchantId) {
        MerchantPayoutProfile profile = repository.findByMerchantId(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + merchantId));

        // CRASH: If record was written before backfill, iban is NULL!
        return profile.getIban().toUpperCase();
    }
}
