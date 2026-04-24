package com.averycorp.prismtask.sync.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test 9 — Concurrent edit, last-write-wins.
 *
 * Scenario: a task exists in Firestore with a known cloud_id. Device A
 * and device B both update the task's `description` within ~2 seconds.
 * B's update carries the later `updated_at` timestamp. After both
 * writes land and A pulls, the task's description must equal B's value
 * (B won), and exactly one task doc exists in Firestore (no duplicate).
 *
 * Implementation notes (for the PR that turns on the `@Ignore`):
 *  1. taskRepository.addTask(...) + push → capture cloud_id from
 *     database.syncMetadataDao().getByLocalAndType(taskId, "task")?.cloudId
 *  2. taskRepository.updateTask(task.copy(description = "A")) — A's edit
 *     with updated_at = now
 *  3. harness.writeAsDeviceB("tasks", cloudId, mapOf(
 *        "title" to originalTitle,
 *        "description" to "B",
 *        "updated_at" to now + 1000  // later → B wins
 *     ))
 *  4. push on A
 *  5. pull on A
 *  6. Assert Firestore "tasks" count == 1 and the doc's description == "B"
 *  7. Assert local task.description == "B"
 *
 * Left as `@Ignore` for PR2: requires reading `sync_metadata.cloud_id`
 * from the DAO directly (not currently exposed on TaskRepository), plus
 * care about SyncMapper's exact "updated_at" key name and how the push
 * path compares timestamps (client-side merge vs. server-authoritative).
 * Deserves a focused look at SyncService.pushUpdate() + SyncMapper.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Test9ConcurrentEditLastWriteWinsTest : SyncScenarioTestBase() {

    @Test
    @Ignore("TODO(PR2-followup): needs cloud_id extraction + SyncMapper timestamp field verification")
    fun concurrentEditSameTask_laterTimestampWins() {
        // See class KDoc for implementation sketch.
    }
}
