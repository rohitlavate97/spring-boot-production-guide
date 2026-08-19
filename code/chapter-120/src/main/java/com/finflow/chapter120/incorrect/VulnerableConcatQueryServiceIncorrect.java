package com.finflow.chapter120.incorrect;

import com.finflow.chapter120.domain.PaymentIntentEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VulnerableConcatQueryServiceIncorrect {

    @PersistenceContext
    private EntityManager entityManager;

    // Incorrect: Using string concatenation for dynamic queries exposes the application to SQL/HQL Injection.
    @SuppressWarnings("unchecked")
    public List<PaymentIntentEntity> findByCurrencyVulnerable(String currency) {
        String hql = "FROM PaymentIntentEntity p WHERE p.currency = '" + currency + "'";
        Query query = entityManager.createQuery(hql);
        return query.getResultList();
    }
}
