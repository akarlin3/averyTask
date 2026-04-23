/**
 * Heuristic detector for batch-style commands typed into the quick-add bar.
 *
 * Port of Android's `BatchIntentDetector.kt`. Requires **at least two
 * distinct signal categories** before routing to the batch flow — a single
 * signal ("buy groceries today" = TIME_RANGE only) stays on the single-task
 * NLP path. False positives here would trap normal users in the heavier
 * batch flow, so the bar is intentionally high.
 */

/**
 * `enum` is not available under `erasableSyntaxOnly` (tsconfig); modeled
 * as a literal union + frozen constant so imports like
 * `BatchSignal.QUANTIFIER` still read the same.
 */
export type BatchSignal =
  | 'QUANTIFIER'
  | 'TIME_RANGE'
  | 'TAG_FILTER'
  | 'BULK_VERB_AND_PLURAL';

export const BatchSignal = Object.freeze({
  QUANTIFIER: 'QUANTIFIER',
  TIME_RANGE: 'TIME_RANGE',
  TAG_FILTER: 'TAG_FILTER',
  BULK_VERB_AND_PLURAL: 'BULK_VERB_AND_PLURAL',
}) satisfies Record<BatchSignal, BatchSignal>;

export type BatchIntentResult =
  | { kind: 'not_batch' }
  | { kind: 'batch'; command_text: string; signals: Set<BatchSignal> };

const QUANTIFIER_TOKENS = new Set(['all', 'everything', 'every', 'each']);

const BULK_VERBS = new Set([
  'cancel',
  'clear',
  'move',
  'reschedule',
  'delete',
  'complete',
  'skip',
  'archive',
]);

const ENTITY_PLURALS = new Set([
  'tasks',
  'habits',
  'medications',
  'meds',
  'projects',
  'items',
]);

const TIME_RANGE_TOKENS = new Set([
  'today',
  'tonight',
  'tomorrow',
  'monday',
  'tuesday',
  'wednesday',
  'thursday',
  'friday',
  'saturday',
  'sunday',
  'weekend',
]);

const TIME_RANGE_PHRASES = [
  'this week',
  'next week',
  'the weekend',
  'the rest of the day',
  'the rest of the week',
  'this morning',
  'this afternoon',
  'this evening',
];

const TAG_FILTER_REGEX = /(?:\btagged\s+\S+|#\S+)/i;

export function detectBatchIntent(rawText: string): BatchIntentResult {
  const text = rawText.trim();
  if (!text) return { kind: 'not_batch' };
  const lower = text.toLowerCase();
  const tokens = lower.split(/\s+/).filter(Boolean);

  const signals = new Set<BatchSignal>();

  if (tokens.some((t) => QUANTIFIER_TOKENS.has(t))) {
    signals.add(BatchSignal.QUANTIFIER);
  }

  if (tokens.some((t) => TIME_RANGE_TOKENS.has(t))) {
    signals.add(BatchSignal.TIME_RANGE);
  }
  if (TIME_RANGE_PHRASES.some((p) => lower.includes(p))) {
    signals.add(BatchSignal.TIME_RANGE);
  }

  if (TAG_FILTER_REGEX.test(text)) {
    signals.add(BatchSignal.TAG_FILTER);
  }

  const hasBulkVerb = tokens.some((t) => BULK_VERBS.has(t));
  const hasEntityPlural = tokens.some((t) => ENTITY_PLURALS.has(t));
  if (hasBulkVerb && hasEntityPlural) {
    signals.add(BatchSignal.BULK_VERB_AND_PLURAL);
  }

  if (signals.size >= 2) {
    return { kind: 'batch', command_text: text, signals };
  }
  return { kind: 'not_batch' };
}
