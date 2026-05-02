# Medication Notification Log — Tier-Time Drift Audit

**Scope (one sentence):** the admin Notification Log shows medication
reminders at clock times that don't match the user-set medication-slot
"tier times" — investigate the projector vs. the production schedulers
to find the divergence and decide whether to fix the log, the schedulers,
or both.

**Why now:** user reports the log is misleading; it's the only in-app
surface for verifying scheduling without inspecting AlarmManager (which
Android does not expose to apps), so a lying log silently undermines
every future scheduling debug session.

---

## Background — schedulers and the log

Three live scheduling pipelines own medication alarms:

1. **`MedicationReminderScheduler`** (`notifications/MedicationReminderScheduler.kt`,
   request-code base `400_000`) — legacy. Reads `MedicationEntity.scheduleMode`
   ∈ `TIMES_OF_DAY` / `SPECIFIC_TIMES` / `INTERVAL` / `AS_NEEDED`.
   `TIMES_OF_DAY` maps the bucket strings (`morning`/`afternoon`/`evening`/`night`)
   onto a hard-coded `TIME_OF_DAY_CLOCK = { morning="08:00", afternoon="13:00",
   evening="18:00", night="21:00" }`.

2. **`MedicationClockRescheduler`** (added PR #980, request-code base `700_000`)
   — slot-driven CLOCK alarms. Walks active `MedicationSlotEntity` rows;
   for any slot whose resolved reminder mode is CLOCK fires one wall-clock
   alarm at `slot.idealTime` (`"HH:mm"`) and re-arms tomorrow's
   occurrence in `onAlarmFired`. PR #1017 wired
   `medicationSlotDao.observeAll()` so slot edits / soft-deletes /
   sync pulls re-arm the alarm in place.

3. **`MedicationIntervalRescheduler`** (request-code base `500_000` per
   slot, `600_000` per per-medication override) — slot- and
   medication-driven INTERVAL alarms. Uses the most-recent dose as the
   anchor; bootstraps `interval_minutes` from now when no dose exists.

The **Notification Log** screen (`ui/screens/admin/AdminNotificationLogScreen.kt`)
calls `NotificationProjector.projectAll()` and renders the result. The
projector was added in PR #1040 with the explicit promise (in code
comments and the Notification Log screen body text) that it
"mirrors the production schedulers' trigger-time logic … so the projection
stays honest as that logic evolves."

---

## Sole scoped item — Notification Log shows wrong medication times (RED)

**Findings.**

`NotificationProjector.projectMedications()`
(`domain/usecase/NotificationProjector.kt:204–224`) projects medication
reminders by switching on `med.scheduleMode`:

```kotlin
when (med.scheduleMode) {
    "TIMES_OF_DAY" -> projectMedicationFixedTimes(
        med = med,
        clocks = parseTimesOfDay(med.timesOfDay).mapNotNull { TIME_OF_DAY_CLOCK[it] },
        ...
    )
    "SPECIFIC_TIMES" -> projectMedicationFixedTimes(
        med = med,
        clocks = parseSpecificTimes(med.specificTimes),
        ...
    )
    "INTERVAL" -> projectMedicationInterval(med, now)
    else -> emptyList()
}
```

The projector copies `MedicationReminderScheduler`'s logic verbatim,
including the same hard-coded `TIME_OF_DAY_CLOCK` map (`NotificationProjector.kt:500–505`).
It **never reads `MedicationSlotEntity.idealTime`, never queries
`MedicationSlotDao`, and is not injected with the slot DAO at all**
(`NotificationProjector.kt:69–80`). The slot-driven CLOCK and INTERVAL
schedulers — which are what actually fire user-visible reminders for
slot-linked medications — are invisible to the projection.

This produces three observable failure modes for the user:

1. **Pure slot-based medications are silently absent.** A medication
   created via `MedicationViewModel.addMedication`
   (`ui/screens/medication/MedicationViewModel.kt:289–325`) inherits
   `MedicationEntity.scheduleMode = "TIMES_OF_DAY"` (the entity default,
   `data/local/entity/MedicationEntity.kt:56`) but never populates
   `timesOfDay`. `parseTimesOfDay(null)` is empty, so the projector emits
   nothing. Meanwhile `MedicationClockRescheduler` is firing a real
   alarm at every linked slot's `idealTime`. The Notification Log shows
   no entry for that medication on a day when alarms will demonstrably
   fire — the log lies by omission.

