package com.finflow.chapter230.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

@Component
public class JwtKeyManager {

    private final String keyId = "finflow-rsa-2026-v1";
    private final RSAKey rsaJwk;
    private final JWSSigner signer;
    private final JWSVerifier verifier;

    public JwtKeyManager() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

            this.rsaJwk = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyID(keyId)
                    .build();

            this.signer = new RSASSASigner(privateKey);
            this.verifier = new RSASSAVerifier(publicKey);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize RSA KeyPair for JWT signing", e);
        }
    }

    public String getKeyId() { return keyId; }
    public JWSSigner getSigner() { return signer; }
    public JWSVerifier getVerifier() { return verifier; }
    public RSAKey getPublicJwk() { return rsaJwk.toPublicJWK(); }
}
