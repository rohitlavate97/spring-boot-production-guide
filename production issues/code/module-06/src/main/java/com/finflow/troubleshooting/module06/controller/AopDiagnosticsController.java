package com.finflow.troubleshooting.module06.controller;

import com.finflow.troubleshooting.module06.aspect.TransactionAuditAspect;
import com.finflow.troubleshooting.module06.service.AccountBalanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounts")
public class AopDiagnosticsController {

    private final AccountBalanceService accountBalanceService;
    private final TransactionAuditAspect auditAspect;

    public AopDiagnosticsController(AccountBalanceService accountBalanceService,
                                    TransactionAuditAspect auditAspect) {
        this.accountBalanceService = accountBalanceService;
        this.auditAspect = auditAspect;
    }

    @PostMapping("/debit/buggy")
    public ResponseEntity<Map<String, Object>> debitBuggy(@RequestParam String accountId, @RequestParam BigDecimal amount) {
        int beforeCount = auditAspect.getInterceptionCount();
        String txnId = accountBalanceService.processDebitBuggy(accountId, amount);
        int afterCount = auditAspect.getInterceptionCount();

        return ResponseEntity.ok(Map.of(
                "transactionId", txnId,
                "aspectIntercepted", afterCount > beforeCount,
                "interceptionCount", afterCount,
                "pattern", "BUGGY_SELF_INVOCATION"
        ));
    }

    @PostMapping("/debit/fixed-collaborator")
    public ResponseEntity<Map<String, Object>> debitFixedCollaborator(@RequestParam String accountId, @RequestParam BigDecimal amount) {
        int beforeCount = auditAspect.getInterceptionCount();
        String txnId = accountBalanceService.processDebitWithCollaborator(accountId, amount);
        int afterCount = auditAspect.getInterceptionCount();

        return ResponseEntity.ok(Map.of(
                "transactionId", txnId,
                "aspectIntercepted", afterCount > beforeCount,
                "interceptionCount", afterCount,
                "pattern", "FIXED_COLLABORATOR_BEAN"
        ));
    }

    @PostMapping("/debit/fixed-self-proxy")
    public ResponseEntity<Map<String, Object>> debitFixedSelfProxy(@RequestParam String accountId, @RequestParam BigDecimal amount) {
        int beforeCount = auditAspect.getInterceptionCount();
        String txnId = accountBalanceService.processDebitWithSelfProxy(accountId, amount);
        int afterCount = auditAspect.getInterceptionCount();

        return ResponseEntity.ok(Map.of(
                "transactionId", txnId,
                "aspectIntercepted", afterCount > beforeCount,
                "interceptionCount", afterCount,
                "pattern", "FIXED_SELF_INJECTED_PROXY"
        ));
    }

    @GetMapping("/aspect/count")
    public ResponseEntity<Map<String, Object>> getAspectCount() {
        return ResponseEntity.ok(Map.of("interceptionCount", auditAspect.getInterceptionCount()));
    }
}
