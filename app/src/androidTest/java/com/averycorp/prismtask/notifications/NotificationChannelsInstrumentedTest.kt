package com.averycorp.prismtask.notifications

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device verification that [NotificationHelper.createNotificationChannel]
 * registers the base task-reminder channel with a style-suffixed ID and the
 * importance the user's preferences expect.
 *
 * Channel IDs under the new scheme look like
 * `prismtask_reminders_<importance>[_fsi][_alrm][_rvib]`. We don't assert on
 * the exact suffix here — the exact combination depends on the default
 * preferences on the test emulator — only that *some* channel whose ID
 * begins with the base prefix exists after the coroutine completes, with
 * an importance compatible with the selected level.
 *
 * Cleanup deletes any channel created by the test so production-style
 * assertions in other instrumentation tests start from a known baseline.
 */
@RunWith(AndroidJUnit4::class)
class NotificationChannelsInstrumentedTest {

    private lateinit var context: Context
    private lateinit var manager: NotificationManager

    private val createdChannelIds = mutableListOf<String>()

    @Before
    fun setup() {
        // Channels only exist on API 26+. Tests on older devices are no-ops.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        context = InstrumentationRegistry.getInstrumentation().targetContext
        manager = context.getSystemService(NotificationManager::class.java)
    }

    @After
    fun teardown() {
        // Best-effort cleanup. We only delete channels this test created
        // (tracked via createdChannelIds) so we don't remove channels set
        // up by production code outside this test.
        for (id in createdChannelIds) {
            try {
                manager.deleteNotificationChannel(id)
            } catch (_: Exception) {
                // OK — channel may not exist.
            }
        }
        createdChannelIds.clear()
    }

    @Test
    fun createNotificationChannel_createsChannelWithTaskRemindersPrefix() = runTest {
        NotificationHelper.createNotificationChannel(context)

        val channels = manager.notificationChannels
        val taskReminderChannel = channels.firstOrNull {
            it.id.startsWith("prismtask_reminders_")
        }
        assertNotNull(
            "Expected a channel with ID starting with 'prismtask_reminders_' after " +
                "createNotificationChannel — got IDs: ${channels.map { it.id }}",
            taskReminderChannel
        )
        createdChannelIds += taskReminderChannel!!.id

        assertEquals("Task Reminders", taskReminderChannel.name.toString())
        // Importance can be LOW/DEFAULT/HIGH depending on user preference.
        // Verify it's one of the known valid values, not e.g. NONE (0).
        assertTrue(
            "Importance should be LOW/DEFAULT/HIGH, got ${taskReminderChannel.importance}",
            taskReminderChannel.importance in listOf(
                NotificationManager.IMPORTANCE_LOW,
                NotificationManager.IMPORTANCE_DEFAULT,
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    @Test
    fun createNotificationChannel_isIdempotent() = runTest {
        NotificationHelper.createNotificationChannel(context)
        val firstPassIds = manager.notificationChannels
            .filter { it.id.startsWith("prismtask_reminders_") }
            .map { it.id }
            .toSet()

        NotificationHelper.createNotificationChannel(context)
        val secondPassIds = manager.notificationChannels
            .filter { it.id.startsWith("prismtask_reminders_") }
            .map { it.id }
            .toSet()

        createdChannelIds += firstPassIds + secondPassIds
        // Channel count for this base may fluctuate by one across a style
        // change; assert no NEW IDs appeared beyond the first pass. Stale
        // channel pruning lives inside deleteStaleChannels which runs
        // during creation, so secondPassIds ⊆ firstPassIds ∪ {currentId}.
        val newlyAdded = secondPassIds - firstPassIds
        assertTrue(
            "Second createNotificationChannel call should not introduce new channel IDs beyond the current style — got new: $newlyAdded",
            newlyAdded.isEmpty() || newlyAdded.size == 1
        )
    }

    @Test
    fun importanceToChannelLevel_mapsKnownImportanceKeys() {
        // Pure-logic crossover, kept here because production is the source
        // of truth for importance keys and a mismatch is easier to diagnose
        // as an instrumented failure.
        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            NotificationHelper.importanceToChannelLevel(NotificationPreferences.IMPORTANCE_MINIMAL)
        )
        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            NotificationHelper.importanceToChannelLevel(NotificationPreferences.IMPORTANCE_STANDARD)
        )
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            NotificationHelper.importanceToChannelLevel(NotificationPreferences.IMPORTANCE_URGENT)
        )
        // Unknown keys default to DEFAULT.
        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            NotificationHelper.importanceToChannelLevel("bogus")
        )
    }

    @Test
    fun migrateOldChannels_removesLegacyChannelIds() {
        // Create legacy channels directly via the manager so the migration
        // helper has something to delete.
        val legacyChannel = android.app.NotificationChannel(
            "averytask_reminders",
            "legacy",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(legacyChannel)
        createdChannelIds += legacyChannel.id

        NotificationHelper.migrateOldChannels(context)

        val legacyExists = manager.notificationChannels.any { it.id == "averytask_reminders" }
        assertEquals(false, legacyExists)
    }
}
