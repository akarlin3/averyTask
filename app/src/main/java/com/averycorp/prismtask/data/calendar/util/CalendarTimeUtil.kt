package com.averycorp.prismtask.data.calendar.util

import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.EventDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.TimeZone

/**
 * Time helpers for translating between PrismTask's task fields and Google
 * Calendar's `EventDateTime` representation. Extracted from the now-removed
 * device calendar path; `toUtcDayStartMillis` and the all-day detection
 * rules are preserved here so the backend-mediated push/pull path uses
 * exactly the same semantics as the legacy device path did for existing
 * user data.
 */
object CalendarTimeUtil {
    /**
     * Converts a local-midnight epoch-millis timestamp (the format stored
     * in [TaskEntity.dueDate]) to the epoch-millis of 00:00 UTC on the
     * same civil date in [zone]. Google Calendar all-day events use a
     * `date`-only representation; translating via this helper guarantees
     * the civil date lands on the same day regardless of the caller's
     * timezone offset.
     */
    fun toUtcDayStartMillis(
        localMidnightMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val localDate = Instant.ofEpochMilli(localMidnightMillis)
            .atZone(zone)
            .toLocalDate()
        return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    /**
     * True when the task has no time-of-day information — neither a
     * [TaskEntity.dueTime] offset nor a [TaskEntity.scheduledStartTime].
     */
    fun isAllDay(dueTime: Long?, scheduledStartTime: Long?): Boolean =
        (dueTime == null || dueTime <= 0L) &&
            (scheduledStartTime == null || scheduledStartTime <= 0L)

    /**
     * Builds a Google Calendar [EventDateTime] for either the start or end
     * of the event that represents [task]. All-day tasks use the `date`
     * field; timed tasks use `dateTime` + `timeZone`.
     *
     * For all-day events, the end date is the start date + 1 day, matching
     * Google's exclusive end-date convention.
     *
     * The timezone is the caller's system default unless the caller
     * overrides it — the backend should always pass the user's stored
     * timezone so events land correctly for users who travel.
     */
    fun taskToEventDateTime(
        task: TaskEntity,
        isStart: Boolean,
        zone: ZoneId = ZoneId.systemDefault()
    ): EventDateTime {
        val dueDate = task.dueDate ?: return EventDateTime()
        if (isAllDay(task.dueTime, task.scheduledStartTime)) {
            val startMs = toUtcDayStartMillis(dueDate, zone)
            val ms = if (isStart) startMs else startMs + DAY_MS
            return EventDateTime()
                .setDate(DateTime(true, ms, 0))
        }
        val startMillis = computeStartMillis(task)
        val durationMinutes = task.estimatedDuration ?: DEFAULT_DURATION_MINUTES
        val endMillis = startMillis + durationMinutes * 60_000L
        val ms = if (isStart) startMillis else endMillis
        return EventDateTime()
            .setDateTime(DateTime(Date(ms), TimeZone.getTimeZone(zone)))
            .setTimeZone(zone.id)
    }

    /**
     * Reverse of [taskToEventDateTime]: given a Google [EventDateTime],
     * returns the pair of `(dueDate, dueTime)` in the local-midnight epoch
     * convention used by [TaskEntity]. `dueTime` is `null` for all-day
     * events.
     */
    fun eventDateTimeToTaskFields(
        eventDateTime: EventDateTime,
        zone: ZoneId = ZoneId.systemDefault()
    ): Pair<Long, Long?> {
        val dateOnly = eventDateTime.date
        if (dateOnly != null) {
            // Google returns date-only as a DateTime at UTC 00:00; convert
            // to local midnight for the same civil date.
            val localDate = Instant.ofEpochMilli(dateOnly.value)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
            val localMidnight = localDate.atStartOfDay(zone).toInstant().toEpochMilli()
            return localMidnight to null
        }
        val dt = eventDateTime.dateTime ?: return 0L to null
        val instant = Instant.ofEpochMilli(dt.value)
        val zoned = instant.atZone(zone)
        val localMidnight = zoned.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val timeOfDayMs = instant.toEpochMilli() - localMidnight
        return localMidnight to timeOfDayMs
    }

    private fun computeStartMillis(task: TaskEntity): Long {
        val dueDate = task.dueDate ?: 0L
        return when {
            task.scheduledStartTime != null && task.scheduledStartTime > 0 ->
                task.scheduledStartTime
            task.dueTime != null && task.dueTime > 0 -> dueDate + task.dueTime
            else -> dueDate
        }
    }

    private const val DAY_MS = 86_400_000L
    private const val DEFAULT_DURATION_MINUTES = 60
}
