package com.averycorp.prismtask.notifications

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.HabitEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device round-trip test for the habit-nag-suppression columns added in
 * migration 49→50 (`nag_suppression_override_enabled`,
 * `nag_suppression_days_override`). Confirms the fields round-trip through
 * Room + DAO and the resolution helper reads them correctly off a fetched
 * [HabitEntity].
 *
 * Uses an in-memory [PrismTaskDatabase] per the prompt constraints — never
 * touches the production `averytask.db` path. The entity constructor
 * defaults to the sentinel values (override disabled, -1 days) so any
 * regression that drops the column or changes the default would surface
 * as an assertion failure here before a field-level unit test would pick
 * it up.
 */
@RunWith(AndroidJUnit4::class)
class HabitSuppressionPersistenceInstrumentedTest {

    private lateinit var database: PrismTaskDatabase

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun roundTrip_overrideEnabledWithExplicitDays_preservesValues() = runTest {
        val dao = database.habitDao()
        val id = dao.insert(
            HabitEntity(
                name = "Meditation",
                nagSuppressionOverrideEnabled = true,
                nagSuppressionDaysOverride = 3
            )
        )
        val fetched = dao.getHabitByIdOnce(id)
        assertNotNull(fetched)
        assertEquals(true, fetched!!.nagSuppressionOverrideEnabled)
        assertEquals(3, fetched.nagSuppressionDaysOverride)

        // Resolution layer should honor the override regardless of the
        // global value.
        assertEquals(
            3,
            HabitNotificationUtils.resolveSuppressionDays(fetched, globalSuppressionDays = 7)
        )
    }

    @Test
    fun roundTrip_disabledOverrideFallsBackToGlobal() = runTest {
        val dao = database.habitDao()
        val id = dao.insert(HabitEntity(name = "Stretch"))
        val fetched = dao.getHabitByIdOnce(id)!!
        assertEquals(false, fetched.nagSuppressionOverrideEnabled)
        assertEquals(-1, fetched.nagSuppressionDaysOverride)
        assertEquals(
            7,
            HabitNotificationUtils.resolveSuppressionDays(fetched, globalSuppressionDays = 7)
        )
    }

    @Test
    fun roundTrip_overrideEnabledWithZeroExplicitlyDisables() = runTest {
        val dao = database.habitDao()
        val id = dao.insert(
            HabitEntity(
                name = "Water",
                nagSuppressionOverrideEnabled = true,
                nagSuppressionDaysOverride = 0
            )
        )
        val fetched = dao.getHabitByIdOnce(id)!!
        assertEquals(
            0,
            HabitNotificationUtils.resolveSuppressionDays(fetched, globalSuppressionDays = 7)
        )
    }
}
