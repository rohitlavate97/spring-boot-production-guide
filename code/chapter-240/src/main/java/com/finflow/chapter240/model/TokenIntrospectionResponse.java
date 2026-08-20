package com.finflow.chapter240.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * RFC 7662 OAuth2 Token Introspection Response Representation.
 */
public record TokenIntrospectionResponse(
        @JsonProperty("active") boolean active,
        @JsonProperty("scope") String scope,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("sub") String sub,
        @JsonProperty("merchant_id") String merchantId,
        @JsonProperty("authorities") List<String> authorities,
        @JsonProperty("exp") Long exp,
        @JsonProperty("iat") Long iat,
        @JsonProperty("iss") String iss
) {
    public static TokenIntrospectionResponse inactive() {
        return new TokenIntrospectionResponse(false, null, null, null, null, List.of(), null, null, null);
    }
}
