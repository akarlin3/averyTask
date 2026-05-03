/**
 * Client-side project burndown compute — mirrors
 * `backend/app/services/analytics.py::compute_project_burndown`.
 *
 * The backend `/analytics/project-progress` endpoint expects an
 * integer Postgres `project_id`, but web projects live in Firestore
 * with string doc IDs. Rather than round-tripping an ID lookup or
 * changing the backend, we compute locally from the same task
 * fields the server uses: `created_at`, `completed_at`, and
 * `status`. Fields on the web `Task` type carry ISO strings which
 * we parse to Date just for the date-only comparison.
 *
 * Output shape matches `ProjectProgressResponse` so downstream code
 * renders it with no wiring difference from the backend version.
 */

import type {
  BurndownEntry,
  ProjectProgressResponse,
} from '@/types/analytics';
import type { Project } from '@/types/project';
import type { Task } from '@/types/task';

const DAY_MS = 86_400_000;

function toDateOnly(iso: string | null | undefined): Date | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  d.setHours(0, 0, 0, 0);
  return d;
}

function formatDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function parseDateInput(iso: string): Date {
  // Parse a plain `YYYY-MM-DD` as local midnight to avoid UTC drift.
  const [y, m, d] = iso.split('-').map((x) => Number(x));
  return new Date(y, (m || 1) - 1, d || 1);
}

export interface BurndownInput {
  project: Pick<Project, 'id' | 'title' | 'due_date'>;
  tasks: Pick<
    Task,
    'id' | 'project_id' | 'status' | 'created_at' | 'completed_at' | 'progress_percent'
  >[];
  startIso: string;
  endIso: string;
}

/**
 * Returns the completed fraction for a single task in [0, 1].
 *
 * PrismTask-Timeline-Class scope (audit § P9 option a): tasks under
 * a project may carry a 0–100 ``progress_percent``; everywhere else
 * the legacy binary ``status === 'done'`` check still holds. Mirror
 * of `analytics.task_progress_fraction` on the backend.
 */
function taskProgressFraction(task: {
  status: string;
  progress_percent?: number | null;
}): number {
  if (task.progress_percent != null) {
    return Math.max(0, Math.min(1, task.progress_percent / 100));
  }
  return task.status === 'done' ? 1 : 0;
}

export function computeProjectBurndown({
  project,
  tasks,
  startIso,
  endIso,
}: BurndownInput): ProjectProgressResponse {
  const projectTasks = tasks.filter(
    (t) => t.project_id === project.id && t.status !== 'cancelled',
  );

  const totalTasks = projectTasks.length;
  const completedTotal = projectTasks.reduce(
    (sum, t) => sum + taskProgressFraction(t),
    0,
  );
  const completedTasks = Math.round(completedTotal * 100) / 100;

  // Pre-compute per-task created + completed date-only values + the
  // task's progress fraction so the per-day inner loop doesn't re-parse.
  const taskDates = projectTasks.map((t) => ({
    created: toDateOnly(t.created_at),
    completed: toDateOnly(t.completed_at),
    fraction: taskProgressFraction(t),
  }));

  const startDate = parseDateInput(startIso);
  const endDate = parseDateInput(endIso);
  const burndown: BurndownEntry[] = [];

  for (
    let d = new Date(startDate);
    d.getTime() <= endDate.getTime();
    d = new Date(d.getTime() + DAY_MS)
  ) {
    const dayMs = d.getTime();
    let existing = 0;
    let completedCumulative = 0;
    let added = 0;
    for (const td of taskDates) {
      if (td.created && td.created.getTime() <= dayMs) existing += 1;
      if (td.completed && td.completed.getTime() <= dayMs) {
        completedCumulative += td.fraction;
      }
      if (td.created && td.created.getTime() === dayMs) added += 1;
    }
    const completedRounded = Math.round(completedCumulative * 100) / 100;
    burndown.push({
      date: formatDate(d),
      remaining: Math.round((existing - completedCumulative) * 100) / 100,
      completed_cumulative: completedRounded,
      added,
    });
  }

  const daysElapsed = Math.max(
    1,
    Math.round((endDate.getTime() - startDate.getTime()) / DAY_MS) + 1,
  );
  const velocity =
    daysElapsed > 0 ? Math.round((completedTotal / daysElapsed) * 10) / 10 : 0;

  const remainingNow = Math.round((totalTasks - completedTotal) * 100) / 100;
  let projectedCompletion: string | null = null;
  let isOnTrack = true;
  if (velocity > 0 && remainingNow > 0) {
    const daysToComplete = remainingNow / velocity;
    const projected = new Date(endDate.getTime() + daysToComplete * DAY_MS);
    projectedCompletion = formatDate(projected);
    if (project.due_date) {
      const dueMs = parseDateInput(project.due_date).getTime();
      isOnTrack = projected.getTime() <= dueMs;
    }
  } else if (remainingNow === 0) {
    isOnTrack = true;
    projectedCompletion = formatDate(endDate);
  }

  return {
    project_name: project.title,
    total_tasks: totalTasks,
    completed_tasks: completedTasks,
    burndown,
    velocity,
    projected_completion: projectedCompletion,
    is_on_track: isOnTrack,
  };
}
