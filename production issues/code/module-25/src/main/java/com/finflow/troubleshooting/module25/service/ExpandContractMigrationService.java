package com.finflow.troubleshooting.module25.service;

import com.finflow.troubleshooting.module25.model.AccountEntity;
import com.finflow.troubleshooting.module25.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ExpandContractMigrationService {

    private static final Logger log = LoggerFactory.getLogger(ExpandContractMigrationService.class);

    private final AccountRepository accountRepository;

    public ExpandContractMigrationService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * ✅ PHASE 1 (EXPAND) - DUAL WRITE:
     * Writes to BOTH the legacy column (account_number) and new column (account_uuid)
     * so both Version N (old) and Version N+1 (new) microservice pods remain 100% functional.
     */
    @Transactional
    public AccountEntity createAccountWithDualWrite(String accountNumber, BigDecimal balance) {
        String generatedUuid = "UUID-" + UUID.randomUUID().toString().substring(0, 8);
        AccountEntity account = new AccountEntity(accountNumber, generatedUuid, balance, "STANDARD");
        AccountEntity saved = accountRepository.save(account);
        log.info("[DUAL WRITE SUCCESS] Created Account ID={} [LegacyNum={}, NewUuid={}]",
                saved.getId(), saved.getAccountNumber(), saved.getAccountUuid());
        return saved;
    }

    /**
     * ✅ PHASE 2 (BACKFILL) - SMALL BATCH DATA MIGRATION:
     * Updates legacy rows in small, non-blocking batches to prevent table-level lock contention.
     */
    @Transactional
    public int backfillBatch(int batchSize) {
        List<AccountEntity> pending = accountRepository.findAccountsNeedingBackfill(PageRequest.of(0, batchSize));
        if (pending.isEmpty()) {
            return 0;
        }

        for (AccountEntity entity : pending) {
            String backfilledUuid = "UUID-BACKFILLED-" + entity.getId();
            entity.setAccountUuid(backfilledUuid);
            accountRepository.save(entity);
            log.info("[BACKFILL MIGRATED] Row ID={} assigned AccountUuid={}", entity.getId(), backfilledUuid);
        }

        return pending.size();
    }

    /**
     * ✅ PHASE 3 (READ COMPATIBILITY):
     * Reads using new identifier with graceful fallback to old identifier if backfill is pending.
     */
    @Transactional(readOnly = true)
    public Optional<AccountEntity> findAccountByIdentifier(String identifier) {
        if (identifier.startsWith("UUID-")) {
            return accountRepository.findByAccountUuid(identifier);
        } else {
            return accountRepository.findByAccountNumber(identifier);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMigrationProgress() {
        long total = accountRepository.count();
        long pending = accountRepository.countByAccountUuidIsNull();
        long migrated = total - pending;
        double percentage = total > 0 ? ((double) migrated / total) * 100.0 : 100.0;

        return Map.of(
                "totalAccounts", total,
                "migratedWithNewColumn", migrated,
                "pendingBackfill", pending,
                "progressPercentage", percentage,
                "phase", pending == 0 ? "PHASE_3_READS_READY_FOR_CONTRACT" : "PHASE_2_BACKFILL_IN_PROGRESS"
        );
    }
}
