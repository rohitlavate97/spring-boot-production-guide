package com.finflow.chapter230.unit;

import com.finflow.chapter230.Chapter230Application;
import com.finflow.chapter230.model.AuthTokens;
import com.finflow.chapter230.service.RefreshTokenRotationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = Chapter230Application.class)
public class RefreshTokenRotationTest {

    @Autowired
    private RefreshTokenRotationService rotationService;

    @Test
    public void testRefreshTokenRotation_singleRotation_succeeds() {
        AuthTokens initial = rotationService.issueInitialTokens("USER_ROHIT", "MERCHANT_ACME", List.of("PAYMENT:WRITE"));
        assertThat(initial.refreshToken()).isNotBlank();

        // Rotate once
        AuthTokens rotated = rotationService.rotateTokens(initial.refreshToken(), List.of("PAYMENT:WRITE"));
        assertThat(rotated.refreshToken()).isNotEqualTo(initial.refreshToken());
        assertThat(rotated.accessToken()).isNotBlank();
    }

    @Test
    public void testReuseDetection_replayAttacker_invalidatesTokenFamily() {
        AuthTokens initial = rotationService.issueInitialTokens("USER_ALICE", "MERCHANT_ACME", List.of("PAYMENT:WRITE"));
        String originalRefreshToken = initial.refreshToken();

        // Legitimate user rotates token -> gets new token R2
        AuthTokens rotated1 = rotationService.rotateTokens(originalRefreshToken, List.of("PAYMENT:WRITE"));
        String activeRefreshToken = rotated1.refreshToken();

        // Attacker attempts to replay originalRefreshToken (already used) -> Exception & Token Family Revocation!
        assertThatThrownBy(() -> rotationService.rotateTokens(originalRefreshToken, List.of("PAYMENT:WRITE")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Refresh token reuse detected");

        // Verify that legitimate user's active token is NOW ALSO REVOKED (entire family invalidated)
        assertThat(rotationService.isTokenRevoked(activeRefreshToken)).isTrue();
        assertThatThrownBy(() -> rotationService.rotateTokens(activeRefreshToken, List.of("PAYMENT:WRITE")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Refresh token is revoked");
    }
}
