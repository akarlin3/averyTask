# Medication Log — One-Time Custom Dose Audit

**Scope.** Add the ability to log a one-time *custom* medication dose to the
medication log — i.e. record "took Tylenol 500mg at 10pm" without first
creating a permanent `MedicationEntity`. The feature is a single coherent
new capability, so Phase 2 fan-out resolves to **one** implementation PR
(plus the audit-doc PR per the local pre-push hook).

**Local main HEAD at audit time:** `7ec14d2e` (chore: bump to v1.8.11).
Last commits to medication paths (most recent first): `a7d530aa`
(widget data wiring), `d6c1090e` (slot-edit reminder rearm), `0aebeae3`
(ktlint), `6d36c081` (slot CLOCK skip when INTERVAL), `70156dc3`
(slot-driven CLOCK reminders), `e0a46938` (slot INTERVAL alarm route).
None of these touch `MedicationDoseEntity` / `MedicationRepository.logDose`
/ `MedicationLogScreen`, so the design surface below is up to date.

The findings below are framed as **design items**, not bug fixes — each
one resolves a load-bearing decision before code lands. They all bundle
into a single PR because the feature is one coherent slice.

---

## D1 — Schema strategy: nullable FK + `custom_medication_name` column **(RED)**

**Findings.** `MedicationDoseEntity` (`MedicationDoseEntity.kt:50-72`)
declares `medication_id` as a non-nullable `Long` with a CASCADE FK to
`medications.id`. There is no escape hatch for a dose without a parent
medication. The existing surface — `logDose`, `logSyntheticSkipDose`,
`unlogDose`, `updateDose` — all assume a valid `medicationId`.

Three credible designs:

1. **Nullable FK + new `custom_medication_name TEXT` column.** Cleanest.
   Domain invariant: exactly one of `medicationId` / `customMedicationName`
   is non-null. Enforced at the entity / repository layer (Room CHECK
   constraints are awkward and would require yet another migration the
   moment we wanted to relax them).
2. **Sentinel "Custom" `MedicationEntity` + always-non-null FK.** Less
   schema churn but pollutes the medication list and seeding logic, and
   the sync layer would still need to track/dedupe the sentinel per
   account. Net cost is higher, not lower.
3. **Sibling `CustomMedicationDoseEntity` table.** Doubles every read
   path (queries union, ViewModel merges, log screen iterates two lists)
   and every sync / export pathway. Unjustified for a feature whose
   only difference is "no FK to parent".

Picking design (1). SQLite `ALTER TABLE` cannot drop `NOT NULL`, so the
migration is a standard table-recreation:

```sql
CREATE TABLE medication_doses_new (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  cloud_id TEXT,
  medication_id INTEGER,                    -- was NOT NULL
  custom_medication_name TEXT,              -- new
  slot_key TEXT NOT NULL,
  taken_at INTEGER NOT NULL,
  taken_date_local TEXT NOT NULL,
  note TEXT NOT NULL DEFAULT '',
  is_synthetic_skip INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  FOREIGN KEY(medication_id) REFERENCES medications(id) ON DELETE CASCADE
);
INSERT INTO medication_doses_new
  (id, cloud_id, medication_id, custom_medication_name, slot_key,
   taken_at, taken_date_local, note, is_synthetic_skip, created_at, updated_at)
SELECT
  id, cloud_id, medication_id, NULL, slot_key,
  taken_at, taken_date_local, note, is_synthetic_skip, created_at, updated_at
FROM medication_doses;
DROP TABLE medication_doses;
ALTER TABLE medication_doses_new RENAME TO medication_doses;
CREATE UNIQUE INDEX index_medication_doses_cloud_id ON medication_doses(cloud_id);
CREATE INDEX index_medication_doses_medication_id_taken_date_local
  ON medication_doses(medication_id, taken_date_local);
CREATE INDEX index_medication_doses_taken_date_local
  ON medication_doses(taken_date_local);
```

