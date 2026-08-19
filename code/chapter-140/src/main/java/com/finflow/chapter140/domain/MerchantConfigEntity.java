package com.finflow.chapter140.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "merchant_config")
public class MerchantConfigEntity {

    @Id
    private UUID id;

    private String merchantCode;
    private String configValue;
    private String encryptedSecret;

    public MerchantConfigEntity() {}

    public MerchantConfigEntity(UUID id, String merchantCode, String configValue, String encryptedSecret) {
        this.id = id;
        this.merchantCode = merchantCode;
        this.configValue = configValue;
        this.encryptedSecret = encryptedSecret;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getMerchantCode() { return merchantCode; }
    public void setMerchantCode(String merchantCode) { this.merchantCode = merchantCode; }
    
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    
    public String getEncryptedSecret() { return encryptedSecret; }
    public void setEncryptedSecret(String encryptedSecret) { this.encryptedSecret = encryptedSecret; }
}
