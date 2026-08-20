package com.finflow.chapter240.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class MockJwtTokenFactory {

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;

    public MockJwtTokenFactory() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            this.publicKey = (RSAPublicKey) keyPair.getPublic();
            this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize RSA keypair", e);
        }
    }

    public RSAPublicKey getPublicKey() { return publicKey; }
    public RSAPrivateKey getPrivateKey() { return privateKey; }

    public String createToken(String subject, String merchantId, String clientId, String scopes, List<String> roles) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("https://auth.finflow.io")
                .audience("finflow-api")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .jwtID(UUID.randomUUID().toString())
                .claim("merchant_id", merchantId)
                .claim("client_id", clientId)
                .claim("scope", scopes)
                .claim("finflow_roles", roles)
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID("finflow-key-2026")
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        try {
            signedJWT.sign(new RSASSASigner(privateKey));
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign token", e);
        }
    }
}
