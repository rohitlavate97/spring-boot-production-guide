package com.finflow.chapter190.incorrect;

import com.finflow.chapter190.domain.PaymentConnectionRecord;
import com.finflow.chapter190.repository.PaymentConnectionRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Explicit connection leak: Borrows JDBC Connection from DataSource and never calls close().
 * 2. Apparent connection leak: Holds @Transactional boundary open across slow blocking I/O (3,000ms),
 *    triggering HikariCP's leak-detection-threshold warning.
 */
@Service
public class ConnectionLeakServiceIncorrect {

    private final DataSource dataSource;
    private final PaymentConnectionRecordRepository repository;

    public ConnectionLeakServiceIncorrect(DataSource dataSource, PaymentConnectionRecordRepository repository) {
        this.dataSource = dataSource;
        this.repository = repository;
    }

    /**
     * Anti-Pattern 1: Unclosed Connection.
     * Drains the connection pool permanently until exhaustion.
     */
    public void executeUnclosedConnectionLeak() throws SQLException {
        // Obtains connection from HikariCP pool and intentionally does NOT close it!
        Connection conn = dataSource.getConnection();
        // Missing conn.close() or try-with-resources!
    }

    /**
     * Anti-Pattern 2: Apparent Connection Leak.
     * Holds transactional DB connection for 2,500ms when leak-detection-threshold is 2,000ms.
     * HikariCP logs: "Apparent connection leak detected".
     */
    @Transactional
    public PaymentConnectionRecord processWithSimulatedSlowIo(String orderRef, BigDecimal amount) {
        PaymentConnectionRecord record = new PaymentConnectionRecord(
                UUID.randomUUID(),
                orderRef,
                amount,
                "PROCESSING",
                Instant.now()
        );
        repository.save(record);

        try {
            // Simulates slow downstream payment gateway HTTP call while holding DB connection
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        record.setStatus("SUCCESS");
        return repository.save(record);
    }
}
