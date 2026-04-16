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
 * Periodically pulls new/changed/deleted events from the backend so tasks
 * and Today Glance events stay in sync without the user opening Settings.
 * Scheduled by [CalendarSyncScheduler]; runs every 15 minutes (or the
 * user's chosen frequency) when the device has network connectivity.
 */
@HiltWorker
class CalendarSyncWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val calendarSyncRepository: CalendarSyncRepository
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val outcome = calendarSyncRepository.pullUpdates()
        return outcome.fold(
            onSuccess = { Result.success() },
            onFailure = {
                Log.w(TAG, "Calendar pull failed", it)
                Result.retry()
            }
        )
    }

    companion object {
        private const val TAG = "CalendarSyncWorker"
    }
}
