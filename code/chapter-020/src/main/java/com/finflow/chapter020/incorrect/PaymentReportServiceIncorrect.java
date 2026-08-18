package com.finflow.chapter020.incorrect;

import com.finflow.chapter020.domain.PaymentIntent;
import com.finflow.chapter020.domain.ReportSummary;
import com.finflow.chapter020.repository.PaymentIntentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentReportServiceIncorrect {

    private static final Logger log = LoggerFactory.getLogger(PaymentReportServiceIncorrect.class);
    private final PaymentIntentRepository repository;

    public PaymentReportServiceIncorrect(PaymentIntentRepository repository) {
        this.repository = repository;
    }

    public ReportSummary generateMonthlyReport(UUID merchantId, YearMonth month) {
        log.info("Generating report for merchant {} for month {}", merchantId, month);
        
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.atEndOfMonth().atTime(23, 59, 59, 999999999);

        // BAD: Loading all entities into memory. 
        // For a large merchant this could be 500K+ entities, causing severe GC pressure.
        List<PaymentIntent> intents = repository.findByMerchantIdAndCreatedAtBetween(merchantId, start, end);

        long totalAmount = 0;
        long successfulCount = 0;
        long failedCount = 0;
        Map<String, Long> statusBreakdown = new HashMap<>();

        for (PaymentIntent intent : intents) {
            totalAmount += intent.getAmountCents();
            statusBreakdown.put(intent.getStatus(), statusBreakdown.getOrDefault(intent.getStatus(), 0L) + 1);

            if ("COMPLETED".equals(intent.getStatus())) {
                successfulCount++;
            } else if ("FAILED".equals(intent.getStatus())) {
                failedCount++;
            }
        }

        return new ReportSummary(
                merchantId,
                month,
                intents.size(),
                totalAmount,
                successfulCount,
                failedCount,
                statusBreakdown
        );
    }
}
