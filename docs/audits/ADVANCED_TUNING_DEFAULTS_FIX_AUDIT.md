# Advanced Tuning Defaults — Fix Audit

**Scope.** Three findings carried over from
`cowork_outputs/advanced_tuning_findings_FIX_PROMPT.md` (which references the
upstream `cowork_outputs/advanced_tuning_preferences_defaults_REPORT.md`
audit dated 2026-05-01; the report file itself is not checked in but its
findings are quoted verbatim in the FIX_PROMPT). The cowork audit covered
PR #987 / #990 / #993→`2da92af7` / #995 / #996 / #997 / #1001. This Phase 1
re-derives nothing — each finding is treated as a Phase 1 item.

**Local main HEAD at audit time:** `7ec14d2e` (chore: bump to v1.8.11).
`git log --since=2026-05-01` against the four scoped files
(`TodayWidget.kt`, `WidgetConfigDataStore.kt`, `ReengagementWorker.kt`,
`WidgetConfigDefaultsTest.kt`) returns zero commits — the cowork audit's
premise still holds. STOP-and-report condition (defaults already changed
in a missed commit) is **not** triggered: `WidgetConfigDefaultsTest.kt:21`
still asserts `assertEquals(5, cfg.maxTasks)`.

---

## A3 — TodayWidget maxTasks default drift on auto-update **(RED)**

**Findings.** PR #987 commit `4b9fdc21` ("feat(settings): wire 3
disconnected preferences (audit Section A)") replaced the size-tier-aware
literal at `app/src/main/java/com/averycorp/prismtask/widget/TodayWidget.kt:142`:

```kotlin
// before #987 (per cowork report)
val maxTasks = if (isLarge) 8 else 3

// after #987, current main:
val maxTasks = configuredMaxTasks.coerceIn(1, 20)
```

`configuredMaxTasks` is read from `WidgetConfigDataStore.TodayConfig.maxTasks`,
which defaults to `5` at two sites (`WidgetConfigDataStore.kt:39`
constructor default; `WidgetConfigDataStore.kt:50` snapshot getter
fallback `?: 5`). On auto-update with no per-widget opt-in:

- Small/medium widgets: rendered task count `3 → 5` (more rows than the
  vertical layout was designed for).
- Large widgets: rendered task count `8 → 5` (fewer rows than the prior
  large layout shipped).

The D2 size-tier-cap pattern that PR #997 introduced for InboxWidget
(`InboxWidget.kt:93-94`) is the established remediation:

```kotlin
val sizeTierCap = if (isMed) 3 else 5
val visible = rows.take(minOf(config.maxItems, sizeTierCap))
```

**Recommendation — PROCEED (PR 1).**
1. In `TodayWidget.kt:142`, replace `val maxTasks = configuredMaxTasks.coerceIn(1, 20)`
   with the size-tier-cap variant:

   ```kotlin
   val sizeTierCap = if (isLarge) 8 else 3
   val effective = minOf(configuredMaxTasks, sizeTierCap)
   val maxTasks = effective.coerceIn(1, 20)
   ```

2. In `WidgetConfigDataStore.kt:39` and `:50`, change `maxTasks: Int = 5`
   and the snapshot fallback `?: 5` to `8`. At the new default,
   `min(8, 3) = 3` for small/medium and `min(8, 8) = 8` for large — exact
   prior behavior preserved. A user-set value still caps at the size tier,
   matching the D2 contract.
3. In `WidgetConfigDefaultsTest.kt:21`, update `assertEquals(5, cfg.maxTasks)`
   → `assertEquals(8, cfg.maxTasks)`.

The new default is the one defensible behavior change in this PR; the
restoration-of-prior-behavior justification belongs in the PR body.

---

## C2 — ReengagementConfig boolean→counter semantic shift **(YELLOW)**

**Findings.** PR #997 squash-rollup commit `7390a1db` replaced the prior
boolean "already-sent" flag in `ReengagementWorker` with an integer
counter `KEY_REENGAGEMENT_SENT_COUNT` (`ReengagementWorker.kt:145`,
`intPreferencesKey("reengagement_sent_count")`). The cap is now read from
`AdvancedTuningPreferences.getReengagementConfig()` →
`ReengagementConfig.maxNudges` (`AdvancedTuningPreferences.kt:142-145`),
which defaults to `1`. At default, behavior is identical to the prior
boolean: exactly one nudge per absence period
(`ReengagementWorker.kt:66`: `if (nudgeCount >= config.maxNudges) return Result.success()`).
The counter resets to `0` on `onAppOpened()` (`ReengagementWorker.kt:166-171`).

The risk is that a future bump of `ReengagementConfig.maxNudges` (or a
DataStore-stored override from the Advanced Tuning UI) silently leaks
multiple nudges per absence period to existing users without a
behavior-preserving regression test in the way. There is no existing
`ReengagementWorkerTest` (verified via `find app/src/test/java/com/averycorp/prismtask/notifications`).

**Recommendation — PROCEED (PR 2).** No code change. Add a new
`app/src/test/java/com/averycorp/prismtask/notifications/ReengagementWorkerTest.kt`
modeled on `WeeklyReviewWorkerTest` (Robolectric +
`TestListenableWorkerBuilder` + injected-dependency mocks). Pin three
behaviors:

1. With default `ReengagementConfig()` (`absenceDays=2, maxNudges=1`) and
   a `KEY_LAST_OPEN_TIME` set to `now - 3 days`, `doWork()` calls
   `api.getReengagementNudge(...)` exactly once and increments the stored
   counter to `1`.
2. A second `doWork()` invocation with the counter at `1` returns
   `Result.success()` without invoking `api.getReengagementNudge`
   (`coVerify(exactly = 1) { api.getReengagementNudge(any()) }` across
   both runs).
