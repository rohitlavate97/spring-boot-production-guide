package com.finflow.chapter260.unit;

import com.finflow.chapter260.correct.PaymentEventConsumerService;
import com.finflow.chapter260.domain.PaymentEvent;
import com.finflow.chapter260.incorrect.PaymentEventConsumerIncorrect;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

public class IdempotentConsumerLogicTest {

    @Test
    public void testConsumerDeduplication_preventsDoubleProcessing() {
        PaymentEventConsumerService correctConsumer = new PaymentEventConsumerService();
        PaymentEventConsumerIncorrect incorrectConsumer = new PaymentEventConsumerIncorrect();

        PaymentEvent event = new PaymentEvent(
                "EVT-IDEMP-1",
                "PAY-500",
                "MERCHANT_ACME",
                BigDecimal.valueOf(100.00),
                "USD",
                "AUTHORIZED",
                Instant.now(),
                "KEY-123"
        );

        AtomicBoolean ackCalled = new AtomicBoolean(false);
        Acknowledgment mockAck = () -> ackCalled.set(true);

        // First delivery
        correctConsumer.consume(event, mockAck);
        incorrectConsumer.consumeWithoutIdempotency(event);

        assertThat(correctConsumer.getProcessedCount()).isEqualTo(1);
        assertThat(correctConsumer.getDuplicateCount()).isEqualTo(0);
        assertThat(incorrectConsumer.getDoubleBillingCount()).isEqualTo(1);

        // Replay delivery of same event
        correctConsumer.consume(event, mockAck);
        incorrectConsumer.consumeWithoutIdempotency(event);

        // Correct consumer deduplicated: processed count remains 1, duplicate count = 1
        assertThat(correctConsumer.getProcessedCount()).isEqualTo(1);
        assertThat(correctConsumer.getDuplicateCount()).isEqualTo(1);

        // Incorrect consumer suffered double billing: count = 2
        assertThat(incorrectConsumer.getDoubleBillingCount()).isEqualTo(2);
    }
}
