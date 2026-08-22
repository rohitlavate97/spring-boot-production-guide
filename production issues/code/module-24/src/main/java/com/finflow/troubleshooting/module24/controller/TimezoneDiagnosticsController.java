package com.finflow.troubleshooting.module24.controller;

import com.finflow.troubleshooting.module24.service.TemporalResilienceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.TimeZone;

@RestController
@RequestMapping("/api/v1/time")
public class TimezoneDiagnosticsController {

    private final TemporalResilienceService temporalService;

    public TimezoneDiagnosticsController(TemporalResilienceService temporalService) {
        this.temporalService = temporalService;
    }

    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentTime() {
        Instant now = Instant.now();
        return ResponseEntity.ok(Map.of(
                "utcInstant", now.toString(),
                "epochMilli", now.toEpochMilli(),
                "jvmDefaultTimezone", TimeZone.getDefault().getID(),
                "zoneOffset", "+00:00"
        ));
    }

    @GetMapping("/convert-timezone")
    public ResponseEntity<Map<String, Object>> convertTimezone(
            @RequestParam(required = false) String isoTimestamp,
            @RequestParam(defaultValue = "America/New_York") String targetZoneId
    ) {
        Instant instant = (isoTimestamp != null && !isoTimestamp.isBlank()) ? Instant.parse(isoTimestamp) : Instant.now();
        var result = temporalService.formatForUserTimezone(instant, targetZoneId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/simulate-dst")
    public ResponseEntity<TemporalResilienceService.DstAnalysisResult> simulateDst(
            @RequestParam(defaultValue = "2026-03-08") String dateStr,
            @RequestParam(defaultValue = "America/New_York") String zoneStr
    ) {
        LocalDate date = LocalDate.parse(dateStr);
        ZoneId zone = ZoneId.of(zoneStr);
        var result = temporalService.analyzeDstTransition(date, zone);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/validate-token")
    public ResponseEntity<TemporalResilienceService.TokenValidationResult> validateToken(
            @RequestParam long tokenExpiryEpochSec,
            @RequestParam(defaultValue = "5") long clockSkewToleranceSec
    ) {
        Instant expiry = Instant.ofEpochSecond(tokenExpiryEpochSec);
        Instant now = Instant.now();
        var result = temporalService.validateTokenWithClockSkewLeeway(expiry, now, clockSkewToleranceSec);
        return ResponseEntity.ok(result);
    }
}
