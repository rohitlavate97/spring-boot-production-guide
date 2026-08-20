package com.finflow.chapter240.unit;

import com.finflow.chapter240.model.TokenIntrospectionResponse;
import com.finflow.chapter240.service.TokenIntrospectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TokenIntrospectionServiceTest {

    private TokenIntrospectionService introspectionService;

    @BeforeEach
    public void setup() {
        introspectionService = new TokenIntrospectionService();
    }

    @Test
    public void testIntrospect_validToken_returnsActiveResponseWithClaims() {
        TokenIntrospectionResponse response = introspectionService.introspect("opaque_token_acme_pos_valid");

        assertThat(response.active()).isTrue();
        assertThat(response.merchantId()).isEqualTo("MERCHANT_ACME");
        assertThat(response.clientId()).isEqualTo("pos-client-app-1");
        assertThat(response.scope()).contains("payment:write", "payout:execute");
        assertThat(response.authorities()).contains("ROLE_MERCHANT_ADMIN");
    }

    @Test
    public void testIntrospect_revokedToken_returnsInactive() {
        TokenIntrospectionResponse response = introspectionService.introspect("opaque_token_revoked");
        assertThat(response.active()).isFalse();
    }

    @Test
    public void testIntrospect_unknownToken_returnsInactive() {
        TokenIntrospectionResponse response = introspectionService.introspect("random_unknown_token");
        assertThat(response.active()).isFalse();
    }
}