2. **Legacy-data medications appear at the wrong wall-clock time.** A
   medication that still carries `scheduleMode="TIMES_OF_DAY",
   timesOfDay="morning"` (e.g. from a sync-pulled cloud row predating
   the slot system, or from `data/seed/` / sync-fuzz-generator paths)
   is projected at `08:00` (the hard-coded bucket clock) regardless of
   what slot it is linked to. If the user has set a "Morning" slot to
   `07:30` via `Settings → Medication Slots`, `ClockRescheduler` arms
   the actual alarm at `07:30`; the log still says `08:00`. Same
   medication, two wall-clocks, no shared source of truth.

3. **`SPECIFIC_TIMES` legacy medications drift the same way.** The
   projector projects from `med.specificTimes` while `ClockRescheduler`
   fires from `slot.idealTime`. If the legacy field and the slot
   `idealTime` disagree (the editor doesn't reconcile them — slot edits
   write to `MedicationSlotEntity`, never back to `med.specificTimes`),
   the log shows the stale clock.

A fourth, related gap: INTERVAL slots are projected only via
`med.scheduleMode == "INTERVAL"` (which slot-based medications never
have), so slot-driven INTERVAL alarms — fired by
`MedicationIntervalRescheduler` from `slot.reminderIntervalMinutes` and
the most-recent dose anchor — never appear in the log either.

`projectMedicationFixedTimes` cites `HabitReminderScheduler.timeStringToNextTrigger`
— the projector and the legacy scheduler share that helper, so the time
math is *internally* consistent. The drift is purely a **wrong source of
truth** problem: the projector reads the legacy schema while production
fires from the slot schema.

**Why PR #1040 introduced this.** The PR's commit message reads "Habit-interval,
medication, escalation, and worker-driven notifications are not yet
projected." The same PR landed `projectMedications()` anyway, copying
the legacy scheduler — but the comment was written before the
PR-#980 slot scheduler was on the author's radar. The projector was
honest about what it modeled at the time of writing; the slot scheduler
silently invalidated half of it. There is no test in
`app/src/test/java/com/averycorp/prismtask/domain/usecase/NotificationProjectorTest.kt`
that injects a `MedicationSlotEntity` — `git grep` for `idealTime` in
that file returns zero hits — so the gap was invisible to CI.

**Risk classification: RED.**

The Notification Log is the *only* in-app surface for verifying
scheduling, and its on-screen body text claims it mirrors the production
schedulers. A user-facing claim that doesn't match reality is the
strongest signal a debug surface can give that something is structurally
wrong elsewhere — and that's exactly what it's giving here, falsely.
A future debugging session that trusts the log will draw the wrong
conclusion. Admin-only ≠ low-stakes when the admin screen is the
ground-truth surface for everyone else's bug reports.

**Recommendation: PROCEED.**

Make `MedicationSlotDao` the source of truth for the projector when
slots exist, exactly as `MedicationClockRescheduler` and
`MedicationIntervalRescheduler` do. Specific scope:

- Inject `MedicationSlotDao` into `NotificationProjector`.
- Add `projectMedicationSlotsClock(now, horizonEnd)` that walks active
  slots, resolves the reminder mode via `MedicationReminderModeResolver`
  (with `medication = null, slot = slot, global = global`), and for
  CLOCK-mode slots projects daily occurrences at `slot.idealTime`
  using `MedicationClockRescheduler.nextTriggerForClock` (already
  `internal` and unit-testable).
- Add `projectMedicationSlotsInterval(now)` that mirrors
  `MedicationIntervalRescheduler.rescheduleAll`'s loop — anchor on
  `medicationDoseDao.getMostRecentDoseAnyOnce()?.takenAt`, bootstrap
  to `now + intervalMillis` when no anchor exists.
- Skip the legacy `projectMedications()` path for any medication that
  has at least one linked active slot (the slot scheduler owns those
  alarms; double-projection would double-count). Keep the legacy path
  for medications with no linked slots and a populated
  `scheduleMode`/`timesOfDay`/`specificTimes` — the legacy
  `MedicationReminderScheduler` is still wired and still fires for
  them.
- Update the on-screen blurb in
  `ui/screens/admin/AdminNotificationLogScreen.kt` to mention slot-driven
  reminders explicitly so the contract stays honest.
- Add `NotificationProjectorTest` cases that seed a slot, link it to a
  medication, and assert the projected trigger matches `slot.idealTime`
  — the absence of any such test is what let the drift land in the
  first place.

Avoid widening scope to "delete the legacy scheduler" — that's a
separate, heavier piece of work that needs a data-migration plan
(legacy `med.timesOfDay` / `med.specificTimes` columns are still
populated by sync pulls and seed data).

---

## Ranked improvement table

