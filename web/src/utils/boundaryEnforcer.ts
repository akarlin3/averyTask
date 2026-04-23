import type { BoundaryRule } from '@/api/firestore/boundaryRules';

/**
 * Pure boundary-enforcement check. The caller supplies the rules +
 * the current snapshot signals; this returns an array of active
 * breaches the UI should surface (e.g. "You're outside your work
 * hours" or "You have 15 tasks today — over your 12 cap").
 *
 * Kept pure so the UI can call this inline without triggering Firestore
 * reads; the caller owns the data assembly.
 */

export interface EnforcerSignals {
  /** Count of active (non-done, non-cancelled) tasks due or planned
   *  for the logical day. */
  active_tasks_today: number;
  /** Local hour (0–23) for the "now" the check is relative to. */
  hour_now: number;
}

export interface Breach {
  rule_id: string;
  rule_type: BoundaryRule['type'];
  label: string;
  message: string;
  severity: 'info' | 'warn' | 'alert';
}

export function checkBoundaries(
  rules: BoundaryRule[],
  signals: EnforcerSignals,
): Breach[] {
  const breaches: Breach[] = [];
  for (const rule of rules) {
    if (!rule.enabled) continue;
    switch (rule.type) {
      case 'daily_task_cap': {
        if (signals.active_tasks_today > rule.value) {
          breaches.push({
            rule_id: rule.id,
            rule_type: rule.type,
            label: rule.label,
            message: `${signals.active_tasks_today} active tasks — ${rule.value} cap crossed`,
            severity: signals.active_tasks_today > rule.value * 1.5 ? 'alert' : 'warn',
          });
        }
        break;
      }
      case 'work_hours_window': {
        const start = rule.value;
        const end = rule.secondary_value ?? rule.value;
        if (start === end) break;
        const outside = isOutsideWindow(signals.hour_now, start, end);
        if (outside) {
          breaches.push({
            rule_id: rule.id,
            rule_type: rule.type,
            label: rule.label,
            message: `Outside ${formatHour(start)}–${formatHour(end)} work window`,
            severity: 'info',
          });
        }
        break;
      }
      case 'weekly_hour_budget':
        // Not live-checked. Informational only — surfaced via
        // BurnoutScorer.
        break;
    }
  }
  return breaches;
}

function isOutsideWindow(hour: number, start: number, end: number): boolean {
  if (start < end) return hour < start || hour >= end;
  // Overnight window (e.g. 22 → 6): inside means hour >= start || hour < end.
  return !(hour >= start || hour < end);
}

function formatHour(h: number): string {
  const clamped = Math.max(0, Math.min(23, Math.round(h)));
  return `${String(clamped).padStart(2, '0')}:00`;
}
