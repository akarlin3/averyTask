# Medication slot-label drift audit (P1 follow-up)

**Scope.** A medication reminder fired at the right wall-clock time but its
notification body (`"$slotName Medications"` / `"It's $idealTime — time
for your $slotName dose."`) carried the wrong time-of-day label. Single
production occurrence, 2026-04-30. Triggered by user spec
`/audit-first` invocation; this audit closes the diagnostic gap left
open in `docs/audits/MEDICATION_REMINDERS_BOTH_MODES_AUDIT.md` (PRs
#977 / #979 / #980 / #986 / #991 fixed dispatch + fresh-install + flip
+ stale-on-flip; this one investigates label rendering at fire time).

**Date.** 2026-04-30
**Author.** Audit-first sweep
**Pre-audit baseline.** `main` @ `61502982`

---

## Architectural recap

Two reschedulers share `MedicationReminderReceiver`, both reach
`NotificationHelper.showSlotClockReminder` / `showSlotIntervalReminder`
which build the visible string straight from `slot.name` and
`slot.idealTime`:

```kotlin
// NotificationHelper.kt:454-465
suspend fun showSlotClockReminder(
    context: Context,
    slotId: Long,
    slotName: String,
    idealTime: String
) = showSlotReminder(
    context = context,
    slotId = slotId,
    notificationId = slotId.toInt() + SLOT_CLOCK_NOTIFICATION_OFFSET,
    title = "$slotName Medications",
    contentText = "It's $idealTime — time for your $slotName dose."
)
```

The receiver pulls `slot` fresh from `MedicationSlotDao` at fire time:

```kotlin
// MedicationReminderReceiver.kt:217-237
private suspend fun handleSlotClockAlarm(...) {
    val slot = entryPoint.medicationSlotDao().getByIdOnce(slotId) ?: return
    if (!slot.isActive) return
    entryPoint.medicationClockRescheduler().onAlarmFired(slotId)
    NotificationHelper.showSlotClockReminder(
        context = context, slotId = slotId,
        slotName = slot.name, idealTime = slot.idealTime
    )
}
```

So the rendered label / time always reflects the **current** slot row.
The alarm's `triggerMillis`, however, is locked in at the last
`AlarmManager.setExact()` call — and that only happens inside
`MedicationClockRescheduler.rescheduleAll()` /
`MedicationIntervalRescheduler.rescheduleAll()` /
`MedicationClockRescheduler.onAlarmFired()`.

**The drift surface is the gap between when `slot` rows change and when
those reschedulers re-run.**

---

## Hypothesis verification

The user spec listed four candidate causes. Verification:

### Hypothesis 1 — stale alarm with old slot metadata (RED, root-cause, PROCEED)

**Premise verification.** "Stale alarm metadata" turns out to be
slightly off — the receiver always re-reads slot data fresh, so the
*intent extras* aren't load-bearing for the rendered label. What *is*
stale is the alarm's `triggerMillis`. See Item 1.

### Hypothesis 2 — slot resolver confusion at fire time (GREEN, no work)

**Premise verification.** No window-based lookup exists. Both
`handleSlotClockAlarm` and `handleSlotIntervalAlarm` (`MedicationReminderReceiver.kt:198-237`)
read the slot by the concrete `clockSlotId` / `intervalSlotId` extra
the rescheduler put in the intent. There is no slot-by-time-window
resolver to confuse. The rescheduler also keys alarms by `slotRequestCode(slot.id)`,
so two slots can't share a `PendingIntent`. **No bug here.**

### Hypothesis 3 — sync race (RED, same fix as Item 1, PROCEED)

**Premise verification.** Confirmed real, but the mechanism isn't
"receiver fires using cached state while body reads fresh state" —
it's the same triggerMillis-staleness as Hypothesis 1, this time
caused by sync delivering the slot rename rather than a local edit.
See Item 2 — same fix vector resolves it.

### Hypothesis 4 — user-error in slot config (GREEN, no work)

**Premise verification.** `MedicationSlotEntity.name` and
`MedicationSlotEntity.idealTime` are deliberately independent
columns (`MedicationSlotEntity.kt:36-46`). Custom names like "Before
bed" or "After dinner" don't have an inherent wall-clock; the entity
documentation explicitly endorses this. **Not a bug — by design.**
Anti-pattern note in the trailing section if we want to harden the
editor's UX.