| # | Improvement | Wall-clock savings | Implementation cost | S/C |
|---|-------------|--------------------|---------------------|-----|
| 1 | Project medication slots in `NotificationProjector` (CLOCK + INTERVAL) and skip legacy path for slot-linked meds; add tests; update screen blurb | High — restores trust in the only in-app scheduling-verification surface | ~150 LOC + 4–6 unit tests | high |

Single PROCEED item; no DEFER / STOP-no-work-needed entries.

---

## Anti-patterns flagged (not in scope of this PR)

- **`TIME_OF_DAY_CLOCK` is duplicated** between
  `MedicationReminderScheduler.kt:250` and `NotificationProjector.kt:500`.
  Two copies of the same constant invite future drift; if either is ever
  edited, the other rots silently. Worth a small follow-up to extract.
- **`MedicationReminderScheduler.scheduleForMedication` skips the
  CLOCK-mode wall-clock paths when the resolved mode is INTERVAL** but
  has no equivalent inverse — there is nothing stopping
  `MedicationReminderScheduler` *and* `MedicationClockRescheduler` from
  both arming alarms for the same medication when the medication has
  both a populated `timesOfDay` and a linked slot. The two
  request-code namespaces (`400_000` vs `700_000`) keep them from
  colliding at the AlarmManager level, but the user receives duplicate
  notifications. Worth a follow-up audit.
- **The on-screen blurb in `AdminNotificationLogScreen.kt:80–86`** lists
  what the log *does* cover but not what it omits. Even after this fix
  it should call out reactive-only sources (escalation, follow-up nags)
  the same way the projector's KDoc does.

---

## Phase 3 — Bundle summary

**Shipped.**

- **PR #1052** (`fix(notifications): project slot-driven medication reminders
  in admin log`) — squash-merged as `cc23ab92`. Bundles the audit doc + the
  projector fix + the on-screen blurb update in a single PR. Inject
  `MedicationSlotDao` + `UserPreferencesDataStore` into `NotificationProjector`,
  add `projectMedicationSlotsClock` and `projectMedicationSlotsInterval`
  paths that mirror `MedicationClockRescheduler` /
  `MedicationIntervalRescheduler`, and skip the legacy
  `med.scheduleMode`-driven projection for any medication with at least one
  linked active slot. Adds two new `Source` enum entries
  (`MEDICATION_SLOT_CLOCK`, `MEDICATION_SLOT_INTERVAL`) so the log labels
  the row by which scheduler owns the alarm.

**Tests added with the fix.** Five new unit tests in
`NotificationProjectorTest`, all passing locally (`./gradlew
:app:testDebugUnitTest --tests "...NotificationProjectorTest"` reports 18
tests, 0 failures, ~1.3 s):

- `slot CLOCK projects daily occurrences at slot idealTime` — anchors the
  fix's premise: a slot at `07:30` produces seven daily projected rows at
  `07:30`, not the legacy `08:00` bucket.
- `slot CLOCK fans out one row per linked medication` — confirms the
  `(slot × medication)` fanout and per-med labelling.
- `slot-linked medication is skipped by legacy projection to avoid
  double-count` — locks in the legacy-skip rule.
- `slot INTERVAL anchors on most recent dose plus interval minutes` —
  mirrors `MedicationIntervalRescheduler.rescheduleAll`'s anchor math.
- `slot INTERVAL bootstraps to now plus interval when no dose exists` —
  matches the bootstrap path the production rescheduler also takes.

**Measured impact.** Cannot be measured post-merge in isolation
(the projector is read on demand by `AdminNotificationLogViewModel.refresh`),
but the on-screen blurb now matches what the projector computes and the
slot-aware tests are wired into CI, so a future regression of the same
shape would fail the suite.

**Memory entry candidates.** None — the lesson here ("when a new
production scheduler lands, every read-side mirror needs to be updated
in the same PR or guarded by a coverage gap audit") is already implicit
in `feedback_audit_drive_by_migration_fixes.md`'s spirit
(`git log -p -S` before recommending fixes). The duplicate
`TIME_OF_DAY_CLOCK` constant (flagged in the anti-patterns section) is a
candidate for a small drive-by extraction PR but doesn't warrant a
memory entry.

**Schedule for next audit.** None — the two follow-ups in the
anti-patterns section (dual-scheduler duplicate-firing risk; on-screen
blurb completeness for reactive-only sources) are small enough to land
opportunistically the next time the surrounding code is touched.

**Re-baselined wall-clock estimate.** This audit + fix took roughly the
expected time for a single-PROCEED-item audit (one source-of-truth
divergence, one reader to fix, one screen blurb to sync, five
behaviour-anchoring tests). No re-baseline needed — the validated
single-pass audit shape from PR #859 still holds.

