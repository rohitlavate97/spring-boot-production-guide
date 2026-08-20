package com.finflow.chapter270.incorrect;

import com.finflow.chapter270.domain.PayoutCommand;
import com.rabbitmq.client.Channel;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. basicNack with requeue = true on poison pill creates an Infinite Requeue Loop.
 * 2. Unconstrained prefetch causes consumer memory saturation (OOM).
 */
@Service
public class PayoutConsumerIncorrect {

    private final AtomicInteger redeliveryLoopCount = new AtomicInteger(0);

    /**
     * Anti-Pattern: basicNack with requeue=true on fatal exceptions.
     * RabbitMQ re-delivers the failed message immediately to the same consumer in a tight loop!
     */
    public void processWithInfiniteRequeue(
            PayoutCommand command,
            Channel channel,
            long deliveryTag) throws IOException {

        redeliveryLoopCount.incrementAndGet();

        if ("POISON_PILL".equals(command.getPayoutType())) {
            // FATAL ANTI-PATTERN: requeue = true
            // Puts poison pill back at head of queue; re-delivered immediately at 80k+ req/sec!
            channel.basicNack(deliveryTag, false, true);
        } else {
            channel.basicAck(deliveryTag, false);
        }
    }

    public int getRedeliveryLoopCount() {
        return redeliveryLoopCount.get();
    }

    public void reset() {
        redeliveryLoopCount.set(0);
    }
}
