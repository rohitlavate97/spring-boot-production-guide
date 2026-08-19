package com.finflow.chapter070.domain;

public record MerchantContext(
        String merchantId,
        String tier,
        String apiKeyId,
        String countryCode
) {}
