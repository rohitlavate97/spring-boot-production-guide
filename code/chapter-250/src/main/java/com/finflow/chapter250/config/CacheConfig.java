package com.finflow.chapter250.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(List.of(
                "merchant_fee_schedules",
                "merchant_fee_schedules_hardened",
                "null_safe_merchant_fees"
        ));
        cacheManager.setAllowNullValues(true); // Protection against Cache Penetration
        return cacheManager;
    }
}
