package com.finflow.chapter250.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class MerchantFeeSchedule implements Serializable {

    private String merchantId;
    private String tier;
    private BigDecimal percentageFee;
    private BigDecimal fixedFee;
    private Instant effectiveDate;
    private long version;

    public MerchantFeeSchedule() {}

    public MerchantFeeSchedule(String merchantId, String tier, BigDecimal percentageFee,
                               BigDecimal fixedFee, Instant effectiveDate, long version) {
        this.merchantId = merchantId;
        this.tier = tier;
        this.percentageFee = percentageFee;
        this.fixedFee = fixedFee;
        this.effectiveDate = effectiveDate;
        this.version = version;
    }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public BigDecimal getPercentageFee() { return percentageFee; }
    public void setPercentageFee(BigDecimal percentageFee) { this.percentageFee = percentageFee; }
    public BigDecimal getFixedFee() { return fixedFee; }
    public void setFixedFee(BigDecimal fixedFee) { this.fixedFee = fixedFee; }
    public Instant getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(Instant effectiveDate) { this.effectiveDate = effectiveDate; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MerchantFeeSchedule that)) return false;
        return Objects.equals(merchantId, that.merchantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(merchantId);
    }
}
