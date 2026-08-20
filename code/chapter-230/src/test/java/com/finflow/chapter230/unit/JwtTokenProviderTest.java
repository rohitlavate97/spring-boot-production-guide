package com.finflow.chapter230.unit;

import com.finflow.chapter230.Chapter230Application;
import com.finflow.chapter230.security.JwtKeyManager;
import com.finflow.chapter230.security.JwtTokenProvider;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = Chapter230Application.class)
public class JwtTokenProviderTest {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private JwtKeyManager keyManager;

    @Test
    public void testGenerateAndValidateAccessToken_success() {
        String token = tokenProvider.generateAccessToken("USER-101", "MERCHANT_ACME", List.of("ROLE_MERCHANT_ADMIN", "PAYMENT:WRITE"));

        assertThat(token).isNotBlank();

        JwtTokenProvider.JwtClaims claims = tokenProvider.parseAndValidateToken(token);
        assertThat(claims.subject()).isEqualTo("USER-101");
        assertThat(claims.merchantId()).isEqualTo("MERCHANT_ACME");
        assertThat(claims.roles()).containsExactlyInAnyOrder("ROLE_MERCHANT_ADMIN", "PAYMENT:WRITE");
    }

    @Test
    public void testAlgorithmConfusion_algNone_isStrictlyRejected() {
        // Build an unauthenticated plain token with alg=none
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("HACKER")
                .issuer("https://auth.finflow.io")
                .claim("merchantId", "MERCHANT_ACME")
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();

        PlainJWT plainJWT = new PlainJWT(new PlainHeader(), claimsSet);
        String noneToken = plainJWT.serialize();

        assertThatThrownBy(() -> tokenProvider.parseAndValidateToken(noneToken))
                .isInstanceOf(SecurityException.class);
    }
}
