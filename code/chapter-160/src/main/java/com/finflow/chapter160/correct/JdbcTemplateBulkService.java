package com.finflow.chapter160.correct;

import com.finflow.chapter160.domain.SettlementStatus;
import com.finflow.chapter160.dto.SettlementIngestItem;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HIGH-THROUGHPUT BULK INGESTION SERVICE:
 * Uses Spring JdbcTemplate batchUpdate to bypass Hibernate Persistence Context,
 * dirty checking, and entity lifecycle overhead completely.
 * Directly executes JDBC PreparedStatement.addBatch() / executeBatch().
 */
@Service
public class JdbcTemplateBulkService {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplateBulkService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void ingestBulkViaJdbcTemplate(String batchId, List<SettlementIngestItem> items) {
        String sql = "INSERT INTO settlement_records " +
                     "(id, batch_id, merchant_code, transaction_ref, gross_amount, fee_amount, net_amount, currency, status, processed_at, version) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Instant now = Instant.now();

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                SettlementIngestItem item = items.get(i);
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, batchId);
                ps.setString(3, item.merchantCode());
                ps.setString(4, item.transactionRef());
                ps.setBigDecimal(5, item.grossAmount());
                ps.setBigDecimal(6, item.feeAmount());
                ps.setBigDecimal(7, item.netAmount());
                ps.setString(8, item.currency());
                ps.setString(9, SettlementStatus.PENDING.name());
                ps.setTimestamp(10, Timestamp.from(now));
                ps.setLong(11, 0L);
            }

            @Override
            public int getBatchSize() {
                return items.size();
            }
        });
    }
}
