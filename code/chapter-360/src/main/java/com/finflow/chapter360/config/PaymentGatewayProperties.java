package com.finflow.chapter360.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Dynamic Configuration Properties for FinFlow Payment Gateway.
 * Annotated with @RefreshScope so property updates from Config Server
 * re-instantiate this bean at runtime without pod restarts.
 */
@Component
@RefreshScope
@ConfigurationProperties(prefix = "finflow.payment")
public class PaymentGatewayProperties {

    private BigDecimal transactionFeePercent = BigDecimal.valueOf(2.5);
    private int fixedFeeCents = 30;
    private boolean instantSettlementEnabled = true;
    private BigDecimal maxDailyVolume = BigDecimal.valueOf(1000000.00);
    private String partnerGatewayUrl = "https://api.stripe-mock.finflow.internal/v1";
    private String environmentTier = "PRODUCTION";

    public BigDecimal getTransactionFeePercent() {
        return transactionFeePercent;
    }

    public void setTransactionFeePercent(BigDecimal transactionFeePercent) {
        this.transactionFeePercent = transactionFeePercent;
    }

    public int getFixedFeeCents() {
        return fixedFeeCents;
    }

    public void setFixedFeeCents(int fixedFeeCents) {
        this.fixedFeeCents = fixedFeeCents;
    }

    public boolean isInstantSettlementEnabled() {
        return instantSettlementEnabled;
    }

    public void setInstantSettlementEnabled(boolean instantSettlementEnabled) {
        this.instantSettlementEnabled = instantSettlementEnabled;
    }

    public BigDecimal getMaxDailyVolume() {
        return maxDailyVolume;
    }

    public void setMaxDailyVolume(BigDecimal maxDailyVolume) {
        this.maxDailyVolume = maxDailyVolume;
    }

    public String getPartnerGatewayUrl() {
        return partnerGatewayUrl;
    }

    public void setPartnerGatewayUrl(String partnerGatewayUrl) {
        this.partnerGatewayUrl = partnerGatewayUrl;
    }

    public String getEnvironmentTier() {
        return environmentTier;
    }

    public void setEnvironmentTier(String environmentTier) {
        this.environmentTier = environmentTier;
    }
}
