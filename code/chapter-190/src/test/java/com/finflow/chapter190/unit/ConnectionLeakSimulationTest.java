package com.finflow.chapter190.unit;

import com.finflow.chapter190.Chapter190Application;
import com.finflow.chapter190.domain.PaymentConnectionRecord;
import com.finflow.chapter190.incorrect.ConnectionLeakServiceIncorrect;
import com.finflow.chapter190.repository.PaymentConnectionRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter190Application.class)
public class ConnectionLeakSimulationTest {

    @Autowired
    private ConnectionLeakServiceIncorrect incorrectService;

    @Autowired
    private PaymentConnectionRecordRepository repository;

    @BeforeEach
    public void setup() {
        repository.deleteAll();
    }

    @Test
    public void testSlowIoInsideTransaction_completesSuccessfully() {
        // Runs method holding connection for 2.5s (triggers HikariCP leak detection threshold of 2.0s in background logs)
        PaymentConnectionRecord record = incorrectService.processWithSimulatedSlowIo("ORDER-SLOW-1", BigDecimal.valueOf(99.00));

        assertThat(record).isNotNull();
        assertThat(record.getStatus()).isEqualTo("SUCCESS");
        assertThat(repository.findByOrderRef("ORDER-SLOW-1")).isPresent();
    }
}
