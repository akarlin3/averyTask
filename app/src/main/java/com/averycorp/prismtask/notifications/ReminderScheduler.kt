package com.averycorp.prismtask.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.averycorp.prismtask.data.local.dao.TaskDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao
) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    fun scheduleReminder(
        taskId: Long,
        taskTitle: String,
        taskDescription: String?,
        dueDate: Long,
        reminderOffset: Long
    ) {
        val triggerTime = dueDate - reminderOffset
        if (triggerTime <= System.currentTimeMillis()) return

        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra("taskId", taskId)
            putExtra("taskTitle", taskTitle)
            putExtra("taskDescription", taskDescription)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // Fall back to inexact alarm when exact alarm permission is not granted
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w("ReminderScheduler", "Exact alarm not allowed, falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelReminder(taskId: Long) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    suspend fun rescheduleAllReminders() {
        val tasks = taskDao.getIncompleteTasksWithReminders()
        for (task in tasks) {
            val dueDate = task.dueDate ?: continue
            val offset = task.reminderOffset ?: continue
            scheduleReminder(task.id, task.title, task.description, dueDate, offset)
        }
    }

    companion object {
        /**
         * Pure helper: compute the wall-clock time at which a reminder should
         * fire given a task's due date and how far in advance the user wants
         * to be nudged. Returned timestamp may be in the past — callers should
         * use [isInFuture] to decide whether to actually schedule an alarm.
         */
        fun computeTriggerTime(dueDate: Long, reminderOffset: Long): Long =
            dueDate - reminderOffset

        /**
         * Pure helper: the alarm should only be registered when the computed
         * trigger time is strictly in the future. Mirrors the guard clause in
         * [scheduleReminder].
         */
        fun isInFuture(triggerTime: Long, now: Long): Boolean = triggerTime > now
    }
}
