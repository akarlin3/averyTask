package com.averycorp.prismtask.data.remote.api

import com.google.gson.annotations.SerializedName

/**
 * Data models exchanged with the PrismTask FastAPI backend.
 *
 * Field names use snake_case to match the backend JSON contract; Kotlin
 * property names use camelCase via [SerializedName].
 */

// region Auth

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String
)

// endregion

// region Tasks

data class ParseRequest(
    val text: String
)

data class ParsedTaskResponse(
    val title: String,
    @SerializedName("project_suggestion") val projectSuggestion: String?,
    @SerializedName("tag_suggestions") val tagSuggestions: List<String>?,
    @SerializedName("due_date") val dueDate: String?,
    @SerializedName("due_time") val dueTime: String?,
    val priority: Int?,
    @SerializedName("recurrence_hint") val recurrenceHint: String?,
    val confidence: Double?
)

// endregion

// region App version

data class VersionResponse(
    @SerializedName("version_code") val versionCode: Int,
    @SerializedName("version_name") val versionName: String,
    @SerializedName("release_notes") val releaseNotes: String?,
    @SerializedName("apk_url") val apkUrl: String,
    @SerializedName("apk_size_bytes") val apkSizeBytes: Long,
    val sha256: String?,
    @SerializedName("is_mandatory") val isMandatory: Boolean
)

// endregion

// region AI Productivity

data class EisenhowerRequest(
    @SerializedName("task_ids") val taskIds: List<Long>? = null
)

data class EisenhowerCategorization(
    @SerializedName("task_id") val taskId: Long,
    val quadrant: String,
    val reason: String
)

data class EisenhowerSummary(
    @SerializedName("Q1") val q1: Int = 0,
    @SerializedName("Q2") val q2: Int = 0,
    @SerializedName("Q3") val q3: Int = 0,
    @SerializedName("Q4") val q4: Int = 0
)

data class EisenhowerResponse(
    val categorizations: List<EisenhowerCategorization>,
    val summary: EisenhowerSummary
)

data class PomodoroRequest(
    @SerializedName("available_minutes") val availableMinutes: Int = 120,
    @SerializedName("session_length") val sessionLength: Int = 25,
    @SerializedName("break_length") val breakLength: Int = 5,
    @SerializedName("long_break_length") val longBreakLength: Int = 15,
    @SerializedName("focus_preference") val focusPreference: String = "balanced"
)

data class SessionTaskResponse(
    @SerializedName("task_id") val taskId: Long,
    val title: String,
    @SerializedName("allocated_minutes") val allocatedMinutes: Int
)

data class PomodoroSessionResponse(
    @SerializedName("session_number") val sessionNumber: Int,
    val tasks: List<SessionTaskResponse>,
    val rationale: String
)

data class SkippedTaskResponse(
    @SerializedName("task_id") val taskId: Long,
    val reason: String
)

data class PomodoroResponse(
    val sessions: List<PomodoroSessionResponse>,
    @SerializedName("total_sessions") val totalSessions: Int,
    @SerializedName("total_work_minutes") val totalWorkMinutes: Int,
    @SerializedName("total_break_minutes") val totalBreakMinutes: Int,
    @SerializedName("skipped_tasks") val skippedTasks: List<SkippedTaskResponse> = emptyList()
)

// endregion

// region AI Daily Briefing

data class DailyBriefingRequest(
    val date: String? = null
)

data class BriefingPriorityResponse(
    @SerializedName("task_id") val taskId: Long,
    val title: String,
    val reason: String
)

data class SuggestedTaskResponse(
    @SerializedName("task_id") val taskId: Long,
    val title: String,
    @SerializedName("suggested_time") val suggestedTime: String,
    val reason: String
)

data class DailyBriefingResponse(
    val greeting: String,
    @SerializedName("top_priorities") val topPriorities: List<BriefingPriorityResponse>,
    @SerializedName("heads_up") val headsUp: List<String> = emptyList(),
    @SerializedName("suggested_order") val suggestedOrder: List<SuggestedTaskResponse>,
    @SerializedName("habit_reminders") val habitReminders: List<String> = emptyList(),
    @SerializedName("day_type") val dayType: String
)

// endregion

// region AI Weekly Plan

data class WeeklyPlanPreferencesRequest(
    @SerializedName("work_days") val workDays: List<String> = listOf("MO", "TU", "WE", "TH", "FR"),
    @SerializedName("focus_hours_per_day") val focusHoursPerDay: Int = 6,
    @SerializedName("prefer_front_loading") val preferFrontLoading: Boolean = true
)

data class WeeklyPlanRequest(
    @SerializedName("week_start") val weekStart: String? = null,
    val preferences: WeeklyPlanPreferencesRequest = WeeklyPlanPreferencesRequest()
)

data class PlannedTaskResponse(
    @SerializedName("task_id") val taskId: Long,
    val title: String,
    @SerializedName("suggested_time") val suggestedTime: String,
    @SerializedName("duration_minutes") val durationMinutes: Int,
    val reason: String
)

