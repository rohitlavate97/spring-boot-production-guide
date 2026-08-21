package com.finflow.troubleshooting.module07.repository;

import com.finflow.troubleshooting.module07.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {
    Optional<AccountEntity> findByAccountId(String accountId);
}