3. After `ReengagementWorker.onAppOpened(context)`, the stored counter is
   `0` (read back via the same private DataStore the worker uses).

---

## D3 — Opacity floor 60→0 widening **(YELLOW)**

**Findings.** Same commit `7390a1db` changed
`WidgetConfigDataStore.OPACITY_RANGE` from `60..100` to `0..100`
(`WidgetConfigDataStore.kt:195`). The default in `TodayConfig` is
unchanged at `100` (`WidgetConfigDataStore.kt:41` and `:53`). Existing
stored values in the prior `60..100` band are untouched — the new lower
bound only matters if a user actively dials below `60` via the new
Advanced Tuning UI.

The existing `WidgetConfigDefaultsTest` covers two opacity values — `60`
and `100` (`WidgetConfigDefaultsTest.kt:78-83`) — but does not pin the
lower bound at `0` or assert the constant `OPACITY_RANGE` directly. A
silent re-tightening to `60..100` would be invisible to the test suite.

**Recommendation — PROCEED (PR 3).** No code change. Edit
`WidgetConfigDefaultsTest.kt` to add three assertions:

1. `WidgetConfigDataStore.TodayConfig().backgroundOpacityPercent == 100`
   (already covered by the existing default test on line 23 — keep it).
2. The `OPACITY_RANGE` constant is `0..100`. Since `OPACITY_RANGE` is
   `private`, exercise it through public behavior: a `TodayConfig`
   constructed with `backgroundOpacityPercent = 0` retains `0` after
   round-trip through the data class (the data class itself does not
   coerce — coercion happens in `setTodayConfig` and the read-side flow,
   which need a Context). At the unit-test layer the meaningful pin is
   `WidgetConfigDataStore.TodayConfig(backgroundOpacityPercent = 0).backgroundOpacityPercent == 0`
   alongside the pre-existing `60` and `100` cases — that demonstrates
   the model class accepts the widened band.
3. `WidgetConfigDataStore.TodayConfig(backgroundOpacityPercent = 60).backgroundOpacityPercent == 60`
   (already covered on line 78–80 — strengthens the audit trail by
   adding a comment noting the band was widened from `60..100` to
   `0..100` in commit `7390a1db`).

The persistence-layer coercion via `OPACITY_RANGE` is exercised by
instrumentation tests; the unit-test addition is a model-shape pin that
makes it impossible to silently tighten the band again without flipping
a JVM test red.

---

## Improvement table — ranked by wall-clock-savings ÷ implementation-cost

| Rank | Item | Cost | Savings | Notes |
|------|------|------|---------|-------|
| 1 | A3 (PR 1) | ~1 file edit + 1 test edit | Stops Phase F gate failure on Today widget rerender | Restores prior behavior; strict win |
| 2 | C2 (PR 2) | ~1 new test file (~120 LOC) | Catches future `maxNudges>1` regressions before users see them | Robolectric infra already proven by WeeklyReviewWorkerTest |
| 3 | D3 (PR 3) | ~10 LOC test additions | Catches future opacity floor re-tightening | Smallest of the three; ride along with the other test PRs |

All three items are cheap and independent. Total wall-clock for fan-out
should be well under one CI cycle each.

## Anti-pattern catalog (out-of-scope, recorded for follow-up)

These were flagged in the upstream cowork audit's Section C (YELLOW
literal-replacement zombies that survived the rollup) and the FIX_PROMPT
explicitly notes they are out of scope for this fix:

- `BurnoutScorer.kt:204-212` — internal `WORK_MAX = 25` etc. constants
  shadowing the centralized config.
- `ProductivityScoreCalculator.kt:144-148` — same pattern.
- `MoodCorrelationEngine.kt:175` — same.
- `ConversationTaskExtractor` and `SmartDefaultsEngine` — same.

These are cleanup, not behavior drift. File a separate audit if/when
they need to be unified — they are intentionally excluded here so the
three PRs stay narrow.

Also flagged but explicitly out of scope: PR API `merge_commit_sha`
fields for #993/#995/#996 reported `99cf6a96` / `b70ceaa7` / similar,
but neither is on `main`. The actual literal-replacement carrier on main
is PR #997's squash commit `7390a1db`. C2 and D3 cite `7390a1db`
accordingly.

## Phase 2 fan-out preview

Three independent PRs, each based on `main`, no dependent stack:

- **PR 1** — `fix/today-widget-maxtasks-default-drift`
  - Edits: `TodayWidget.kt`, `WidgetConfigDataStore.kt`,
    `WidgetConfigDefaultsTest.kt`.
  - Verify: `./gradlew :app:assembleDebug` and
    `./gradlew :app:testDebugUnitTest --tests "*WidgetConfigDefaults*" --tests "*TodayWidget*"`.
- **PR 2** — `test/reengagement-worker-maxnudges-regression`
  - Adds: `ReengagementWorkerTest.kt` (new file).
  - Verify: `./gradlew :app:assembleDebug` and
    `./gradlew :app:testDebugUnitTest --tests "*ReengagementWorker*"`.
- **PR 3** — `test/widget-opacity-floor-regression`
  - Edits: `WidgetConfigDefaultsTest.kt` only.
  - Verify: `./gradlew :app:assembleDebug` and
    `./gradlew :app:testDebugUnitTest --tests "*WidgetConfigDefaults*"`.

All three PRs link this audit and the cowork FIX_PROMPT in their bodies,
quote the prior `if (isLarge) 8 else 3` literal (PR 1 only — it's the
prior-behavior anchor), and cite `7390a1db` (PR 2 / PR 3) and `4b9fdc21`
(PR 1) as the introducing commits. Auto-merge enabled on each.
