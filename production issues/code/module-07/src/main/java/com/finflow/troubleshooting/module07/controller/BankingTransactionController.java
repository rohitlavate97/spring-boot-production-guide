package com.finflow.troubleshooting.module07.controller;

import com.finflow.troubleshooting.module07.entity.AccountEntity;
import com.finflow.troubleshooting.module07.repository.AccountRepository;
import com.finflow.troubleshooting.module07.repository.AuditLogRepository;
import com.finflow.troubleshooting.module07.service.BankingTransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/banking")
public class BankingTransactionController {

    private final BankingTransactionService transactionService;
    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;

    public BankingTransactionController(BankingTransactionService transactionService,
                                        AccountRepository accountRepository,
                                        AuditLogRepository auditLogRepository) {
        this.transactionService = transactionService;
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @PostMapping("/transfer/swallowed-bug")
    public ResponseEntity<Map<String, Object>> transferSwallowedBug(@RequestParam String from,
                                                                   @RequestParam String to,
                                                                   @RequestParam BigDecimal amount) {
        transactionService.transferWithSwallowedExceptionBug(from, to, amount);
        return ResponseEntity.ok(Map.of("message", "Executed with swallowed exception bug"));
    }

    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<Map<String, Object>> getBalance(@PathVariable String accountId) {
        AccountEntity account = accountRepository.findByAccountId(accountId).orElse(null);
        return ResponseEntity.ok(Map.of(
                "accountId", accountId,
                "balance", account != null ? account.getBalance() : BigDecimal.ZERO
        ));
    }

    @GetMapping("/audit-logs/count")
    public ResponseEntity<Map<String, Object>> getAuditLogCount() {
        return ResponseEntity.ok(Map.of("totalLogs", auditLogRepository.count()));
    }
}
