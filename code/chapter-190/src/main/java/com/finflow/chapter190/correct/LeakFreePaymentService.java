package com.finflow.chapter190.correct;

import com.finflow.chapter190.domain.PaymentConnectionRecord;
import com.finflow.chapter190.repository.PaymentConnectionRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

@Service
public class LeakFreePaymentService {

    private final DataSource dataSource;
    private final PaymentConnectionRecordRepository repository;

    public LeakFreePaymentService(DataSource dataSource, PaymentConnectionRecordRepository repository) {
        this.dataSource = dataSource;
        this.repository = repository;
    }

    /**
     * Correct Pattern 1: Fast, focused transactional boundary.
     * Database connection is acquired, manipulated, and released in milliseconds.
     */
    @Transactional
    public PaymentConnectionRecord recordPaymentInitiation(String orderRef, BigDecimal amount) {
        PaymentConnectionRecord record = new PaymentConnectionRecord(
                UUID.randomUUID(),
                orderRef,
                amount,
                "INITIATED",
                Instant.now()
        );
        return repository.save(record);
    }

    /**
     * Correct Pattern 2: Safe Direct JDBC Access using Try-With-Resources.
     * Guaranteed connection closure even in the event of exceptions.
     */
    public void executeSafeDirectJdbc(String orderRef, BigDecimal amount) throws SQLException {
        String sql = "INSERT INTO payment_conn_records (id, order_ref, amount, status, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, orderRef);
            ps.setBigDecimal(3, amount);
            ps.setString(4, "SAFE_JDBC");
            ps.setObject(5, Instant.now());
            ps.executeUpdate();
        }
    }
}
