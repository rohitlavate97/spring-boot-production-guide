package com.finflow.troubleshooting.module24.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TemporalResilienceService {

    private static final Logger log = LoggerFactory.getLogger(TemporalResilienceService.class);

    public record TokenValidationResult(
            boolean isValidWithoutLeeway,
            boolean isValidWithLeeway,
            long clockSkewToleranceSec,
            String message
    ) {}

    public record DstAnalysisResult(
            LocalDate date,
            String zoneId,
            boolean isGapDay,
            boolean isOverlapDay,
            String description,
            List<String> validOffsetsAt230AM
    ) {}

    /**
     * ✅ PRODUCTION FIX 1: Clock Skew Tolerance for Distributed Token Validation
     * Applies a tolerance leeway window (e.g. 5 seconds) to absorb NTP drift between microservice nodes.
     */
    public TokenValidationResult validateTokenWithClockSkewLeeway(Instant tokenExpiry, Instant currentEvaluationTime, long clockSkewToleranceSec) {
        boolean validStrict = currentEvaluationTime.isBefore(tokenExpiry);
        // If node clock is slightly ahead, allow leeway window
        boolean validWithLeeway = currentEvaluationTime.minusSeconds(clockSkewToleranceSec).isBefore(tokenExpiry);

        String message;
        if (validStrict) {
            message = "TOKEN_VALID_STRICT";
        } else if (validWithLeeway) {
            message = "TOKEN_VALID_WITH_CLOCK_SKEW_LEEWAY";
            log.warn("[CLOCK SKEW ABSORPTION] Token was strictly expired by {}s, but accepted within {}s skew leeway window",
                    Duration.between(tokenExpiry, currentEvaluationTime).toSeconds(), clockSkewToleranceSec);
        } else {
            message = "TOKEN_EXPIRED";
        }

        return new TokenValidationResult(validStrict, validWithLeeway, clockSkewToleranceSec, message);
    }

    /**
     * ✅ PRODUCTION FIX 2: DST Transition Analysis & Safe Scheduling
     */
    public DstAnalysisResult analyzeDstTransition(LocalDate date, ZoneId zoneId) {
        ZoneRules rules = zoneId.getRules();
        LocalDateTime testTime = LocalDateTime.of(date, LocalTime.of(2, 30));

        boolean isGap = rules.isDaylightSavings(testTime.atZone(zoneId).toInstant()) && rules.getValidOffsets(testTime).isEmpty();
        boolean isOverlap = rules.getValidOffsets(testTime).size() > 1;

        List<String> offsets = rules.getValidOffsets(testTime).stream()
                .map(ZoneOffset::toString)
                .toList();

        String description;
        if (isGap) {
            description = "SPRING_FORWARD_GAP: 02:30 AM does not exist in this timezone on this date (clock jumped from 02:00 to 03:00). Scheduled job using LocalDateTime would be SKIPPED!";
        } else if (isOverlap) {
            description = "FALL_BACK_OVERLAP: 02:30 AM occurs TWICE in this timezone on this date (clock set back from 02:00 to 01:00). Scheduled job using LocalDateTime would RUN TWICE!";
        } else {
            description = "NORMAL_DAY: No DST transition at 02:30 AM.";
        }

        return new DstAnalysisResult(date, zoneId.getId(), isGap, isOverlap, description, offsets);
    }

    /**
     * ✅ PRODUCTION FIX 3: Converting UTC Instant to User Presentation Timezone
     */
    public Map<String, Object> formatForUserTimezone(Instant timestamp, String targetZoneId) {
        ZoneId zone = ZoneId.of(targetZoneId);
        ZonedDateTime zdt = timestamp.atZone(zone);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("utcInstant", timestamp.toString());
        result.put("targetZoneId", zone.getId());
        result.put("formattedDateTime", zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")));
        result.put("zoneOffset", zdt.getOffset().toString());
        result.put("isDaylightSaving", zone.getRules().isDaylightSavings(timestamp));
        return result;
    }
}
