import { describe, it, expect } from 'vitest';
import {
  BatchSignal,
  detectBatchIntent,
} from '@/utils/batchIntentDetector';

describe('detectBatchIntent', () => {
  it('returns not_batch for empty input', () => {
    expect(detectBatchIntent('')).toEqual({ kind: 'not_batch' });
    expect(detectBatchIntent('   ')).toEqual({ kind: 'not_batch' });
  });

  it('treats single-task commands as not_batch even with one signal', () => {
    // "buy groceries today" — TIME_RANGE only; should stay on single-task path.
    const res = detectBatchIntent('buy groceries today');
    expect(res.kind).toBe('not_batch');
  });

  it('treats a bare quantifier sentence as not_batch', () => {
    const res = detectBatchIntent('all the lights are on');
    expect(res.kind).toBe('not_batch');
  });

  it('routes commands with 2 signals to batch', () => {
    // QUANTIFIER + TIME_RANGE
    const res = detectBatchIntent('reschedule all overdue tasks to tomorrow');
    expect(res.kind).toBe('batch');
    if (res.kind === 'batch') {
      expect(res.signals.has(BatchSignal.QUANTIFIER)).toBe(true);
      // "tomorrow" is TIME_RANGE and "reschedule ... tasks" is BULK_VERB+PLURAL
      expect(res.signals.size).toBeGreaterThanOrEqual(2);
      expect(res.command_text).toBe('reschedule all overdue tasks to tomorrow');
    }
  });

  it('detects tag filter + time range', () => {
    const res = detectBatchIntent('cancel #work items this week');
    expect(res.kind).toBe('batch');
    if (res.kind === 'batch') {
      expect(res.signals.has(BatchSignal.TAG_FILTER)).toBe(true);
      expect(res.signals.has(BatchSignal.TIME_RANGE)).toBe(true);
    }
  });

  it('detects "tagged X" tag filter', () => {
    const res = detectBatchIntent('complete everything tagged urgent today');
    expect(res.kind).toBe('batch');
    if (res.kind === 'batch') {
      expect(res.signals.has(BatchSignal.TAG_FILTER)).toBe(true);
      expect(res.signals.has(BatchSignal.QUANTIFIER)).toBe(true);
    }
  });

  it('detects bulk verb + plural alongside a quantifier', () => {
    const res = detectBatchIntent('archive all tasks');
    expect(res.kind).toBe('batch');
    if (res.kind === 'batch') {
      expect(res.signals.has(BatchSignal.BULK_VERB_AND_PLURAL)).toBe(true);
      expect(res.signals.has(BatchSignal.QUANTIFIER)).toBe(true);
    }
  });

  it('rejects bulk verb + plural alone (one signal)', () => {
    // "clear tasks" — only BULK_VERB_AND_PLURAL, one signal, should NOT route.
    const res = detectBatchIntent('clear tasks');
    expect(res.kind).toBe('not_batch');
  });

  it('trims the command text before returning', () => {
    const res = detectBatchIntent('   reschedule all tasks this week   ');
    expect(res.kind).toBe('batch');
    if (res.kind === 'batch') {
      expect(res.command_text).toBe('reschedule all tasks this week');
    }
  });

  it('is case-insensitive across keywords', () => {
    const res = detectBatchIntent('Cancel ALL Habits This Week');
    expect(res.kind).toBe('batch');
  });

  it('handles multiword time-range phrase "next week"', () => {
    // QUANTIFIER + TIME_RANGE(phrase)
    const res = detectBatchIntent('move every task to next week');
    expect(res.kind).toBe('batch');
    if (res.kind === 'batch') {
      expect(res.signals.has(BatchSignal.TIME_RANGE)).toBe(true);
    }
  });
});
