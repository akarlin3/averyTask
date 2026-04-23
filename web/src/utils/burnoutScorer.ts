import type { Breach } from '@/utils/boundaryEnforcer';
import type { MoodEnergyLog } from '@/api/firestore/moodEnergyLogs';

/**
 * Simple burnout-risk score (0–100, higher = closer to burnout).
 * Mirrors Android's `BurnoutScorer` at the signal level without the
 * full weight-tuning pass — we deliberately keep this transparent
 * and easy to tweak.
 *
 * Signals (weights sum to 100 when all firing):
 *   - active breaches            40
 *   - low mood (≤ 2 on 1–5 avg)  25
 *   - low energy (≤ 2 avg)       20
 *   - task-count overload        15
 */

export type BurnoutBucket = 'calm' | 'moderate' | 'risky' | 'burning';

export interface BurnoutScore {
  score: number;
  bucket: BurnoutBucket;
  /** Sub-score contributions in the same order the compute ran. */
  factors: Array<{ label: string; value: number }>;
}

export interface BurnoutInputs {
  breaches: Breach[];
  recent_mood_logs: MoodEnergyLog[];
  active_tasks_today: number;
  task_soft_cap: number;
}

export function scoreBurnout(inputs: BurnoutInputs): BurnoutScore {
  const factors: Array<{ label: string; value: number }> = [];

  const breachComponent = Math.min(
    40,
    inputs.breaches.reduce(
      (acc, b) =>
        acc + (b.severity === 'alert' ? 20 : b.severity === 'warn' ? 10 : 4),
      0,
    ),
  );
  factors.push({ label: 'Active breaches', value: breachComponent });

  const moodAvg =
    inputs.recent_mood_logs.length === 0
      ? 3
      : inputs.recent_mood_logs.reduce((a, l) => a + l.mood, 0) /
        inputs.recent_mood_logs.length;
  const moodComponent = Math.max(0, Math.min(25, (3 - moodAvg) * 12.5));
  factors.push({ label: 'Recent mood', value: moodComponent });

  const energyAvg =
    inputs.recent_mood_logs.length === 0
      ? 3
      : inputs.recent_mood_logs.reduce((a, l) => a + l.energy, 0) /
        inputs.recent_mood_logs.length;
  const energyComponent = Math.max(0, Math.min(20, (3 - energyAvg) * 10));
  factors.push({ label: 'Recent energy', value: energyComponent });

  const overTask = Math.max(0, inputs.active_tasks_today - inputs.task_soft_cap);
  const taskComponent = Math.min(15, overTask * 2);
  factors.push({ label: 'Task overload', value: taskComponent });

  const score = Math.round(
    breachComponent + moodComponent + energyComponent + taskComponent,
  );
  const bucket: BurnoutBucket =
    score < 25 ? 'calm' : score < 50 ? 'moderate' : score < 75 ? 'risky' : 'burning';

  return { score, bucket, factors };
}
