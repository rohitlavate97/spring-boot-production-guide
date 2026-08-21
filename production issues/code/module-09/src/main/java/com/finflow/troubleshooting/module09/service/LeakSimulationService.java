package com.finflow.troubleshooting.module09.service;

import com.finflow.troubleshooting.module09.entity.SettlementEntity;
import com.finflow.troubleshooting.module09.repository.PaymentSettlementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

@Service
public class LeakSimulationService {

    private static final Logger log = LoggerFactory.getLogger(LeakSimulationService.class);

    private final PaymentSettlementRepository repository;
    private final DataSource dataSource;

    public LeakSimulationService(PaymentSettlementRepository repository, DataSource dataSource) {
        this.repository = repository;
        this.dataSource = dataSource;
    }

    // ❌ ANTI-PATTERN: Holding a database transaction/connection while making a slow external network call
    @Transactional
    public String settlePaymentHoldingConnection(BigDecimal amount, long simulatedExternalCallDurationMs) {
        String txnId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        repository.save(new SettlementEntity(txnId, amount, "PENDING_EXTERNAL"));

        if (simulatedExternalCallDurationMs > 0) {
            log.warn("[ANTI-PATTERN] Holding DB connection while waiting on external API ({}ms)...", simulatedExternalCallDurationMs);
            try {
                Thread.sleep(simulatedExternalCallDurationMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        SettlementEntity entity = repository.findByTransactionId(txnId).orElseThrow();
        entity.setStatus("SETTLED");
        repository.save(entity);
        return txnId;
    }

    // ❌ ANTI-PATTERN: Raw JDBC connection borrowed without closing (triggers HikariCP leak detection)
    public void simulateUnclosedRawJdbcConnection() throws SQLException {
        log.warn("[ANTI-PATTERN] Borrowing raw JDBC connection without closing it...");
        // Deliberately do not close connection to trigger leak-detection-threshold
        Connection conn = dataSource.getConnection();
        conn.isValid(1);
    }
}