---

## Item 1 — Slot edits don't re-arm AlarmManager (RED, PROCEED)

**Findings.** `MedicationSlotsViewModel.{create,update,softDelete,restore}`
(`MedicationSlotsViewModel.kt:34-87`) call into
`MedicationSlotRepository`, which in turn writes the DAO and bumps
`SyncTracker`. Neither layer touches a rescheduler:

```kotlin
// MedicationSlotRepository.kt:59-69
suspend fun updateSlot(slot: MedicationSlotEntity) {
    val updated = slot.copy(updatedAt = System.currentTimeMillis())
    slotDao.update(updated)
    syncTracker.trackUpdate(updated.id, "medication_slot")
}

suspend fun softDeleteSlot(id: Long) {
    val now = System.currentTimeMillis()
    slotDao.softDelete(id, now)
    syncTracker.trackUpdate(id, "medication_slot")
}
```

`MedicationClockRescheduler` is invoked only from:

- `BootReceiver.onReceive` (device boot)
- `PrismTaskApplication.startMedicationReschedulers()` (app launch,
  PrismTaskApplication.kt:368)
- `MedicationReminderModeSettingsViewModel.save()` (global mode flip,
  MedicationReminderModeSettingsViewModel.kt:43)
- `MedicationClockRescheduler.onAlarmFired()` (alarm self-rearm)

`MedicationIntervalRescheduler.start()` adds one more trigger:

```kotlin
// MedicationIntervalRescheduler.kt:142-146
fun start(scope: CoroutineScope = defaultScope) {
    medicationDoseDao.observeMostRecentDoseAny()
        .onEach { scope.launch { rescheduleAll() } }
        .launchIn(scope)
}
```

**Dose** changes propagate; **slot** changes do not. Concrete drift
scenario:

1. Day 0: user creates slot 1 = "Morning" @ "08:00". App launch fires
   `rescheduleAll`, alarm 700_001 armed for tomorrow 08:00.
2. Day 1, 08:00: alarm fires → receiver re-arms for Day 2 08:00,
   notification renders "Morning Medications · It's 08:00 — time for
   your Morning dose." All consistent.
3. Day 1, 22:00: user opens slot editor and changes slot 1 to
   "Evening" @ "20:00". `MedicationSlotsViewModel.update` writes the
   row. **No rescheduler runs.** Alarm 700_001 still set for Day 2 08:00.
4. Day 2, 08:00: alarm fires. `handleSlotClockAlarm` reads slot 1
   fresh → "Evening" @ "20:00". Notification body: **"Evening Medications
   · It's 20:00 — time for your Evening dose."** Wall clock at delivery
   is 08:00.

User experience: an "Evening" notification fires at 8 AM, with the
body claiming it is 20:00. Matches the reported P1 exactly. Re-arm in
`onAlarmFired` then fixes the alarm for Day 3, so the bug is
self-healing after the first miss — which matches "single
occurrence."

The INTERVAL path drifts the same way: `slot.name` is the only
slot-derived string in `showSlotIntervalReminder`, but a slot rename
between rolling alarms produces "wrong-name-fires-at-old-anchor"
output until the next dose triggers `MedicationIntervalRescheduler.start`'s
Flow observer.

**Risk.** RED. P1 user-trust regression, content-correctness, no
mitigation in code today. Mode-agnostic (CLOCK and INTERVAL both
affected via different mechanisms — CLOCK via stale `triggerMillis`,
INTERVAL via stale anchor relative to slot rename).

**Recommendation.** PROCEED. Add a slot-Flow observer to both
reschedulers, mirroring the dose-Flow observer that already exists on
the interval side. Single change point closes Item 1 + Item 2 + a
chunk of Item 3.

