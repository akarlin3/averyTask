# Work / Play / Relax — task mode philosophy

PrismTask classifies every task two ways. **Life category** answers
*what is this task about?* (work / personal / self-care / health). **Mode**
answers *what does the user want this task to produce?*

This doc is about mode. It exists so that when we add a chip, write a
prompt, draw a chart, or copy a setting, we share one definition of what
those three labels mean.

---

## The three modes

| Mode      | Produces        | Examples                                                   |
|-----------|-----------------|------------------------------------------------------------|
| **Work**  | Output          | Ship a feature, send the invoice, finish the lab report    |
| **Play**  | Enjoyment       | Play guitar, hike with a friend, cook a meal for fun       |
| **Relax** | Restored energy | Nap, breathing exercise, slow walk, lie in the hammock     |

A fourth value — **Uncategorized** — is the default for any task the
user hasn't tagged and the auto-classifier hasn't matched. Uncategorized
tasks are visible in lists and counts the same way as today, but do not
contribute to mode-balance ratios. (Same shape as
`LifeCategory.UNCATEGORIZED`.)

## Mode is orthogonal to life category

A task carries both a life category and a mode. Neither is derived from
the other. Examples:

| Title                                | Life category | Mode  | Why                                               |
|--------------------------------------|---------------|-------|---------------------------------------------------|
| Finish quarterly report              | Work          | Work  | Subject = job; produces output (the report)       |
| Side project: build a synth          | Work          | Play  | Subject = work-adjacent; produces enjoyment       |
| Physical therapy — knee exercises    | Health        | Work  | Subject = health; produces output (compliance)    |
| Pickup basketball with friends       | Health        | Play  | Subject = health; produces enjoyment              |
| Slow walk after dinner               | Health        | Relax | Subject = health; produces restoration            |
| Cook elaborate Sunday dinner         | Self-care     | Play  | Subject = self-care; produces enjoyment           |
| Take a nap                           | Self-care     | Relax | Subject = self-care; produces restoration         |
| Pay the electric bill                | Personal      | Work  | Subject = personal; produces output (paid bill)   |

The product team uses this matrix when writing copy: *"is this thing
about a category, or about a mode?"*. If the answer is "both", the copy
needs to make clear which one it's talking about — never collapse them.

## What mode is **not**

- **Not a measure of difficulty.** A demanding climb can be Play. A
  trivial inbox-sort can be Work.
- **Not about urgency or importance.** Eisenhower remains orthogonal.
  An urgent-and-important task can be in any mode.
- **Not a recommendation about what the user "should" do.** PrismTask
  describes the split. It does not prescribe one.
- **Not a recovery prescription.** Relax tasks are user-chosen
  restorative activities. The app does not decide that the user "needs
  more Relax this week" — it shows the split and lets the user
  interpret.
- **Not a permanent identity.** A task tagged Play today can be tagged
  Work tomorrow if the user's relationship to it changes. The
  classifier always defers to the user's latest manual override.

## Descriptive, not prescriptive

PrismTask renders mode-balance numbers and trends. It does not lecture.

- ✅ "Your week was 60% Work, 25% Play, 15% Relax."
- ✅ "Tomorrow you have 3 Work tasks scheduled and 1 Play task."
- ❌ "You're working too much — schedule more Play time."
- ❌ "Low Relax this week — try a 10-minute walk."

This rule applies everywhere mode appears: balance bar, weekly report,
notifications, AI coaching, widget surfaces, settings copy. If a copy
change reads as a value judgment about the user's mode split, rewrite
it.

## Streak strictness

Mode-aware leniency composes with the existing forgiveness-first streak
core (see [`FORGIVENESS_FIRST.md`](FORGIVENESS_FIRST.md)). Defaults:

| Mode     | Default streak strictness          |
|----------|------------------------------------|
| Work     | Standard forgiveness window        |
| Play     | Wider forgiveness window           |
| Relax    | Wider forgiveness window           |

Rationale: Work tasks tend to have external deadlines or contractual
shape that survives a single missed day intact. Play and Relax tasks
are user-chosen and self-paced; tightening their streaks creates
guilt-by-streak, which is exactly the failure mode forgiveness-first
exists to avoid. The user can override per-task / per-habit.

This is a default, not a wall: a user who explicitly wants strict Play
streaks (e.g., a daily-practice musician) can dial them up.

## Inference rules (auto-classifier)

The mode auto-classifier follows the same shape as
`LifeCategoryClassifier`: keyword-based, offline, with user-supplied
custom keywords from Settings → Advanced Tuning. When the AI Features
toggle is on, the Claude Haiku NLP path can also suggest a mode from
title + notes; the suggestion is always overrideable.

Default keyword vocabulary (starter set; expandable via custom
keywords):

- **Work:** ship, finish, fix, send, write, review, file, submit,
  deliver, deadline, meeting, call, invoice, email, report, draft,
  schedule, present, prepare, plan, complete
- **Play:** play, game, hobby, project (when paired with non-work
  context), cook (recreational), bake, climb, hike, paint, draw, read
  (recreational), watch, listen, jam, practice (creative), dance,
  visit, party, dinner-with, brunch, picnic
- **Relax:** rest, nap, sleep, breathe, meditate, stretch, soak, bath,
  spa, lie, sit, sunbathe, journal (low-effort), tea, walk (slow),
  unwind, recover, decompress

When two modes match, the classifier prefers Relax → Play → Work in
that order, mirroring the same conservative bias as
`LifeCategoryClassifier`'s `[HEALTH, SELF_CARE, WORK, PERSONAL]`
tie-break: in mode terms, "if you can't tell, lean toward the
restorative read so the system never accidentally inflates Work."

## NLP hashtags

Mode hashtags use a `-mode` suffix to avoid colliding with the existing
LifeCategory hashtags (`#work` / `#self-care` / `#personal` / `#health`):

- `#work-mode`
- `#play-mode`
- `#relax-mode`

A task's text can carry both: `Tennis match #health #play-mode`.

## Ratio surfacing

`ModeBalance` is computed the same way as `BalanceTracker`:

- Window: last 7 days for the current ratio, last 28 days for rolling.
- Day boundary: SoD-aware (after the v1.8 `BalanceTracker` SoD fix).
- Excluded: tasks with mode = Uncategorized, tasks before the cutoff.
- Output: ratio per mode (sums to ~1.0), total tracked count, dominant
  mode.

The Today balance bar shows mode alongside life category (two stacked
bars or a toggle, design TBD in the implementation PR). The Weekly
Balance Report adds a mode section beneath the existing category
section.

## Defaults & migration semantics

- Existing tasks have mode = `null` (Uncategorized) after migration.
  No retroactive classification.
- Auto-classification only applies to new tasks created after the
  feature ships, so a user's archived history is not silently re-tagged.
- The user can manually tag any historical task at any time.
- The Today balance bar's mode section is hidden until the user has
  tagged at least one task with a mode (mirrors how the LifeCategory
  bar gates on `totalTracked > 0`).

## What this doc does not cover

- The schema layout, migration shape, and exact column types are in
  the implementation PR's commit messages and `Migrations.kt` KDoc.
- Picker UI placement and copy strings live in the implementation PR's
  diff.
- AI prompt templates live in `backend/app/routers/` and
  `backend/app/services/` next to the existing classifiers.
- Mode integration with Pomodoro / Eisenhower is intentionally not
  scoped here — those features remain orthogonal until a future PR
  documents how mode interacts with them.
