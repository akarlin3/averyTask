# Forgiveness-first — streak philosophy

PrismTask streaks are designed to survive a missed day.

This is a deliberate departure from the "miss-a-day-lose-everything"
streak model that most habit apps inherit. We treat streaks as a
motivational signal about the user's pattern over time — not as a
fragile token that punishes a single off day.

This doc exists so that when someone touches streak code, writes
streak copy, or designs a new streak surface, they share one
definition of what "forgiveness" means in PrismTask.

---

## The core rule

A streak counts a day as "kept" when **either** of these is true:

1. The user completed the streak's activity on that day.
2. The user completed it on the previous day, and that previous day
   itself is part of the active streak.

This is the **grace window**. It means a single missed day inside an
otherwise active streak does not break it. Two consecutive missed
days do.

The implementation lives in
[`DailyForgivenessStreakCore.kt`](../app/src/main/java/com/averycorp/prismtask/domain/usecase/DailyForgivenessStreakCore.kt)
and is shared by:

- Habit streaks (see `StreakCalculator.calculateResilientDailyStreak`)
- Project streaks (`ProjectRepository`'s `ProjectWithProgress`
  projection)
- Daily-essentials streaks
- Forgiveness-streak ND-friendly mode

There is intentionally one implementation, not five. New streak
surfaces should call `DailyForgivenessStreakCore`, not roll their own.

## What forgiveness is not

- **Not a free pass for the whole week.** Two missed days in a row
  break the streak. The window is one day, not seven.
- **Not retroactive editing.** The user cannot "buy back" a missed
  day after the streak has already broken.
- **Not unique per user.** All users get the same grace window.
  Per-habit overrides exist (`nag_suppression_days_override`,
  `today_skip_after_complete_days`) but they tune adjacent behavior,
  not the grace window itself.
- **Not optional for built-in habits.** The shipped built-ins
  (school, leisure, morning self-care, bedtime self-care, medication,
  housework) all use forgiveness-first by default. The user can opt
  individual habits out of streak display via `show_streak`, but the
  grace window itself is not a toggle.

## Why we do this

Streak-based motivation works when it tracks the user's pattern, not
their punctuality. Streaks that shatter on a single off day:

- Discourage starting again ("I already broke it, why bother").
- Punish ND-friendly users who use schedules as scaffolding rather
  than as a strict contract.
- Inflate the felt cost of a single missed dose / habit / project
  check-in past anything proportional to the actual setback.

PrismTask explicitly trades a small amount of "did you really do this
every day?" precision for a large amount of "are you still in the
pattern?" signal. We optimize for the second number.

## Day boundaries

Streak day boundaries respect the user-configured Start-of-Day from
`TaskBehaviorPreferences`. A completion logged at 02:30 with a 4 AM
SoD counts toward yesterday's streak day, not today's. See
[`DayBoundary.kt`](../app/src/main/java/com/averycorp/prismtask/util/DayBoundary.kt)
for the shared utility.

## Copy guidelines

- ✅ "On a 12-day streak."
- ✅ "Yesterday's streak day still counts as long as you log today."
- ✅ "Streak paused — log any day in the next 24 hours to keep it."
- ❌ "Don't break your streak!"
- ❌ "12 days perfect — don't let yourself down."
- ❌ "Streak broken. Start over." (when the grace window is still open)

The same descriptive-not-prescriptive rule from
[`WORK_PLAY_RELAX.md`](WORK_PLAY_RELAX.md) applies. Streak copy
describes the streak's state. It does not lecture, threaten, or
dramatize.

## Surfaces that already use this

- Today screen — habit cards
- Weekly Balance Report — burnout band copy
- Built-in habit list — streak badges
- Project list — project streaks
- Streak Calendar widget
- Daily Essentials card

## When you'd add a new streak

- Call `DailyForgivenessStreakCore`. Don't reimplement the grace
  window.
- Pass the user's SoD via `DayBoundary.startOfCurrentDay(...)`. Never
  use system midnight directly.
- Default `show_streak` to off for any new built-in unless you have
  evidence the streak surface adds clarity (e.g.,
  `BUILT_IN_HABIT_STREAKS_AUDIT.md` for the v65→v66 enable migration).
- Write copy following the rules above.
- Add a unit test that asserts the grace window: 1 missed day = streak
  preserved, 2 consecutive missed days = streak broken.
