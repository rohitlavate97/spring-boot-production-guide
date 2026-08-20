package com.finflow.chapter270.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String DIRECT_EXCHANGE = "payout.direct.exchange";
    public static final String TOPIC_EXCHANGE = "payout.topic.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "payout.dlx";

    public static final String INSTANT_QUEUE = "payout.instant.queue";
    public static final String ACH_QUEUE = "payout.ach.queue";
    public static final String DEAD_LETTER_QUEUE = "payout.dlq";

    public static final String ROUTING_KEY_INSTANT = "payout.instant";
    public static final String ROUTING_KEY_ACH_PATTERN = "payout.ach.#";
    public static final String ROUTING_KEY_DEAD_LETTER = "payout.dead-letter";

    // --- Exchanges ---

    @Bean
    public DirectExchange payoutDirectExchange() {
        return new DirectExchange(DIRECT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange payoutTopicExchange() {
        return new TopicExchange(TOPIC_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange payoutDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    // --- Queues with Dead Letter Routing ---

    @Bean
    public Queue payoutInstantQueue() {
        return QueueBuilder.durable(INSTANT_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DEAD_LETTER)
                .withArgument("x-message-ttl", 60000) // 60s message TTL
                .build();
    }

    @Bean
    public Queue payoutAchQueue() {
        return QueueBuilder.durable(ACH_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue payoutDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    // --- Bindings ---

    @Bean
    public Binding instantBinding(Queue payoutInstantQueue, DirectExchange payoutDirectExchange) {
        return BindingBuilder.bind(payoutInstantQueue).to(payoutDirectExchange).with(ROUTING_KEY_INSTANT);
    }

    @Bean
    public Binding achBinding(Queue payoutAchQueue, TopicExchange payoutTopicExchange) {
        return BindingBuilder.bind(payoutAchQueue).to(payoutTopicExchange).with(ROUTING_KEY_ACH_PATTERN);
    }

    @Bean
    public Binding dlqBinding(Queue payoutDeadLetterQueue, DirectExchange payoutDeadLetterExchange) {
        return BindingBuilder.bind(payoutDeadLetterQueue).to(payoutDeadLetterExchange).with(ROUTING_KEY_DEAD_LETTER);
    }

    // --- Serialization & Container Factory ---

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setMandatory(true); // Guarantees returnsCallback triggers on unroutable messages
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL); // Manual ACK mode
        factory.setPrefetchCount(20); // Prevents consumer memory exhaustion (OOM)
        factory.setDefaultRequeueRejected(false); // Prevents infinite requeue loop
        return factory;
    }
}
