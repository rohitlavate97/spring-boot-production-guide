package com.finflow.chapter150.repository;

import com.finflow.chapter150.domain.MerchantAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantAccountRepository extends JpaRepository<MerchantAccount, UUID> {
    Optional<MerchantAccount> findByMerchantCode(String merchantCode);
}
