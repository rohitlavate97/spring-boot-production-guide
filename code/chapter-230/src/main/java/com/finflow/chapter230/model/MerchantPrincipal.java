package com.finflow.chapter230.model;

import org.springframework.security.core.GrantedAuthority;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class MerchantPrincipal implements Serializable {

    private final String keyId;
    private final String merchantId;
    private final Set<GrantedAuthority> authorities;

    public MerchantPrincipal(String keyId, String merchantId, Collection<? extends GrantedAuthority> authorities) {
        this.keyId = keyId;
        this.merchantId = merchantId;
        this.authorities = Collections.unmodifiableSet(Set.copyOf(authorities));
    }

    public String getKeyId() { return keyId; }
    public String getMerchantId() { return merchantId; }
    public Set<GrantedAuthority> getAuthorities() { return authorities; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MerchantPrincipal that)) return false;
        return Objects.equals(keyId, that.keyId) && Objects.equals(merchantId, that.merchantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyId, merchantId);
    }
}
