package com.averycorp.prismtask.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_prefs"
)

/**
 * Persists user-controlled notification settings:
 *  - per-type enable/disable flags for every notification surface,
 *  - a global importance/intrusiveness setting that maps to channel
 *    importance + builder priority,
 *  - a default reminder lead-time used to pre-fill `reminderOffset` on
 *    newly-created tasks.
 *
 * Takes a [DataStore] directly so it can be unit-tested without an Android
 * Context; production wiring lives in
 * [com.averycorp.prismtask.di.PreferencesModule]. The [from] factory exists
 * for non-Hilt callers like [com.averycorp.prismtask.notifications.NotificationHelper]
 * that only have a [Context] on hand.
 *
 * Everything is read/written via [Flow]/`suspend` setters — callers
 * (workers, the notification helper, ViewModels) read with `.first()`
 * before posting, or collect the flow when they need live updates.
 */
class NotificationPreferences(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        // Per-type enable flags (default true)
        private val TASK_REMINDERS_ENABLED = booleanPreferencesKey("task_reminders_enabled")
        private val TIMER_ALERTS_ENABLED = booleanPreferencesKey("timer_alerts_enabled")
        private val MEDICATION_REMINDERS_ENABLED = booleanPreferencesKey("medication_reminders_enabled")
        private val DAILY_BRIEFING_ENABLED = booleanPreferencesKey("daily_briefing_enabled")
        private val EVENING_SUMMARY_ENABLED = booleanPreferencesKey("evening_summary_enabled")
        private val WEEKLY_SUMMARY_ENABLED = booleanPreferencesKey("weekly_summary_enabled")
        private val OVERLOAD_ALERTS_ENABLED = booleanPreferencesKey("overload_alerts_enabled")
        private val REENGAGEMENT_ENABLED = booleanPreferencesKey("reengagement_enabled")

        // Full-screen heads-up reminder (opens in full-screen over lock screen)
        private val FULL_SCREEN_NOTIFICATIONS_ENABLED = booleanPreferencesKey("full_screen_notifications_enabled")

        // Play reminders at alarm volume (bypasses silent ringer)
        private val OVERRIDE_VOLUME_ENABLED = booleanPreferencesKey("override_volume_enabled")

        // Use a long, repeating vibration pattern for reminders
        private val REPEATING_VIBRATION_ENABLED = booleanPreferencesKey("repeating_vibration_enabled")

        // Importance / intrusiveness
        private val NOTIFICATION_IMPORTANCE = stringPreferencesKey("notification_importance")

        /**
         * Tracks the importance level the channel was *last* created with so
         * the next change can delete the stale channel before creating the
         * new one. Channel importance is immutable after creation, so we use
         * a per-importance suffix on the channel ID.
         */
        private val PREVIOUS_IMPORTANCE = stringPreferencesKey("previous_importance")

        // Default reminder lead time (millis before due date)
        private val DEFAULT_REMINDER_OFFSET = longPreferencesKey("default_reminder_offset")

        const val IMPORTANCE_MINIMAL = "minimal"
        const val IMPORTANCE_STANDARD = "standard"
        const val IMPORTANCE_URGENT = "urgent"

        const val DEFAULT_IMPORTANCE = IMPORTANCE_STANDARD

        /** Default reminder offset = 15 minutes before the task is due. */
        const val DEFAULT_REMINDER_OFFSET_MS = 900_000L

        /** Sentinel meaning "user has opted out of any default offset". */
        const val OFFSET_NONE = -1L

        val ALL_IMPORTANCES = listOf(IMPORTANCE_MINIMAL, IMPORTANCE_STANDARD, IMPORTANCE_URGENT)

        val ALL_REMINDER_OFFSETS = listOf(
            0L,
            300_000L,
            900_000L,
            1_800_000L,
            3_600_000L,
            86_400_000L,
            OFFSET_NONE
        )

        /** Factory for non-Hilt callers that only have a [Context]. */
        fun from(context: Context): NotificationPreferences =
            NotificationPreferences(context.notificationDataStore)
    }

    // region Per-type enable flags

    val taskRemindersEnabled: Flow<Boolean> = dataStore.data
        .map { it[TASK_REMINDERS_ENABLED] ?: true }

    suspend fun setTaskRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[TASK_REMINDERS_ENABLED] = enabled }
    }

    val timerAlertsEnabled: Flow<Boolean> = dataStore.data
        .map { it[TIMER_ALERTS_ENABLED] ?: true }

    suspend fun setTimerAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[TIMER_ALERTS_ENABLED] = enabled }
    }

    val medicationRemindersEnabled: Flow<Boolean> = dataStore.data
        .map { it[MEDICATION_REMINDERS_ENABLED] ?: true }

    suspend fun setMedicationRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[MEDICATION_REMINDERS_ENABLED] = enabled }
    }

    val dailyBriefingEnabled: Flow<Boolean> = dataStore.data
        .map { it[DAILY_BRIEFING_ENABLED] ?: true }

    suspend fun setDailyBriefingEnabled(enabled: Boolean) {
        dataStore.edit { it[DAILY_BRIEFING_ENABLED] = enabled }
    }

    val eveningSummaryEnabled: Flow<Boolean> = dataStore.data
        .map { it[EVENING_SUMMARY_ENABLED] ?: true }

    suspend fun setEveningSummaryEnabled(enabled: Boolean) {
        dataStore.edit { it[EVENING_SUMMARY_ENABLED] = enabled }
    }

    val weeklySummaryEnabled: Flow<Boolean> = dataStore.data
        .map { it[WEEKLY_SUMMARY_ENABLED] ?: true }

    suspend fun setWeeklySummaryEnabled(enabled: Boolean) {
        dataStore.edit { it[WEEKLY_SUMMARY_ENABLED] = enabled }
    }

    val overloadAlertsEnabled: Flow<Boolean> = dataStore.data
        .map { it[OVERLOAD_ALERTS_ENABLED] ?: true }

    suspend fun setOverloadAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[OVERLOAD_ALERTS_ENABLED] = enabled }
    }

    val reengagementEnabled: Flow<Boolean> = dataStore.data
        .map { it[REENGAGEMENT_ENABLED] ?: true }

    suspend fun setReengagementEnabled(enabled: Boolean) {
        dataStore.edit { it[REENGAGEMENT_ENABLED] = enabled }
    }

    // endregion

    // region Intrusive delivery behaviors
    //
    // Three orthogonal toggles that change *how* a fired reminder is
    // delivered. All default OFF so users opt in explicitly — they affect
    // the lock screen, audio stream, and vibrator. Because NotificationChannel
    // sound and vibration are immutable after creation, changing
    // [overrideVolumeEnabled] or [repeatingVibrationEnabled] causes the
    // channel to be recreated under a new ID (see NotificationHelper).

    val fullScreenNotificationsEnabled: Flow<Boolean> = dataStore.data
        .map { it[FULL_SCREEN_NOTIFICATIONS_ENABLED] ?: false }

    suspend fun setFullScreenNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[FULL_SCREEN_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun getFullScreenNotificationsEnabledOnce(): Boolean =
        fullScreenNotificationsEnabled.first()

    val overrideVolumeEnabled: Flow<Boolean> = dataStore.data
        .map { it[OVERRIDE_VOLUME_ENABLED] ?: false }

    suspend fun setOverrideVolumeEnabled(enabled: Boolean) {
        dataStore.edit { it[OVERRIDE_VOLUME_ENABLED] = enabled }
    }

    suspend fun getOverrideVolumeEnabledOnce(): Boolean =
        overrideVolumeEnabled.first()

    val repeatingVibrationEnabled: Flow<Boolean> = dataStore.data
        .map { it[REPEATING_VIBRATION_ENABLED] ?: false }

    suspend fun setRepeatingVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[REPEATING_VIBRATION_ENABLED] = enabled }
    }

    suspend fun getRepeatingVibrationEnabledOnce(): Boolean =
        repeatingVibrationEnabled.first()

    // endregion

    // region Importance

    val importance: Flow<String> = dataStore.data.map {
        val stored = it[NOTIFICATION_IMPORTANCE] ?: DEFAULT_IMPORTANCE
        if (stored in ALL_IMPORTANCES) stored else DEFAULT_IMPORTANCE
    }

    suspend fun setImportance(level: String) {
        val normalized = if (level in ALL_IMPORTANCES) level else DEFAULT_IMPORTANCE
        dataStore.edit { it[NOTIFICATION_IMPORTANCE] = normalized }
    }

    suspend fun getImportanceOnce(): String = importance.first()

    val previousImportance: Flow<String?> = dataStore.data.map {
        it[PREVIOUS_IMPORTANCE]
    }

    suspend fun getPreviousImportanceOnce(): String? = dataStore.data
        .first()[PREVIOUS_IMPORTANCE]

    suspend fun setPreviousImportance(level: String) {
        dataStore.edit { it[PREVIOUS_IMPORTANCE] = level }
    }

    // endregion

    // region Default reminder offset

    val defaultReminderOffset: Flow<Long> = dataStore.data.map {
        it[DEFAULT_REMINDER_OFFSET] ?: DEFAULT_REMINDER_OFFSET_MS
    }

    suspend fun setDefaultReminderOffset(offset: Long) {
        dataStore.edit { it[DEFAULT_REMINDER_OFFSET] = offset }
    }

    suspend fun getDefaultReminderOffsetOnce(): Long = defaultReminderOffset.first()

    // endregion
}
