# Self-Care / Cleaning Habit Default-Tier Setting

## Scope

User request: "Make a setting that allows the default tier of each
self-care/cleaning habit to be set."

Concretely: the four built-in `SelfCare*` routines — Morning, Bedtime,
Medication, Housework — each ship with their own tier hierarchy
(survival/solid/full, etc.). There is no user-configurable default
tier today. This audit verifies the premise and scopes the work.

## Phase 1 — Audit

### Item 1 — Tier concept actually exists for all four routines (GREEN)

**Findings.**

- `app/src/main/java/com/averycorp/prismtask/domain/model/SelfCareRoutine.kt`
  defines the tier sets and ordering used everywhere downstream:
  - `morningTierOrder = listOf("survival", "solid", "full")` — line 46
  - `bedtimeTierOrder = listOf("survival", "basic", "solid", "full")` — line 68
  - `medicationTierOrder = listOf("essential", "prescription", "complete")`
    — line 133. Note: the `medicationTiers` list at lines 113–118 also
    contains a `"skipped"` row, but it is intentionally excluded from
    the order so it never makes any med visible (line 131 comment).
  - `houseworkTierOrder = listOf("quick", "regular", "deep")` — line 160
- `SelfCareRoutines.getTierOrder(routineType)` (line 88) is the canonical
  lookup that everything else (visibility filter, defaults fallback)
  reads from.

**Recommendation.** PROCEED — premise is correct, the four-routine
shape matches what the user is asking for.

### Item 2 — Current "default tier" behaviour is implicit, not user-tunable (GREEN)

**Findings.**

- The picked tier is persisted **per day, per routine** in
  `SelfCareLogEntity.selectedTier`
  (`app/src/main/java/com/averycorp/prismtask/data/local/entity/SelfCareLogEntity.kt:24`).
  Default column value is the literal string `"solid"`.
- When no log exists yet for today, `SelfCareViewModel.getSelectedTier`
  (`app/src/main/java/com/averycorp/prismtask/ui/screens/selfcare/SelfCareViewModel.kt:145-147`)
  falls back to the **penultimate** tier of the routine's order:
  ```kotlin
  log?.selectedTier ?: SelfCareRoutines.getTierOrder(_routineType.value).let {
      if (it.size >= 2) it[it.size - 2] else it.first()
  }
  ```
  Effective defaults today: morning → `solid`, bedtime → `solid`,
  medication → `prescription`, housework → `regular`.
- `SelfCareRepository.setTier`
  (`app/src/main/java/com/averycorp/prismtask/data/repository/SelfCareRepository.kt:443-478`)
  is the only writer. When the user picks a tier and no log exists for
  today, a new `SelfCareLogEntity` is inserted with that explicit tier
  (line 469-476) — so the entity-level `"solid"` default at line 24 of
  the entity file is **never used in practice**; it is just a
  schema-level fallback for nullable column reads. The implicit
  behaviour the user perceives is the ViewModel fallback above.

**Why this matters for the design.** The default tier only needs to
intercept one place: the `getSelectedTier` fallback path. We do **not**
need to backfill existing `SelfCareLogEntity` rows or migrate the
`"solid"` column default — those rows already have an explicit tier the
user picked. New routines on a fresh day will read the new preference.

**Recommendation.** PROCEED — confirms the surface area is small (one
fallback site, four preference values).

### Item 3 — Pattern for adding the preference is established (GREEN)

**Findings.**

- `AdvancedTuningPreferences`
  (`app/src/main/java/com/averycorp/prismtask/data/preferences/AdvancedTuningPreferences.kt`)
  is the canonical home for power-user tuning knobs. Existing entries
  cover urgency bands, burnout weights, refill urgency, energy-pomodoro
  timings, weekly summary schedule, widget refresh, editor field rows,
  etc. — Sections B–E of `CUSTOMIZABLE_SETTINGS_GAP_AUDIT.md` (commits
  `3e94bb67`, `2da92af7`, `be2df2ff`, `450d3c7e`, `2efbb605` on the
  current branch).
- The pattern is: data class + four `stringPreferencesKey` (one per
  routine) + Flow getter + suspend setter — see
  `LifeCategoryCustomKeywords` (lines 113–118 + 424–431 + 532–539) for
  the closest-shaped precedent (a 4-string config object).
