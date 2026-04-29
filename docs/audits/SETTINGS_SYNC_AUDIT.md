# Settings Sync Audit — Appearance / Experience Level

**Scope (verbatim from user):** "Settings such as appearance or experience
level aren't being synced."

**Audit date:** 2026-04-28
**Branch baseline:** `main` @ `8d9271c0` (post-merge of #897, the analytics
summary tile row).
**Audited surface:** the entire DataStore → Firestore → DataStore round-trip
for the user-configurable preferences mapped to the two named symptoms.

## Premise mapping

The two user-named symptoms map to PrismTask preference DataStores as
follows:

| User term | DataStore | Sync owner |
|---|---|---|
| "Experience Level" (Basic / Standard / Power) | `user_prefs` (`KEY_UI_TIER` = `ui_complexity_tier`) | `GenericPreferenceSyncService` |
| "Appearance" — theme mode, accent, prism theme, font scale, priority colors, recent custom colors, color overrides | `theme_prefs` | bespoke `ThemePreferencesSyncService` |
| "Appearance" — compact mode, card corner radius, show card borders | `user_prefs` (`KEY_COMPACT_MODE`, `KEY_CARD_CORNER_RADIUS`, `KEY_SHOW_CARD_BORDERS`) | `GenericPreferenceSyncService` |

`ui_complexity_tier` is stored in the *user_prefs* file (not the *nd_prefs*
file as the "ND-friendly Modes" feature might suggest). Both DataStores are
registered for sync — **the framework is correctly wired in source for every
preference covered by the user's report**.

## Item 1 — `PreferenceSyncModule` registration coverage (GREEN)

`di/PreferenceSyncModule.kt:54-71` registers 18 DataStores into
`Set<PreferenceSyncSpec>` via `@ElementsIntoSet` multibinding:

```
a11y_prefs, archive_prefs, coaching_prefs, daily_essentials_prefs,
dashboard_prefs, habit_list_prefs, leisure_prefs, medication_prefs,
morning_checkin_prefs, nd_prefs, notification_prefs, onboarding_prefs,
tab_prefs, task_behavior_prefs, template_prefs, timer_prefs,
user_prefs, voice_prefs
```

`user_prefs` is registered (line 70) — this is the file that holds
`ui_complexity_tier` (the "Experience Level" key). `theme_prefs` is
**deliberately excluded** because it has a dedicated, pre-existing sync
service (`ThemePreferencesSyncService`) that does extra work the generic
engine doesn't (cold-start initial-pull, push guard via
`THEME_LAST_SYNCED_AT_KEY`).

