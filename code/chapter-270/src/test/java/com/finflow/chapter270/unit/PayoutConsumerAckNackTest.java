package com.finflow.chapter270.unit;

import com.finflow.chapter270.correct.PayoutConsumerService;
import com.finflow.chapter270.domain.PayoutCommand;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class PayoutConsumerAckNackTest {

    private PayoutConsumerService consumerService;
    private TestChannelStub channelStub;
    private Channel mockChannel;

    @BeforeEach
    public void setup() {
        consumerService = new PayoutConsumerService();
        channelStub = new TestChannelStub();
        mockChannel = TestChannelStub.createMockChannel(channelStub);
    }

    @Test
    public void testValidPayout_acknowledgesSuccessfully() throws IOException {
        PayoutCommand validPayout = new PayoutCommand(
                "PO-VALID-1",
                "MERCHANT_ACME",
                BigDecimal.valueOf(500.00),
                "USD",
                "INSTANT",
                "US89370400440532013000",
                "PENDING",
                0,
                Instant.now()
        );

        long deliveryTag = 42L;
        consumerService.processPayout(validPayout, mockChannel, deliveryTag);

        assertThat(consumerService.getProcessedCount()).isEqualTo(1);
        assertThat(consumerService.getDeadLetteredCount()).isEqualTo(0);

        // Verifies manual basicAck is called with exact deliveryTag and multiple=false
        assertThat(channelStub.getAckDeliveryTag()).isEqualTo(deliveryTag);
        assertThat(channelStub.isAckMultiple()).isFalse();
    }

    @Test
    public void testPoisonPillPayout_rejectsWithRequeueFalse_routesToDlx() throws IOException {
        PayoutCommand poisonPill = new PayoutCommand(
                "PO-POISON-1",
                "MERCHANT_BETA",
                BigDecimal.valueOf(999.00),
                "EUR",
                "POISON_PILL",
                "INVALID_IBAN",
                "PENDING",
                0,
                Instant.now()
        );

        long deliveryTag = 99L;
        consumerService.processPayout(poisonPill, mockChannel, deliveryTag);

        assertThat(consumerService.getProcessedCount()).isEqualTo(0);
        assertThat(consumerService.getDeadLetteredCount()).isEqualTo(1);

        // Verifies basicNack is called with requeue=false (routes to DLX)
        assertThat(channelStub.getNackDeliveryTag()).isEqualTo(deliveryTag);
        assertThat(channelStub.isNackMultiple()).isFalse();
        assertThat(channelStub.isNackRequeue()).isFalse();
    }
}
