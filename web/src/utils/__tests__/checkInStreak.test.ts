import { describe, it, expect } from 'vitest';
import { computeCheckInStreak } from '@/utils/checkInStreak';
import type { CheckInLog } from '@/api/firestore/checkInLogs';

function mkLog(date_iso: string): CheckInLog {
  return {
    id: date_iso,
    date_iso,
    steps_completed_csv: '',
    medications_confirmed: true,
    tasks_reviewed: true,
    habits_completed: true,
    created_at: 0,
    updated_at: 0,
  };
}

describe('computeCheckInStreak', () => {
  it('returns zeroes for no logs', () => {
    const res = computeCheckInStreak([], '2026-04-15');
    expect(res.current).toBe(0);
    expect(res.longest).toBe(0);
    expect(res.logged_today).toBe(false);
  });

  it('counts consecutive days logged including today', () => {
    const logs = ['2026-04-13', '2026-04-14', '2026-04-15'].map(mkLog);
    const res = computeCheckInStreak(logs, '2026-04-15');
    expect(res.logged_today).toBe(true);
    expect(res.current).toBe(3);
    expect(res.longest).toBe(3);
  });

  it('bends the streak over a single missed day', () => {
    // Logs on 10, 12, 13 — the 11th is missing.
    const logs = ['2026-04-10', '2026-04-12', '2026-04-13'].map(mkLog);
    const res = computeCheckInStreak(logs, '2026-04-13');
    expect(res.current).toBe(3);
    // Longest should also bend.
    expect(res.longest).toBe(3);
  });

  it('breaks on two consecutive misses', () => {
    // Logs on 10, 13, 14 — gap of 3 days between 10 and 13.
    const logs = ['2026-04-10', '2026-04-13', '2026-04-14'].map(mkLog);
    const res = computeCheckInStreak(logs, '2026-04-14');
    expect(res.current).toBe(2);
    // Longest stays at the latest bent chain (13,14) = 2.
    expect(res.longest).toBe(2);
  });

  it('keeps streak alive when today is unlogged (grace day)', () => {
    const logs = ['2026-04-13', '2026-04-14'].map(mkLog);
    const res = computeCheckInStreak(logs, '2026-04-15');
    expect(res.logged_today).toBe(false);
    expect(res.current).toBe(2);
  });
});
