package com.averycorp.prismtask.notifications

import com.averycorp.prismtask.data.local.entity.HabitEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [HabitNotificationUtils.resolveSuppressionDays].
 *
 * This is the pure slice of the "notification-delay" feature: given a
 * [HabitEntity] carrying optional per-habit overrides and the global
 * suppression window from user preferences, return the effective window
 * (in days) that nag notifications should be suppressed for.
 *
 * Suppression rules (mirrored from the production function):
 * - `nagSuppressionOverrideEnabled = false` → always return the global.
 * - Override enabled + `nagSuppressionDaysOverride >= 0` → return the override.
 * - Override enabled + `nagSuppressionDaysOverride < 0` → return the global
 *   (the `-1` sentinel means "inherit").
 * - A value of `0` explicitly disables suppression for that habit, even if
 *   the global is non-zero (the override wins).
 *
 * The eight scenarios below match the prompt's required coverage for
 * delay-window math: each combination of (override-enabled, override-value,
 * global-value) is pinned to its expected effective window. A later
 * "does the nag fire right now?" test can layer on top by calling
 * [HabitNotificationUtils.resolveSuppressionDays] + compare against the
 * number of days until the next scheduled occurrence.
 */
class HabitNotificationUtilsTest {

    private fun habit(
        overrideEnabled: Boolean = false,
        overrideDays: Int = -1
    ): HabitEntity = HabitEntity(
        id = 1L,
        name = "Test",
        nagSuppressionOverrideEnabled = overrideEnabled,
        nagSuppressionDaysOverride = overrideDays
    )

    @Test
    fun `override disabled returns global regardless of override value`() {
        // Field value on the row is irrelevant when the toggle is off.
        val h = habit(overrideEnabled = false, overrideDays = 999)
        assertEquals(7, HabitNotificationUtils.resolveSuppressionDays(h, globalSuppressionDays = 7))
    }

    @Test
    fun `override disabled with global zero returns zero (feature off)`() {
        // Global-disabled end-to-end → never suppress.
        val h = habit(overrideEnabled = false, overrideDays = -1)
        assertEquals(0, HabitNotificationUtils.resolveSuppressionDays(h, globalSuppressionDays = 0))
    }

    @Test
    fun `override enabled with sentinel minus-one falls back to global`() {
        // -1 is the DB default; it means "inherit the global value".
        val h = habit(overrideEnabled = true, overrideDays = -1)
        assertEquals(7, HabitNotificationUtils.resolveSuppressionDays(h, globalSuppressionDays = 7))
    }

    @Test
    fun `override enabled with explicit value wins over global`() {
        val h = habit(overrideEnabled = true, overrideDays = 3)
        assertEquals(3, HabitNotificationUtils.resolveSuppressionDays(h, globalSuppressionDays = 7))
    }

    @Test
    fun `override enabled with zero explicitly disables for this habit`() {
        // Even if the user has a global suppression window of 7 days, a
        // per-habit override of 0 turns it off — this is the "disable for
        // habit" checkbox in AddEditHabitViewModel.
        val h = habit(overrideEnabled = true, overrideDays = 0)
        assertEquals(0, HabitNotificationUtils.resolveSuppressionDays(h, globalSuppressionDays = 7))
    }

    @Test
    fun `override enabled with large value is returned unchanged`() {
        val h = habit(overrideEnabled = true, overrideDays = 30)
        assertEquals(30, HabitNotificationUtils.resolveSuppressionDays(h, globalSuppressionDays = 7))
    }

    @Test
    fun `both global and override disabled returns zero`() {
        val h = habit(overrideEnabled = false, overrideDays = -1)
        assertEquals(0, HabitNotificationUtils.resolveSuppressionDays(h, globalSuppressionDays = 0))
    }

    @Test
    fun `override enabled wins even when override equals global`() {
        // A habit with override=7 and global=7 should still use the override
        // (the override-enabled toggle is the semantic switch, not equality).
        val h = habit(overrideEnabled = true, overrideDays = 7)
        assertEquals(7, HabitNotificationUtils.resolveSuppressionDays(h, globalSuppressionDays = 7))
    }

    // The function under test takes an Int unconditionally; callers in
    // production do `coerceIn(1, 30)` upstream. If a negative override
    // slips past that guard, the `takeIf { it >= 0 }` branch rejects it
    // and the global is used instead.
    @Test
    fun `override enabled with negative non-sentinel value falls back to global`() {
        val h = habit(overrideEnabled = true, overrideDays = -5)
        assertEquals(7, HabitNotificationUtils.resolveSuppressionDays(h, globalSuppressionDays = 7))
    }
}
