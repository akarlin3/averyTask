package com.averycorp.prismtask.data.calendar.util

import com.averycorp.prismtask.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Regression coverage for the "off by one day" Google Calendar sync bug:
 * task.dueDate is stored as local-midnight epoch millis, but Google
 * Calendar requires all-day events' `date` to be the UTC 00:00 of the
 * target civil date. The previous implementation lived on
 * `CalendarSyncService`; it has been extracted verbatim to
 * [CalendarTimeUtil] so the backend-mediated push path preserves the same
 * semantics and the original test cases continue to pass.
 */
class CalendarTimeUtilTest {
    private fun localMidnightMillis(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun utcMidnightMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `converts local midnight to UTC midnight of same civil date in Eastern time`() {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 4, 18)
        val localMidnight = localMidnightMillis(date, zone)
        val result = CalendarTimeUtil.toUtcDayStartMillis(localMidnight, zone)
        assertEquals(utcMidnightMillis(date), result)
    }

    @Test
    fun `converts local midnight to UTC midnight of same civil date in Tokyo time`() {
        val zone = ZoneId.of("Asia/Tokyo")
        val date = LocalDate.of(2026, 4, 18)
        val localMidnight = localMidnightMillis(date, zone)
        val result = CalendarTimeUtil.toUtcDayStartMillis(localMidnight, zone)
        assertEquals(utcMidnightMillis(date), result)
    }

    @Test
    fun `is a no-op in UTC zone`() {
        val zone = ZoneOffset.UTC
        val date = LocalDate.of(2026, 4, 18)
        val localMidnight = localMidnightMillis(date, zone)
        val result = CalendarTimeUtil.toUtcDayStartMillis(localMidnight, zone)
        assertEquals(localMidnight, result)
    }

    @Test
    fun `preserves Saturday as Saturday in Eastern time regression test`() {
        val zone = ZoneId.of("America/New_York")
        val saturday = LocalDate.of(2026, 4, 18)
        val localMidnight = localMidnightMillis(saturday, zone)
        val result = CalendarTimeUtil.toUtcDayStartMillis(localMidnight, zone)
        val resultDate = java.time.Instant.ofEpochMilli(result)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        assertEquals(saturday, resultDate)
    }

    @Test
    fun `isAllDay returns true when both time fields are null or zero`() {
        assertTrue(CalendarTimeUtil.isAllDay(null, null))
        assertTrue(CalendarTimeUtil.isAllDay(0L, 0L))
        assertTrue(CalendarTimeUtil.isAllDay(null, 0L))
    }

    @Test
    fun `isAllDay returns false when dueTime or scheduledStartTime is set`() {
        assertTrue(!CalendarTimeUtil.isAllDay(9L * 60 * 60 * 1000, null))
        assertTrue(!CalendarTimeUtil.isAllDay(null, 1_700_000_000_000L))
    }

    @Test
    fun `taskToEventDateTime builds all-day start with date and no dateTime`() {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 4, 18)
        val task = TaskEntity(
            id = 1L,
            title = "All day test",
            dueDate = localMidnightMillis(date, zone)
        )
        val start = CalendarTimeUtil.taskToEventDateTime(task, isStart = true, zone = zone)
        val end = CalendarTimeUtil.taskToEventDateTime(task, isStart = false, zone = zone)

        assertNotNull(start.date)
        assertNull(start.dateTime)
        // End date is exclusive — start + 1 day
        val startMs = start.date.value
        val endMs = end.date.value
        assertEquals(86_400_000L, endMs - startMs)
    }

    @Test
    fun `taskToEventDateTime builds timed event with dateTime and timeZone`() {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 4, 18)
        val threePmOffset = 15L * 60 * 60 * 1000
        val task = TaskEntity(
            id = 2L,
            title = "Timed test",
            dueDate = localMidnightMillis(date, zone),
            dueTime = threePmOffset,
            estimatedDuration = 30
        )
        val start = CalendarTimeUtil.taskToEventDateTime(task, isStart = true, zone = zone)

        assertNotNull(start.dateTime)
        assertNull(start.date)
        assertEquals(zone.id, start.timeZone)
    }

    @Test
    fun `eventDateTimeToTaskFields round-trips all-day`() {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 4, 18)
        val task = TaskEntity(
            id = 3L,
            title = "Roundtrip",
            dueDate = localMidnightMillis(date, zone)
        )
        val eventDateTime = CalendarTimeUtil.taskToEventDateTime(task, isStart = true, zone = zone)
        val (dueDate, dueTime) = CalendarTimeUtil.eventDateTimeToTaskFields(eventDateTime, zone)

        assertEquals(task.dueDate, dueDate)
        assertNull(dueTime)
    }

    @Test
    fun `eventDateTimeToTaskFields round-trips timed event`() {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 4, 18)
        val threePmOffset = 15L * 60 * 60 * 1000
        val task = TaskEntity(
            id = 4L,
            title = "Timed roundtrip",
            dueDate = localMidnightMillis(date, zone),
            dueTime = threePmOffset,
            estimatedDuration = 30
        )
        val eventDateTime = CalendarTimeUtil.taskToEventDateTime(task, isStart = true, zone = zone)
        val (dueDate, dueTime) = CalendarTimeUtil.eventDateTimeToTaskFields(eventDateTime, zone)

        assertEquals(task.dueDate, dueDate)
        assertEquals(threePmOffset, dueTime)
    }
}
