package com.finflow.troubleshooting.module20.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaResilienceConfig.class);

    /**
     * Production Error Handler with Dead Letter Queue (DLT) Routing:
     * - Retries transient exceptions up to 2 times with 1000ms backoff.
     * - Catches Fatal Deserialization / Poison Pill exceptions and immediately
     *   routes them to .DLT topic without retrying or stalling partition.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception exception) -> {
                    log.error("[DLT RECOVERY] Routing failed record [key={}, partition={}, offset={}] to DLT due to: {}",
                            record.key(), record.partition(), record.offset(), exception.getMessage());
                    return new org.apache.kafka.common.TopicPartition(record.topic() + ".DLT", record.partition());
                });

        // 2 retries with 1-second fixed backoff
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));

        // Mark Deserialization exceptions as un-retryable (immediately route to DLT)
        errorHandler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class,
                org.apache.kafka.common.errors.SerializationException.class,
                ClassCastException.class,
                IllegalArgumentException.class
        );

        return errorHandler;
    }
}
