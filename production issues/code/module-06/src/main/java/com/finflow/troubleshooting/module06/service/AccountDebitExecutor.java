package com.finflow.troubleshooting.module06.service;

import com.finflow.troubleshooting.module06.annotation.AuditedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AccountDebitExecutor {

    private static final Logger log = LoggerFactory.getLogger(AccountDebitExecutor.class);

    @AuditedTransaction(action = "COLLABORATOR_DEBIT")
    public String executeDebit(String accountId, BigDecimal amount) {
        log.info("[AccountDebitExecutor] Executing debit of ${} on account {}", amount, accountId);
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