```kotlin
// MedicationClockRescheduler.kt — add an observer
fun start(scope: CoroutineScope = defaultScope) {
    medicationSlotDao.observeAll()
        .onEach { scope.launch { rescheduleAll() } }
        .launchIn(scope)
}

// MedicationIntervalRescheduler.kt — extend the existing start()
fun start(scope: CoroutineScope = defaultScope) {
    medicationDoseDao.observeMostRecentDoseAny()
        .onEach { scope.launch { rescheduleAll() } }
        .launchIn(scope)
    medicationSlotDao.observeAll()
        .onEach { scope.launch { rescheduleAll() } }
        .launchIn(scope)
}
```

Wire both `start()`s in `PrismTaskApplication.startMedicationReschedulers`
(the interval side already calls `start(appScope)`; clock side needs
the same line added).

`rescheduleAll` is idempotent — every pass cancels and re-registers,
so over-triggering is safe; the dominant cost is AlarmManager IPC and
a slot edit is rare. The legacy interval doc-comment (`MedicationIntervalRescheduler.kt:135-145`)
already says "a few extra passes per second is fine"; identical
reasoning applies.

---

## Item 2 — Cross-device sync rename doesn't re-arm (RED, same fix as Item 1, PROCEED)

**Findings.** `SyncService.pullCollection("medication_slots")` writes
slot rows straight to the DAO:

```kotlin
// SyncService.kt:2083-2098
val existingByName = medicationSlotDao.getByNameOnce(incoming.name)
…
medicationSlotDao.update(incoming.copy(id = existingByName.id))
…
val newId = medicationSlotDao.insert(incoming)
```

`BackendSyncService.kt:57` registers a similar `medicationSlotDao`
write path. Neither calls `medicationClockRescheduler.rescheduleAll()`
or `medicationIntervalRescheduler.rescheduleAll()`.

The Flow observer proposed in Item 1 picks up sync writes for free:
Room emits to all `Flow<List<MedicationSlotEntity>>` subscribers
whenever the table changes, regardless of write origin. Sync race ⇒
slot Flow emission ⇒ `rescheduleAll` ⇒ alarms tracked to the latest
row state.

**Risk.** RED. Same bug class as Item 1, separate trigger.

**Recommendation.** PROCEED. Bundled into the Item 1 PR — the slot
Flow observer is the same code change. Add an `androidTest` (or a
`Robolectric` unit test using a stub Flow source) asserting that an
inserted/updated slot row produces a fresh `setExact` call against
the AlarmManager. Don't ship a synthetic `SyncService → reschedule`
hook — fewer entry points; the Flow is the right seam.

---

## Item 3 — Notification body could snapshot label at registration (YELLOW, DEFERRED)

**Findings.** A defensive layer would put `slot.name` /
`slot.idealTime` in the alarm intent extras at registration time, and
the receiver would prefer those over a fresh DB read. That preserves
the *intent* of the alarm armed for that wall-clock time even if the
slot row has drifted. Trade-off: adds two redundant sources of truth
(intent extras vs. DB row), and any future schema change to the
notification body has to update both.

