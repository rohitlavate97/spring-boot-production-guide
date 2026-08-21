package com.finflow.chapter360.service;

import com.finflow.chapter360.config.PaymentGatewayProperties;
import com.finflow.chapter360.model.FeeCalculationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class DynamicPaymentRateService {

    private static final Logger log = LoggerFactory.getLogger(DynamicPaymentRateService.class);

    private final PaymentGatewayProperties properties;

    public DynamicPaymentRateService(PaymentGatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Calculates fee based on dynamically resolved @ConfigurationProperties.
     */
    public FeeCalculationResult calculateTransactionFee(String transactionId, BigDecimal grossAmount) {
        BigDecimal feePercent = properties.getTransactionFeePercent();
        int fixedCents = properties.getFixedFeeCents();
        String environmentTier = properties.getEnvironmentTier();

        log.info("[DynamicFeeEngine] Calculating fee for tx: {} | Gross: ${} | Rate: {}% + {}¢ | Tier: {}",
                transactionId, grossAmount, feePercent, fixedCents, environmentTier);

        // Percentage fee: grossAmount * (feePercent / 100)
        BigDecimal percentageFee = grossAmount.multiply(feePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // Fixed fee: fixedCents / 100
        BigDecimal fixedFee = BigDecimal.valueOf(fixedCents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal totalFee = percentageFee.add(fixedFee);
        BigDecimal netPayout = grossAmount.subtract(totalFee);

        return new FeeCalculationResult(
                transactionId,
                grossAmount,
                percentageFee,
                fixedFee,
                totalFee,
                netPayout,
                feePercent,
                environmentTier
        );
    }

    public PaymentGatewayProperties getProperties() {
        return properties;
    }
}
