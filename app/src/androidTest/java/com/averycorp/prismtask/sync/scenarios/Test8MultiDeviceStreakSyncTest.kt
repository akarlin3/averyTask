package com.averycorp.prismtask.sync.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test 8 — Multi-device streak sync.
 *
 * Scenario: device A and device B both complete the same habit on the
 * same calendar day. After both completions propagate, the habit's
 * streak should show +1 on both (not +2) — same-day completions must
 * dedup in the streak calculation.
 *
 * Implementation notes (for the PR that turns on the `@Ignore`):
 *  1. addHabit + push so the habit has a cloud_id both devices can
 *     reference.
 *  2. completeHabit(habitId, today) locally + push.
 *  3. harness.writeAsDeviceB("habit_completions", ..., mapOf(
 *        "habit_id" to habitCloudId,
 *        "completed_date_local" to <today's local-date string per
 *           DayBoundary>,
 *        "created_at" to now
 *     )) — the exact field shape lives in SyncMapper.habitCompletionToMap().
 *  4. syncService.pullRemoteChanges() on A.
 *  5. Assert habitRepository.getResilientStreak(habitId)?.currentStreak == 1
 *     (not 2).
 *
 * Left as `@Ignore` for PR2: needs the exact SyncMapper field names for
 * habit_completion (the completed_date_local backfill migration v50
 * added the column, so the shape is non-trivial) and confirmation that
 * same-day dedup happens in StreakCalculator (vs. at DAO-insert time).
 * Both details deserve a focused pass with the production code open
 * rather than coded blind.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Test8MultiDeviceStreakSyncTest : SyncScenarioTestBase() {

    @Test
    @Ignore("TODO(PR2-followup): needs SyncMapper.habitCompletionToMap shape + streak dedup confirmation")
    fun multiDeviceCompletionsSameDay_streakIsOneNotTwo() {
        // See class KDoc for implementation sketch.
    }
}
