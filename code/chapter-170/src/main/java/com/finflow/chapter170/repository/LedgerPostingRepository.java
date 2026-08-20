package com.finflow.chapter170.repository;

import com.finflow.chapter170.domain.LedgerPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerPostingRepository extends JpaRepository<LedgerPosting, UUID> {
    List<LedgerPosting> findAllByTransactionRef(String transactionRef);
}
