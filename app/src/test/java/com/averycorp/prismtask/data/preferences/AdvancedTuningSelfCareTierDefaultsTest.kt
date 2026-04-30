package com.averycorp.prismtask.data.preferences

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [33], application = Application::class)
class AdvancedTuningSelfCareTierDefaultsTest {

    @Test
    fun setSelfCareTierDefaults_roundTrips_andHistoricalDefaultsApplyOnFirstRead() = runTest {
        val prefs = AdvancedTuningPreferences(ApplicationProvider.getApplicationContext())

        // First read on a clean Robolectric DataStore returns the historical
        // penultimate-of-order tiers the SelfCareViewModel previously hard-coded.
        val initial = prefs.getSelfCareTierDefaults().first()
        assertEquals("solid", initial.morning)
        assertEquals("solid", initial.bedtime)
        assertEquals("prescription", initial.medication)
        assertEquals("regular", initial.housework)

        val updated = SelfCareTierDefaults(
            morning = "survival",
            bedtime = "full",
            medication = "essential",
            housework = "deep"
        )
        prefs.setSelfCareTierDefaults(updated)
        assertEquals(updated, prefs.getSelfCareTierDefaults().first())
    }
}
