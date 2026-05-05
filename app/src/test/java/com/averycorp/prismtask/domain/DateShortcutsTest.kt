package com.averycorp.prismtask.domain

import com.averycorp.prismtask.domain.usecase.DateShortcuts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for the pure date-math helpers used by the quick-reschedule
 * popup. All inputs are pinned via [buildDate] so results are deterministic
 * across time zones.
 */
class DateShortcutsTest {
    /**
     * Builds a "now" anchored to a specific [dayOfWeek] (e.g. [Calendar.MONDAY])
     * so assertions don't depend on hard-coded calendar dates that might not
     * match the intended day.
     */
    private fun noonOnDayOfWeek(dayOfWeek: Int): Long {
        // Start from a fixed reference date then advance until we hit the
        // requested day of week. Using 2026-01-01 (a Thursday) as the anchor.
        val cal = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.JANUARY, 1, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (cal.get(Calendar.DAY_OF_WEEK) != dayOfWeek) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun dayOfWeekOf(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.DAY_OF_WEEK)
    }

    private fun daysBetween(a: Long, b: Long): Int {
        val diff = b - a
        return Math.round(diff.toDouble() / (24L * 60L * 60L * 1000L)).toInt()
    }

    @Test
    fun nextMonday_fromMonday_skipsAFullWeek() {
        val monday = noonOnDayOfWeek(Calendar.MONDAY)
        val result = DateShortcuts.nextMonday(monday)
        assertEquals(Calendar.MONDAY, dayOfWeekOf(result))
        // Should jump a full 7 days instead of returning "today"
        assertEquals(7, daysBetween(DateShortcuts.today(monday), result))
    }

    @Test
    fun nextMonday_fromWednesday_landsOnNextMonday() {
        val wednesday = noonOnDayOfWeek(Calendar.WEDNESDAY)
        val result = DateShortcuts.nextMonday(wednesday)
        assertEquals(Calendar.MONDAY, dayOfWeekOf(result))
        assertEquals(5, daysBetween(DateShortcuts.today(wednesday), result))
    }

    @Test
    fun nextMonday_fromSunday_returnsTomorrow() {
        val sunday = noonOnDayOfWeek(Calendar.SUNDAY)
        val result = DateShortcuts.nextMonday(sunday)
        assertEquals(Calendar.MONDAY, dayOfWeekOf(result))
        assertEquals(1, daysBetween(DateShortcuts.today(sunday), result))
    }

    @Test
    fun nextMonday_fromSaturday_landsTwoDaysLater() {
        val saturday = noonOnDayOfWeek(Calendar.SATURDAY)
        val result = DateShortcuts.nextMonday(saturday)
        assertEquals(Calendar.MONDAY, dayOfWeekOf(result))
        assertEquals(2, daysBetween(DateShortcuts.today(saturday), result))
    }

    @Test
    fun today_andTomorrow_differByExactlyOneDay() {
        val now = noonOnDayOfWeek(Calendar.WEDNESDAY)
        val today = DateShortcuts.today(now)
        val tomorrow = DateShortcuts.tomorrow(now)
        assertEquals(1, daysBetween(today, tomorrow))
    }

    @Test
    fun nextWeek_isSevenDaysFromToday() {
        val now = noonOnDayOfWeek(Calendar.WEDNESDAY)
        val today = DateShortcuts.today(now)
        val plus7 = DateShortcuts.nextWeek(now)
        assertEquals(7, daysBetween(today, plus7))
    }

    @Test
    fun startOfDay_zerosTimeComponents() {
        val noon = noonOnDayOfWeek(Calendar.WEDNESDAY)
        val start = DateShortcuts.startOfDay(noon)
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    // ── SoD-aware logical-day shortcuts ─────────────────────────────────────
    //
    // These regression-protect the bug where "Move to Tomorrow" / "Plan for
    // Today" / Quick Add NLP all snapped to calendar midnight, so a tap at
    // 02:00 with SoD = 04:00 advanced the wrong day.

    private fun atHourOnDay(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun calendarMidnight(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun todayLogical_beforeStartOfDay_returnsPreviousCalendarMidnight() {
        // 02:00 on May 15 with SoD = 04:00 → logical today is May 14
        val now = atHourOnDay(2026, Calendar.MAY, 15, 2)
        val expected = calendarMidnight(2026, Calendar.MAY, 14)
        assertEquals(expected, DateShortcuts.todayLogical(sodHour = 4, now = now))
    }

    @Test
    fun todayLogical_afterStartOfDay_returnsCurrentCalendarMidnight() {
        // 06:00 with SoD = 04:00 → already past SoD, logical today = today
        val now = atHourOnDay(2026, Calendar.MAY, 15, 6)
        val expected = calendarMidnight(2026, Calendar.MAY, 15)
        assertEquals(expected, DateShortcuts.todayLogical(sodHour = 4, now = now))
    }

    @Test
    fun tomorrowLogical_beforeStartOfDay_returnsCurrentCalendarMidnight() {
        // 02:00 on May 15 with SoD = 04:00 → logical today is May 14, so
        // logical "tomorrow" is May 15. The user thinks of May 15 as today
        // already; tapping "Move to Tomorrow" must NOT skip to May 16.
        val now = atHourOnDay(2026, Calendar.MAY, 15, 2)
        val expected = calendarMidnight(2026, Calendar.MAY, 15)
        assertEquals(expected, DateShortcuts.tomorrowLogical(sodHour = 4, now = now))
    }

    @Test
    fun tomorrowLogical_afterStartOfDay_returnsNextCalendarMidnight() {
        // 06:00 on May 15 with SoD = 04:00 → logical today = May 15,
        // so logical "tomorrow" = May 16.
        val now = atHourOnDay(2026, Calendar.MAY, 15, 6)
        val expected = calendarMidnight(2026, Calendar.MAY, 16)
        assertEquals(expected, DateShortcuts.tomorrowLogical(sodHour = 4, now = now))
    }

    @Test
    fun todayLogical_withSodZero_matchesCalendarToday() {
        // SoD = 0 (default for users who never opened the prompt) means the
        // logical day boundary is calendar midnight, so todayLogical and
        // today should agree no matter when in the day we sample.
        val now = atHourOnDay(2026, Calendar.MAY, 15, 2)
        assertEquals(DateShortcuts.today(now), DateShortcuts.todayLogical(sodHour = 0, now = now))
    }

    @Test
    fun todayLogical_honorsMinutePrecision() {
        // 04:15 with SoD = 04:30 → still before SoD by 15 min → logical
        // today is yesterday. Catches the case where someone sets SoD with
        // minute precision (e.g. 04:30) and the matcher only consulted hour.
        val now = atHourOnDay(2026, Calendar.MAY, 15, 4) +
            (15L * 60L * 1000L) // 04:15
        val expected = calendarMidnight(2026, Calendar.MAY, 14)
        assertEquals(expected, DateShortcuts.todayLogical(sodHour = 4, sodMinute = 30, now = now))
    }
}
