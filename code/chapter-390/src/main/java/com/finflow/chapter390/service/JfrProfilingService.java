package com.finflow.chapter390.service;

import jdk.jfr.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service providing programmatic control of JDK Flight Recorder (JFR)
 * for low-overhead production profiling and custom event emission.
 */
@Service
public class JfrProfilingService {

    private static final Logger log = LoggerFactory.getLogger(JfrProfilingService.class);

    private Recording activeRecording;
    private final Map<String, Object> recordingMetadata = new ConcurrentHashMap<>();

    @Name("com.finflow.PaymentSettlement")
    @Label("Payment Settlement Event")
    @Category({"FinFlow", "Settlement"})
    @Description("Emitted when a batch payment settlement is processed")
    public static class PaymentSettlementEvent extends Event {
        @Label("Merchant ID")
        private String merchantId;

        @Label("Batch Size")
        private int batchSize;

        @Label("Duration (ms)")
        private long durationMs;

        public void setMerchantId(String merchantId) {
            this.merchantId = merchantId;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }
    }

    public synchronized String startRecording(String recordingName, int maxAgeMinutes) {
        if (activeRecording != null && activeRecording.getState() == RecordingState.RUNNING) {
            return "Recording '" + activeRecording.getName() + "' is already running.";
        }

        try {
            Configuration config = Configuration.getConfiguration("default");
            activeRecording = new Recording(config);
            activeRecording.setName(recordingName);
            activeRecording.setToDisk(true);
            activeRecording.start();

            recordingMetadata.put("name", recordingName);
            recordingMetadata.put("state", "RUNNING");
            recordingMetadata.put("startTime", System.currentTimeMillis());

            log.info("[JFR] Started continuous flight recording '{}'", recordingName);
            return "STARTED";
        } catch (Exception e) {
            log.error("[JFR] Failed to start JFR recording", e);
            return "ERROR: " + e.getMessage();
        }
    }

    public synchronized String stopAndDumpRecording(Path destinationFile) {
        if (activeRecording == null || activeRecording.getState() != RecordingState.RUNNING) {
            return "No active recording running to dump.";
        }

        try {
            activeRecording.stop();
            if (destinationFile != null) {
                activeRecording.dump(destinationFile);
                log.info("[JFR] Dumped JFR recording to file: {}", destinationFile.toAbsolutePath());
            }
            activeRecording.close();
            activeRecording = null;
            recordingMetadata.put("state", "STOPPED");
            return "STOPPED_AND_DUMPED";
        } catch (IOException e) {
            log.error("[JFR] Failed to dump JFR recording", e);
            return "ERROR: " + e.getMessage();
        }
    }

    public void emitSettlementEvent(String merchantId, int batchSize, long durationMs) {
        PaymentSettlementEvent event = new PaymentSettlementEvent();
        if (event.isEnabled()) {
            event.setMerchantId(merchantId);
            event.setBatchSize(batchSize);
            event.setDurationMs(durationMs);
            event.commit();
        }
    }

    public boolean isRecordingActive() {
        return activeRecording != null && activeRecording.getState() == RecordingState.RUNNING;
    }

    public Map<String, Object> getRecordingStatus() {
        return Map.of(
                "active", isRecordingActive(),
                "details", recordingMetadata
        );
    }
}
