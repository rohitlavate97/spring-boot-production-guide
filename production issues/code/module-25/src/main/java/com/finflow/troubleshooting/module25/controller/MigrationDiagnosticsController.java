package com.finflow.troubleshooting.module25.controller;

import com.finflow.troubleshooting.module25.model.AccountEntity;
import com.finflow.troubleshooting.module25.repository.AccountRepository;
import com.finflow.troubleshooting.module25.service.ExpandContractMigrationService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/migration")
public class MigrationDiagnosticsController {

    private final ExpandContractMigrationService migrationService;
    private final AccountRepository accountRepository;
    private final Flyway flyway;

    public MigrationDiagnosticsController(ExpandContractMigrationService migrationService,
                                          AccountRepository accountRepository,
                                          Flyway flyway) {
        this.migrationService = migrationService;
        this.accountRepository = accountRepository;
        this.flyway = flyway;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        List<Map<String, Object>> migrationHistory = new ArrayList<>();
        for (MigrationInfo info : flyway.info().all()) {
            migrationHistory.add(Map.of(
                    "version", info.getVersion() != null ? info.getVersion().getVersion() : "BASELINE",
                    "description", info.getDescription(),
                    "state", info.getState().name(),
                    "installedOn", info.getInstalledOn() != null ? info.getInstalledOn().toString() : "N/A"
            ));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("flywayCleanDisabled", true);
        response.put("appliedMigrations", migrationHistory);
        response.put("dataMigrationProgress", migrationService.getMigrationProgress());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/dual-write")
    public ResponseEntity<AccountEntity> createAccount(
            @RequestParam(defaultValue = "ACC-99001") String accountNumber,
            @RequestParam(defaultValue = "75000.00") BigDecimal balance
    ) {
        AccountEntity account = migrationService.createAccountWithDualWrite(accountNumber, balance);
        return ResponseEntity.ok(account);
    }

    @PostMapping("/backfill")
    public ResponseEntity<Map<String, Object>> backfillBatch(@RequestParam(defaultValue = "5") int batchSize) {
        int backfilledCount = migrationService.backfillBatch(batchSize);
        return ResponseEntity.ok(Map.of(
                "backfilledInThisBatch", backfilledCount,
                "progress", migrationService.getMigrationProgress()
        ));
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<AccountEntity>> listAccounts() {
        return ResponseEntity.ok(accountRepository.findAll());
    }
}