Items 1 + 2 fix the root cause directly — the alarm and the rendered
text both follow the slot row. A snapshot layer adds defense in depth
but at the cost of intent-extras drift, which is exactly the
single-source-of-truth principle the receiver was deliberately
designed around (`MedicationReminderReceiver.kt:32-38` doc-comment
explicitly notes "tomorrow's occurrence is re-armed from the receiver
before the notification is shown").

**Risk.** YELLOW (low — only useful as a safety net once Items 1+2
land).

**Recommendation.** DEFER. Re-evaluate only if a recurrence shows up
after Items 1+2 are deployed. Track in a follow-up note rather than a
file-PR; if shipped later, it should be the small surface
"render-from-snapshot when slot row changed since alarm armed" rather
than wholesale duplication.

---

## Item 4 — Per-medication slot-time override is ignored by reschedulers (YELLOW, DEFERRED)

**Findings.** `MedicationSlotOverrideEntity` carries `overrideIdealTime`
(per `(medication, slot)` pair). `MedicationClockRescheduler.rescheduleAll`
uses only `slot.idealTime`; overrides are never consulted. So a user
who sets "this med fires at 19:00 on Evening slot, but Evening slot's
idealTime is 18:00" will still get a 18:00 alarm.

This is orthogonal to the slot-label-drift bug but it's another flavor
of "alarm trigger doesn't reflect current entity state." Adjacent
surface, separable fix.

**Risk.** YELLOW. Different bug; adjacent surface; not what the user
reported.

**Recommendation.** DEFER. Open as a separate scope (`SLOT_OVERRIDE_NOT_HONORED_AUDIT`)
once telemetry confirms the override surface is in active use. Fix
shape would be: extend `nextTriggerForClock` to consult overrides per
`(medication, slot)`, register one alarm per `(med, slot)` pair when
an override exists. That's a request-code namespace decision that
needs design, not a one-line fix.

---

## Item 5 — Test gap: no rename → re-arm regression coverage (YELLOW, PROCEED)

**Findings.** `MedicationClockReschedulerTest.kt` (101 lines) covers
pure helpers (`slotRequestCode`, `nextTriggerForClock`). Nothing
asserts that a slot rename produces a fresh AlarmManager pass. The
seam that breaks is:

`MedicationSlotDao.update` ⇒ Flow emission ⇒ `rescheduleAll` ⇒
`registerAlarmForSlot` (AlarmManager).

`grep "observeAll\|MedicationSlotDao" app/src/test/**/*.kt` returns
zero hits in any rescheduler test. The dispatch contract test
(`MedicationReminderReceiverDispatchTest`, added by PR #979) is at the
receiver layer — too far down the pipeline to catch this gap.

**Risk.** YELLOW. The Item 1 fix is small; without coverage it can
silently regress on any future scheduler refactor.

**Recommendation.** PROCEED. Co-ship with Item 1. Two cases:

1. `slotEdit_triggersReschedulePass()` — Robolectric or `runTest`
   harness with a fake `MedicationSlotDao` whose `observeAll()`
   emits a renamed slot row; assert `rescheduleAll` ran and
   `AlarmManager.setExact` was called once with `slotRequestCode(slot.id)`.
2. `multipleSlotEditsCoalesceCheaply()` — sanity bound on extra
   `setExact` calls per emission, so a rapid sync burst doesn't
   AlarmManager-flood.

---

## Improvement ranking

Sorted by wall-clock-savings ÷ implementation-cost. Item 1 + Item 2
collapse into one PR; Item 5 co-ships. Items 3 / 4 deferred.

| Rank | Item | Severity | Cost | Why |
|------|------|----------|------|-----|
| 1 | #1 + #2 slot-Flow observer wires reschedulers to local + sync edits | RED | low | One-file edit on each rescheduler, mirrors the dose-Flow pattern that already works. Closes the P1 root cause. |
| 2 | #5 rename → re-arm regression test | YELLOW | low | Co-ships with #1. Flow seam is the obvious spot to pin. |
| 3 | #3 snapshot label at registration | YELLOW | medium | Defense in depth only — defer pending recurrence. |
| 4 | #4 honor `overrideIdealTime` in clock rescheduler | YELLOW | medium | Adjacent bug, separate scope, separate audit. |

---

## Anti-patterns flagged but not blocking

- **Slot editor (`MedicationSlotsScreen` / `MedicationSlotsViewModel`)
  allows arbitrary `(name, idealTime)` pairs without warning.** Custom
  names are intentional — but a soft confirmation when the entered
  name matches a built-in time-of-day bucket but the time doesn't
  (e.g. naming a slot "Morning" with idealTime "20:00") would catch
  Hypothesis 4 cleanly. Defer; cosmetic UX, not the cause of the P1.

- **`MedicationClockRescheduler` and `MedicationIntervalRescheduler`
  have asymmetric `start()` surfaces.** Interval has a public
  `start(scope)` invoked from `PrismTaskApplication.startMedicationReschedulers`;
  clock has none. Item 1 normalizes them. After this audit lands the
  symmetry is enforced — record in the doc-comment that both
  reschedulers expose `start()`.

- **`SyncTracker.trackUpdate(id, "medication_slot")` is the only
  side-effect of `updateSlot` outside the DAO write.** Adding
  rescheduler triggers to the repository layer would proliferate
  effects per call site; the Flow observer is the right
  single-source-of-truth seam.

---

## Phase 3 — Bundle summary

(Populated after Phase 2 PRs auto-merge.)

## Phase 4 — Claude Chat handoff

(Populated last in this run, in a fenced markdown block.)
