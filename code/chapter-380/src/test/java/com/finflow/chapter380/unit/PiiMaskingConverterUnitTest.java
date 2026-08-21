package com.finflow.chapter380.unit;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.finflow.chapter380.logging.PiiMaskingConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

public class PiiMaskingConverterUnitTest {

    private PiiMaskingConverter converter;
    private ILoggingEvent mockEvent;

    @BeforeEach
    void setUp() {
        converter = new PiiMaskingConverter();
        mockEvent = Mockito.mock(ILoggingEvent.class);
    }

    @Test
    void testMaskHyphenatedCreditCardNumber() {
        String input = "Processing payment for card 4111-2222-3333-4444 for order ORD-1";
        // PiiMaskingConverter extends CompositeConverter and its transform method is protected,
        // or we can test it directly or via reflection/subclass
        String output = new TestPiiMaskingConverter().testTransform(mockEvent, input);

        assertThat(output).isEqualTo("Processing payment for card 4111-****-****-4444 for order ORD-1");
    }

    @Test
    void testMaskContinuousCreditCardNumber() {
        String input = "Customer entered PAN: 5500112233449988 for authentication";
        String output = new TestPiiMaskingConverter().testTransform(mockEvent, input);

        assertThat(output).isEqualTo("Customer entered PAN: 5500-****-****-9988 for authentication");
    }

    @Test
    void testRedactApiKeySecret() {
        String input = "Failed upstream call with api_key=sk_live_secretkey9988776655";
        String output = new TestPiiMaskingConverter().testTransform(mockEvent, input);

        assertThat(output).contains("api_key=***REDACTED***");
    }

    // Helper subclass to expose protected transform method for unit testing
    private static class TestPiiMaskingConverter extends PiiMaskingConverter {
        public String testTransform(ILoggingEvent event, String in) {
            return super.transform(event, in);
        }
    }
}
