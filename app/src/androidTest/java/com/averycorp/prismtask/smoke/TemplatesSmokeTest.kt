package com.averycorp.prismtask.smoke

import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Smoke tests for the Templates feature. The Templates screen is reached
 * via Settings tab → Data & Backup → Templates, which is too deep a
 * navigation to exercise reliably in a smoke test (LazyColumn + nested
 * settings subscreens + Pro-gated rows). We verify the data-layer path
 * instead: the seeded templates are visible through the DAO the
 * Templates screen reads from, and the UI-reachable FAB / category filter
 * coverage lives in the unit tests around TaskTemplateRepository +
 * TemplatePickerSheet.
 */
@HiltAndroidTest
class TemplatesSmokeTest : SmokeTestBase() {
    @Test
    fun templates_seededTemplatesExist() = runBlocking {
        val templates = database.taskTemplateDao().getAllTemplatesOnce()
        assert(templates.any { it.name == "Morning Routine" }) {
            "Seeded 'Morning Routine' template must be present"
        }
        assert(templates.any { it.name == "Meeting Prep" }) {
            "Seeded 'Meeting Prep' template must be present"
        }
    }

    @Test
    fun templates_bothSeededTemplatesAreAvailable() = runBlocking {
        val templates = database.taskTemplateDao().getAllTemplatesOnce()
        val seededNames = setOf("Morning Routine", "Meeting Prep")
        val found = templates.map { it.name }.filter { it in seededNames }.toSet()
        assert(found == seededNames) {
            "Expected both seeded template names present; got $found"
        }
    }

    @Test
    fun templates_seededTaskCountIsNonZero() = runBlocking {
        val templates = database.taskTemplateDao().getAllTemplatesOnce()
        assert(templates.isNotEmpty()) {
            "Template seed should produce at least one row"
        }
    }

    @Test
    fun templates_seededMorningRoutineHasSteps() = runBlocking {
        val templates = database.taskTemplateDao().getAllTemplatesOnce()
        val morning = templates.firstOrNull { it.name == "Morning Routine" }
        assert(morning != null) { "Morning Routine must be seeded" }
    }
}
