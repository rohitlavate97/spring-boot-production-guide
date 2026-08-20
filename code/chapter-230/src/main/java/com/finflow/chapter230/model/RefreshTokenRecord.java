package com.finflow.chapter230.model;

import java.time.Instant;
import java.util.Objects;

public class RefreshTokenRecord {

    private final String tokenValue;
    private final String userId;
    private final String merchantId;
    private final String familyId;
    private boolean used;
    private boolean revoked;
    private final Instant expiresAt;

    public RefreshTokenRecord(String tokenValue, String userId, String merchantId, String familyId, Instant expiresAt) {
        this.tokenValue = tokenValue;
        this.userId = userId;
        this.merchantId = merchantId;
        this.familyId = familyId;
        this.used = false;
        this.revoked = false;
        this.expiresAt = expiresAt;
    }

    public String getTokenValue() { return tokenValue; }
    public String getUserId() { return userId; }
    public String getMerchantId() { return merchantId; }
    public String getFamilyId() { return familyId; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public Instant getExpiresAt() { return expiresAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RefreshTokenRecord that)) return false;
        return Objects.equals(tokenValue, that.tokenValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenValue);
    }
}
