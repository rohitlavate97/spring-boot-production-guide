package com.finflow.troubleshooting.module25;

import com.finflow.troubleshooting.module25.model.AccountEntity;
import com.finflow.troubleshooting.module25.repository.AccountRepository;
import com.finflow.troubleshooting.module25.service.ExpandContractMigrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExpandContractMigrationTest {

    @Autowired
    private ExpandContractMigrationService migrationService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("Phase 1 Expand: Dual-write MUST populate both legacy and new column simultaneously")
    void testDualWriteCreation() {
        String accNum = "ACC-TEST-100";
        AccountEntity account = migrationService.createAccountWithDualWrite(accNum, BigDecimal.valueOf(5000.00));

        assertThat(account.getId()).isNotNull();
        assertThat(account.getAccountNumber()).isEqualTo(accNum);
        assertThat(account.getAccountUuid()).startsWith("UUID-");
    }

    @Test
    @DisplayName("Phase 2 Backfill: MUST successfully migrate legacy rows where accountUuid IS NULL")
    void testBackfillLegacyRows() {
        // Initial V1 migration seeded ACC-1001 and ACC-1002 with NULL accountUuid
        int backfilled = migrationService.backfillBatch(10);
        assertThat(backfilled).isGreaterThanOrEqualTo(0);

        // Verify remaining pending backfills is 0
        long pending = accountRepository.countByAccountUuidIsNull();
        assertThat(pending).isEqualTo(0);
    }

    @Test
    @DisplayName("Phase 3 Read: MUST support lookups by both new accountUuid and legacy accountNumber")
    void testReadCompatibility() {
        AccountEntity created = migrationService.createAccountWithDualWrite("ACC-READ-99", BigDecimal.valueOf(100.00));

        Optional<AccountEntity> byUuid = migrationService.findAccountByIdentifier(created.getAccountUuid());
        assertThat(byUuid).isPresent();
        assertThat(byUuid.get().getAccountNumber()).isEqualTo("ACC-READ-99");

        Optional<AccountEntity> byNum = migrationService.findAccountByIdentifier("ACC-READ-99");
        assertThat(byNum).isPresent();
        assertThat(byNum.get().getAccountUuid()).isEqualTo(created.getAccountUuid());
    }
}
