import { describe, it, expect } from 'vitest';
import { computeTimerStatus, formatClock } from '@/utils/goodEnoughTimer';

describe('computeTimerStatus', () => {
  it('reports no unlock when elapsed < 80%', () => {
    const s = computeTimerStatus({ planned_seconds: 60, elapsed_seconds: 30 });
    expect(s.good_enough_unlocked).toBe(false);
    expect(s.fully_elapsed).toBe(false);
    expect(s.remaining_seconds).toBe(30);
    expect(s.progress_ratio).toBeCloseTo(0.5);
  });

  it('unlocks good-enough at exactly 80%', () => {
    const s = computeTimerStatus({
      planned_seconds: 100,
      elapsed_seconds: 80,
    });
    expect(s.good_enough_unlocked).toBe(true);
    expect(s.fully_elapsed).toBe(false);
  });

  it('reports fully elapsed at 100% or beyond', () => {
    const s = computeTimerStatus({
      planned_seconds: 60,
      elapsed_seconds: 120,
    });
    expect(s.good_enough_unlocked).toBe(true);
    expect(s.fully_elapsed).toBe(true);
    expect(s.remaining_seconds).toBe(0);
    expect(s.progress_ratio).toBe(1);
  });

  it('handles zero planned duration gracefully (auto-unlocked)', () => {
    const s = computeTimerStatus({ planned_seconds: 0, elapsed_seconds: 0 });
    expect(s.good_enough_unlocked).toBe(true);
    expect(s.fully_elapsed).toBe(true);
    expect(s.progress_ratio).toBe(1);
  });

  it('clamps negative elapsed to zero', () => {
    const s = computeTimerStatus({ planned_seconds: 60, elapsed_seconds: -10 });
    expect(s.progress_ratio).toBe(0);
    expect(s.remaining_seconds).toBe(60);
  });
});

describe('formatClock', () => {
  it('renders MM:SS with zero padding', () => {
    expect(formatClock(0)).toBe('00:00');
    expect(formatClock(9)).toBe('00:09');
    expect(formatClock(61)).toBe('01:01');
    expect(formatClock(600)).toBe('10:00');
  });

  it('rounds floats and clamps negatives', () => {
    expect(formatClock(-5)).toBe('00:00');
    expect(formatClock(59.6)).toBe('01:00');
  });
});
