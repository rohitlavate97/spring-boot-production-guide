package com.finflow.chapter140.unit;

import com.finflow.chapter140.correct.MerchantConfigRepository;
import com.finflow.chapter140.correct.PersistenceContextOptimizationService;
import com.finflow.chapter140.domain.MerchantConfigEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class DirtyCheckingBehaviorTest {

    @Autowired
    private MerchantConfigRepository repository;

    @Autowired
    private PersistenceContextOptimizationService optimizationService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID configId;

    @BeforeEach
    public void setup() {
        configId = UUID.randomUUID();
        MerchantConfigEntity entity = new MerchantConfigEntity(configId, "MERCH_1", "  secret_val  ", "enc_xyz");
        repository.saveAndFlush(entity);
    }

    @Test
    public void testAccidentalDirtyCheck_triggersUpdate() {
        // Run in a read-write transaction without save()
        transactionTemplate.executeWithoutResult(status -> {
            MerchantConfigEntity config = repository.findById(configId).orElseThrow();
            // Mutate property on managed entity
            config.setConfigValue(config.getConfigValue().trim().toUpperCase());
            // Intentionally NO repository.save() called
        });

        // Verify that the database was updated automatically via dirty checking
        String dbValue = jdbcTemplate.queryForObject(
                "SELECT config_value FROM merchant_config WHERE id = ?",
                String.class,
                configId
        );
        assertThat(dbValue).isEqualTo("SECRET_VAL");
    }

    @Test
    public void testReadOnlyTransaction_preventsUpdate() {
        // Execute service method marked with @Transactional(readOnly = true)
        optimizationService.getFormattedMerchantConfigSafe(configId);

        // Verify database was NOT updated because readOnly=true skipped dirty checking flush
        String dbValue = jdbcTemplate.queryForObject(
                "SELECT config_value FROM merchant_config WHERE id = ?",
                String.class,
                configId
        );
        assertThat(dbValue).isEqualTo("  secret_val  ");
    }
}
