package com.finflow.troubleshooting.module21.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class WalletBalanceService {

    private static final Logger log = LoggerFactory.getLogger(WalletBalanceService.class);

    // In-memory account store with atomic reference for CAS operations
    private final Map<String, AtomicReference<Double>> accounts = new ConcurrentHashMap<>();

    // Plain primitive double for demonstrating check-then-act lost update race conditions
    private final Map<String, Double> unsafeAccounts = new ConcurrentHashMap<>();

    private final DistributedLockService lockService;

    public WalletBalanceService(DistributedLockService lockService) {
        this.lockService = lockService;
        resetAccounts();
    }

    public void resetAccounts() {
        accounts.put("ACC-101", new AtomicReference<>(500.00));
        accounts.put("ACC-102", new AtomicReference<>(500.00));
        accounts.put("ACC-103", new AtomicReference<>(500.00));

        unsafeAccounts.put("ACC-101", 500.00);
        unsafeAccounts.put("ACC-102", 500.00);
        unsafeAccounts.put("ACC-103", 500.00);
    }

    /**
     * ❌ ANTI-PATTERN: Check-Then-Act Concurrency Flaw (Lost Updates & Double-Spending)
     * Reads balance, pauses for simulated DB/network I/O, then updates balance without locking.
     */
    public boolean unsafeDebit(String accountId, double amount, long ioDelayMs) {
        Double currentBalance = unsafeAccounts.get(accountId);
        if (currentBalance == null || currentBalance < amount) {
            return false;
        }

        if (ioDelayMs > 0) {
            try {
                Thread.sleep(ioDelayMs); // Window where concurrent threads read stale balance
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Lost update overwrites concurrent deductions!
        unsafeAccounts.put(accountId, currentBalance - amount);
        log.info("[UNSAFE DEBIT] Deducted ${} from {}. Stored balance: ${}", amount, accountId, unsafeAccounts.get(accountId));
        return true;
    }

    /**
     * ✅ PRODUCTION FIX 1: Atomic Compare-And-Swap (CAS) Update
     * In PostgreSQL / MySQL: UPDATE accounts SET balance = balance - :amount WHERE id = :id AND balance >= :amount
     * In-memory: atomicReference.compareAndSet loop.
     * Guaranteed 0 lost updates, 0 negative balances, lock-free high throughput!
     */
    public boolean atomicCasDebit(String accountId, double amount) {
        AtomicReference<Double> balanceRef = accounts.get(accountId);
        if (balanceRef == null) return false;

        while (true) {
            Double current = balanceRef.get();
            if (current < amount) {
                return false; // Insufficient funds
            }
            Double updated = current - amount;
            if (balanceRef.compareAndSet(current, updated)) {
                log.info("[ATOMIC CAS DEBIT] Successfully deducted ${} from {}. New balance: ${}",
                        amount, accountId, updated);
                return true;
            }
            // CAS failed due to concurrent modification: retry loop seamlessly!
        }
    }

    /**
     * ✅ PRODUCTION FIX 2: Distributed Locking with Safe Lua Release
     */
    public boolean distributedLockDebit(String accountId, double amount, long leaseMs) {
        String lockKey = "lock:wallet:" + accountId;
        String ownerId = UUID.randomUUID().toString();

        boolean acquired = lockService.tryAcquire(lockKey, ownerId, leaseMs);
        if (!acquired) {
            log.warn("[LOCK BUSY] Could not acquire lock for account {}", accountId);
            return false;
        }

        try {
            return atomicCasDebit(accountId, amount);
        } finally {
            lockService.releaseSafely(lockKey, ownerId);
        }
    }

    /**
     * ✅ PRODUCTION FIX 3: Deterministic Lock Ordering (Eliminates AB-BA Deadlocks)
     * Always acquires locks in sorted lexicographical order:
     * min(accA, accB) -> max(accA, accB)
     */
    public boolean transferWithLockOrdering(String fromAcc, String toAcc, double amount, long leaseMs) {
        String firstLock = fromAcc.compareTo(toAcc) < 0 ? "lock:wallet:" + fromAcc : "lock:wallet:" + toAcc;
        String secondLock = fromAcc.compareTo(toAcc) < 0 ? "lock:wallet:" + toAcc : "lock:wallet:" + fromAcc;

        String ownerId = UUID.randomUUID().toString();

        if (!lockService.tryAcquire(firstLock, ownerId, leaseMs)) {
            return false;
        }

        try {
            if (!lockService.tryAcquire(secondLock, ownerId, leaseMs)) {
                return false;
            }

            try {
                boolean debited = atomicCasDebit(fromAcc, amount);
                if (debited) {
                    AtomicReference<Double> toRef = accounts.get(toAcc);
                    if (toRef != null) {
                        toRef.updateAndGet(b -> b + amount);
                    }
                    return true;
                }
                return false;
            } finally {
                lockService.releaseSafely(secondLock, ownerId);
            }
        } finally {
            lockService.releaseSafely(firstLock, ownerId);
        }
    }

    public double getAtomicBalance(String accountId) {
        AtomicReference<Double> ref = accounts.get(accountId);
        return ref != null ? ref.get() : 0.0;
    }

    public double getUnsafeBalance(String accountId) {
        Double bal = unsafeAccounts.get(accountId);
        return bal != null ? bal : 0.0;
    }
}
