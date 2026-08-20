package com.finflow.chapter260.correct;

import com.finflow.chapter260.domain.PaymentEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class PaymentEventProducerService {

    public static final String TOPIC_PAYMENT_EVENTS = "payment.events";

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public PaymentEventProducerService(KafkaTemplate<String, PaymentEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes payment event with merchantId as the partition key.
     * Guarantees strict per-merchant sequential ordering across Kafka partitions.
     */
    public CompletableFuture<SendResult<String, PaymentEvent>> publishPaymentEvent(PaymentEvent event) {
        return kafkaTemplate.send(TOPIC_PAYMENT_EVENTS, event.getMerchantId(), event);
    }
}
