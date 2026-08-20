package com.finflow.chapter210.repository;

import com.finflow.chapter210.domain.MerchantPayoutProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantPayoutProfileRepository extends JpaRepository<MerchantPayoutProfile, String> {
    Optional<MerchantPayoutProfile> findByMerchantId(String merchantId);
}
