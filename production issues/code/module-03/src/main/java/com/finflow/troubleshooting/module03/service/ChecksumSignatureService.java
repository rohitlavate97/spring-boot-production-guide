package com.finflow.troubleshooting.module03.service;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class ChecksumSignatureService {

    public String generatePayloadSignature(String payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload must not be null");
        }
        return DigestUtils.sha256Hex(payload.getBytes(StandardCharsets.UTF_8));
    }

    public String generateStreamSignature(InputStream stream) {
        try {
            // DigestUtils.sha256Hex(InputStream) was introduced in commons-codec 1.11+
            // In older commons-codec 1.9, this method does not exist and throws NoSuchMethodError!
            return DigestUtils.sha256Hex(stream);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate SHA-256 stream signature", e);
        }
    }
}
