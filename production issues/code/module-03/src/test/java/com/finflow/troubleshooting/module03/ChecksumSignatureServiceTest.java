package com.finflow.troubleshooting.module03;

import com.finflow.troubleshooting.module03.service.ChecksumSignatureService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ChecksumSignatureServiceTest {

    private final ChecksumSignatureService signatureService = new ChecksumSignatureService();

    @Test
    void testSha256ChecksumMatchesKnownVector() {
        String input = "finflow-checkout-payload-2026";
        String byteHash = signatureService.generatePayloadSignature(input);
        String streamHash = signatureService.generateStreamSignature(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(byteHash).isNotEmpty().hasSize(64);
        assertThat(streamHash).isEqualTo(byteHash);
    }
}
