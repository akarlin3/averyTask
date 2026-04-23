import { addDays, differenceInCalendarDays, parseISO } from 'date-fns';
import type { CheckInLog } from '@/api/firestore/checkInLogs';

/**
 * Forgiveness-first streak compute for morning check-ins. Mirrors
 * the Android `DailyForgivenessStreakCore` semantics:
 *
 *   - A single missed day does NOT break the streak; it "bends".
 *   - Two consecutive missed days DO break it.
 *   - A user who checks in today but missed yesterday has a streak
 *     of (longest run since last double-miss).
 *
 * The caller supplies a `today` ISO so this function stays pure and
 * testable. Logs can come in any order; we sort internally.
 */

export interface StreakResult {
  current: number;
  longest: number;
  /** Whether the user has checked in for `today` yet. */
  logged_today: boolean;
  /** ISO date of the day before `today` where the chain would end
   *  if a missed-day today is allowed. Undefined when no streak. */
  last_chain_end: string | null;
}

function isoOnly(iso: string): string {
  // Normalize away any time components if they sneak in; we key by
  // YYYY-MM-DD only.
  return iso.slice(0, 10);
}

export function computeCheckInStreak(
  logs: CheckInLog[],
  todayIso: string,
): StreakResult {
  const loggedDays = new Set(logs.map((l) => isoOnly(l.date_iso)));
  const today = parseISO(todayIso);
  const loggedToday = loggedDays.has(todayIso);

  // Walk backwards from today; allow a single-miss bend.
  let cursor = today;
  let streak = 0;
  let bendUsedSinceLast = false;
  let lastChainEnd: string | null = null;

  // If the user hasn't logged today, start scanning from yesterday —
  // today being blank is not itself a break yet.
  if (!loggedToday) {
    cursor = addDays(cursor, -1);
  }

  for (let i = 0; i < 400; i += 1) {
    const iso = cursor.toISOString().slice(0, 10);
    if (loggedDays.has(iso)) {
      streak += 1;
      if (lastChainEnd === null) lastChainEnd = iso;
      bendUsedSinceLast = false;
      cursor = addDays(cursor, -1);
    } else if (!bendUsedSinceLast) {
      bendUsedSinceLast = true;
      cursor = addDays(cursor, -1);
    } else {
      break;
    }
  }

  // Longest streak: scan forward across the log set.
  const sorted = Array.from(loggedDays).sort();
  let longest = 0;
  let run = 0;
  let bendUsed = false;
  for (let i = 0; i < sorted.length; i += 1) {
    const currentIso = sorted[i];
    const prevIso = sorted[i - 1];
    if (i === 0) {
      run = 1;
      bendUsed = false;
    } else {
      const gap = differenceInCalendarDays(
        parseISO(currentIso),
        parseISO(prevIso),
      );
      if (gap === 1) {
        run += 1;
      } else if (gap === 2 && !bendUsed) {
        run += 1;
        bendUsed = true;
      } else {
        run = 1;
        bendUsed = false;
      }
    }
    if (run > longest) longest = run;
  }

  return {
    current: streak,
    longest,
    logged_today: loggedToday,
    last_chain_end: lastChainEnd,
  };
}
