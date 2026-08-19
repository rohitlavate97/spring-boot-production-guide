package com.finflow.chapter120.correct.service;

import com.finflow.chapter120.correct.repository.PaymentIntentRepository;
import com.finflow.chapter120.correct.specification.PaymentIntentSpecifications;
import com.finflow.chapter120.correct.specification.PaymentSearchCriteria;
import com.finflow.chapter120.domain.PaymentIntentEntity;
import com.finflow.chapter120.domain.PaymentIntentSummary;
import com.finflow.chapter120.domain.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PaymentQueryService {

    private final PaymentIntentRepository paymentIntentRepository;

    public PaymentQueryService(PaymentIntentRepository paymentIntentRepository) {
        this.paymentIntentRepository = paymentIntentRepository;
    }

    @Transactional(readOnly = true)
    public List<PaymentIntentSummary> getSummariesForCustomer(UUID customerId) {
        return paymentIntentRepository.findSummariesByCustomerId(customerId);
    }

    @Transactional(readOnly = true)
    public List<PaymentIntentEntity> searchPayments(PaymentSearchCriteria criteria) {
        return paymentIntentRepository.findAll(PaymentIntentSpecifications.withCriteria(criteria));
    }

    @Transactional
    public boolean updatePaymentStatus(UUID id, PaymentStatus currentStatus, PaymentStatus newStatus) {
        int updated = paymentIntentRepository.updateStatus(id, currentStatus, newStatus, java.time.Instant.now());
        return updated > 0;
    }
}
