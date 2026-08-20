package com.finflow.chapter270.correct;

import com.finflow.chapter270.config.RabbitMqConfig;
import com.finflow.chapter270.domain.PayoutCommand;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DeadLetterQueueConsumerService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueueConsumerService.class);

    private final List<PayoutCommand> deadLetteredPayouts = Collections.synchronizedList(new ArrayList<>());

    @RabbitListener(
            queues = RabbitMqConfig.DEAD_LETTER_QUEUE,
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void processDeadLetter(
            PayoutCommand command,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.warn("AUDIT DLQ: Received dead-lettered payout command: id={}, merchantId={}, amount={}",
                command.getPayoutId(), command.getMerchantId(), command.getAmount());

        deadLetteredPayouts.add(command);

        // Acknowledge processing from DLQ so it doesn't accumulate indefinitely
        channel.basicAck(deliveryTag, false);
    }

    public List<PayoutCommand> getDeadLetteredPayouts() {
        return Collections.unmodifiableList(deadLetteredPayouts);
    }

    public void clear() {
        deadLetteredPayouts.clear();
    }
}
