package com.averycorp.prismtask.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.averycorp.prismtask.MainActivity
import com.averycorp.prismtask.data.preferences.NotificationPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object NotificationHelper {
    private const val BASE_CHANNEL_ID = "prismtask_reminders"
    private const val CHANNEL_NAME = "Task Reminders"
    private const val BASE_MED_CHANNEL_ID = "prismtask_medication_reminders"
    private const val MED_CHANNEL_NAME = "Medication Reminders"
    private const val BASE_TIMER_CHANNEL_ID = "prismtask_timer_alerts"
    private const val TIMER_CHANNEL_NAME = "Timer Alerts"
    private const val TIMER_NOTIFICATION_ID = 8_001

    private const val LEGACY_CHANNEL_ID = "averytask_reminders"
    private const val LEGACY_MED_CHANNEL_ID = "averytask_medication_reminders"

    /**
     * Long, repeating-feel vibration used when the user enables "Buzz
     * Repeatedly". Android plays the channel pattern once per notification,
     * so we approximate a repeating buzz by laying a dense sequence of
     * pulses over ~10 seconds. Users dismissing or tapping the notification
     * stops it naturally.
     */
    private val REPEATING_VIBRATION_PATTERN = longArrayOf(
        0,
        500, 300, 500, 300, 500, 300, 500, 300,
        500, 300, 500, 300, 500, 300, 500, 300,
        500, 300, 500, 300
    )

    /**
     * Bundle of user-configurable delivery-style flags. Channels are
     * immutable for sound / vibration / importance, so each unique
     * combination gets its own channel ID via [channelSuffix].
     */
    private data class Style(
        val importance: String,
        val fullScreen: Boolean,
        val overrideVolume: Boolean,
        val repeatingVibration: Boolean
    )

    private fun currentStyle(context: Context): Style = runBlocking {
        val prefs = NotificationPreferences.from(context)
        Style(
            importance = prefs.getImportanceOnce(),
            fullScreen = prefs.getFullScreenNotificationsEnabledOnce(),
            overrideVolume = prefs.getOverrideVolumeEnabledOnce(),
            repeatingVibration = prefs.getRepeatingVibrationEnabledOnce()
        )
    }

    private fun previousImportance(context: Context): String? = runBlocking {
        NotificationPreferences.from(context).getPreviousImportanceOnce()
    }

    private fun recordImportance(context: Context, importance: String) {
        runBlocking {
            NotificationPreferences.from(context).setPreviousImportance(importance)
        }
    }

    private fun channelSuffix(style: Style): String = buildString {
        append('_').append(style.importance)
        if (style.fullScreen) append("_fsi")
        if (style.overrideVolume) append("_alrm")
        if (style.repeatingVibration) append("_rvib")
    }

    fun channelIdFor(base: String, importance: String): String = "${base}_$importance"

    private fun channelIdFor(base: String, style: Style): String = base + channelSuffix(style)

    fun importanceToChannelLevel(importance: String): Int = when (importance) {
        NotificationPreferences.IMPORTANCE_MINIMAL -> NotificationManager.IMPORTANCE_LOW
        NotificationPreferences.IMPORTANCE_URGENT -> NotificationManager.IMPORTANCE_HIGH
        else -> NotificationManager.IMPORTANCE_DEFAULT
    }

    private fun effectiveChannelImportance(style: Style): Int {
        // Full-screen intents and heads-up alarm behavior require HIGH.
        // When the user opts into either, bump the channel so the behavior
        // actually takes effect regardless of the "Importance" picker.
        val base = importanceToChannelLevel(style.importance)
        return if (style.fullScreen || style.overrideVolume) {
            maxOf(base, NotificationManager.IMPORTANCE_HIGH)
        } else {
            base
        }
    }

    fun importanceToBuilderPriority(importance: String): Int = when (importance) {
        NotificationPreferences.IMPORTANCE_MINIMAL -> NotificationCompat.PRIORITY_LOW
        NotificationPreferences.IMPORTANCE_URGENT -> NotificationCompat.PRIORITY_HIGH
        else -> NotificationCompat.PRIORITY_DEFAULT
    }

    private fun effectiveBuilderPriority(style: Style): Int {
        val base = importanceToBuilderPriority(style.importance)
        return if (style.fullScreen || style.overrideVolume) {
            maxOf(base, NotificationCompat.PRIORITY_HIGH)
        } else {
            base
        }
    }

    /**
     * Drops every channel the app has previously created for [base] under
     * any style combination other than the current one, so a style change
     * wipes stale channels (whose sound/vibration/importance are immutable).
     */
    private fun deleteStaleChannels(context: Context, base: String, style: Style) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val currentId = channelIdFor(base, style)
        // Bare legacy channel (pre-importance-suffix scheme).
        manager.deleteNotificationChannel(base)
        for (importance in NotificationPreferences.ALL_IMPORTANCES) {
            for (fsi in listOf(false, true)) {
                for (alrm in listOf(false, true)) {
                    for (rvib in listOf(false, true)) {
                        val candidate = Style(importance, fsi, alrm, rvib)
                        val id = channelIdFor(base, candidate)
                        if (id != currentId) {
                            manager.deleteNotificationChannel(id)
                        }
                    }
                }
            }
        }
    }

    private fun buildChannel(
        id: String,
        name: String,
        description: String,
        style: Style
    ): NotificationChannel = NotificationChannel(
        id,
        name,
        effectiveChannelImportance(style)
    ).apply {
        this.description = description
        if (style.overrideVolume) {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(alarmUri, attrs)
            setBypassDnd(true)
        }
        if (style.repeatingVibration) {
            enableVibration(true)
            vibrationPattern = REPEATING_VIBRATION_PATTERN
        }
    }

    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        migrateOldChannels(context)
        val style = currentStyle(context)
        deleteStaleChannels(context, BASE_CHANNEL_ID, style)
        val channel = buildChannel(
            id = channelIdFor(BASE_CHANNEL_ID, style),
            name = CHANNEL_NAME,
            description = "Reminders for upcoming tasks",
            style = style
        )
        manager.createNotificationChannel(channel)
        recordImportance(context, style.importance)
    }

    fun migrateOldChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.deleteNotificationChannel(LEGACY_MED_CHANNEL_ID)
    }

    /**
     * Applies the user's delivery-style preferences (full-screen, alarm
     * stream, repeating vibration) to [builder]. Callers still set their
     * own content/actions — this only touches priority, category, sound,
     * vibration, and the full-screen intent.
     */
    private fun applyStyle(
        builder: NotificationCompat.Builder,
        style: Style,
        tapPending: PendingIntent
    ) {
        // API 26+ uses the channel's sound/vibration; builder-level calls
        // are kept only for flags that still have runtime effect
        // (priority, category, full-screen intent).
        builder.priority = effectiveBuilderPriority(style)
        if (style.overrideVolume) {
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
        }
        if (style.fullScreen) {
            builder.setFullScreenIntent(tapPending, true)
        }
    }

    fun showTaskReminder(
        context: Context,
        taskId: Long,
        taskTitle: String,
        taskDescription: String?
    ) {
        val prefs = NotificationPreferences.from(context)
        val enabled = runBlocking { prefs.taskRemindersEnabled.first() }
        if (!enabled) {
            Log.d("NotificationHelper", "Task reminders disabled — skipping task=$taskId")
            return
        }
        Log.d("NotificationHelper", "Showing notification for task=$taskId")
        createNotificationChannel(context)
        val style = currentStyle(context)
        val channelId = channelIdFor(BASE_CHANNEL_ID, style)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, CompleteTaskReceiver::class.java).apply {
            putExtra("taskId", taskId)
        }
        val completePending = PendingIntent.getBroadcast(
            context,
            taskId.toInt() + 100_000,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$taskTitle is coming up")
            .setContentText(taskDescription ?: "Ready when you are.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Complete",
                completePending
            )
        applyStyle(builder, style, tapPending)

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(taskId.toInt(), builder.build())
    }

    private fun createMedicationChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val style = currentStyle(context)
        deleteStaleChannels(context, BASE_MED_CHANNEL_ID, style)
        val channel = buildChannel(
            id = channelIdFor(BASE_MED_CHANNEL_ID, style),
            name = MED_CHANNEL_NAME,
            description = "Reminders for medication and timed habits",
            style = style
        )
        manager.createNotificationChannel(channel)
        recordImportance(context, style.importance)
    }

    fun showMedicationReminder(
        context: Context,
        habitId: Long,
        habitName: String,
        habitDescription: String?,
        intervalMillis: Long,
        doseNumber: Int = 0,
        totalDoses: Int = 1
    ) {
        val prefs = NotificationPreferences.from(context)
        val enabled = runBlocking { prefs.medicationRemindersEnabled.first() }
        if (!enabled) {
            Log.d("NotificationHelper", "Medication reminders disabled — skipping habit=$habitId")
            return
        }
        createMedicationChannel(context)
        val style = currentStyle(context)
        val channelId = channelIdFor(BASE_MED_CHANNEL_ID, style)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            habitId.toInt() + 200_000,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val logIntent = Intent(context, LogMedicationReceiver::class.java).apply {
            putExtra("habitId", habitId)
        }
        val logPending = PendingIntent.getBroadcast(
            context,
            habitId.toInt() + 300_000,
            logIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalText = formatInterval(intervalMillis)
        val contentText = habitDescription ?: "$habitName \u2014 whenever you're ready."

        val doseInfo = if (totalDoses > 1 && doseNumber > 0) " (dose $doseNumber of $totalDoses)" else ""
        val title = "$habitName$doseInfo"

        val bigText = if (totalDoses > 1 && doseNumber > 0 && doseNumber < totalDoses) {
            "$contentText\nDose $doseNumber of $totalDoses \u2022 next reminder $intervalText after logging."
        } else if (totalDoses > 1 && doseNumber >= totalDoses) {
            "$contentText\nFinal dose ($doseNumber of $totalDoses)."
        } else {
            "$contentText\nNext reminder $intervalText after logging."
        }

        val builder = NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Log",
                logPending
            )
        applyStyle(builder, style, tapPending)

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(habitId.toInt() + 200_000, builder.build())
    }

    fun showMedStepReminder(
        context: Context,
        stepId: String,
        medName: String,
        medNote: String
    ) {
        val prefs = NotificationPreferences.from(context)
        val enabled = runBlocking { prefs.medicationRemindersEnabled.first() }
        if (!enabled) {
            Log.d("NotificationHelper", "Medication reminders disabled — skipping step=$stepId")
            return
        }
        createMedicationChannel(context)
        val style = currentStyle(context)
        val channelId = channelIdFor(BASE_MED_CHANNEL_ID, style)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            stepId.hashCode() + 400_000,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (medNote.isNotEmpty()) medNote else "$medName \u2014 whenever you're ready."

        val builder = NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$medName \u2014 Heads Up")
            .setContentText(contentText)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
        applyStyle(builder, style, tapPending)

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(stepId.hashCode() + 400_000, builder.build())
    }

    private fun createTimerChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val style = currentStyle(context)
        deleteStaleChannels(context, BASE_TIMER_CHANNEL_ID, style)
        val channel = buildChannel(
            id = channelIdFor(BASE_TIMER_CHANNEL_ID, style),
            name = TIMER_CHANNEL_NAME,
            description = "Alerts when a Timer countdown completes",
            style = style
        )
        manager.createNotificationChannel(channel)
        recordImportance(context, style.importance)
    }

    fun showTimerCompleteNotification(context: Context, mode: String) {
        val prefs = NotificationPreferences.from(context)
        val enabled = runBlocking { prefs.timerAlertsEnabled.first() }
        if (!enabled) {
            Log.d("NotificationHelper", "Timer alerts disabled — skipping mode=$mode")
            return
        }
        createTimerChannel(context)
        val style = currentStyle(context)
        val channelId = channelIdFor(BASE_TIMER_CHANNEL_ID, style)

        val isBreak = mode.equals("BREAK", ignoreCase = true)
        val title = if (isBreak) "Break Complete!" else "Timer Complete!"
        val body = if (isBreak) {
            "Ready to get back to focus?"
        } else {
            "Nice work \u2014 time for a break."
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            TIMER_NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
        applyStyle(builder, style, tapPending)

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(TIMER_NOTIFICATION_ID, builder.build())
    }

    private fun formatInterval(millis: Long): String {
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }
}
