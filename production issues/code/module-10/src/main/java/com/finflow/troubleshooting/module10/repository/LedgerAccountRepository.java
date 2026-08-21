package com.finflow.troubleshooting.module10.repository;

import com.finflow.troubleshooting.module10.entity.LedgerAccountEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccountEntity, Long> {

    Optional<LedgerAccountEntity> findByAccountNumber(String accountNumber);

    // Pessimistic Write Lock with 1000ms lock timeout hint (SELECT ... FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "1000")})
    @Query("SELECT a FROM LedgerAccountEntity a WHERE a.id = :id")
    Optional<LedgerAccountEntity> findByIdForUpdate(@Param("id") Long id);
}
