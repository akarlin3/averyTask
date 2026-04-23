/**
 * "Good-enough" timer helpers. The UX goal is ND-friendly: the
 * user declares a planned duration, and at 80% elapsed we unlock a
 * "Call it good enough" affordance so they can release the task
 * without hitting the wall of exact duration. Pure helpers here;
 * the component owns the interval.
 */

export interface TimerSnapshot {
  planned_seconds: number;
  elapsed_seconds: number;
}

export interface TimerStatus {
  progress_ratio: number;
  /** True once ≥80% of the planned duration has elapsed — the UI
   *  can unlock the "Good enough" button. */
  good_enough_unlocked: boolean;
  /** True once 100% has elapsed — session completes automatically. */
  fully_elapsed: boolean;
  remaining_seconds: number;
}

const GOOD_ENOUGH_THRESHOLD = 0.8;

export function computeTimerStatus(snapshot: TimerSnapshot): TimerStatus {
  const planned = Math.max(0, snapshot.planned_seconds);
  const elapsed = Math.max(0, snapshot.elapsed_seconds);
  const ratio = planned === 0 ? 1 : Math.min(1, elapsed / planned);
  return {
    progress_ratio: ratio,
    good_enough_unlocked: ratio >= GOOD_ENOUGH_THRESHOLD,
    fully_elapsed: ratio >= 1,
    remaining_seconds: Math.max(0, planned - elapsed),
  };
}

export function formatClock(seconds: number): string {
  const safe = Math.max(0, Math.round(seconds));
  const m = Math.floor(safe / 60);
  const s = safe % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}
