package com.finflow.chapter180.repository;

import com.finflow.chapter180.domain.UnversionedWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnversionedWalletRepository extends JpaRepository<UnversionedWallet, UUID> {
    Optional<UnversionedWallet> findByMerchantId(String merchantId);
}
