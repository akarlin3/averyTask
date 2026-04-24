package com.averycorp.prismtask.sync.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test 10 — Concurrent delete vs. offline edit: delete wins.
 *
 * Scenario: a task exists in Firestore. Device A goes offline and edits
 * its description. Device B deletes the task's Firestore doc. Device A
 * reconnects, pushes (its edit to a non-existent doc), and pulls. The
 * local task row must be deleted (delete wins on conflict).
 *
 * Implementation notes (for the PR that turns on the `@Ignore`):
 *  1. addTask + push → capture cloud_id.
 *  2. setDeviceAOffline()
 *  3. A edits: updateTask(task.copy(description = "offline-edit"))
 *  4. deleteAsDeviceB("tasks", cloudId)
 *  5. setDeviceAOnline()
 *  6. push on A (may error silently — Firestore doc is gone).
 *  7. pull on A.
 *  8. Assert database.taskDao().getAllTasksOnce() doesn't contain the task.
 *  9. Assert Firestore "tasks" count == 0.
 *
 * Left as `@Ignore` for PR2: depends on SyncService.pushUpdate
 * semantics when the remote doc has been deleted. If pushUpdate
 * succeeds (creates the doc anew), this scenario's assertion flips —
 * the test may surface a real conflict-resolution bug that routes to a
 * separate session per PR2's scope guardrails ("Do NOT modify production
 * sync code to make tests pass").
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Test10ConcurrentDeleteTest : SyncScenarioTestBase() {

    @Test
    @Ignore("TODO(PR2-followup): needs SyncService.pushUpdate behavior when remote doc missing")
    fun concurrentDeleteVsEdit_deleteWins() {
        // See class KDoc for implementation sketch.
    }
}
