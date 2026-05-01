/**
 * Pure-function deterministic medication-name resolver.
 *
 * Mirrors `MedicationNameMatcher.kt` (Android) and
 * `backend/app/services/medication_name_matcher.py` byte-for-byte: the same
 * command + medication list MUST produce the same `MatchResult` on every
 * surface, otherwise the audit's failure-mode #6
 * (case/whitespace/unicode brittleness) reopens.
 *
 * NEVER does fuzzy or substring matching. Typos and partial words return
 * `NoMatch` so the caller falls through to the Haiku batch path. This is
 * the audit's failure-mode #2 firewall: silent typo confidence is exactly
 * the bug we are guarding against, so a stricter "exact whole-word match
 * or nothing" contract is the safe default.
 *
 * Normalization (must stay byte-identical with Kotlin + Python twins):
 *   1. Unicode NFC normalize
 *   2. trim
 *   3. lowercase
 *   4. strip trailing ASCII punctuation [.,!?;:]
 */

export interface Medication {
  id: string;
  name: string;
  display_label?: string | null;
}

export interface AmbiguousPhrase {
  phrase: string;
  candidate_entity_ids: string[];
}

export type MatchResult =
  | { kind: 'no_match' }
  | { kind: 'unambiguous'; matches: Record<string, string> }
  | { kind: 'ambiguous'; phrases: AmbiguousPhrase[] }
  | {
      kind: 'mixed';
      unambiguous: Record<string, string>;
      ambiguous: AmbiguousPhrase[];
    };

const TRAILING_PUNCT = new Set(['.', ',', '!', '?', ';', ':']);

export function normalize(s: string): string {
  const nfc = s.normalize('NFC');
  const trimmed = nfc.trim();
  const lower = trimmed.toLowerCase();
  let end = lower.length;
  while (end > 0 && TRAILING_PUNCT.has(lower[end - 1])) end -= 1;
  return lower.slice(0, end);
}

function isWordChar(ch: string): boolean {
  if (!ch) return false;
  return /[\p{L}\p{N}_]/u.test(ch);
}

function buildKeyIndex(medications: Medication[]): Map<string, string[]> {
  const keyToIds = new Map<string, string[]>();
  for (const med of medications) {
    for (const raw of [med.name, med.display_label ?? null]) {
      if (raw == null) continue;
      const key = normalize(raw);
      if (!key) continue;
      const ids = keyToIds.get(key) ?? [];
      if (!ids.includes(med.id)) ids.push(med.id);
      keyToIds.set(key, ids);
    }
  }
  return keyToIds;
}

function findLongestKeyAt(
  haystack: string,
  start: number,
  keys: string[],
): string | null {
  for (const k of keys) {
    const end = start + k.length;
    if (end > haystack.length) continue;
    if (end < haystack.length && isWordChar(haystack[end])) continue;
    if (haystack.slice(start, end) === k) return k;
  }
  return null;
}

function classify(
  unambiguous: Record<string, string>,
  ambiguousMap: Map<string, string[]>,
): MatchResult {
  const ambiguous: AmbiguousPhrase[] = [];
  for (const [phrase, ids] of ambiguousMap) {
    ambiguous.push({
      phrase,
      candidate_entity_ids: [...ids].sort(),
    });
  }
  const hasUnambiguous = Object.keys(unambiguous).length > 0;
  const hasAmbiguous = ambiguous.length > 0;
  if (!hasUnambiguous && !hasAmbiguous) return { kind: 'no_match' };
  if (!hasAmbiguous) return { kind: 'unambiguous', matches: unambiguous };
  if (!hasUnambiguous) return { kind: 'ambiguous', phrases: ambiguous };
  return { kind: 'mixed', unambiguous, ambiguous };
}

export function matchMedicationsInCommand(
  commandText: string,
  medications: Medication[],
): MatchResult {
  if (!commandText || !commandText.trim() || medications.length === 0) {
    return { kind: 'no_match' };
  }
  const normalizedCommand = normalize(commandText);
  if (!normalizedCommand) return { kind: 'no_match' };

  const keyToIds = buildKeyIndex(medications);
  if (keyToIds.size === 0) return { kind: 'no_match' };

  const keys = [...keyToIds.keys()].sort((a, b) => b.length - a.length);
  const unambiguous: Record<string, string> = {};
  const ambiguousMap = new Map<string, string[]>();

  let i = 0;
  while (i < normalizedCommand.length) {
    const atWordStart =
      isWordChar(normalizedCommand[i]) &&
      (i === 0 || !isWordChar(normalizedCommand[i - 1]));
    if (!atWordStart) {
      i += 1;
      continue;
    }
    const matchedKey = findLongestKeyAt(normalizedCommand, i, keys);
    if (matchedKey != null) {
      const ids = keyToIds.get(matchedKey)!;
      if (ids.length === 1) {
        unambiguous[matchedKey] = ids[0];
      } else {
        const bucket = ambiguousMap.get(matchedKey) ?? [];
        for (const medId of ids) {
          if (!bucket.includes(medId)) bucket.push(medId);
        }
        ambiguousMap.set(matchedKey, bucket);
      }
      i += matchedKey.length;
    } else {
      while (
        i < normalizedCommand.length &&
        isWordChar(normalizedCommand[i])
      ) {
        i += 1;
      }
    }
  }

  return classify(unambiguous, ambiguousMap);
}
