package com.finflow.chapter020.correct;

import com.finflow.chapter020.domain.PaymentIntent;
import com.finflow.chapter020.domain.ReportSummary;
import com.finflow.chapter020.repository.PaymentIntentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class PaymentReportServiceCorrect {

    private static final Logger log = LoggerFactory.getLogger(PaymentReportServiceCorrect.class);
    private final PaymentIntentRepository repository;

    public PaymentReportServiceCorrect(PaymentIntentRepository repository) {
        this.repository = repository;
    }

    // GOOD: Using @Transactional is required when dealing with Streams in JPA
    // to keep the Session/Connection open during stream processing.
    @Transactional(readOnly = true)
    public ReportSummary generateMonthlyReportStreaming(UUID merchantId, YearMonth month) {
        log.info("Generating report for merchant {} for month {} using streaming", merchantId, month);
        
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.atEndOfMonth().atTime(23, 59, 59, 999999999);

        long totalAmount = 0;
        long successfulCount = 0;
        long failedCount = 0;
        long totalCount = 0;
        Map<String, Long> statusBreakdown = new HashMap<>();

        // GOOD: try-with-resources ensures the Stream (and underlying ResultSet) is closed
        try (Stream<PaymentIntent> stream = repository.streamByMerchantIdAndCreatedAtBetween(merchantId, start, end)) {
            // Since stream elements are fetched in batches (HINT_FETCH_SIZE), 
            // GC pressure is minimal. We process them one by one.
            Iterable<PaymentIntent> iterable = stream::iterator;
            for (PaymentIntent intent : iterable) {
                totalCount++;
                totalAmount += intent.getAmountCents();
                statusBreakdown.put(intent.getStatus(), statusBreakdown.getOrDefault(intent.getStatus(), 0L) + 1);

                if ("COMPLETED".equals(intent.getStatus())) {
                    successfulCount++;
                } else if ("FAILED".equals(intent.getStatus())) {
                    failedCount++;
                }
            }
        }

        return new ReportSummary(
                merchantId,
                month,
                totalCount,
                totalAmount,
                successfulCount,
                failedCount,
                statusBreakdown
        );
    }
    
    // EVEN BETTER: Let the database do the heavy lifting when possible.
    // Drastically reduces data transfer, JVM heap usage, and execution time.
    @Transactional(readOnly = true)
    public ReportSummary generateMonthlyReportDatabaseAggregated(UUID merchantId, YearMonth month) {
        log.info("Generating report for merchant {} for month {} using DB aggregation", merchantId, month);
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.atEndOfMonth().atTime(23, 59, 59, 999999999);
        
        return repository.aggregateReport(merchantId, month, start, end);
    }
}
