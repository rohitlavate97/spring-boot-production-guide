package com.finflow.troubleshooting.module12;

import com.finflow.troubleshooting.module12.client.ExternalCreditAgencyClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = Module12Application.class)
public class ClientTimeoutConfigurationTest {

    @Autowired
    private ExternalCreditAgencyClient agencyClient;

    @Test
    void testClientThrowsResourceAccessExceptionWhenDownstreamTimesOut() {
        assertThrows(ResourceAccessException.class, () ->
                agencyClient.assessCredit("CUST-TIMEOUT-1", false, true));
    }
}