`CURRENT_DB_VERSION` (`Migrations.kt:1985`) goes `68 → 69`. The
`StartupCrashDiagnosticTest` will fail until `MIGRATION_68_69` is
appended to `ALL_MIGRATIONS`, which is the desired safety belt.

**Recommendation — PROCEED.** Bundled into PR 1.

---

## D2 — Repository API: `logCustomDose(name, takenAt, note)` **(GREEN)**

**Findings.** `MedicationRepository.logDose` (`MedicationRepository.kt:98-120`)
hardcodes `medicationId` as a required `Long`. Adding a sibling method
is the lowest-friction path:

```kotlin
suspend fun logCustomDose(
    name: String,
    takenAt: Long = System.currentTimeMillis(),
    note: String = ""
): Long {
    require(name.isNotBlank()) { "custom medication name must be non-blank" }
    val dayStartHour = taskBehaviorPreferences.getDayStartHour().first()
    val dateLocal = DayBoundary.currentLocalDateString(dayStartHour, takenAt)
    val now = System.currentTimeMillis()
    val dose = MedicationDoseEntity(
        medicationId = null,
        customMedicationName = name.trim(),
        slotKey = "anytime",
        takenAt = takenAt,
        takenDateLocal = dateLocal,
        note = note,
        createdAt = now,
        updatedAt = now
    )
    val id = medicationDoseDao.insert(dose)
    syncTracker.trackCreate(id, "medication_dose")
    widgetUpdateManager.updateMedicationWidget()
    return id
}
```

`slotKey = "anytime"` is the right default — custom doses are inherently
ad-hoc. The existing log screen already has an `ANYTIME` legacy bucket
(`MedicationLogScreen.kt:222-233`) that will pick these up without
further dispatch logic. Existing `unlogDose` / `updateDose` work
unchanged because they don't read `medicationId`.

**Recommendation — PROCEED.** Bundled into PR 1.

---

## D3 — UI affordance: TopAppBar action on `MedicationLogScreen` **(GREEN)**

**Findings.** `MedicationLogScreen` currently has no add affordance —
it's a read-only history view (`MedicationLogScreen.kt:54-117`). The
TopAppBar has only the back arrow. Right place for an `IconButton(...)`
with `Icons.Default.Add` and a content description "Log Custom Dose".

Tap opens a `ModalBottomSheet` (matches `MedicationTimeEditSheet.kt`
component pattern) with three fields:

