package com.finflow.chapter260.unit;

import com.finflow.chapter260.Chapter260Application;
import com.finflow.chapter260.correct.DeadLetterTopicConsumerService;
import com.finflow.chapter260.correct.PaymentEventConsumerService;
import com.finflow.chapter260.correct.PaymentEventProducerService;
import com.finflow.chapter260.domain.PaymentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = Chapter260Application.class, properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(partitions = 3, topics = {"payment.events", "payment.events.DLT"})
@DirtiesContext
public class EmbeddedKafkaPaymentEventTest {

    @Autowired
    private PaymentEventProducerService producerService;

    @Autowired
    private PaymentEventConsumerService consumerService;

    @Autowired
    private DeadLetterTopicConsumerService dltConsumerService;

    @BeforeEach
    public void setup() {
        consumerService.reset();
        dltConsumerService.clear();
    }

    @Test
    public void testPublishAndConsume_success() {
        PaymentEvent event = new PaymentEvent(
                "EVT-KAFKA-1",
                "PAY-KAFKA-1",
                "MERCHANT_ACME",
                BigDecimal.valueOf(250.00),
                "USD",
                "AUTHORIZED",
                Instant.now(),
                "KEY-KAFKA-1"
        );

        producerService.publishPaymentEvent(event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(consumerService.getProcessedCount()).isGreaterThanOrEqualTo(1);
        });
    }

    @Test
    public void testIdempotentDeduplication_underKafkaDelivery() {
        PaymentEvent event = new PaymentEvent(
                "EVT-KAFKA-DUP",
                "PAY-KAFKA-DUP",
                "MERCHANT_BETA",
                BigDecimal.valueOf(50.00),
                "EUR",
                "AUTHORIZED",
                Instant.now(),
                "KEY-DUP-1"
        );

        // Send same event twice
        producerService.publishPaymentEvent(event);
        producerService.publishPaymentEvent(event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(consumerService.getProcessedCount()).isEqualTo(1);
            assertThat(consumerService.getDuplicateCount()).isEqualTo(1);
        });
    }

    @Test
    public void testPoisonPill_routesToDeadLetterTopic() {
        PaymentEvent poisonPill = new PaymentEvent(
                "EVT-POISON-99",
                "PAY-POISON-99",
                "MERCHANT_GAMMA",
                BigDecimal.valueOf(999.99),
                "GBP",
                "POISON_PILL",
                Instant.now(),
                "KEY-POISON"
        );

        producerService.publishPaymentEvent(poisonPill);

        // Verify that after retries, the event is delivered to the DLT topic
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(dltConsumerService.getDltEvents()).hasSize(1);
            assertThat(dltConsumerService.getDltEvents().get(0).getEventId()).isEqualTo("EVT-POISON-99");
        });
    }
}
