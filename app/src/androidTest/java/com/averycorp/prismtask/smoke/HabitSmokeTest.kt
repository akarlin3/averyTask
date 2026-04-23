package com.averycorp.prismtask.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/**
 * Smoke tests for the Habits tab. Verifies the habit list renders the
 * seeded habits and that basic navigation / interactions don't crash.
 */
@HiltAndroidTest
class HabitSmokeTest : SmokeTestBase() {
    @Test
    fun habitsTab_showsSeededHabits() {
        composeRule.waitForIdle()
        // Bottom-nav label is "Daily" for the habit list (see NavGraph).
        clickTab("Daily")

        // Seeded habit names can appear on the list row and on a summary
        // panel; onFirst() tolerates both shapes.
        composeRule.onAllNodesWithText("Exercise").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("Read").onFirst().assertIsDisplayed()
    }

    @Test
    fun habitList_tappingHabitDoesNotCrash() {
        composeRule.waitForIdle()
        clickTab("Daily")

        // Tapping a habit opens its detail/analytics — we don't assert on the
        // detail screen specifically, just that the tap doesn't blow up the app.
        composeRule.onAllNodesWithText("Exercise").onFirst().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun habitsTab_todayScreenShowsHabitsSection() {
        composeRule.waitForIdle()
        // Today screen is the default destination; verify the tab itself
        // is rendered (generic "Today" text has 5+ matches on the screen).
        findTab("Today").assertIsDisplayed()
    }
}
