package com.averycorp.prismtask.ui.screens.medication.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Regression gate for [composeIntendedTime] — the helper that turns the
 * user's picked HH:mm + the slot card's logical day into an absolute
 * epoch-millis timestamp.
 *
 * Per `docs/audits/MEDICATION_TAB_LOG_INVARIANT_AUDIT.md` § anti-pattern 1,
 * the legacy compose path used `Calendar.getInstance()` to fix the
 * calendar date at wall-clock-now, then capped to `now`. That silently
 * collapsed cross-midnight SoD-window picks onto the wrong calendar
 * date. These tests pin the FIXED contract: pick the latest moment ≤ now
 * within the logical-day window matching the picked HH:mm.
 */
class MedicationTimeEditComposeTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun usesLogicalDay_whenWallClockMatchesLogicalDay() {
        // Common case: wall-clock 14:00 on Apr 28, logical day = Apr 28.
        // Pick 08:00 → expect Apr 28 08:00.
        val now = millis(2026, 4, 28, 14, 0)
        val result = composeIntendedTime(
            pickedHour = 8,
            pickedMinute = 0,
            logicalDay = LocalDate.of(2026, 4, 28),
            nowMillis = now,
            zone = zone
        )
        assertEquals(millis(2026, 4, 28, 8, 0), result)
    }

    @Test
    fun usesLogicalDay_whenWallClockHasCrossedMidnightInsideSoDWindow() {
        // The audit's headline case: wall-clock Apr 29 02:00 (after midnight
        // but before SoD), logical day = Apr 28. User picks 08:00 — they
        // mean "this morning by SoD" = Apr 28 08:00. The legacy compose
        // would have produced Apr 29 02:00 (capped from Apr 29 08:00).
        val now = millis(2026, 4, 29, 2, 0)
        val result = composeIntendedTime(
            pickedHour = 8,
            pickedMinute = 0,
            logicalDay = LocalDate.of(2026, 4, 28),
            nowMillis = now,
            zone = zone
        )
        assertEquals(millis(2026, 4, 28, 8, 0), result)
    }

    @Test
    fun prefersMoreRecentCandidate_whenBothCalendarDatesAreInThePast() {
        // wall-clock Apr 29 02:00, logical day = Apr 28. User picks 01:00
        // — both Apr 28 01:00 AND Apr 29 01:00 are ≤ now. The user almost
        // certainly meant "an hour ago", not "25 hours ago".
        val now = millis(2026, 4, 29, 2, 0)
        val result = composeIntendedTime(
            pickedHour = 1,
            pickedMinute = 0,
            logicalDay = LocalDate.of(2026, 4, 28),
            nowMillis = now,
            zone = zone
        )
        assertEquals(millis(2026, 4, 29, 1, 0), result)
    }

    @Test
    fun capsToNow_whenBothCandidatesAreInTheFuture() {
        // Defensive: if neither composed candidate is ≤ now (a degenerate
        // input), fall back to now so the row still records a sane value
        // rather than an arbitrary future timestamp.
        val now = millis(2026, 4, 28, 14, 0)
        val result = composeIntendedTime(
            pickedHour = 23,
            pickedMinute = 0,
            logicalDay = LocalDate.of(2026, 4, 28),
            nowMillis = now,
            zone = zone
        )
        // Apr 28 23:00 > now AND Apr 29 23:00 > now → cap to now.
        assertEquals(now, result)
    }

    @Test
    fun handlesEarlyMorningOnLogicalDay_whenWallClockIsLater() {
        // wall-clock Apr 28 14:00, logical day = Apr 28. User picks 05:00.
        // Apr 28 05:00 is past, Apr 29 05:00 is future. Result: Apr 28 05:00.
        val now = millis(2026, 4, 28, 14, 0)
        val result = composeIntendedTime(
            pickedHour = 5,
            pickedMinute = 0,
            logicalDay = LocalDate.of(2026, 4, 28),
            nowMillis = now,
            zone = zone
        )
        assertEquals(millis(2026, 4, 28, 5, 0), result)
    }

    @Test
    fun handlesLateEveningOnLogicalDay_whenWallClockHasCrossedMidnight() {
        // wall-clock Apr 29 02:00, logical day = Apr 28. User picks 23:30
        // — they mean "11:30 PM last night" = Apr 28 23:30. Apr 29 23:30
        // is in the future, so it's correctly excluded.
        val now = millis(2026, 4, 29, 2, 0)
        val result = composeIntendedTime(
            pickedHour = 23,
            pickedMinute = 30,
            logicalDay = LocalDate.of(2026, 4, 28),
            nowMillis = now,
            zone = zone
        )
        assertEquals(millis(2026, 4, 28, 23, 30), result)
    }
}
