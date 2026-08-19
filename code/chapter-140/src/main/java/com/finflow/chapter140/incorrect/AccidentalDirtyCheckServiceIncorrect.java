package com.finflow.chapter140.incorrect;

import com.finflow.chapter140.correct.MerchantConfigRepository;
import com.finflow.chapter140.domain.MerchantConfigEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class AccidentalDirtyCheckServiceIncorrect {

    private final MerchantConfigRepository merchantConfigRepository;

    public AccidentalDirtyCheckServiceIncorrect(MerchantConfigRepository merchantConfigRepository) {
        this.merchantConfigRepository = merchantConfigRepository;
    }

    @Transactional
    public String getFormattedMerchantConfig(UUID configId) {
        MerchantConfigEntity config = merchantConfigRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("Config not found"));
        
        // Anti-pattern: Modifying a managed entity in a seemingly read-only method.
        // This will trigger an unexpected SQL UPDATE at transaction commit.
        String originalValue = config.getConfigValue();
        String formattedValue = originalValue != null ? originalValue.trim().toUpperCase() : "";
        config.setConfigValue(formattedValue);
        
        return formattedValue;
    }
}
