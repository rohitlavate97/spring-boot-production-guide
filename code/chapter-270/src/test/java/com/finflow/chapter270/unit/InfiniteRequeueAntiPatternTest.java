package com.finflow.chapter270.unit;

import com.finflow.chapter270.domain.PayoutCommand;
import com.finflow.chapter270.incorrect.PayoutConsumerIncorrect;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class InfiniteRequeueAntiPatternTest {

    private PayoutConsumerIncorrect incorrectConsumer;
    private TestChannelStub channelStub;
    private Channel mockChannel;

    @BeforeEach
    public void setup() {
        incorrectConsumer = new PayoutConsumerIncorrect();
        channelStub = new TestChannelStub();
        mockChannel = TestChannelStub.createMockChannel(channelStub);
    }

    @Test
    public void testIncorrectConsumer_basicNackWithRequeueTrue_demonstratesAntiPattern() throws IOException {
        PayoutCommand poisonPill = new PayoutCommand(
                "PO-POISON-FAIL",
                "MERCHANT_GAMMA",
                BigDecimal.valueOf(10.00),
                "GBP",
                "POISON_PILL",
                "BAD_ROUTING",
                "PENDING",
                0,
                Instant.now()
        );

        long deliveryTag = 123L;
        incorrectConsumer.processWithInfiniteRequeue(poisonPill, mockChannel, deliveryTag);

        assertThat(incorrectConsumer.getRedeliveryLoopCount()).isEqualTo(1);

        // Verifies the anti-pattern: requeue is TRUE, which triggers an immediate broker re-delivery loop!
        assertThat(channelStub.getNackDeliveryTag()).isEqualTo(deliveryTag);
        assertThat(channelStub.isNackMultiple()).isFalse();
        assertThat(channelStub.isNackRequeue()).isTrue();
    }
}
