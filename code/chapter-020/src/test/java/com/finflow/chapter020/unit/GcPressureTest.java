package com.finflow.chapter020.unit;

import com.finflow.chapter020.correct.PaymentReportServiceCorrect;
import com.finflow.chapter020.domain.PaymentIntent;
import com.finflow.chapter020.domain.ReportSummary;
import com.finflow.chapter020.incorrect.PaymentReportServiceIncorrect;
import com.finflow.chapter020.repository.PaymentIntentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class GcPressureTest {
    
    private static final Logger log = LoggerFactory.getLogger(GcPressureTest.class);
    
    private PaymentIntentRepository repository;
    private PaymentReportServiceIncorrect incorrectService;
    private PaymentReportServiceCorrect correctService;
    
    private final int ENTITY_COUNT = 100_000;
    
    @BeforeEach
    void setUp() {
        repository = Mockito.mock(PaymentIntentRepository.class);
        incorrectService = new PaymentReportServiceIncorrect(repository);
        correctService = new PaymentReportServiceCorrect(repository);
    }
    
    private List<PaymentIntent> createMockData() {
        List<PaymentIntent> list = new ArrayList<>(ENTITY_COUNT);
        UUID merchantId = UUID.randomUUID();
        for (int i = 0; i < ENTITY_COUNT; i++) {
            list.add(new PaymentIntent(
                UUID.randomUUID(), merchantId, 1000L, "USD", "COMPLETED", 
                LocalDateTime.now(), LocalDateTime.now(), 1
            ));
        }
        return list;
    }
    
    @Test
    void testIncorrectImplementationMemoryUsage() {
        System.gc(); // Suggest GC before test
        long memoryBefore = getUsedMemory();
        
        List<PaymentIntent> mockData = createMockData();
        when(repository.findByMerchantIdAndCreatedAtBetween(any(), any(), any())).thenReturn(mockData);
        
        ReportSummary summary = incorrectService.generateMonthlyReport(UUID.randomUUID(), YearMonth.now());
        
        long memoryAfter = getUsedMemory();
        long memoryDelta = memoryAfter - memoryBefore;
        
        log.info("Incorrect Implementation (Bulk Load) Memory Delta: {} MB", memoryDelta / (1024 * 1024));
        assertEquals(ENTITY_COUNT, summary.totalTransactions());
    }
    
    @Test
    void testCorrectImplementationMemoryUsage() {
        System.gc(); // Suggest GC before test
        long memoryBefore = getUsedMemory();
        
        List<PaymentIntent> mockData = createMockData();
        when(repository.streamByMerchantIdAndCreatedAtBetween(any(), any(), any()))
            .thenReturn(mockData.stream());
            
        ReportSummary summary = correctService.generateMonthlyReportStreaming(UUID.randomUUID(), YearMonth.now());
        
        long memoryAfter = getUsedMemory();
        long memoryDelta = memoryAfter - memoryBefore;
        
        log.info("Correct Implementation (Streaming) Memory Delta: {} MB", memoryDelta / (1024 * 1024));
        assertEquals(ENTITY_COUNT, summary.totalTransactions());
    }
    
    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
