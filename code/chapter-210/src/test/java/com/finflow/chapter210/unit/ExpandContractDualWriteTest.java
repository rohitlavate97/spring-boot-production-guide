package com.finflow.chapter210.unit;

import com.finflow.chapter210.Chapter210Application;
import com.finflow.chapter210.correct.ExpandContractPayoutService;
import com.finflow.chapter210.domain.MerchantPayoutProfile;
import com.finflow.chapter210.repository.MerchantPayoutProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter210Application.class)
public class ExpandContractDualWriteTest {

    @Autowired
    private MerchantPayoutProfileRepository repository;

    @Autowired
    private ExpandContractPayoutService payoutService;

    @BeforeEach
    public void setup() {
        repository.deleteAll();
    }

    @Test
    public void testDualWrite_persistsBothLegacyAndNewColumns() {
        MerchantPayoutProfile saved = payoutService.registerPayoutProfile(
                "MERCHANT_DUAL_1",
                "EUR",
                "DE89370400440532013000",
                "DBEUMM21"
        );

        assertThat(saved).isNotNull();
        assertThat(saved.getLegacyBankAccount()).isEqualTo("DE89370400440532013000");
        assertThat(saved.getIban()).isEqualTo("DE89370400440532013000");

        String effectiveAccount = payoutService.resolveEffectiveAccountNumber("MERCHANT_DUAL_1");
        assertThat(effectiveAccount).isEqualTo("DE89370400440532013000");
    }

    @Test
    public void testReadFallback_returnsLegacyAccountWhenIbanIsNull() {
        // Seed legacy row with null IBAN (created prior to migration)
        MerchantPayoutProfile legacy = new MerchantPayoutProfile(
                UUID.randomUUID().toString(),
                "MERCHANT_LEGACY_1",
                "USD",
                "ACC-LEGACY-998811",
                null, // IBAN is null
                null,
                "ACTIVE",
                Instant.now()
        );
        repository.saveAndFlush(legacy);

        // Fallback correctly resolves legacy bank account without NullPointerException
        String effectiveAccount = payoutService.resolveEffectiveAccountNumber("MERCHANT_LEGACY_1");
        assertThat(effectiveAccount).isEqualTo("ACC-LEGACY-998811");
    }
}
