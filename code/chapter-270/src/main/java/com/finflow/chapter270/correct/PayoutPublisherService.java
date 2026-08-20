package com.finflow.chapter270.correct;

import com.finflow.chapter270.config.RabbitMqConfig;
import com.finflow.chapter270.domain.PayoutCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PayoutPublisherService {

    private static final Logger log = LoggerFactory.getLogger(PayoutPublisherService.class);

    private final RabbitTemplate rabbitTemplate;

    public PayoutPublisherService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishInstantPayout(PayoutCommand payout) {
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        log.info("Publishing instant payout: id={}, correlationId={}", payout.getPayoutId(), correlationData.getId());

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.DIRECT_EXCHANGE,
                RabbitMqConfig.ROUTING_KEY_INSTANT,
                payout,
                correlationData
        );
    }

    public void publishAchPayout(String subCategory, PayoutCommand payout) {
        String routingKey = "payout.ach." + subCategory;
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        log.info("Publishing ACH payout: id={}, routingKey={}, correlationId={}", payout.getPayoutId(), routingKey, correlationData.getId());

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.TOPIC_EXCHANGE,
                routingKey,
                payout,
                correlationData
        );
    }
}
