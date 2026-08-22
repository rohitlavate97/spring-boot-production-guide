package com.finflow.troubleshooting.module20;

import com.finflow.troubleshooting.module20.model.PaymentEvent;
import com.finflow.troubleshooting.module20.service.KafkaDiagnosticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PoisonPillRecoveryTest {

    private KafkaDiagnosticsService diagnosticsService;

    @BeforeEach
    void setUp() {
        diagnosticsService = new KafkaDiagnosticsService();
    }

    @Test
    @DisplayName("Should successfully route poison pills to DLT and continue consuming valid payments")
    void testPoisonPillRecoveryWithoutPartitionStarvation() {
        // 1. Produce valid payment 1
        diagnosticsService.producePayment(PaymentEvent.of("TXN-001", "ACC-100", 150.0, "USD"));

        // 2. Produce corrupted poison pill (malformed JSON)
        diagnosticsService.producePoisonPill("{\"invalid_json_corrupted\": ###BAD_BYTES###}");

        // 3. Produce valid payment 2
        diagnosticsService.producePayment(PaymentEvent.of("TXN-002", "ACC-200", 250.0, "USD"));

        // Verify poison pill was intercepted and routed to DLT
        assertThat(diagnosticsService.getDiagnosticsStats().get("poisonPillsEncountered")).isEqualTo(1L);
        assertThat(diagnosticsService.getDiagnosticsStats().get("dltRoutedCount")).isEqualTo(1L);
        assertThat(diagnosticsService.getDeadLetterRecords()).hasSize(1);
        assertThat(diagnosticsService.getDeadLetterRecords().get(0).topic()).isEqualTo("payment-events.DLT");

        // Verify valid messages 1 and 2 can still be consumed normally without partition stall!
        PaymentEvent c1 = diagnosticsService.consumeNextPayment();
        assertThat(c1).isNotNull();
        assertThat(c1.transactionId()).isEqualTo("TXN-001");

        PaymentEvent c2 = diagnosticsService.consumeNextPayment();
        assertThat(c2).isNotNull();
        assertThat(c2.transactionId()).isEqualTo("TXN-002");
    }

    @Test
    @DisplayName("Should track consumer lag accurately as messages are produced and consumed")
    void testConsumerLagTracking() {
        assertThat(diagnosticsService.getCurrentConsumerLag()).isEqualTo(0);

        diagnosticsService.producePayment(PaymentEvent.of("TXN-101", "ACC-1", 10.0, "USD"));
        diagnosticsService.producePayment(PaymentEvent.of("TXN-102", "ACC-2", 20.0, "USD"));
        assertThat(diagnosticsService.getCurrentConsumerLag()).isEqualTo(2);

        diagnosticsService.consumeNextPayment();
        assertThat(diagnosticsService.getCurrentConsumerLag()).isEqualTo(1);

        diagnosticsService.consumeNextPayment();
        assertThat(diagnosticsService.getCurrentConsumerLag()).isEqualTo(0);
    }
}
