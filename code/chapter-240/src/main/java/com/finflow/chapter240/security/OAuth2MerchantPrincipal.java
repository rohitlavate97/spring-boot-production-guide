package com.finflow.chapter240.security;

import org.springframework.security.core.GrantedAuthority;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class OAuth2MerchantPrincipal implements Serializable {

    private final String subject;
    private final String merchantId;
    private final String clientId;
    private final Set<GrantedAuthority> authorities;

    public OAuth2MerchantPrincipal(String subject, String merchantId, String clientId, Collection<? extends GrantedAuthority> authorities) {
        this.subject = subject;
        this.merchantId = merchantId;
        this.clientId = clientId;
        this.authorities = Collections.unmodifiableSet(Set.copyOf(authorities));
    }

    public String getSubject() { return subject; }
    public String getMerchantId() { return merchantId; }
    public String getClientId() { return clientId; }
    public Set<GrantedAuthority> getAuthorities() { return authorities; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OAuth2MerchantPrincipal that)) return false;
        return Objects.equals(subject, that.subject) && Objects.equals(merchantId, that.merchantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, merchantId);
    }

    @Override
    public String toString() {
        return "OAuth2MerchantPrincipal{" +
                "subject='" + subject + '\'' +
                ", merchantId='" + merchantId + '\'' +
                ", clientId='" + clientId + '\'' +
                ", authorities=" + authorities +
                '}';
    }
}
