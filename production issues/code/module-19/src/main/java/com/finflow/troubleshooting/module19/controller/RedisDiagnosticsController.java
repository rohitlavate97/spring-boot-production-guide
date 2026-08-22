package com.finflow.troubleshooting.module19.controller;

import com.finflow.troubleshooting.module19.service.CacheStampedeGuardService;
import com.finflow.troubleshooting.module19.service.SimulatedDatabaseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/v1/cache")
public class RedisDiagnosticsController {

    private final CacheStampedeGuardService cacheGuard;
    private final SimulatedDatabaseService databaseService;

    public RedisDiagnosticsController(CacheStampedeGuardService cacheGuard,
                                      SimulatedDatabaseService databaseService) {
        this.cacheGuard = cacheGuard;
        this.databaseService = databaseService;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>(cacheGuard.getCacheStats());
        stats.put("totalPostgresDbQueries", databaseService.getDbQueryCount());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/exchange-rate")
    public ResponseEntity<Map<String, Object>> getExchangeRate(
            @RequestParam(defaultValue = "USD_EUR") String pair,
            @RequestParam(defaultValue = "20") long dbLatencyMs
    ) {
        String cacheKey = "fx:" + pair;
        Double rate = cacheGuard.getOrComputeWithMutex(cacheKey, () ->
                databaseService.queryExchangeRateFromDb(pair, dbLatencyMs), 300);

        return ResponseEntity.ok(Map.of(
                "pair", pair,
                "exchangeRate", rate != null ? rate : 0.0,
                "cached", rate != null
        ));
    }

    @GetMapping("/account")
    public ResponseEntity<Map<String, Object>> getAccount(
            @RequestParam String accountId,
            @RequestParam(defaultValue = "15") long dbLatencyMs
    ) {
        String cacheKey = "account:" + accountId;
        Map<String, Object> account = cacheGuard.getOrComputeWithMutex(cacheKey, () ->
                databaseService.queryAccountFromDb(accountId, dbLatencyMs), 300);

        if (account == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "NOT_FOUND",
                    "accountId", accountId,
                    "message", "Account not found (cached as NULL to prevent cache penetration)"
            ));
        }
        return ResponseEntity.ok(account);
    }

    @PostMapping("/simulate-stampede")
    public ResponseEntity<Map<String, Object>> simulateStampede(
            @RequestParam(defaultValue = "USD_EUR") String pair,
            @RequestParam(defaultValue = "20") int concurrentRequests,
            @RequestParam(defaultValue = "30") long dbLatencyMs,
            @RequestParam(defaultValue = "true") boolean useMutexGuard
    ) throws Exception {
        String cacheKey = "fx:" + pair;
        cacheGuard.expireKey(cacheKey); // Force cache miss for all threads
        long initialDbHits = databaseService.getDbQueryCount();

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        List<Callable<Double>> tasks = new ArrayList<>();

        for (int i = 0; i < concurrentRequests; i++) {
            tasks.add(() -> {
                if (useMutexGuard) {
                    return cacheGuard.getOrComputeWithMutex(cacheKey, () ->
                            databaseService.queryExchangeRateFromDb(pair, dbLatencyMs), 300);
                } else {
                    // Unprotected: Direct DB hit on miss
                    return databaseService.queryExchangeRateFromDb(pair, dbLatencyMs);
                }
            });
        }

        long startTime = System.currentTimeMillis();
        List<Future<Double>> futures = executor.invokeAll(tasks);
        for (Future<Double> f : futures) {
            f.get();
        }
        long durationMs = System.currentTimeMillis() - startTime;
        executor.shutdown();

        long finalDbHits = databaseService.getDbQueryCount();
        long totalDbHitsIncurred = finalDbHits - initialDbHits;

        return ResponseEntity.ok(Map.of(
                "pair", pair,
                "concurrentRequests", concurrentRequests,
                "useMutexGuard", useMutexGuard,
                "totalDbHitsIncurred", totalDbHitsIncurred,
                "dbQueriesSaved", Math.max(0, concurrentRequests - totalDbHitsIncurred),
                "durationMs", durationMs,
                "outcome", useMutexGuard && totalDbHitsIncurred == 1 ? "STAMPEDE_PREVENTED" : "MULTIPLE_DB_HITS"
        ));
    }

    @PostMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearCache() {
        cacheGuard.clearCache();
        databaseService.resetDbQueryCount();
        return ResponseEntity.ok(Map.of("status", "CLEARED"));
    }
}
