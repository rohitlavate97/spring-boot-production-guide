package com.finflow.chapter190.unit;

import com.finflow.chapter190.Chapter190Application;
import com.finflow.chapter190.correct.HikariPoolMonitoringService;
import com.finflow.chapter190.correct.LeakFreePaymentService;
import com.finflow.chapter190.domain.PaymentConnectionRecord;
import com.finflow.chapter190.dto.PoolMetricsSnapshot;
import com.finflow.chapter190.repository.PaymentConnectionRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter190Application.class)
public class LeakFreeExecutionTest {

    @Autowired
    private LeakFreePaymentService paymentService;

    @Autowired
    private PaymentConnectionRecordRepository repository;

    @Autowired
    private HikariPoolMonitoringService monitoringService;

    @BeforeEach
    public void setup() {
        repository.deleteAll();
    }

    @Test
    public void testLeakFreeTransactional_releasesConnectionImmediately() {
        PaymentConnectionRecord record = paymentService.recordPaymentInitiation("ORDER-LEAK-FREE-1", BigDecimal.valueOf(100.00));

        assertThat(record).isNotNull();
        assertThat(repository.findByOrderRef("ORDER-LEAK-FREE-1")).isPresent();

        // Verify that after method execution, active connections return to 0 (pool has 10 idle connections)
        PoolMetricsSnapshot snapshot = monitoringService.getPoolSnapshot();
        assertThat(snapshot.activeConnections()).isEqualTo(0);
        assertThat(snapshot.idleConnections()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void testDirectJdbcWithTryWithResources_releasesConnectionImmediately() throws SQLException {
        paymentService.executeSafeDirectJdbc("ORDER-DIRECT-JDBC-1", BigDecimal.valueOf(250.00));

        assertThat(repository.findByOrderRef("ORDER-DIRECT-JDBC-1")).isPresent();

        PoolMetricsSnapshot snapshot = monitoringService.getPoolSnapshot();
        assertThat(snapshot.activeConnections()).isEqualTo(0);
    }
}
