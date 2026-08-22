package com.finflow.troubleshooting.module24;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InstantVsLocalDateTimeTest {

    @Test
    @DisplayName("Instant MUST represent the exact same universal point in time across all timezones")
    void testInstantIsUniversal() {
        Instant fixedMoment = Instant.parse("2026-08-22T10:00:00Z");

        // New York (UTC-4) and Tokyo (UTC+9) viewing the exact same Instant
        ZonedDateTime nyTime = fixedMoment.atZone(ZoneId.of("America/New_York"));
        ZonedDateTime tokyoTime = fixedMoment.atZone(ZoneId.of("Asia/Tokyo"));

        // Both point to the exact same epoch second!
        assertThat(nyTime.toInstant()).isEqualTo(fixedMoment);
        assertThat(tokyoTime.toInstant()).isEqualTo(fixedMoment);
        assertThat(nyTime.toInstant()).isEqualTo(tokyoTime.toInstant());

        // Local representation reflects local offset
        assertThat(nyTime.getHour()).isEqualTo(6);   // 06:00 AM EDT
        assertThat(tokyoTime.getHour()).isEqualTo(19); // 19:00 PM JST (7:00 PM)
    }

    @Test
    @DisplayName("LocalDateTime without timezone causes drift when interpreted across different regions")
    void testLocalDateTimeAmbiguity() {
        LocalDateTime localTime = LocalDateTime.of(2026, 8, 22, 10, 0, 0);

        // Interpreting the same LocalDateTime in different zones yields DIFFERENT Instants!
        Instant inNy = localTime.atZone(ZoneId.of("America/New_York")).toInstant();
        Instant inTokyo = localTime.atZone(ZoneId.of("Asia/Tokyo")).toInstant();

        // 13-hour difference between the two Instants!
        assertThat(inNy).isNotEqualTo(inTokyo);
        assertThat(java.time.Duration.between(inTokyo, inNy).toHours()).isEqualTo(13);
    }
}
