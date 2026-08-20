package com.finflow.chapter150.correct;

import com.finflow.chapter150.domain.PaymentItem;
import com.finflow.chapter150.domain.PaymentOrder;
import com.finflow.chapter150.dto.PaymentItemDto;
import com.finflow.chapter150.dto.PaymentOrderSummaryDto;
import com.finflow.chapter150.repository.PaymentOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * CORRECT IMPLEMENTATION:
 * 1. Solves the N+1 problem using:
 *    - JOIN FETCH (Approach 1: Single query join)
 *    - @EntityGraph (Approach 2: Declarative fetch graph)
 *    - @BatchSize (Approach 3: Batched SQL IN queries)
 * 2. Maps to immutable records/DTOs inside service transactional boundary.
 * 3. Works deterministically with spring.jpa.open-in-view=false.
 */
@Service
public class OptimizedReconciliationService {

    private final PaymentOrderRepository paymentOrderRepository;

    public OptimizedReconciliationService(PaymentOrderRepository paymentOrderRepository) {
        this.paymentOrderRepository = paymentOrderRepository;
    }

    /**
     * Approach 1: JOIN FETCH.
     * Generates exactly 1 SQL query containing LEFT JOINs for items and INNER JOIN for merchant.
     */
    @Transactional(readOnly = true)
    public List<PaymentOrderSummaryDto> getReconciliationReportViaJoinFetch(String merchantId) {
        List<PaymentOrder> orders = paymentOrderRepository.findAllWithItemsAndMerchantByMerchantId(merchantId);
        return mapToDto(orders);
    }

    /**
     * Approach 2: @EntityGraph.
     * Instructs Hibernate to generate outer joins dynamically at runtime for specified attribute paths.
     */
    @Transactional(readOnly = true)
    public List<PaymentOrderSummaryDto> getReconciliationReportViaEntityGraph(String merchantId) {
        List<PaymentOrder> orders = paymentOrderRepository.findAllWithEntityGraphByMerchantId(merchantId);
        return mapToDto(orders);
    }

    /**
     * Approach 3: Batch Fetching (@BatchSize / default_batch_fetch_size).
     * Executes initial query + 1 batched query for MerchantAccounts + 1 batched query for Items.
     * Total: 3 SQL queries instead of 1 + 2N, completely immune to Cartesian product explosion!
     */
    @Transactional(readOnly = true)
    public List<PaymentOrderSummaryDto> getReconciliationReportViaBatchFetching(String merchantId) {
        List<PaymentOrder> orders = paymentOrderRepository.findAllByMerchantId(merchantId);
        return mapToDto(orders);
    }

    private List<PaymentOrderSummaryDto> mapToDto(List<PaymentOrder> orders) {
        List<PaymentOrderSummaryDto> dtos = new ArrayList<>();
        for (PaymentOrder order : orders) {
            List<PaymentItemDto> itemDtos = new ArrayList<>();
            for (PaymentItem item : order.getItems()) {
                itemDtos.add(new PaymentItemDto(
                        item.getId(),
                        item.getSku(),
                        item.getItemDescription(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getFeeAmount()
                ));
            }

            dtos.add(new PaymentOrderSummaryDto(
                    order.getId(),
                    order.getOrderNumber(),
                    order.getMerchantAccount().getMerchantCode(),
                    order.getMerchantAccount().getBusinessName(),
                    order.getTotalAmount(),
                    order.getCurrency(),
                    order.getStatus().name(),
                    order.getCreatedAt(),
                    itemDtos
            ));
        }
        return dtos;
    }
}