data class DayPlanResponse(
    val date: String,
    val tasks: List<PlannedTaskResponse>,
    @SerializedName("total_hours") val totalHours: Double,
    @SerializedName("calendar_events") val calendarEvents: List<String> = emptyList(),
    val habits: List<String> = emptyList()
)

data class UnscheduledTaskResponse(
    @SerializedName("task_id") val taskId: Long,
    val title: String,
    val reason: String
)

data class WeeklyPlanResponse(
    val plan: Map<String, DayPlanResponse>,
    val unscheduled: List<UnscheduledTaskResponse> = emptyList(),
    @SerializedName("week_summary") val weekSummary: String,
    val tips: List<String> = emptyList()
)

// endregion

// region AI Time Block

data class TimeBlockRequest(
    val date: String? = null,
    @SerializedName("day_start") val dayStart: String = "09:00",
    @SerializedName("day_end") val dayEnd: String = "18:00",
    @SerializedName("block_size_minutes") val blockSizeMinutes: Int = 30,
    @SerializedName("include_breaks") val includeBreaks: Boolean = true,
    @SerializedName("break_frequency_minutes") val breakFrequencyMinutes: Int = 90,
    @SerializedName("break_duration_minutes") val breakDurationMinutes: Int = 15
)

data class ScheduleBlockResponse(
    val start: String,
    val end: String,
    val type: String,
    @SerializedName("task_id") val taskId: Long?,
    val title: String,
    val reason: String
)

data class TimeBlockStatsResponse(
    @SerializedName("total_work_minutes") val totalWorkMinutes: Int,
    @SerializedName("total_break_minutes") val totalBreakMinutes: Int,
    @SerializedName("total_free_minutes") val totalFreeMinutes: Int,
    @SerializedName("tasks_scheduled") val tasksScheduled: Int,
    @SerializedName("tasks_deferred") val tasksDeferred: Int
)

data class TimeBlockResponse(
    val schedule: List<ScheduleBlockResponse>,
    @SerializedName("unscheduled_tasks") val unscheduledTasks: List<UnscheduledTaskResponse> = emptyList(),
    val stats: TimeBlockStatsResponse
)

// endregion

// region AI Coaching

data class CoachingRequest(
    val trigger: String,
    @SerializedName("task_id") val taskId: Long? = null,
    val context: CoachingContext,
    val tier: String
)

data class CoachingContext(
    // Trigger 1 (stuck) & shared
    @SerializedName("task_title") val taskTitle: String? = null,
    @SerializedName("task_description") val taskDescription: String? = null,
    @SerializedName("days_since_creation") val daysSinceCreation: Int? = null,
    @SerializedName("due_date") val dueDate: String? = null,
    val priority: Int? = null,
    @SerializedName("subtask_count") val subtaskCount: Int? = null,
    @SerializedName("completed_subtasks") val completedSubtasks: Int? = null,
    @SerializedName("open_count") val openCount: Int? = null,

    // Trigger 2 (perfectionism)
    @SerializedName("edit_count") val editCount: Int? = null,
    @SerializedName("reschedule_count") val rescheduleCount: Int? = null,
    @SerializedName("subtasks_added") val subtasksAdded: Int? = null,
    @SerializedName("subtasks_completed") val subtasksCompleted: Int? = null,
    val reason: String? = null,

    // Trigger 3 (energy planning)
    @SerializedName("energy_level") val energyLevel: String? = null,
    @SerializedName("tasks_due_today") val tasksDueToday: List<CoachingTaskSummary>? = null,
    @SerializedName("overdue_count") val overdueCount: Int? = null,
    @SerializedName("yesterday_completed") val yesterdayCompleted: Int? = null,
    @SerializedName("yesterday_total") val yesterdayTotal: Int? = null,

    // Trigger 4 (welcome back)
    @SerializedName("days_absent") val daysAbsent: Int? = null,
    @SerializedName("recent_completions") val recentCompletions: Int? = null,

    // Trigger 5 (celebration)
    @SerializedName("completed_subtask_count") val completedSubtaskCount: Int? = null,
    @SerializedName("total_subtask_count") val totalSubtaskCount: Int? = null,
    @SerializedName("days_overdue") val daysOverdue: Int? = null,
    @SerializedName("first_after_gap") val firstAfterGap: Boolean? = null,

    // Trigger 6 (breakdown)
    @SerializedName("duration_minutes") val durationMinutes: Int? = null,
    @SerializedName("project_name") val projectName: String? = null
)

data class CoachingTaskSummary(
    @SerializedName("task_id") val taskId: Long,
    val title: String,
    val priority: Int,
    @SerializedName("estimated_minutes") val estimatedMinutes: Int? = null
)

data class CoachingResponse(
    val message: String,
    val subtasks: List<String>? = null
)

// endregion

// region Export / Import

data class ImportResponse(
    @SerializedName("tasks_imported") val tasksImported: Int,
    @SerializedName("projects_imported") val projectsImported: Int,
    @SerializedName("tags_imported") val tagsImported: Int,
    @SerializedName("habits_imported") val habitsImported: Int,
    val mode: String
)

// endregion
