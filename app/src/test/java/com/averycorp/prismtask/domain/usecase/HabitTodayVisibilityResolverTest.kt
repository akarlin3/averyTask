package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.HabitEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HabitTodayVisibilityResolverTest {
    private val resolver = HabitTodayVisibilityResolver()
    private val zone: ZoneId = ZoneId.of("UTC")

    private fun habit(
        skipAfterComplete: Int = -1,
        skipBeforeSchedule: Int = -1,
        activeDays: String? = null,
        bookedDate: Long? = null,
        frequencyPeriod: String = "daily",
        isBookable: Boolean = false
    ): HabitEntity = HabitEntity(
        id = 1L,
        name = "Test",
        frequencyPeriod = frequencyPeriod,
        activeDays = activeDays,
        isBookable = isBookable,
        bookedDate = bookedDate,
        todaySkipAfterCompleteDays = skipAfterComplete,
        todaySkipBeforeScheduleDays = skipBeforeSchedule
    )

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `resolves per-habit override when set`() {
        val h = habit(skipAfterComplete = 5)
        assertEquals(5, resolver.resolveSkipAfterCompleteDays(h, globalDays = 2))
    }

    @Test
    fun `falls back to global when override is sentinel -1`() {
        val h = habit(skipAfterComplete = -1)
        assertEquals(2, resolver.resolveSkipAfterCompleteDays(h, globalDays = 2))
    }

    @Test
    fun `per-habit override of 0 disables for that habit even when global is set`() {
        val h = habit(skipAfterComplete = 0)
        assertEquals(0, resolver.resolveSkipAfterCompleteDays(h, globalDays = 5))
    }

    @Test
    fun `not hidden when both windows are zero`() {
        val h = habit()
        val now = millis(2026, 4, 16)
        assertFalse(
            resolver.isHidden(
                habit = h,
                lastCompletionDate = now - 1000L,
                skipAfterCompleteDays = 0,
                skipBeforeScheduleDays = 0,
                now = now,
                zone = zone
            )
        )
    }

    @Test
    fun `hidden when completed yesterday and skipAfter is 2`() {
        val h = habit()
        val now = millis(2026, 4, 16, 9)
        val yesterday = millis(2026, 4, 15, 9)
        assertTrue(
            resolver.isHidden(
                habit = h,
                lastCompletionDate = yesterday,
                skipAfterCompleteDays = 2,
                skipBeforeScheduleDays = 0,
                now = now,
                zone = zone
            )
        )
    }

    @Test
    fun `not hidden when last completion was longer ago than the window`() {
        val h = habit()
        val now = millis(2026, 4, 16, 9)
        val threeDaysAgo = millis(2026, 4, 13, 9)
        assertFalse(
            resolver.isHidden(
                habit = h,
                lastCompletionDate = threeDaysAgo,
                skipAfterCompleteDays = 2,
                skipBeforeScheduleDays = 0,
                now = now,
                zone = zone
            )
        )
    }

    @Test
    fun `never hidden when last completion is null`() {
        val h = habit()
        val now = millis(2026, 4, 16, 9)
        assertFalse(
            resolver.isHidden(
                habit = h,
                lastCompletionDate = null,
                skipAfterCompleteDays = 7,
                skipBeforeScheduleDays = 0,
                now = now,
                zone = zone
            )
        )
    }

    @Test
    fun `next scheduled date for daily habit with no activeDays is null`() {
        val today = LocalDate.of(2026, 4, 16)
        assertNull(resolver.nextScheduledDate(habit(), today))
    }

    @Test
    fun `next scheduled date walks forward to the next active weekday`() {
        // 2026-04-16 is a Thursday. ActiveDays = Mon(1), Wed(3), Fri(5).
        // Next active occurrence is Fri (2026-04-17).
        val today = LocalDate.of(2026, 4, 16)
        val h = habit(activeDays = "[1,3,5]", frequencyPeriod = "weekly")
        assertEquals(LocalDate.of(2026, 4, 17), resolver.nextScheduledDate(h, today))
        assertEquals(DayOfWeek.FRIDAY, resolver.nextScheduledDate(h, today)?.dayOfWeek)
    }

    @Test
    fun `next scheduled date prefers booked date when in the future`() {
        val today = LocalDate.of(2026, 4, 16)
        val booked = millis(2026, 4, 18, 10)
        val h = habit(
            isBookable = true,
            bookedDate = booked,
            frequencyPeriod = "weekly"
        )
        assertEquals(LocalDate.of(2026, 4, 18), resolver.nextScheduledDate(h, today))
    }

    @Test
    fun `hidden when next active weekday is within the schedule window`() {
        // Today: Tue 2026-04-14. Active days: Wed (3). Next: Wed 2026-04-15 (1 day away).
        // Window = 2 → hide.
        val now = millis(2026, 4, 14, 9)
        val h = habit(activeDays = "[3]", frequencyPeriod = "weekly")
        assertTrue(
            resolver.isHidden(
                habit = h,
                lastCompletionDate = null,
                skipAfterCompleteDays = 0,
                skipBeforeScheduleDays = 2,
                now = now,
                zone = zone
            )
        )
    }

    @Test
    fun `not hidden by schedule window when next active weekday is past the window`() {
        // Today: Tue 2026-04-14. Active days: Sun (7). Next: Sun 2026-04-19 (5 days).
        // Window = 2 → keep visible.
        val now = millis(2026, 4, 14, 9)
        val h = habit(activeDays = "[7]", frequencyPeriod = "weekly")
        assertFalse(
            resolver.isHidden(
                habit = h,
                lastCompletionDate = null,
                skipAfterCompleteDays = 0,
                skipBeforeScheduleDays = 2,
                now = now,
                zone = zone
            )
        )
    }
}
