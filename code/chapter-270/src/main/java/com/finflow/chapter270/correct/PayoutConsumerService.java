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
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PayoutConsumerService {

    private static final Logger log = LoggerFactory.getLogger(PayoutConsumerService.class);

    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger deadLetteredCount = new AtomicInteger(0);

    @RabbitListener(
            queues = {RabbitMqConfig.INSTANT_QUEUE, RabbitMqConfig.ACH_QUEUE},
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void processPayout(
            PayoutCommand command,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("Received payout command: id={}, type={}, deliveryTag={}",
                command.getPayoutId(), command.getPayoutType(), deliveryTag);

        try {
            // Poison Pill Detection
            if ("POISON_PILL".equals(command.getPayoutType())) {
                log.error("Fatal poison pill encountered for payout id={}. Rejecting with requeue=false -> DLX", command.getPayoutId());
                deadLetteredCount.incrementAndGet();

                // Production Hardening: basicNack with requeue = false -> routes directly to Dead Letter Exchange (payout.dlx)
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            // Normal Business Logic (e.g. Bank Gateway dispatch, ledger balance debit)
            command.setStatus("PROCESSED");
            processedCount.incrementAndGet();

            // Success: Manual Acknowledgment
            channel.basicAck(deliveryTag, false);

        } catch (Exception ex) {
            log.error("Unexpected failure processing payout id={}. Rejecting with requeue=false", command.getPayoutId(), ex);
            deadLetteredCount.incrementAndGet();
            channel.basicNack(deliveryTag, false, false);
        }
    }

    public int getProcessedCount() { return processedCount.get(); }
    public int getDeadLetteredCount() { return deadLetteredCount.get(); }
    public void reset() {
        processedCount.set(0);
        deadLetteredCount.set(0);
    }
}
