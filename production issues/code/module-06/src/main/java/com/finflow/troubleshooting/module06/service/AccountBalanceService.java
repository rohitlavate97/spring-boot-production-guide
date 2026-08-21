package com.finflow.troubleshooting.module06.service;

import com.finflow.troubleshooting.module06.annotation.AuditedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AccountBalanceService {

    private static final Logger log = LoggerFactory.getLogger(AccountBalanceService.class);

    private final AccountDebitExecutor debitExecutor;
    private final AccountBalanceService selfProxy;

    public AccountBalanceService(AccountDebitExecutor debitExecutor,
                                 @Lazy AccountBalanceService selfProxy) {
        this.debitExecutor = debitExecutor;
        this.selfProxy = selfProxy;
    }

    // ❌ BUGGY: Internal self-invocation bypassing Spring CGLIB/JDK proxy aspect interceptor
    public String processDebitBuggy(String accountId, BigDecimal amount) {
        log.info("[AccountBalanceService] processDebitBuggy called -> invoking this.internalDebitWithAspect");
        // Calling 'this' method directly bypasses the proxy! The @AuditedTransaction aspect will NOT run!
        return this.internalDebitWithAspect(accountId, amount);
    }

    // ✅ FIXED (Pattern A): Calling distinct collaborator bean with aspect
    public String processDebitWithCollaborator(String accountId, BigDecimal amount) {
        log.info("[AccountBalanceService] processDebitWithCollaborator called -> delegating to AccountDebitExecutor");
        return debitExecutor.executeDebit(accountId, amount);
    }

    // ✅ FIXED (Pattern B): Calling through self-injected proxy
    public String processDebitWithSelfProxy(String accountId, BigDecimal amount) {
        log.info("[AccountBalanceService] processDebitWithSelfProxy called -> invoking selfProxy.internalDebitWithAspect");
        return selfProxy.internalDebitWithAspect(accountId, amount);
    }

    @AuditedTransaction(action = "INTERNAL_DEBIT")
    public String internalDebitWithAspect(String accountId, BigDecimal amount) {
        log.info("[AccountBalanceService] internalDebitWithAspect executed for account {} with amount ${}", accountId, amount);
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
