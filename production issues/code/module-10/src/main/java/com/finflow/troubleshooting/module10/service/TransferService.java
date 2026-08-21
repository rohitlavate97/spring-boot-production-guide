package com.finflow.troubleshooting.module10.service;

import com.finflow.troubleshooting.module10.entity.LedgerAccountEntity;
import com.finflow.troubleshooting.module10.repository.LedgerAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final LedgerAccountRepository accountRepository;

    public TransferService(LedgerAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    // ❌ ANTI-PATTERN: Locking accounts in arbitrary argument order (triggers circular deadlocks)
    @Transactional
    public void transferUnordered(Long fromId, Long toId, BigDecimal amount, long artificialDelayMs) {
        LedgerAccountEntity from = accountRepository.findByIdForUpdate(fromId)
                .orElseThrow(() -> new IllegalArgumentException("From account not found: " + fromId));

        if (artificialDelayMs > 0) {
            try {
                Thread.sleep(artificialDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        LedgerAccountEntity to = accountRepository.findByIdForUpdate(toId)
                .orElseThrow(() -> new IllegalArgumentException("To account not found: " + toId));

        from.debit(amount);
        to.credit(amount);
        accountRepository.save(from);
        accountRepository.save(to);
    }

    // ✅ SOLUTION A: Deterministic Lock Ordering (Sorted by ID: min ID locked first)
    @Transactional
    public void transferDeterministic(Long fromId, Long toId, BigDecimal amount) {
        Long firstLockId = Math.min(fromId, toId);
        Long secondLockId = Math.max(fromId, toId);

        LedgerAccountEntity firstLocked = accountRepository.findByIdForUpdate(firstLockId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + firstLockId));
        LedgerAccountEntity secondLocked = accountRepository.findByIdForUpdate(secondLockId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + secondLockId));

        LedgerAccountEntity from = (firstLockId.equals(fromId)) ? firstLocked : secondLocked;
        LedgerAccountEntity to = (firstLockId.equals(toId)) ? firstLocked : secondLocked;

        from.debit(amount);
        to.credit(amount);
        accountRepository.save(from);
        accountRepository.save(to);
    }

    // ✅ SOLUTION B: Optimistic Locking with Automatic Spring Retry
    @Transactional
    @Retryable(
            retryFor = {ObjectOptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 2.0)
    )
    public void transferWithOptimisticLockAndRetry(Long fromId, Long toId, BigDecimal amount) {
        LedgerAccountEntity from = accountRepository.findById(fromId)
                .orElseThrow(() -> new IllegalArgumentException("From account not found: " + fromId));
        LedgerAccountEntity to = accountRepository.findById(toId)
                .orElseThrow(() -> new IllegalArgumentException("To account not found: " + toId));

        from.debit(amount);
        to.credit(amount);
        accountRepository.save(from);
        accountRepository.save(to);
    }
}
