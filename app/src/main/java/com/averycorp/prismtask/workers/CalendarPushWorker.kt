package com.averycorp.prismtask.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.averycorp.prismtask.data.repository.CalendarSyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Pushes a single task's create/update/delete to the backend-mediated
 * Google Calendar sync endpoint. Enqueued by
 * [com.averycorp.prismtask.data.calendar.DefaultCalendarPushDispatcher].
 *
 * Retries on transient failures (network, 5xx) via WorkManager's
 * exponential backoff. Returns [Result.failure] on auth errors so the
 * user sees a re-auth prompt the next time they open settings.
 */
@HiltWorker
class CalendarPushWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val calendarSyncRepository: CalendarSyncRepository
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId <= 0L) return Result.failure()
        val op = inputData.getString(KEY_OP) ?: return Result.failure()
        return try {
            val outcome = when (op) {
                OP_UPSERT -> calendarSyncRepository.pushTask(taskId)
                OP_DELETE -> calendarSyncRepository.deleteTaskEvent(taskId)
                else -> return Result.failure()
            }
            when (outcome) {
                CalendarSyncRepository.PushOutcome.SUCCESS -> Result.success()
                CalendarSyncRepository.PushOutcome.RETRYABLE -> Result.retry()
                CalendarSyncRepository.PushOutcome.AUTH_REQUIRED -> Result.failure()
                CalendarSyncRepository.PushOutcome.DISABLED -> Result.success()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Calendar push failed for task=$taskId op=$op", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_OP = "op"
        const val OP_UPSERT = "upsert"
        const val OP_DELETE = "delete"
        private const val TAG = "CalendarPushWorker"
    }
}