**No drift** between the registered set and the on-disk DataStore inventory:
- 22 `Context.*DataStore` extensions exist under `data/preferences/`.
- 5 are correctly excluded (per the module's KDoc): `auth_token_prefs`,
  `pro_status_prefs`, `backend_sync_prefs`, `built_in_sync_prefs`,
  `medication_migration_prefs`. All five are device-local (tokens, billing
  cache, watermarks, one-shot migration flags) and must NOT sync.
- The KDoc additionally lists `gcal_sync_prefs`, `sync_device_prefs`,
  `theme_prefs`, `sort_prefs` as exclusions; these are not under
  `data/preferences/` (they live under `data/calendar/` and
  `data/remote/sync/`) — correctly classified.

**Verdict:** registration coverage is correct. No item missing.

## Item 2 — Cold-start observer wiring (GREEN)

`MainActivity.kt:149-169` invokes (on every cold start, regardless of sign-in
state):

- `sortPreferencesSyncService.startPushObserver()`
- `themePreferencesSyncService.startPushObserver()`
- `themePreferencesSyncService.ensurePullListener()`
- `genericPreferenceSyncService.startPushObserver()`
- `genericPreferenceSyncService.ensurePullListener()`

This is the cold-start gap that PR #582 added a fix for (commit `53adba59`,
"Expand theme sync to full appearance payload; fix cold-start listener
gap"). The fix is still in place — verified.

**Verdict:** cold-start wiring is correct.

## Item 3 — Post-sign-in initial sync wiring (GREEN)

`AuthViewModel.kt:221-236` (`runPostSignInSync`) calls `startAfterSignIn()`
on all three sync services *after* the post-sign-in full Room sync completes:

```kotlin
// Order in source:
// 1. SyncService.startSync() (Room data)
// 2. SyncService.initialUpload()
// 3. realtime listeners
// 4. sortPreferencesSyncService.startAfterSignIn()
// 5. themePreferencesSyncService.startAfterSignIn()
// 6. genericPreferenceSyncService.startAfterSignIn()
```

`startAfterSignIn()` for the generic engine performs an initial **forced
push of every spec** (so a brand-new sign-in seeds Firestore from the
device's local DataStore) before registering pull listeners. The bespoke
theme service does the symmetric initial-pull-then-push to avoid clobbering
a fresher cloud copy with a default-seeded local one (per the trade-off
documented in `ThemePreferencesSyncService`).

**Verdict:** sign-in wiring is correct.

## Item 4 — Self-echo + type-tag plumbing (GREEN)

`PreferenceSyncSerialization.kt`:

- Every push payload includes `__pref_device_id` (the local
  `SyncDevicePreferences` UUID) and `__pref_types` (a key→type-tag map).
- Pull side suppresses self-echo by skipping snapshots whose
  `__pref_device_id` matches the local device.
- Pull side returns `0` (no-op) if `__pref_types` is missing — so a
  malformed-or-pre-typed-tag-era doc cannot corrupt local state.
- Type tags handled: `bool`, `int` (Long → Int via `.toInt()`), `long`,
  `float` (Double → Float via `.toFloat()`), `double`, `string`,
  `stringSet` (List → Set via `.toSet()`).

`GenericPreferenceSyncService.pushNow` uses `SetOptions.merge()` — the
documented trade-off ("local key deletions don't propagate") is acceptable
because PrismTask's preference code writes default values rather than
calling `prefs.remove()` for in-app settings. The "Experience Level"
selector follows this pattern (it always writes one of the three enum
strings, never removes).

**Verdict:** serialization plumbing is correct.

## Item 5 — `ThemePreferences` push/pull symmetry (GREEN, with known TODO)

`ThemePreferencesSyncService` writes to
`/users/{uid}/settings/theme_preferences` with a push guard
(`updatedAt <= lastSynced` short-circuits the push) and explicitly does
NOT update `THEME_UPDATED_AT_KEY` on the pull path (commit `d1924b9b`,
"fix(theme-sync): remove pull-path write to THEME_UPDATED_AT_KEY"). That
asymmetry is intentional — updating the local timestamp on pull would echo
the remote write back as a fresh-looking local change.

`ThemePreferences.kt:20-22` carries a deferred TODO:

> Any new key added here that belongs in the sync payload must also be
> added to `ThemePreferencesSyncService.pushNow()` (push) and
> `applyRemoteSnapshot()` (pull). Full per-user DataStore scoping +
> unified `AppearanceSettingsSyncService` deferred to Option C.

This is a known maintenance hazard (every new key requires two manual
edits) but **not** the cause of the user's reported symptom. The currently
synced theme keys cover everything in the Settings → Appearance UI:

- `theme_mode`, `accent_color`, `prism_theme`, `font_scale`
- `background_color_override`, `surface_color_override`, `error_color_override`
- 5 priority color overrides (`priority_color_p0`…`p4`)
- `recent_custom_colors`

**Verdict:** wiring is correct; consolidation is a deferred refactor, not
a bug.

## Item 6 — End-to-end test coverage (RED — PROCEED)

**Existing tests:**
- `app/src/test/.../sync/PreferenceSyncSerializationTest.kt` — unit tests
  for the type-tag round-trip on `PreferenceSyncSerialization` only.

**Missing tests:**
- **No** `GenericPreferenceSyncServiceTest.kt` (unit or integration).
- **No** `ThemePreferencesSyncServiceTest.kt`.
- **No** `androidTest` exercising any DataStore→Firestore→DataStore
  round-trip — confirmed by grepping `app/src/androidTest` for both
  service names: zero matches.
- The Firebase emulator harness *is* available (used by other sync tests
  per `feedback_firestore_doc_iteration_order.md`) but no test exercises
  the preference-sync path through it.

This is the structural gap that lets a sync regression — for any of the
~50 keys across the 18 generic specs and the 12 theme keys — slip
through CI undetected. PR #582's "cold-start listener gap" was caught by
human bug report, not by an integration test, and any future regression
of similar shape would behave identically.

**Verdict:** RED. PROCEED to Phase 2 with at least one integration test
covering: (a) generic-engine push from device A landing in Firestore,
(b) pull on device B updating its DataStore, (c) self-echo suppression
not blocking a different-device write. Cover at minimum `user_prefs`
(`KEY_UI_TIER`) and `theme_prefs` (`THEME_MODE_KEY`) since those are the
two surfaces in the user's report.

## Item 7 — Production Firestore security rules (YELLOW — STOP-and-report)

The repo's `firestore.rules` is an emulator stub (permissive
`allow read, write: if request.auth != null;`). A header comment notes:

> The production Firestore rules live in the Firebase console and were NOT
> imported into this repo during emulator setup (Phase B).

Production rules **must** allow authenticated read+write to:

- `/users/{uid}/prefs/{any}` (generic engine target)
- `/users/{uid}/settings/{theme_preferences,sort_preferences}` (bespoke
  services)

If a production rule mismatch is silently rejecting the writes, the
push-side error path emits a `PrismSyncLogger` event but the symptom
(observed by the user) is exactly what they reported: settings don't
propagate. **This cannot be verified from the repo.**

**Verdict:** YELLOW. Cannot be ruled in or out from source. STOP-and-report
recommendation: the user (or someone with Firebase Console access) needs
to either (a) paste the production rules block for `/users/{uid}/...`,
or (b) confirm the symptom reproduces on a fresh sign-in with a Firebase
emulator session in front of them, which would isolate the bug to the
client.

## Item 8 — Runtime / device-specific failure modes (DEFERRED)

The audit cannot, from source alone, distinguish the user's report between:

1. Production rules block writes (Item 7).
2. The user signed in on device A but is signed out on device B (the
   sync services no-op when `auth.currentUser == null` — by design).
3. Both devices are on the same account but neither has acquired the
   `currentUser` callback yet (timing — the post-sign-in flow waits for
   it before invoking `startAfterSignIn`).
4. A device-specific Firestore network failure (which `PrismSyncLogger`
   would have logged but the user has not been asked for).
5. The user's "experience level" change happened *before* sign-in and is
   sitting in `user_prefs` waiting to be force-pushed by the next
   `startAfterSignIn` (which runs once per process — if the change pre-dates
   sign-in but post-dates the previous sign-in/out cycle, it should still
   propagate, but timing edge cases exist).

These are all runtime hypotheses. None can be falsified from `main` source.

**Verdict:** DEFERRED. Cannot be classified without device logs or a
reproducer.

## Ranked improvement table

Sorted by `(wall-clock savings) ÷ (implementation cost)`. Wall-clock
savings is **prevention of future user-visible sync regressions** — hard to
quantify, but every sync-regression fix in 2026 (PRs #844, #855, #856,
#882) was bug-report-driven, not test-driven, so the value is non-zero.

| Rank | Item | Verdict | Cost | Savings | Notes |
|---|---|---|---|---|---|
| 1 | **Item 6** — Add `GenericPreferenceSyncServiceTest` (Robolectric + emulator round-trip) | PROCEED | 1 PR, ~3 h | High (prevents regression of every preference sync) | Mirror the shape of existing emulator-backed sync tests |
| 2 | Item 7 — Confirm production rules permit `/users/{uid}/prefs/{any}` | STOP-and-report | 0 (asks user) | Critical if it's the actual cause | Out-of-band — needs Firebase Console access |
| — | Item 5 — Migrate `ThemePreferences` to generic engine (Option C) | DEFER | 1+ PR + state migration | Marginal (consolidation, not bug fix) | Risks orphaning state at `/users/{uid}/settings/theme_preferences` for existing installs unless paired with a one-shot migrator |

## Anti-pattern flags (worth noting; not necessarily fixing)

- **No `flush()` API.** `GenericPreferenceSyncService.pushNow` runs only via
  the `debounce(500ms)` collector. If the user changes a setting and
  background-suspends the app within 500ms, the push is lost. (`Application`
  process death drops the in-flight debounce.) This is a real failure
  mode but unlikely to explain the user's reported symptom (Experience
  Level changes are typically followed by some UI dwell time).
- **Pull listener has no offline retry.** `addSnapshotListener` handles
  reconnect transparently inside Firestore SDK, so this is technically
  fine, but if the listener fails to register at all (e.g. unauthorized
  during the first attempt), there's no retry — `ensurePullListener()`
  is idempotent for re-call but no caller re-invokes it.

---

## Phase 1 verdict

**Framework wiring is correct in source.** The user's report can be
explained by:

1. Production Firestore rules (Item 7) — cannot verify from repo;
   STOP-and-report.
2. Runtime / device-specific failure (Item 8) — cannot verify from repo;
   needs device logs.

The single PROCEED item that ships from this audit regardless of root
cause is **Item 6** (add an integration test for `GenericPreferenceSyncService`).
That test would have caught any regression and gives us a reproducible
harness to validate fixes against if Item 7 or Item 8 turn up an actual
bug.