- `AdvancedTuningScreen` (`AdvancedTuningScreen.kt:101-132`) wires each
  group via `<Name>Group(viewModel)` Composables that read state and
  call setters. `AdvancedTuningViewModel` (file: `…/settings/AdvancedTuningViewModel.kt`)
  exposes one `StateFlow` per config + one `setX(value)` method.
- The screen has no dropdown helper today — only `IntSliderRow`,
  `FloatSliderRow`, `TimeRow`, `DayOfWeekRow`, and an `OutlinedTextField`
  (used by `LifeCategoryKeywordsGroup`). For tier picking, a tap-to-cycle
  row matching `DayOfWeekRow`'s shape (lines 273–298) is the lightest
  fit; a dropdown would be a one-off.

**Recommendation.** PROCEED — follow the `LifeCategoryCustomKeywords`
data shape and the `DayOfWeekRow` UI shape.

### Item 4 — Scope of the consumer change (GREEN)

**Findings.**

- Only `SelfCareViewModel.getSelectedTier` (line 145) needs the new
  default fed in. Inject `AdvancedTuningPreferences`, expose a
  `defaultTiers: StateFlow<SelfCareTierDefaults>`, and let
  `getSelectedTier` consult it when the log is null.
- `SelfCareRepository.setTier` does not need changes — it writes the
  user's actual pick and persists it; the new preference only governs
  the **first-time-today** display before the user taps a tier chip.
- No DB migration. No entity change. No backend change.
- One coercion concern: if an exported/imported preference contains a
  tier id that is no longer in the routine's order (e.g. user uninstalls
  a future build that retired a tier), the getter must coerce back to
  the existing penultimate-tier fallback. Same defensive reduce as the
  current logic — easy to preserve.

**Recommendation.** PROCEED — single consumer, no migration risk.

### Item 5 — Scoping note: "habit" wording in request is loose (GREEN)

**Premise verification.** The user wrote "default tier of each
self-care/cleaning habit". In codebase terms:

- The four routines (morning, bedtime, medication, housework) are
  represented as **built-in `HabitEntity` rows** with `isBuiltIn = true`
  and `templateKey` = `KEY_MORNING_SELFCARE` / `KEY_BEDTIME_SELFCARE` /
  `KEY_MEDICATION` / `KEY_HOUSEWORK`
  (`BuiltInHabitVersionRegistry.kt:47-110`). So calling them "habits"
  is technically correct, but the tier mechanism lives on the
  `SelfCareLogEntity` side, not on `HabitEntity`.
- Treating "self-care/cleaning habit" as the four `SelfCareRoutines`
  routines (rather than e.g. arbitrary user-created habits) matches
  what the user can actually configure today and avoids inventing a
  new HabitEntity column.

**Recommendation.** PROCEED with the four-routine interpretation. If
the user actually meant "every habit, not just the four routines",
they will course-correct — and that variant would need a different
design (a per-`HabitEntity` `default_tier` column with migration), so
it is worth resolving on the wrong-premise quality gate rather than
guessing.

---

## Ranked improvements

| # | Improvement | Wall-clock saved / cost | Notes |
|---|---|---|---|
| 1 | Add `SelfCareTierDefaults` (4 strings) to `AdvancedTuningPreferences`, wire `SelfCareViewModel.getSelectedTier` to read it, add a "Self-Care Default Tier" group to `AdvancedTuningScreen`. | High value / low cost | One PR; follows `LifeCategoryCustomKeywords` + `DayOfWeekRow` precedent; no migration. |

Single coherent scope → single PR per the fan-out bundling rule.

## Anti-patterns flagged (not fixing here)

- **`SelfCareLogEntity.selectedTier = "solid"` literal default**
  (`SelfCareLogEntity.kt:24`) is a schema-level dead default — every
  insert site overrides it. Not load-bearing, but mildly misleading
  when reading the entity. Out of scope here; would only matter if the
  default ever leaked through.
- **`getSelectedTier` fallback duplicates `getTierOrder` knowledge.**
  Today's penultimate-tier rule (`SelfCareViewModel.kt:145-147`) is
  encoded only in the ViewModel. After this work, the rule becomes "use
  user-pref, else penultimate-of-order" — still in the ViewModel.
  Acceptable; promoting it to `SelfCareRoutines` would be a churn-only
  refactor.

---

## Phase 3 — Bundle summary

_Filled in after Phase 2 PR merges._

## Phase 4 — Claude Chat handoff

_Emitted after Phase 3._