- `OutlinedTextField` — medication name (required, validated via the
  Log button's `enabled = name.isNotBlank()`).
- A simple time row defaulting to "Now", with a tap-to-edit
  `TimePickerDialog`. Date defaults to today; users can shift it via a
  date picker exposed alongside the time row for "yesterday" / "two
  days ago" entry.
- `OutlinedTextField` — optional note (multi-line, max 200 chars).

On Log: viewmodel calls `repository.logCustomDose(name, takenAt, note)`,
sheet dismisses, and the new dose appears in today's day card under
the `ANYTIME` slot via the existing legacy bucketing.

**Recommendation — PROCEED.** Bundled into PR 1.

---

## D4 — Log row rendering: resolve display name via `customMedicationName` **(GREEN)**

**Findings.** `MedicationLogDay.medicationName(dose)`
(`MedicationLogViewModel.kt:175-178`) currently does:

```kotlin
fun medicationName(dose: MedicationDoseEntity): String {
    val med = medicationsById[dose.medicationId]
    return med?.displayLabel ?: med?.name ?: "Unknown"
}
```

For a custom dose where `medicationId == null`, the lookup misses and
falls through to `"Unknown"`. New logic:

```kotlin
fun medicationName(dose: MedicationDoseEntity): String {
    if (dose.medicationId == null) {
        return dose.customMedicationName ?: "Custom"
    }
    val med = medicationsById[dose.medicationId]
    return med?.displayLabel ?: med?.name ?: "Unknown"
}
```

Type-system note: changing `medicationId: Long` → `medicationId: Long?`
on `MedicationDoseEntity` ripples to a small set of callers — primarily
`MedicationViewModel` (slot-completion lookups) and the slot-id index
in `MedicationLogViewModel.kt:73-75`. Both compute `slotKey.toLongOrNull()`,
not the medication FK, so they remain unaffected. The few sites that
DO read `dose.medicationId` are `unlogDose` / `updateDose` (just pass
through), `BuiltInMedicationReconciler`, `CloudIdOrphanHealer`, and
`SyncService`'s upload-by-id-routing — each will need a null-skip
branch for custom doses (they have no parent medication to reconcile
against, no orphan to heal, no `medCloudId` to attach).

**Recommendation — PROCEED.** Bundled into PR 1. The null-skip branches
are mechanical, additive, and tested separately from the feature path.

---

## D5 — Sync mapping: `customMedicationName` field, drop FK requirement **(YELLOW)**

**Findings.** `MedicationSyncMapper.medicationDoseToMap`
(`MedicationSyncMapper.kt:93-106`) and `mapToMedicationDose`
(`MedicationSyncMapper.kt:108-124`) both assume a parent medication exists.
`SyncService.kt:1252-1256` actively bails when `medCloudId` is missing:

```kotlin
"medication_dose" -> {
    val dose = medicationDoseDao.getAllOnce().find { it.id == meta.localId } ?: return
    val medCloudId = syncMetadataDao.getCloudId(dose.medicationId, "medication") ?: return
    MedicationSyncMapper.medicationDoseToMap(dose, medCloudId)
}
```

For custom doses (`medicationId == null`), there is no parent medication
to look up. The mapper signature must accept a nullable `medicationCloudId`
and add a `customMedicationName` field. The upload-routing block above
must skip the medCloudId lookup when `dose.medicationId == null` and
pass `null` instead of bailing.

Pull side (`SyncService.kt:2203-2209` + `MedicationSyncMapper.mapToMedicationDose`)
mirrors: a custom dose pulled from cloud has no `medicationCloudId` to
remap, so it lands as a free-standing row with `medicationId = null` and
the carried `customMedicationName`.

This is YELLOW because the change has cross-device-sync implications.
The mitigation is straightforward — a new optional Firestore field
defaulting to `null` on legacy reads — but it must land in the same PR
as the entity change, otherwise an in-flight cloud doc could re-pull as
`medicationId = null` against a mapper that still requires it.

**Recommendation — PROCEED.** Bundled into PR 1.

---

## D6 — Export / import: handle nullable FK + new column **(YELLOW)**

**Findings.**

- **Export** (`DataExporter.kt:287`): emits the dose list via
  `gson.toJsonTree(database.medicationDoseDao().getAllOnce())`. Gson
  reflects over the entity, so the new `customMedicationName` field
  serializes for free. Nullable `medicationId` serializes as JSON
  `null`. No code change required.
- **Import** (`DataImporter.kt:800-842`): currently bails when
  `medicationId` is null —

  ```kotlin
  val exportedMedId = obj.get("medicationId")
      ?.takeIf { !it.isJsonNull }?.asLong
      ?: return@forEach
  ```

  This drops every custom dose on the floor. New logic: only require a
  remapped `medicationId` when `customMedicationName` is absent; for
  custom doses, insert with `medicationId = null` and the carried
  `customMedicationName`.

YELLOW because the exporter/importer lane is the only round-trip surface
that doesn't have a CI-enforced contract test pinning it. Manual smoke
test (export → wipe → import) is needed before the PR merges.

**Recommendation — PROCEED.** Bundled into PR 1.

---

## D7 — Tests: repository, log row resolution, migration **(GREEN)**

**Findings.** Existing test surface that needs additions:

- `MedicationRepositoryTest` — add `logCustomDose_inserts_dose_with_null_medication_id_and_custom_name`
  and `logCustomDose_blank_name_throws`.
- `MedicationLogViewModelTest` (or a new
  `MedicationLogDayMedicationNameTest` if the existing one is fixture-heavy)
  — assert `medicationName(dose)` returns `customMedicationName` when
  `medicationId == null`, and `"Custom"` when both are null (defensive).
- A migration test pinning the v68 → v69 schema, modeled on the existing
  `MIGRATION_*_*` test pattern under `app/src/androidTest/`. Out of
  scope for the unit-test layer — add as an `androidTest` for full
  schema validation.

**Recommendation — PROCEED.** Bundled into PR 1. Migration test is
optional only if `connectedDebugAndroidTest` infrastructure isn't
available locally; in that case, ship without it and let CI catch
schema regressions on the next push (the diagnostic test in
`StartupCrashDiagnosticTest` already gates the migration array).

---

## Improvement table — ranked by wall-clock-savings ÷ implementation-cost

This audit is one feature, not a portfolio of fixes — there is one
PROCEED line item (the bundled feature PR). The "savings" framing
matters less here than the sequencing. Implementation cost estimate:

| Scope | Files touched | Approx LOC |
|-------|---------------|------------|
| Entity (nullable FK + new column) | `MedicationDoseEntity.kt` | ~5 LOC |
| Migration v68 → v69 | `Migrations.kt` (new block + array append + version bump) | ~50 LOC |
| Repository | `MedicationRepository.kt` | ~25 LOC |
| ViewModel name resolution | `MedicationLogViewModel.kt` | ~5 LOC |
| Log screen TopAppBar action + sheet | `MedicationLogScreen.kt` + new `LogCustomDoseSheet.kt` | ~150 LOC |
| Sync mapper + service | `MedicationSyncMapper.kt`, `SyncService.kt` | ~30 LOC |
| Importer | `DataImporter.kt` | ~10 LOC |
| Tests (repository + view-model resolver) | new + edits | ~80 LOC |
| Total | ~9 files | ~355 LOC |

Well under any single-PR-too-big threshold. Bundling is correct here per
the workflow's "single coherent scope" rule.

## Anti-pattern catalog (out-of-scope for this audit)

- **Sentinel `MedicationEntity` for "Custom"** — pollutes seeding,
  reconciler, and the medication list. Documented above as design (2)
  for posterity; do not adopt.
- **Mirror columns on the `medications` table** (e.g. `is_one_off`) —
  same problem with extra cardinality on the parent table.
- **Free-text dose entry in `note`** — surfaces in history but doesn't
  participate in any analytics, refill projection, or correlation. The
  whole point of the feature is that the custom name is structured.
- **Adding the affordance to `MedicationScreen` instead of
  `MedicationLogScreen`** — wrong surface. The main screen is for
  scheduled / tracked meds; the log is the historical view, which is
  exactly where users look when they realize they forgot to track
  something one-off.

## Phase 2 fan-out preview

One PR (plus the audit-doc PR per the local pre-push hook):

- **PR 0** — `docs/medication-log-one-time-custom-audit` — this audit
  doc.
- **PR 1** — `feat/medication-log-one-time-custom` — entity + migration
  + repository + ViewModel + log screen + sync mapper + importer +
  tests, all bundled.

Acceptance for PR 1:
- `./gradlew :app:assembleDebug` green locally.
- `./gradlew :app:testDebugUnitTest --tests "*Medication*"` green
  locally (covers `MedicationRepositoryTest`, `MedicationLogViewModelTest`,
  any new tests).
- Manual smoke: open Medication Log → tap "+" → enter "Tylenol 500mg" /
  "Now" / note → Log → confirm row appears under today's `ANYTIME`
  bucket showing "Tylenol 500mg".
- Auto-merge enabled.
