package com.finflow.chapter180.repository;

import com.finflow.chapter180.domain.MerchantWallet;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantWalletRepository extends JpaRepository<MerchantWallet, UUID> {

    // 1. Optimistic read (relies on @Version during flush/commit)
    Optional<MerchantWallet> findByMerchantId(String merchantId);

    // 2. Pessimistic Write Lock (SELECT FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM MerchantWallet w WHERE w.merchantId = :merchantId")
    Optional<MerchantWallet> findByMerchantIdWithPessimisticWriteLock(@Param("merchantId") String merchantId);

    // 3. Pessimistic Write Lock with Lock Timeout (3000ms)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT w FROM MerchantWallet w WHERE w.merchantId = :merchantId")
    Optional<MerchantWallet> findByMerchantIdWithLockTimeout(@Param("merchantId") String merchantId);
}
