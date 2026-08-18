package com.finflow.chapter030.incorrect;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INCORRECT IMPLEMENTATION: Blocks startup thread.
 */
public class ReportGenerationServiceIncorrect {
    private static final Logger log = LoggerFactory.getLogger(ReportGenerationServiceIncorrect.class);
    
    private byte[] referenceData;

    @PostConstruct
    public void init() {
        log.warn("Starting heavy data load in @PostConstruct - THIS WILL BLOCK STARTUP");
        loadReferenceData();
    }

    private void loadReferenceData() {
        try {
            // Simulating heavy I/O or big data processing blocking the main thread
            Thread.sleep(12000); 
            referenceData = new byte[1024 * 1024]; // dummy data
            log.info("Loaded reference data.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String generateReport() {
        return "Report based on " + (referenceData != null ? referenceData.length : 0) + " bytes of reference data";
    }
}
