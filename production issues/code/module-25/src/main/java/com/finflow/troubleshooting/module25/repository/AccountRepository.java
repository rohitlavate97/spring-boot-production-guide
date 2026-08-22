package com.finflow.troubleshooting.module25.repository;

import com.finflow.troubleshooting.module25.model.AccountEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    Optional<AccountEntity> findByAccountNumber(String accountNumber);

    Optional<AccountEntity> findByAccountUuid(String accountUuid);

    @Query("SELECT a FROM AccountEntity a WHERE a.accountUuid IS NULL ORDER BY a.id ASC")
    List<AccountEntity> findAccountsNeedingBackfill(Pageable pageable);

    long countByAccountUuidIsNull();
}
