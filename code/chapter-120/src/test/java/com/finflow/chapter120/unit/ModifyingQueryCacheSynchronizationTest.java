package com.finflow.chapter120.unit;

import com.finflow.chapter120.correct.repository.PaymentIntentRepository;
import com.finflow.chapter120.domain.PaymentIntentEntity;
import com.finflow.chapter120.domain.PaymentStatus;
import com.finflow.chapter120.incorrect.UnsynchronizedModifyingRepositoryIncorrect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ModifyingQueryCacheSynchronizationTest {

    @Autowired
    private PaymentIntentRepository correctRepository;

    @Autowired
    private UnsynchronizedModifyingRepositoryIncorrect incorrectRepository;

    @Test
    @Transactional
    void testCorrectModifyingQueryClearsCache() {
        // Setup
        PaymentIntentEntity entity = new PaymentIntentEntity(UUID.randomUUID(), UUID.randomUUID(), 1000L, "USD", PaymentStatus.CREATED, "key1");
        entity = correctRepository.saveAndFlush(entity);

        // Load into L1 cache
        PaymentIntentEntity cachedEntity = correctRepository.findById(entity.getId()).orElseThrow();
        assertThat(cachedEntity.getStatus()).isEqualTo(PaymentStatus.CREATED);

        // Execute correct @Modifying(clearAutomatically = true)
        int updated = correctRepository.updateStatus(entity.getId(), PaymentStatus.CREATED, PaymentStatus.AUTHORIZED, java.time.Instant.now());
        assertThat(updated).isEqualTo(1);

        // Read again within same transaction
        PaymentIntentEntity refreshedEntity = correctRepository.findById(entity.getId()).orElseThrow();
        
        // Because of clearAutomatically=true, it hits the DB again and gets the fresh state
        assertThat(refreshedEntity.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    @Test
    @Transactional
    void testIncorrectModifyingQueryLeavesStaleCache() {
        // Setup
        PaymentIntentEntity entity = new PaymentIntentEntity(UUID.randomUUID(), UUID.randomUUID(), 1000L, "USD", PaymentStatus.CREATED, "key2");
        entity = incorrectRepository.saveAndFlush(entity);

        // Load into L1 cache
        PaymentIntentEntity cachedEntity = incorrectRepository.findById(entity.getId()).orElseThrow();
        assertThat(cachedEntity.getStatus()).isEqualTo(PaymentStatus.CREATED);

        // Execute incorrect @Modifying WITHOUT clearAutomatically=true
        int updated = incorrectRepository.updateStatusWithoutClearingCache(entity.getId(), PaymentStatus.AUTHORIZED);
        assertThat(updated).isEqualTo(1);

        // Read again within same transaction
        PaymentIntentEntity staleEntity = incorrectRepository.findById(entity.getId()).orElseThrow();
        
        // Stale state from L1 cache!
        assertThat(staleEntity.getStatus()).isEqualTo(PaymentStatus.CREATED);
    }
}
