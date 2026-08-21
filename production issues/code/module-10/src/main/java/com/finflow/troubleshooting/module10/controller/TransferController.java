package com.finflow.troubleshooting.module10.controller;

import com.finflow.troubleshooting.module10.entity.LedgerAccountEntity;
import com.finflow.troubleshooting.module10.repository.LedgerAccountRepository;
import com.finflow.troubleshooting.module10.service.TransferService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ledger")
public class TransferController {

    private final TransferService transferService;
    private final LedgerAccountRepository accountRepository;

    public TransferController(TransferService transferService, LedgerAccountRepository accountRepository) {
        this.transferService = transferService;
        this.accountRepository = accountRepository;
    }

    @PostMapping("/accounts")
    public ResponseEntity<LedgerAccountEntity> createAccount(@RequestParam String accountNumber,
                                                             @RequestParam String ownerName,
                                                             @RequestParam BigDecimal initialBalance) {
        LedgerAccountEntity account = accountRepository.save(new LedgerAccountEntity(accountNumber, ownerName, initialBalance));
        return ResponseEntity.ok(account);
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<LedgerAccountEntity> getAccount(@PathVariable Long id) {
        return accountRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/transfer/deterministic")
    public ResponseEntity<Map<String, Object>> transferDeterministic(@RequestParam Long fromId,
                                                                     @RequestParam Long toId,
                                                                     @RequestParam BigDecimal amount) {
        transferService.transferDeterministic(fromId, toId, amount);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "fromId", fromId, "toId", toId, "amount", amount));
    }

    @PostMapping("/transfer/optimistic")
    public ResponseEntity<Map<String, Object>> transferOptimistic(@RequestParam Long fromId,
                                                                  @RequestParam Long toId,
                                                                  @RequestParam BigDecimal amount) {
        transferService.transferWithOptimisticLockAndRetry(fromId, toId, amount);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "fromId", fromId, "toId", toId, "amount", amount));
    }
}
