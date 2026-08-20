package com.finflow.chapter270.unit;

import com.finflow.chapter270.config.RabbitMqConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.assertj.core.api.Assertions.assertThat;

public class RabbitMqTopologyConfigTest {

    @Test
    public void testRabbitMqTopologyDeclarations_configuredCorrectly() {
        RabbitMqConfig config = new RabbitMqConfig();

        DirectExchange directExchange = config.payoutDirectExchange();
        assertThat(directExchange.getName()).isEqualTo(RabbitMqConfig.DIRECT_EXCHANGE);
        assertThat(directExchange.isDurable()).isTrue();

        TopicExchange topicExchange = config.payoutTopicExchange();
        assertThat(topicExchange.getName()).isEqualTo(RabbitMqConfig.TOPIC_EXCHANGE);

        DirectExchange dlx = config.payoutDeadLetterExchange();
        assertThat(dlx.getName()).isEqualTo(RabbitMqConfig.DEAD_LETTER_EXCHANGE);

        Queue instantQueue = config.payoutInstantQueue();
        assertThat(instantQueue.getName()).isEqualTo(RabbitMqConfig.INSTANT_QUEUE);
        assertThat(instantQueue.getArguments().get("x-dead-letter-exchange")).isEqualTo(RabbitMqConfig.DEAD_LETTER_EXCHANGE);
        assertThat(instantQueue.getArguments().get("x-dead-letter-routing-key")).isEqualTo(RabbitMqConfig.ROUTING_KEY_DEAD_LETTER);
        assertThat(instantQueue.getArguments().get("x-message-ttl")).isEqualTo(60000);

        Queue dlq = config.payoutDeadLetterQueue();
        assertThat(dlq.getName()).isEqualTo(RabbitMqConfig.DEAD_LETTER_QUEUE);

        Binding instantBinding = config.instantBinding(instantQueue, directExchange);
        assertThat(instantBinding.getRoutingKey()).isEqualTo(RabbitMqConfig.ROUTING_KEY_INSTANT);

        Binding dlqBinding = config.dlqBinding(dlq, dlx);
        assertThat(dlqBinding.getRoutingKey()).isEqualTo(RabbitMqConfig.ROUTING_KEY_DEAD_LETTER);
    }
}
