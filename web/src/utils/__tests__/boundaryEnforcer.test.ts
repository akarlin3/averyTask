import { describe, it, expect } from 'vitest';
import { checkBoundaries } from '@/utils/boundaryEnforcer';
import type { BoundaryRule } from '@/api/firestore/boundaryRules';
import { scoreBurnout } from '@/utils/burnoutScorer';
import type { MoodEnergyLog } from '@/api/firestore/moodEnergyLogs';

function rule(overrides: Partial<BoundaryRule>): BoundaryRule {
  return {
    id: 'r',
    type: 'daily_task_cap',
    label: 'r',
    value: 10,
    secondary_value: null,
    enabled: true,
    created_at: 0,
    updated_at: 0,
    ...overrides,
  };
}

describe('checkBoundaries', () => {
  it('flags a warn breach when tasks cross the cap', () => {
    const breaches = checkBoundaries(
      [rule({ id: 'r1', type: 'daily_task_cap', value: 10 })],
      { active_tasks_today: 12, hour_now: 14 },
    );
    expect(breaches).toHaveLength(1);
    expect(breaches[0].severity).toBe('warn');
  });

  it('escalates to alert at >1.5x cap', () => {
    const breaches = checkBoundaries(
      [rule({ id: 'r1', type: 'daily_task_cap', value: 10 })],
      { active_tasks_today: 20, hour_now: 14 },
    );
    expect(breaches[0].severity).toBe('alert');
  });

  it('stays silent inside the work-hours window', () => {
    const breaches = checkBoundaries(
      [
        rule({
          id: 'r1',
          type: 'work_hours_window',
          value: 9,
          secondary_value: 17,
        }),
      ],
      { active_tasks_today: 0, hour_now: 12 },
    );
    expect(breaches).toHaveLength(0);
  });

  it('fires outside the work-hours window', () => {
    const breaches = checkBoundaries(
      [
        rule({
          id: 'r1',
          type: 'work_hours_window',
          value: 9,
          secondary_value: 17,
        }),
      ],
      { active_tasks_today: 0, hour_now: 20 },
    );
    expect(breaches).toHaveLength(1);
    expect(breaches[0].severity).toBe('info');
  });

  it('handles overnight windows (22 → 6)', () => {
    const rules: BoundaryRule[] = [
      rule({
        id: 'r1',
        type: 'work_hours_window',
        value: 22,
        secondary_value: 6,
      }),
    ];
    expect(checkBoundaries(rules, { active_tasks_today: 0, hour_now: 2 }))
      .toHaveLength(0);
    expect(checkBoundaries(rules, { active_tasks_today: 0, hour_now: 14 }))
      .toHaveLength(1);
  });

  it('skips disabled rules', () => {
    const breaches = checkBoundaries(
      [rule({ id: 'r1', type: 'daily_task_cap', value: 5, enabled: false })],
      { active_tasks_today: 50, hour_now: 14 },
    );
    expect(breaches).toHaveLength(0);
  });
});

describe('scoreBurnout', () => {
  const log = (mood: number, energy: number): MoodEnergyLog => ({
    id: 'x',
    date_iso: '2026-04-10',
    mood,
    energy,
    notes: '',
    time_of_day: 'morning',
    created_at: 0,
    updated_at: 0,
  });

  it('returns calm with no signals', () => {
    const s = scoreBurnout({
      breaches: [],
      recent_mood_logs: [],
      active_tasks_today: 0,
      task_soft_cap: 10,
    });
    expect(s.bucket).toBe('calm');
    expect(s.score).toBe(0);
  });

  it('weights breach severity heavily', () => {
    const s = scoreBurnout({
      breaches: [
        {
          rule_id: 'r',
          rule_type: 'daily_task_cap',
          label: 'x',
          message: '',
          severity: 'alert',
        },
      ],
      recent_mood_logs: [],
      active_tasks_today: 5,
      task_soft_cap: 10,
    });
    expect(s.score).toBeGreaterThanOrEqual(20);
  });

  it('factors in low mood + low energy', () => {
    const s = scoreBurnout({
      breaches: [],
      recent_mood_logs: [log(1, 1), log(1, 1), log(1, 1)],
      active_tasks_today: 5,
      task_soft_cap: 10,
    });
    // Low mood (25) + low energy (20) = 45 — risky bucket threshold is 50.
    expect(s.bucket).toBe('moderate');
    expect(s.score).toBeGreaterThan(40);
  });

  it('rolls up to burning with breach + low mood + task overload', () => {
    const s = scoreBurnout({
      breaches: [
        {
          rule_id: 'r',
          rule_type: 'daily_task_cap',
          label: 'x',
          message: '',
          severity: 'alert',
        },
      ],
      recent_mood_logs: [log(1, 1)],
      active_tasks_today: 40,
      task_soft_cap: 10,
    });
    expect(s.bucket).toBe('burning');
  });
});
