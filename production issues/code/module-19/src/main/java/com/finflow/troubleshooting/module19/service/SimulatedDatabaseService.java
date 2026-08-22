package com.finflow.troubleshooting.module19.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SimulatedDatabaseService {

    private static final Logger log = LoggerFactory.getLogger(SimulatedDatabaseService.class);

    private final AtomicLong dbQueryCount = new AtomicLong(0);
    private final Map<String, Double> exchangeRates = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> accounts = new ConcurrentHashMap<>();

    public SimulatedDatabaseService() {
        exchangeRates.put("USD_EUR", 0.9215);
        exchangeRates.put("USD_GBP", 0.7840);
        exchangeRates.put("USD_JPY", 154.60);

        accounts.put("ACC-1001", Map.of("accountId", "ACC-1001", "name", "FinFlow Global Treasury", "balance", 5000000.00));
        accounts.put("ACC-1002", Map.of("accountId", "ACC-1002", "name", "FinFlow Retail Settlement", "balance", 1250000.00));
    }

    public Double queryExchangeRateFromDb(String pair, long simulatedLatencyMs) {
        dbQueryCount.incrementAndGet();
        log.info("[DB HIT] Querying exchange rate for {} from PostgreSQL (Total DB hits: {})", pair, dbQueryCount.get());
        if (simulatedLatencyMs > 0) {
            try {
                Thread.sleep(simulatedLatencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return exchangeRates.get(pair);
    }

    public Map<String, Object> queryAccountFromDb(String accountId, long simulatedLatencyMs) {
        dbQueryCount.incrementAndGet();
        log.info("[DB HIT] Querying account {} from PostgreSQL (Total DB hits: {})", accountId, dbQueryCount.get());
        if (simulatedLatencyMs > 0) {
            try {
                Thread.sleep(simulatedLatencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return accounts.get(accountId); // Returns null if non-existent
    }

    public long getDbQueryCount() {
        return dbQueryCount.get();
    }

    public void resetDbQueryCount() {
        dbQueryCount.set(0);
    }
}
