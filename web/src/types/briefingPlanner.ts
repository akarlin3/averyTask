/**
 * Types matching `backend/app/schemas/ai.py`:
 *   DailyBriefingRequest / DailyBriefingResponse
 *   WeeklyPlanRequest / WeeklyPlanResponse
 *
 * Both endpoints are stateless from the client's perspective — the
 * backend reads the user's tasks/habits itself (via `firebase_uid` on
 * the JWT), so the request payload is just a date + preferences.
 */

// ── Daily Briefing ───────────────────────────────────────────────

export interface DailyBriefingRequest {
  /** ISO date `YYYY-MM-DD`. Server defaults to today when omitted. */
  date?: string;
}

export interface BriefingPriority {
  task_id: string;
  title: string;
  reason: string;
}

export interface SuggestedTask {
  task_id: string;
  title: string;
  suggested_time: string;
  reason: string;
}

export type BriefingDayType = 'light' | 'moderate' | 'heavy';

export interface DailyBriefingResponse {
  greeting: string;
  top_priorities: BriefingPriority[];
  heads_up: string[];
  suggested_order: SuggestedTask[];
  habit_reminders: string[];
  day_type: BriefingDayType;
}

// ── Weekly Plan ──────────────────────────────────────────────────

export type WeekdayCode = 'MO' | 'TU' | 'WE' | 'TH' | 'FR' | 'SA' | 'SU';

export interface WeeklyPlanPreferences {
  /** Two-letter weekday codes. Defaults to MO–FR on the server. */
  work_days: WeekdayCode[];
  focus_hours_per_day: number;
  prefer_front_loading: boolean;
}

export interface WeeklyPlanRequest {
  /** Monday of the target week (ISO YYYY-MM-DD). Server defaults to next Monday. */
  week_start?: string;
  preferences?: WeeklyPlanPreferences;
}

export interface PlannedTask {
  task_id: string;
  title: string;
  suggested_time: string;
  duration_minutes: number;
  reason: string;
}

export interface DayPlan {
  date: string;
  tasks: PlannedTask[];
  total_hours: number;
  calendar_events: string[];
  habits: string[];
}

export interface UnscheduledTask {
  task_id: string;
  title: string;
  reason: string;
}

export interface WeeklyPlanResponse {
  /** Keyed by weekday name (per backend — e.g. "Monday"); value is the
   *  plan for that day. */
  plan: Record<string, DayPlan>;
  unscheduled: UnscheduledTask[];
  week_summary: string;
  tips: string[];
}
