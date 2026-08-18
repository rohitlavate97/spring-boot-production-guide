package com.finflow.chapter030.correct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Lazy
public class ReportGenerationServiceCorrect {
    private static final Logger log = LoggerFactory.getLogger(ReportGenerationServiceCorrect.class);
    
    private byte[] referenceData;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    public ReportGenerationServiceCorrect() {
        log.info("ReportGenerationServiceCorrect instantiated, but no heavy work done yet.");
    }

    private void ensureInitialized() {
        if (isInitialized.compareAndSet(false, true)) {
            log.info("Initializing reference data on first access...");
            try {
                // Simulating heavy I/O
                Thread.sleep(2000); // Kept smaller for test practicality, but represents heavy load
                referenceData = new byte[1024 * 1024]; 
                log.info("Reference data initialization complete.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                isInitialized.set(false);
            }
        } else {
            // Wait if another thread is initializing
            while (referenceData == null) {
                Thread.yield();
            }
        }
    }

    public String generateReport() {
        ensureInitialized();
        return "Report based on " + (referenceData != null ? referenceData.length : 0) + " bytes of reference data";
    }
}
