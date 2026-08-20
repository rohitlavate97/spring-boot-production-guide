package com.finflow.chapter210.unit;

import com.finflow.chapter210.Chapter210Application;
import com.finflow.chapter210.domain.MerchantPayoutProfile;
import com.finflow.chapter210.incorrect.BreakingMigrationServiceIncorrect;
import com.finflow.chapter210.repository.MerchantPayoutProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = Chapter210Application.class)
public class BreakingMigrationFailureTest {

    @Autowired
    private MerchantPayoutProfileRepository repository;

    @Autowired
    private BreakingMigrationServiceIncorrect incorrectService;

    @BeforeEach
    public void setup() {
        repository.deleteAll();

        // Seed un-backfilled legacy record
        repository.saveAndFlush(new MerchantPayoutProfile(
                UUID.randomUUID().toString(),
                "MERCHANT_CRASH_1",
                "GBP",
                "ACC-112233",
                null, // IBAN unpopulated
                null,
                "ACTIVE",
                Instant.now()
        ));
    }

    @Test
    public void testUnsafeRead_throwsNullPointerExceptionOnUnbackfilledData() {
        // Demonstrates the danger of breaking migrations without Expand-Contract fallback
        assertThatThrownBy(() -> incorrectService.getIbanUnsafe("MERCHANT_CRASH_1"))
                .isInstanceOf(NullPointerException.class);
    }
}
