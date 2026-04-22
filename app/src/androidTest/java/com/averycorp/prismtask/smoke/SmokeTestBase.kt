package com.averycorp.prismtask.smoke

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.averycorp.prismtask.MainActivity
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.preferences.OnboardingPreferences
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import dagger.hilt.android.testing.HiltAndroidRule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import javax.inject.Inject

/**
 * Base class for smoke tests that need a running Activity with Hilt DI
 * and a seeded in-memory database.
 *
 * Subclasses get:
 *  - [composeRule] for Compose UI testing
 *  - [hiltRule] for Hilt injection
 *  - [database] for direct DB access
 *  - [seededIds] with all inserted entity IDs
 *  - Helper extensions: [findByText], [findByContentDescription]
 */
abstract class SmokeTestBase {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: PrismTaskDatabase

    @Inject
    lateinit var onboardingPreferences: OnboardingPreferences

    @Inject
    lateinit var taskBehaviorPreferences: TaskBehaviorPreferences

    protected lateinit var seededIds: TestDataSeeder.SeededIds

    @Before
    fun baseSetUp() {
        hiltRule.inject()
        // MainActivity gates the main UI behind hasCompletedOnboarding; a
        // fresh test install has that unset so the rule launches straight
        // into the onboarding flow, and compose assertions for "Today" /
        // "Tasks" / etc. fail because those labels only appear in the main
        // tab bar. Same story for hasSetStartOfDay — without this, a
        // blocking StartOfDayPickerDialog covers the main UI. Seed both
        // flags before any assertion runs; runBlocking guarantees the
        // DataStore write completes before we return control to the test.
        runBlocking {
            onboardingPreferences.setOnboardingCompleted()
            taskBehaviorPreferences.setHasSetStartOfDay(true)
        }
        runTest {
            seededIds = TestDataSeeder.seed(database)
        }
        composeRule.waitForIdle()
    }

    @After
    fun baseTearDown() {
        runTest {
            TestDataSeeder.clear(database)
        }
    }

    // ---- helpers ----

    protected fun findByText(text: String) = composeRule.onNodeWithText(text)

    protected fun findByContentDescription(description: String) =
        composeRule.onNodeWithContentDescription(description)

    protected fun waitForIdle() = composeRule.waitForIdle()
}
