package com.averycorp.prismtask.data.calendar

/**
 * Dispatches single-task push/delete operations to the backend-mediated
 * Google Calendar sync pipeline. Introduced when the device-calendar code
 * path was removed; the concrete implementation in
 * [com.averycorp.prismtask.workers.CalendarPushWorkerDispatcher] enqueues a
 * `OneTimeWorkRequest` so push/delete happens off the main thread and
 * retries on transient failure.
 *
 * Repositories inject this instead of calling WorkManager directly so unit
 * tests can mock calendar side effects without a Context.
 */
interface CalendarPushDispatcher {
    /** Enqueue a push for [taskId]. Creates or updates the Google event. */
    fun enqueuePushTask(taskId: Long)

    /** Enqueue a delete for the Google event previously associated with [taskId]. */
    fun enqueueDeleteTaskEvent(taskId: Long)
}
