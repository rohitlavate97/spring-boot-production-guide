package com.finflow.chapter390.unit;

import com.finflow.chapter390.service.JfrProfilingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class JfrProfilingServiceUnitTest {

    private JfrProfilingService jfrService;
    private Path tempJfrFile;

    @BeforeEach
    void setUp() throws IOException {
        jfrService = new JfrProfilingService();
        tempJfrFile = Files.createTempFile("test-jfr-dump-", ".jfr");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (jfrService.isRecordingActive()) {
            jfrService.stopAndDumpRecording(null);
        }
        Files.deleteIfExists(tempJfrFile);
    }

    @Test
    void testStartStopAndDumpJfrRecording() {
        // 1. Start JFR
        String startResult = jfrService.startRecording("unit-test-jfr", 5);
        assertThat(startResult).isEqualTo("STARTED");
        assertThat(jfrService.isRecordingActive()).isTrue();

        // 2. Emit custom JFR event
        jfrService.emitSettlementEvent("MERCHANT-101", 50, 120);

        // 3. Stop and dump
        String stopResult = jfrService.stopAndDumpRecording(tempJfrFile);
        assertThat(stopResult).isEqualTo("STOPPED_AND_DUMPED");
        assertThat(jfrService.isRecordingActive()).isFalse();
        assertThat(Files.exists(tempJfrFile)).isTrue();
    }
}
