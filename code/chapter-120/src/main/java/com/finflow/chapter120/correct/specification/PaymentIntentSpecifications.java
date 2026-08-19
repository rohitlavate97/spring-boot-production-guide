package com.finflow.chapter120.correct.specification;

import com.finflow.chapter120.domain.PaymentIntentEntity;
import com.finflow.chapter120.domain.PaymentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public class PaymentIntentSpecifications {

    public static Specification<PaymentIntentEntity> hasCustomerId(UUID customerId) {
        return (root, query, cb) -> customerId == null ? null : cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<PaymentIntentEntity> hasStatus(PaymentStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<PaymentIntentEntity> hasCurrency(String currency) {
        return (root, query, cb) -> currency == null ? null : cb.equal(root.get("currency"), currency);
    }

    public static Specification<PaymentIntentEntity> amountBetween(Long minAmountCents, Long maxAmountCents) {
        return (root, query, cb) -> {
            if (minAmountCents != null && maxAmountCents != null) {
                return cb.between(root.get("amountCents"), minAmountCents, maxAmountCents);
            } else if (minAmountCents != null) {
                return cb.greaterThanOrEqualTo(root.get("amountCents"), minAmountCents);
            } else if (maxAmountCents != null) {
                return cb.lessThanOrEqualTo(root.get("amountCents"), maxAmountCents);
            }
            return null;
        };
    }

    public static Specification<PaymentIntentEntity> createdBetween(Instant createdAfter, Instant createdBefore) {
        return (root, query, cb) -> {
            if (createdAfter != null && createdBefore != null) {
                return cb.between(root.get("createdAt"), createdAfter, createdBefore);
            } else if (createdAfter != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter);
            } else if (createdBefore != null) {
                return cb.lessThanOrEqualTo(root.get("createdAt"), createdBefore);
            }
            return null;
        };
    }

    public static Specification<PaymentIntentEntity> withCriteria(PaymentSearchCriteria criteria) {
        return Specification.where(hasCustomerId(criteria.customerId()))
                .and(hasStatus(criteria.status()))
                .and(hasCurrency(criteria.currency()))
                .and(amountBetween(criteria.minAmountCents(), criteria.maxAmountCents()))
                .and(createdBetween(criteria.createdAfter(), criteria.createdBefore()));
    }
}
