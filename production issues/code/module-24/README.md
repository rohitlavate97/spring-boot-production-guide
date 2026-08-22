# Module 24: Timezones, DST, Instant vs LocalDateTime & Clock Skew

## Overview
This module explores temporal pitfalls in enterprise Spring Boot architectures: Daylight Saving Time (DST) non-existent time gaps and duplicate execution overlaps, database timestamp corruption caused by `LocalDateTime` vs `Instant`, Jackson serialization timezone drift, and multi-node Clock Skew (NTP drift) in distributed token validation.

## Key Scenarios Covered
1. **The DST Double-Debit & Skipped Accrual:**
   - Why scheduling jobs at 02:30 AM local time causes jobs to be skipped during Spring-Forward gaps (02:00 -> 03:00) and executed twice during Fall-Back overlaps (02:00 -> 01:00).
2. **`Instant` vs `LocalDateTime` Database Corruption:**
   - Why `LocalDateTime` lacks timezone offsets, causing silent timestamp drift when containers migrate across cloud regions or when database session timezones differ.
   - The Golden Rule: Store and process everything in `java.time.Instant` (UTC), converting to `ZonedDateTime` only at the presentation boundary.
3. **Jackson Timezone Configuration:**
   - Enforcing strict UTC date serialization and deserialization via `spring.jackson.time-zone=UTC`.
4. **Clock Skew (NTP Drift) & Tolerance Windows:**
   - Absorbing distributed node clock drift in JWT/token validation using configurable clock skew leeway windows.

## Project Structure
- `src/main/java/.../model/`:
  - `AuditTransactionRecord.java` (Demonstrates `Instant` UTC vs `LocalDateTime`).
- `src/main/java/.../service/`:
  - `TemporalResilienceService.java` (Clock skew tolerance validator, DST transition analyzer, UTC-to-Zone formatter).
- `src/main/java/.../controller/`:
  - `TimezoneDiagnosticsController.java` (REST endpoints for time inspection, timezone conversion, DST simulation, and token validation).
- `src/test/java/.../`:
  - `InstantVsLocalDateTimeTest.java`
  - `DstTransitionSafetyTest.java`
  - `ClockSkewToleranceTest.java`
  - `TimezoneDiagnosticsControllerTest.java`
  - `Module24IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full deep-dive technical incident guide, see [Module 24 Documentation](../../docs/module-24-timezones-dst-instant-localdatetime-clock-skew.md).
