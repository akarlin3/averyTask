package com.averycorp.prismtask.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Unit tests for [CalendarSyncService.toUtcDayStartMillis].
 *
 * Regression coverage for the "off by one day" Google Calendar sync bug:
 * task.dueDate is stored as local-midnight epoch millis, but Android's
 * CalendarContract requires all-day events' DTSTART to be 00:00 UTC of the
 * target civil date. Passing local-midnight directly shifts the event to the
 * previous day (in zones east of UTC) or the next day (in zones west of UTC).
 */
class CalendarSyncServiceTest {

    private fun localMidnightMillis(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun utcMidnightMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `converts local midnight to UTC midnight of same civil date in Eastern time`() {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 4, 18) // Saturday
        val localMidnight = localMidnightMillis(date, zone)

        val result = CalendarSyncService.toUtcDayStartMillis(localMidnight, zone)

        assertEquals(utcMidnightMillis(date), result)
    }

    @Test
    fun `converts local midnight to UTC midnight of same civil date in Tokyo time`() {
        // Tokyo is UTC+9 — local midnight is 15:00 UTC of the previous day.
        // The bug would have shifted all-day events to the previous civil
        // date; the fix must normalize back to the user's intended date.
        val zone = ZoneId.of("Asia/Tokyo")
        val date = LocalDate.of(2026, 4, 18)
        val localMidnight = localMidnightMillis(date, zone)

        val result = CalendarSyncService.toUtcDayStartMillis(localMidnight, zone)

        assertEquals(utcMidnightMillis(date), result)
    }

    @Test
    fun `is a no-op in UTC zone`() {
        val zone = ZoneOffset.UTC
        val date = LocalDate.of(2026, 4, 18)
        val localMidnight = localMidnightMillis(date, zone)

        val result = CalendarSyncService.toUtcDayStartMillis(localMidnight, zone)

        assertEquals(localMidnight, result)
    }

    @Test
    fun `preserves Saturday as Saturday in Eastern time regression test`() {
        // Bug: a Saturday due date landed on Friday in Google Calendar.
        // After the fix, the converted timestamp must still decode to
        // Saturday at 00:00 UTC.
        val zone = ZoneId.of("America/New_York")
        val saturday = LocalDate.of(2026, 4, 18)
        val localMidnight = localMidnightMillis(saturday, zone)

        val result = CalendarSyncService.toUtcDayStartMillis(localMidnight, zone)

        val resultDate = java.time.Instant.ofEpochMilli(result)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        assertEquals(saturday, resultDate)
    }
}
