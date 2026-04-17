package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.calendar.CalendarEventInfo

/**
 * Read-side repository for Google Calendar events that render in the app
 * (Today Glance step in the Morning Check-In, Calendar widget). The
 * backend-mediated implementation lives in [CalendarSyncRepository]; this
 * interface lets consumers depend on a small surface and stay unaware of
 * the backend wiring.
 */
interface CalendarEventRepository {
    /**
     * Returns up to [limit] upcoming events whose start lies between [now]
     * and [dayEnd]. Returns an empty list when the user hasn't connected
     * Google Calendar, when the backend is unreachable, or when the cached
     * sync state has no matching rows.
     */
    suspend fun getTodayUpcomingEvents(
        now: Long,
        dayEnd: Long,
        limit: Int
    ): List<CalendarEventInfo>
}
