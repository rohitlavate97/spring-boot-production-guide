package com.finflow.troubleshooting.module07.service;

import com.finflow.troubleshooting.module07.entity.AccountEntity;
import com.finflow.troubleshooting.module07.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class BankingTransactionService {

    private static final Logger log = LoggerFactory.getLogger(BankingTransactionService.class);

    private final AccountRepository accountRepository;
    private final AuditLogService auditLogService;

    public BankingTransactionService(AccountRepository accountRepository, AuditLogService auditLogService) {
        this.accountRepository = accountRepository;
        this.auditLogService = auditLogService;
    }

    // ❌ BUGGY: Swallowing exception in try-catch prevents Spring from rolling back the transaction!
    @Transactional
    public void transferWithSwallowedExceptionBug(String fromId, String toId, BigDecimal amount) {
        AccountEntity from = getAccount(fromId);
        from.setBalance(from.getBalance().subtract(amount));
        accountRepository.save(from);

        try {
            // Simulates downstream failure
            throw new RuntimeException("Simulated downstream credit card network failure");
        } catch (RuntimeException ex) {
            log.error("[BUGGY] Exception caught and swallowed in try-catch: {}", ex.getMessage());
            // Swallowing the exception without rethrowing or setting rollBackOnly()
            // causes the debit above to COMMIT dirty data!
        }
    }

    // ❌ BUGGY: Standard @Transactional does NOT roll back on checked Exception by default!
    @Transactional
    public void transferWithCheckedExceptionBug(String fromId, String toId, BigDecimal amount) throws Exception {
        AccountEntity from = getAccount(fromId);
        from.setBalance(from.getBalance().subtract(amount));
        accountRepository.save(from);

        // Checked exception thrown without rollbackFor = Exception.class
        throw new Exception("Checked exception thrown during payment settlement");
    }

    // ✅ FIXED: Throws RuntimeException or specifies rollbackFor = Exception.class
    @Transactional(rollbackFor = Exception.class)
    public void transferWithProperRollback(String fromId, String toId, BigDecimal amount) {
        AccountEntity from = getAccount(fromId);
        from.setBalance(from.getBalance().subtract(amount));
        accountRepository.save(from);

        AccountEntity to = getAccount(toId);
        to.setBalance(to.getBalance().add(amount));
        accountRepository.save(to);

        throw new IllegalStateException("Simulated core ledger post-transfer failure -> must rollback!");
    }

    // ✅ FIXED (REQUIRES_NEW): Outer transaction rolls back, but audit log commits independently
    @Transactional(rollbackFor = Exception.class)
    public void transferWithRequiresNewAudit(String fromId, String toId, BigDecimal amount, boolean failOuter) {
        auditLogService.recordAuditLog("TRANSFER_ATTEMPT", "From: " + fromId + " to " + toId + ", Amount: " + amount);

        AccountEntity from = getAccount(fromId);
        from.setBalance(from.getBalance().subtract(amount));
        accountRepository.save(from);

        AccountEntity to = getAccount(toId);
        to.setBalance(to.getBalance().add(amount));
        accountRepository.save(to);

        if (failOuter) {
            throw new IllegalStateException("Outer transaction aborted -> audit record must still exist!");
        }
    }

    private AccountEntity getAccount(String accountId) {
        return accountRepository.findByAccountId(accountId)
                .orElseGet(() -> accountRepository.save(new AccountEntity(accountId, new BigDecimal("1000.00"))));
    }
}
