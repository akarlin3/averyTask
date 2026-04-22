package com.averycorp.prismtask

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)

    /**
     * Production [com.averycorp.prismtask.PrismTaskApplication] implements
     * `Configuration.Provider` and wires WorkManager via its injected
     * `HiltWorkerFactory`. Instrumentation replaces the Application with
     * [HiltTestApplication], which does NOT implement `Configuration.Provider`,
     * so any MainActivity startup path that touches WorkManager (e.g. a
     * scheduled job firing during the Activity's lifetime) crashes with
     * "WorkManager is not initialized properly" and the Activity never reaches
     * setContent — turning every compose assertion into "No compose
     * hierarchies found in the app." Initialize WorkManager synchronously
     * here with the test-only synchronous executor so the test process has a
     * valid WorkManager by the time any Activity launches.
     */
    override fun onStart() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            targetContext,
            Configuration.Builder().build()
        )
        preGrantRuntimePermissions()
        super.onStart()
    }

    /**
     * MainActivity's onCreate fires a POST_NOTIFICATIONS permission request
     * on API 33+. When the test rule launches MainActivity, the system
     * permission dialog (`GrantPermissionsActivity`) slides in front, pauses
     * MainActivity, and Compose never renders into a visible window — so
     * every smoke test fails with "No compose hierarchies found in the app."
     * Grant the permissions via `UiAutomation` before the first Activity
     * launches so the prompt is a no-op.
     *
     * Also grant a small set of other runtime permissions the app may ask
     * for (contacts/calendar/storage) so any indirect prompt doesn't race
     * with a test later in the suite.
     */
    private fun preGrantRuntimePermissions() {
        val pkg = targetContext.packageName
        val permissions = listOf(
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR"
        )
        val automation = uiAutomation
        for (p in permissions) {
            try {
                automation.executeShellCommand("pm grant $pkg $p").close()
            } catch (_: Exception) {
                // Best-effort — grant may already be set or the permission
                // may not be declared on the current SDK; tests that depend
                // on a specific grant already have per-test GrantPermissionRule.
            }
        }
    }
}
