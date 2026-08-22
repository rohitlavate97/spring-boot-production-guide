package com.finflow.troubleshooting.module21.controller;

import com.finflow.troubleshooting.module21.service.DistributedLockService;
import com.finflow.troubleshooting.module21.service.WalletBalanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/v1/wallet")
public class ConcurrencyDiagnosticsController {

    private final WalletBalanceService walletService;
    private final DistributedLockService lockService;

    public ConcurrencyDiagnosticsController(WalletBalanceService walletService,
                                            DistributedLockService lockService) {
        this.walletService = walletService;
        this.lockService = lockService;
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getBalance(@RequestParam(defaultValue = "ACC-101") String accountId) {
        return ResponseEntity.ok(Map.of(
                "accountId", accountId,
                "atomicCasBalance", walletService.getAtomicBalance(accountId),
                "unsafeBalance", walletService.getUnsafeBalance(accountId)
        ));
    }

    @PostMapping("/simulate-race-condition")
    public ResponseEntity<Map<String, Object>> simulateRaceCondition(
            @RequestParam(defaultValue = "ACC-101") String accountId,
            @RequestParam(defaultValue = "10") int concurrentRequests,
            @RequestParam(defaultValue = "100.00") double debitAmount,
            @RequestParam(defaultValue = "false") boolean useAtomicCas
    ) throws Exception {
        walletService.resetAccounts();
        double initialBalance = 500.00;

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentRequests; i++) {
            tasks.add(() -> {
                boolean success;
                if (useAtomicCas) {
                    success = walletService.atomicCasDebit(accountId, debitAmount);
                } else {
                    success = walletService.unsafeDebit(accountId, debitAmount, 20); // 20ms I/O delay to trigger race
                }
                if (success) successCount.incrementAndGet();
                return success;
            });
        }

        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        for (Future<Boolean> f : futures) {
            f.get();
        }
        executor.shutdown();

        double finalBalance = useAtomicCas ? walletService.getAtomicBalance(accountId) : walletService.getUnsafeBalance(accountId);
        double totalDebited = successCount.get() * debitAmount;

        String resultStatus;
        if (useAtomicCas) {
            resultStatus = (successCount.get() == 5 && finalBalance == 0.00) ? "RACE_PREVENTED_CORRECT" : "UNEXPECTED";
        } else {
            resultStatus = (finalBalance < 0 || totalDebited > initialBalance) ? "LOST_UPDATE_DOUBLE_SPENDING_DETECTED" : "NO_RACE";
        }

        return ResponseEntity.ok(Map.of(
                "accountId", accountId,
                "initialBalance", initialBalance,
                "debitPerRequest", debitAmount,
                "concurrentRequests", concurrentRequests,
                "useAtomicCas", useAtomicCas,
                "successfulDebitsCount", successCount.get(),
                "totalDebitedAmount", totalDebited,
                "finalRemainingBalance", finalBalance,
                "resultStatus", resultStatus
        ));
    }

    @PostMapping("/simulate-lock-release-trap")
    public ResponseEntity<Map<String, Object>> simulateLockReleaseTrap(
            @RequestParam(defaultValue = "true") boolean useSafeLuaRelease
    ) {
        String resourceKey = "lock:wallet:ACC-999";
        String processA_OwnerId = "PROCESS_A_UUID_1111";
        String processB_OwnerId = "PROCESS_B_UUID_2222";

        // 1. Process A acquires 500ms lock
        lockService.tryAcquire(resourceKey, processA_OwnerId, 500);

        // 2. Process A takes too long; lock expires
        lockService.forceExpireLock(resourceKey);

        // 3. Process B acquires the expired lock
        lockService.tryAcquire(resourceKey, processB_OwnerId, 5000);

        // 4. Process A finishes and tries to release lock!
        boolean releaseResult;
        String outcome;

        if (useSafeLuaRelease) {
            releaseResult = lockService.releaseSafely(resourceKey, processA_OwnerId);
            outcome = releaseResult ? "UNEXPECTED" : "LUA_SCRIPT_BLOCKED_UNSAFE_RELEASE_OF_PROCESS_B_LOCK";
        } else {
            releaseResult = lockService.releaseUnsafely(resourceKey, processA_OwnerId);
            outcome = "UNSAFE_DEL_ACCIDENTALLY_DELETED_PROCESS_B_LOCK";
        }

        return ResponseEntity.ok(Map.of(
                "useSafeLuaRelease", useSafeLuaRelease,
                "processA_ReleaseSuccess", releaseResult,
                "outcome", outcome,
                "lockStats", lockService.getLockStats()
        ));
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        walletService.resetAccounts();
        lockService.clear();
        return ResponseEntity.ok(Map.of("status", "RESET_COMPLETE"));
    }

    @GetMapping("/lock-stats")
    public ResponseEntity<Map<String, Object>> getLockStats() {
        return ResponseEntity.ok(lockService.getLockStats());
    }
}
