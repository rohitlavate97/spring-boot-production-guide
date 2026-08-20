package com.finflow.chapter150.incorrect;

import com.finflow.chapter150.domain.PaymentItem;
import com.finflow.chapter150.domain.PaymentOrder;
import com.finflow.chapter150.dto.PaymentItemDto;
import com.finflow.chapter150.dto.PaymentOrderSummaryDto;
import com.finflow.chapter150.repository.PaymentOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Suffers from the classic N+1 query problem during reconciliation loop.
 * 2. In a loop of N orders, it executes:
 *    - 1 query to fetch the list of PaymentOrders
 *    - N queries to initialize each order's MerchantAccount proxy (lazy ManyToOne)
 *    - N queries to initialize each order's PaymentItems PersistentBag (lazy OneToMany)
 *    Total: 1 + 2N queries executed against PostgreSQL!
 * 3. If accessed outside @Transactional with spring.jpa.open-in-view=false,
 *    it throws LazyInitializationException.
 */
@Service
public class NPlusOneReconciliationServiceIncorrect {

    private final PaymentOrderRepository paymentOrderRepository;

    public NPlusOneReconciliationServiceIncorrect(PaymentOrderRepository paymentOrderRepository) {
        this.paymentOrderRepository = paymentOrderRepository;
    }

    @Transactional(readOnly = true)
    public List<PaymentOrderSummaryDto> getDailyReconciliationReport(String merchantId) {
        // Query 1: SELECT * FROM payment_orders WHERE merchant_id = ?
        List<PaymentOrder> orders = paymentOrderRepository.findAllByMerchantId(merchantId);

        List<PaymentOrderSummaryDto> report = new ArrayList<>();

        for (PaymentOrder order : orders) {
            // Query 2..(N+1): Lazy initialization of MerchantAccount ByteBuddy proxy
            String merchantCode = order.getMerchantAccount().getMerchantCode();
            String businessName = order.getMerchantAccount().getBusinessName();

            // Query (N+2)..(2N+1): Lazy initialization of PaymentItem PersistentBag
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

            report.add(new PaymentOrderSummaryDto(
                    order.getId(),
                    order.getOrderNumber(),
                    merchantCode,
                    businessName,
                    order.getTotalAmount(),
                    order.getCurrency(),
                    order.getStatus().name(),
                    order.getCreatedAt(),
                    itemDtos
            ));
        }

        return report;
    }

    /**
     * Non-transactional method demonstrating LazyInitializationException
     * when spring.jpa.open-in-view is false.
     */
    public List<String> getOrderSummaryWithoutTransaction(String merchantId) {
        // Session opens and immediately closes inside repository call
        List<PaymentOrder> orders = paymentOrderRepository.findAllByMerchantId(merchantId);

        List<String> summaries = new ArrayList<>();
        for (PaymentOrder order : orders) {
            // Throws LazyInitializationException: could not initialize proxy - no Session
            summaries.add("Order: " + order.getOrderNumber() + ", Merchant: " + order.getMerchantAccount().getBusinessName());
        }
        return summaries;
    }
}
