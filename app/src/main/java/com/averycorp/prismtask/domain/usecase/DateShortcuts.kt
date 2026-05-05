package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.core.time.DayBoundary
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar

/**
 * Pure date math used by the quick-reschedule popup. All functions are
 * deterministic given a "now" so they can be unit-tested without touching the
 * system clock.
 *
 * Two flavors of "today" / "tomorrow" exist:
 *   - The original [today] / [tomorrow] snap to **calendar** midnight. They
 *     predate the user-configurable Start-of-Day (SoD) and are kept for
 *     legacy callers (week-relative shortcuts, template defaults) that don't
 *     consult SoD.
 *   - The new [todayLogical] / [tomorrowLogical] honor SoD: a user looking
 *     at the app at 02:00 with SoD = 04:00 still considers it yesterday, so
 *     "today" returns yesterday's calendar midnight. Use these anywhere the
 *     user types or taps "today" / "tomorrow" — Quick Add NLP, Move-to-
 *     Tomorrow, Plan-for-Today, the reschedule popup chips.
 */
object DateShortcuts {
    /** Returns the epoch millis at midnight for the day [now] falls into. */
    fun startOfDay(now: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Today (at calendar midnight). Use [todayLogical] for SoD-aware "today". */
    fun today(now: Long = System.currentTimeMillis()): Long = startOfDay(now)

    /** Tomorrow (at calendar midnight). Use [tomorrowLogical] for SoD-aware "tomorrow". */
    fun tomorrow(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(now) }
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /**
     * Calendar midnight of the user's current *logical* day (SoD-aware).
     *
     * Anchored to [DayBoundary.logicalDate] — the canonical SoD resolver
     * also used by habits, streaks, and the Today filter. Returns a
     * timestamp at 00:00 local on that calendar date so it's interchangeable
     * with [today] for fields that store due dates as midnight millis.
     */
    fun todayLogical(
        sodHour: Int,
        sodMinute: Int = 0,
        now: Long = System.currentTimeMillis()
    ): Long = DayBoundary.logicalDate(Instant.ofEpochMilli(now), sodHour, sodMinute)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    /** Calendar midnight of the calendar day after the current logical day. */
    fun tomorrowLogical(
        sodHour: Int,
        sodMinute: Int = 0,
        now: Long = System.currentTimeMillis()
    ): Long = DayBoundary.logicalDate(Instant.ofEpochMilli(now), sodHour, sodMinute)
        .plusDays(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    /**
     * Returns the next Monday strictly *after* today. If today is Monday the
     * result is 7 days from now — callers that want "this Monday or today"
     * should use a different helper. Independent of the user's locale.
     */
    fun nextMonday(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(now) }
        // Calendar.MONDAY == 2, Calendar.SUNDAY == 1
        val currentDow = cal.get(Calendar.DAY_OF_WEEK)
        val daysUntilMonday = when (currentDow) {
            Calendar.MONDAY -> 7
            Calendar.TUESDAY -> 6
            Calendar.WEDNESDAY -> 5
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 3
            Calendar.SATURDAY -> 2
            Calendar.SUNDAY -> 1
            else -> 7
        }
        cal.add(Calendar.DAY_OF_YEAR, daysUntilMonday)
        return cal.timeInMillis
    }

    /** Today + 7 days (at midnight). */
    fun nextWeek(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(now) }
        cal.add(Calendar.DAY_OF_YEAR, 7)
        return cal.timeInMillis
    }
}

    /**
     * Returns the next Monday strictly *after* today. If today is Monday the
     * result is 7 days from now — callers that want "this Monday or today"
     * should use a different helper. Independent of the user's locale.
     */
    fun nextMonday(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(now) }
        // Calendar.MONDAY == 2, Calendar.SUNDAY == 1
        val currentDow = cal.get(Calendar.DAY_OF_WEEK)
        val daysUntilMonday = when (currentDow) {
            Calendar.MONDAY -> 7
            Calendar.TUESDAY -> 6
            Calendar.WEDNESDAY -> 5
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 3
            Calendar.SATURDAY -> 2
            Calendar.SUNDAY -> 1
            else -> 7
        }
        cal.add(Calendar.DAY_OF_YEAR, daysUntilMonday)
        return cal.timeInMillis
    }

    /** Today + 7 days (at midnight). */
    fun nextWeek(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(now) }
        cal.add(Calendar.DAY_OF_YEAR, 7)
        return cal.timeInMillis
    }
}
