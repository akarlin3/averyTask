# Copy & Strings Audit — 2026-04-17

## Summary

Scope covered: `app/src/main/res/values/strings.xml` (7 widget strings only — the app is nearly 100% inline Compose text), 515 Kotlin files under `app/src/main/java/com/averycorp/prismtask/`, `store/listing/en-US/`, plus `docs/`, `CHANGELOG.md`, and `README.md`.

**Overall copy quality is good.** Spelling is clean. Terminology is consistent. No debug strings or raw stack traces leak to the UI. Empty-state composables exist and provide calls to action.

The launch-blocking issues are narrow but real:

1. **Three hardcoded `"Avery"` user-name strings** ship to every user in self-care and leisure completion messages (§1a). This is the single most brand-damaging item — any user on the subreddit will post a screenshot within a day.
2. **Play Store title is 32 chars** (30-char limit) and uses `"To-Do"` when every in-app label says `"Task"` (§9a, §9b).
3. **43 `"Something went wrong"` snackbars** across 12 files with zero context for the user (§7a).
4. **~20 raw `e.message` leaks to user** in settings/sync/import/export paths (§7b).
5. **105 `contentDescription = null`** on likely-interactive icons hurts TalkBack users (§8a).

Categories D (casing) and G (debug strings) are effectively clean. Section 2 (typos) found zero hits against the common-suspect list.

**Totals:** 3 CRITICAL, 5 HIGH, 6 MEDIUM, 6 LOW.

## Severity legend
- **CRITICAL** — stale brand name ("AveryTask"), obvious typos in prominent UI, broken placeholders (`%s` unfilled)
- **HIGH** — grammar errors, inconsistent terminology, confusing phrasing
- **MEDIUM** — casing inconsistency, minor word choice issues
- **LOW** — internal debug strings, log messages, non-user-facing text

## 1. Brand / rename residue (CRITICAL)

The app name `PrismTask` is correctly used in `strings.xml` and all screen/widget titles. However, there are **personal-name leaks** and **legacy identifiers** worth flagging. No literal `AveryTask` string appears in any user-facing Kotlin/XML resource — all remaining occurrences are in legal docs, changelogs, build infrastructure, or migration channel IDs.

### 1a. Hardcoded developer name in user-facing UI (CRITICAL)

These strings will ship to every user — not just the developer. Any r/ADHD user installing the app will see their own praise addressed to "Avery".

- `app/src/main/java/com/averycorp/prismtask/ui/screens/leisure/components/LeisureComponents.kt:162` — `"✓ Leisure day complete. Nice work, Avery."` — shown on leisure routine completion.
- `app/src/main/java/com/averycorp/prismtask/ui/screens/selfcare/SelfCareScreen.kt:293` — `"All done — go get it, Avery."` — shown on morning self-care completion.
- `app/src/main/java/com/averycorp/prismtask/ui/screens/selfcare/SelfCareScreen.kt:295` — `"All done — lights out. Sleep well, Avery."` — shown on evening self-care completion (the housework variant at line 294 is generic, which confirms the others are leftovers).

**Fix:** drop the name or substitute the user's display name from profile/Firebase (or just a generic encouragement).

### 1b. Author credit (LOW — acceptable)

- `app/src/main/java/com/averycorp/prismtask/ui/screens/settings/sections/AboutSection.kt:36` — `"Made by Avery Karlin"` — in About section. Normal developer credit; no action needed.

### 1c. Legacy channel IDs / package paths (LOW — correct as-is)

Do NOT rename these — renaming breaks notification channel continuity for upgraders:
- `notifications/NotificationHelper.kt:31–32` — `"averytask_reminders"`, `"averytask_medication_reminders"` (legacy channel IDs).
- `notifications/WeeklyHabitSummary.kt:38` — `"averytask_weekly_summary"` (legacy channel ID).
- `widget/WidgetDataProvider.kt:88`, `di/DatabaseModule.kt:60` — DB filename `"averytask.db"` (renaming would break upgrades).
- Package `com.averycorp.prismtask` — not user-visible.

### 1d. Backend hostname (MEDIUM — non-blocking, not user-facing)

Backend URL `https://averytask-production.up.railway.app` is still used in `app/build.gradle.kts:70,84`, `NetworkModule.kt:26–29`, `web/` and `mobile/` clients, plus Firebase project id `averytask-50dc5` in `google-services.json`. Not shown to users, but worth a rename sweep when the team is ready for a backend/Firebase migration. No action for this launch.

### 1e. Legal / changelog (LOW — acceptable)

- `docs/TERMS_OF_SERVICE.md`, `docs/PRIVACY_POLICY.md`, the HTML twins, `CHANGELOG.md:426–427` — all correct references to legal entity "AveryCorp" or historical rename notes.

## 2. Typos and misspellings

Scanned all Kotlin sources and `store/listing/` for the common-suspects list (seperate, recieve, occured, untill, calender, defintely, wierd, publically, accomodate, neccessary, occassion, existance, tommorow, thier, succesful, priviledge, refered, alot) plus common missing-apostrophe forms (dont, cant, wont, isnt, lets when possessive is wrong) in user-facing `Text(`, `label =`, `contentDescription =`, `title =`, `showSnackbar(`, and `Toast` calls.

**No typos found in user-facing strings.** The copy quality here is good.

One minor punctuation-style nit that spans multiple files (captured under §6, not here): 24 user-facing strings still use three ASCII dots `...` rather than the single `…` (U+2026) ellipsis character used in Material 3 guidelines.

## 3. Terminology inconsistency

Overall, naming is consistent across the app. A few notable points:

### 3a. "To-Do" vs "Task" (HIGH — store only)

The only user-visible surface that calls them `To-Do` is the Play Store title (see §9b). Everywhere else the app uses `Task(s)`. Fix the store title.

### 3b. "Sign In" (button) vs "Sign-in" (error copy) (MEDIUM)

- Buttons: `"Sign In with Google"` / `"Sign In"` / `"Sign Out"` — Title Case, no hyphen.
- Error / state copy: `"Sign-in failed"` (`AuthViewModel.kt:64`), `"Sign-in cancelled"` (`AuthScreen.kt:184`), `"Sign-in succeeded but user is null"` (`AuthManager.kt:61`).

Pick one spelling and apply to the noun form uniformly. Recommend `"Sign-in"` for the noun and `"Sign in"` for the verb imperative; the current button label `"Sign In with Google"` is fine because it's a named Google button.

### 3c. "Routine" vs "Habit" (LOW — domain distinction is intentional)

`Habit` is the Room entity users create and track. `Routine` is used only for the fixed morning/evening/housework self-care flows (`SelfCareScreen.kt:170–171`, `SelfCareRoutineCard.kt:47`, `DailyEssentialsSettingsSection.kt:75`) and for built-in task templates (`TemplateSeeder.kt:123`). Both concepts coexist cleanly; no rename needed, but the subagent flagged that a boundary-rule parser also mentions "routine" in passing (`BoundaryRuleParser.kt` — internal). No user-facing conflict.

### 3d. "Done" button vs "Show Completed" filter (LOW)

- Buttons: `Done` (dialog dismiss), `Complete` (task action).
- Filter label: `Show Completed`.
- Snackbar: `Task completed`.

The `Done / Complete / Completed` trio is contextually appropriate (verb vs adjective), but if you wanted maximum cohesion pick one. Low priority — this is a common Android convention.

### 3e. No drift found on the rest

- `Task` / `Project` / `Tag` / `Habit` / `Template` are each used uniformly in user copy.
- `Delete` is used for destructive actions (never `Remove` / `Trash`).
- `Archive` is distinct from `Delete` and consistent.
- `Settings` is the only name used for app configuration (no `Preferences` / `Options` drift in UI).
- `Pro` is the only paid tier name shown to users (matches Play Store listing).

## 4. Placeholder / template string issues

No orphaned `%s` / `%d` or unresolved `{name}` placeholders reaching the UI. Kotlin string templates (`"$taskTitle"`, `"${project.name}"`) are used consistently and all interpolated values trace back to non-null fields or sensible fallbacks.

Two minor observations:

### 4a. Inline quoting style mixed in delete dialogs (LOW)

- `ProjectListScreen.kt:229` — `"Delete \"${project.name}\"?"` (escaped straight quotes).
- `TagManagementScreen.kt:212` — `"Delete \"${tag.name}\"? …"` (escaped straight quotes).
- `HabitListScreen.kt:266` — `"Delete \"${hws.habit.name}\"? …"` (escaped straight quotes).
- `OrganizeTab.kt:199` — `"Delete '$taskTitle'? This cannot be undone."` (ASCII single quotes).

Pick one (prefer typographic `"…"` or consistent escaped doubles). MEDIUM at most.

### 4b. No plurals resource usage

`strings.xml` has zero `<plurals>` entries and the app constructs count strings by concatenation (e.g. computing labels inline in code). This is fine for English-only v1, but the moment a translation is added nothing will pluralize correctly. Not a launch blocker; worth noting for i18n readiness.

## 5. Grammar & awkward phrasing

No grammar errors found in user-facing copy — articles, tense, and sentence structure are all fine. Imperative/declarative mixing is consistent (buttons are imperative; state banners are declarative).

One micro-nit:

### 5a. "Task created!" exclamation only in onboarding (LOW)

`OnboardingScreen.kt:895` — `"Task created!"` — elsewhere task-creation confirmations are `"Task created"` / `"Task created: $title"` (`ChatViewModel.kt:183`) without exclamation. Decide: celebratory onboarding tone vs. neutral confirmation across the rest. Not a blocker.

## 6. Casing & style inconsistency

### 6a. Button labels are Title Case, not Material 3 Sentence case (MEDIUM)

`CLAUDE.md` already codifies the project convention: **"Use Title Capitalization in all user-facing strings"**. All audited button/label/dialog copy follows this (`Delete`, `Sign In`, `OK`, `Habit Name`, `Delete Task`, `Delete Project`, etc.). This is intentional — leaving here only so whoever ships the app can confirm they want to stay on Title Case and reject Material 3's Sentence-case default. No change recommended given the explicit convention.

### 6b. `OK` is consistent (good)

All 14 dialog confirm buttons use `"OK"` (uppercase) — zero `"Ok"` / `"Okay"` drift. Keep as-is.

### 6c. Ellipsis character inconsistency (LOW)

24 user-facing strings across 19 files use ASCII `...` rather than the `…` single-character ellipsis. Examples:
- `Loading...` (2 analytics screens)
- `Syncing...` (3 places)
- `Add a task...` (quick-add widget placeholder)

No user-visible difference on most fonts; Material 3 guidelines recommend U+2026. Cosmetic only.

### 6d. Delete-confirmation quoting style (duplicate of §4a — LOW)

See §4a.

### 6e. Em-dash usage (consistent — good)

Widget descriptions in `strings.xml` all use `\u2014` em-dash. Hardcoded UI strings use both `—` (U+2014) and `\u2014` escape; they render the same. No inconsistency.

## 7. Empty / loading / error state copy

This is the **biggest copy risk** for launch. Users judging the app on r/ADHD will see these messages the moment anything goes sideways.

### 7a. "Something went wrong" everywhere (HIGH — 43 call sites, 12 files)

Generic non-actionable error. Counts per file:
- `ui/screens/tasklist/TaskListViewModel.kt` — 17
- `ui/screens/tasklist/TaskListViewModelBulk.kt` — 7
- `ui/screens/today/TodayViewModel.kt` — 4
- `ui/screens/addedittask/AddEditTaskViewModel.kt` — 4
- `ui/screens/habits/AddEditHabitViewModel.kt` — 2
- `ui/screens/templates/AddEditTemplateViewModel.kt` — 2
- `ui/screens/projects/{ProjectListViewModel,AddEditProjectViewModel}.kt` — 1 + 2
- `ui/screens/{monthview,weekview,timeline}/` — 1 each
- `data/repository/CoachingRepository.kt` — 1

Each one swallows the exception silently and shows the same string. At minimum, each call site should name the action ("Couldn't delete task", "Couldn't save project") and, where possible, suggest a retry.

### 7b. Raw exception messages leaked to UI (HIGH — ~20 call sites)

These show the raw `e.message` string (often a stack-trace fragment, Firebase error code, or HTTP body) directly to the user:

- `SettingsViewModel.kt:929` — `e.message ?: "Failed to connect Google Calendar"`
- `SettingsViewModel.kt:1015,1160` — `"Sync failed: ${e.message}"`
- `SettingsViewModel.kt:1218` — `"Could not scan for duplicates: ${e.message}"`
- `SettingsViewModel.kt:1241,1356,1368,1379,1406` — similar `"X failed: ${e.message}"`
- `SettingsViewModelExportImport.kt:21` — `"Export failed: ${e.message}"`
- `ProjectListViewModel.kt:83,93` — `"Import failed: ${e.message}"`
- `TaskListViewModel.kt:838,848` — `"Import failed: ${e.message}"`
- `SchoolworkViewModel.kt:74,87` — `"Import failed: ${e.message}"`
- `EisenhowerViewModel.kt:93`, `DailyBriefingViewModel.kt:104,130`, `SmartPomodoroViewModel.kt:295` — `e.message ?: "<fallback>"`
- `OnboardingViewModel.kt:64`, `AuthViewModel.kt:64`, `AuthScreen.kt:187` — `it.message ?: "Sign-in failed"` / `e.message ?: "Google Sign-In failed"`

Recommendation: map exception types to user-friendly copy in a single helper (`exceptionToUserMessage(e: Throwable): String`) and stop interpolating raw messages.

### 7c. Context-free "Loading..." (MEDIUM — 2 screens)

- `TaskAnalyticsScreen.kt:93` — `Text("Loading...")`
- `HabitAnalyticsScreen.kt:95` — `Text("Loading...")`

Either screen can sit on this Text for several seconds while the Flow emits the first value. Make it specific: `"Loading analytics…"` / `"Loading habit data…"`.

### 7d. Syncing states are fine (good)

`SyncStatusIndicator.kt:51`, `GoogleCalendarSection.kt:247`, `AccountSyncSection.kt:58` all use `"Syncing..."` with visible chrome context (account row / calendar row) that tells the user what's syncing. Keep.

### 7e. No empty-state anti-patterns found

Empty task/habit/project list states include helpful calls-to-action (via `EmptyState.kt` composable). Good.

## 8. Accessibility-impacting strings

### 8a. `contentDescription = null` on interactive icons (MEDIUM — 105 instances, 54 files)

TalkBack users cannot identify these icons. Not all are bugs — many are decorative icons sitting inside a labeled `Row` where the parent semantics cover them. But a sample audit of the most exposed spots found actionable icons missing descriptions:

- `ui/components/QuickAddBar.kt:184` — add-task icon with `contentDescription = null`.
- `ui/components/SubtaskSection.kt:249` — subtask-add icon with `contentDescription = null`.
- `ui/components/BatchEditComponents.kt` — 2 actionable icons with null descriptions.
- `ui/components/MoveToProjectSheet.kt` — 2 actionable icons with null descriptions.
- `ui/screens/addedittask/AddEditTaskScreen.kt` — 8 icon buttons with null descriptions.
- `ui/screens/addedittask/tabs/OrganizeTab.kt` — 7 icons with null descriptions.

Full set is 105 call sites across 54 files — capped display; a sweep by an accessibility-aware reviewer is warranted.

### 8b. No "Tap here" / "Click here" antipatterns found (good)

Zero instances of `"Tap here"`, `"Click here"`, or `"Click me"` in any `contentDescription`.

### 8c. No visual-context-dependent descriptions found (good)

No matches on `"the one above"`, `"the green one"`, or `"this"` as a standalone `contentDescription`.

### 8d. Good patterns already present (reference)

- `SubtaskSection.kt:278` — `"Drag To Reorder"` (descriptive).
- `CoachingCard.kt:83` — `"Dismiss"`.
- `SettingsCommon.kt:156` — `"Move up"` / `"Move down"`.

Use these as templates for filling in the 105 gaps.

## 9. Play Store listing (if present)

Listing copy lives at `store/listing/en-US/{title,short-description,full-description}.txt`.

### 9a. Title exceeds Play Store 30-character limit (CRITICAL)

- `store/listing/en-US/title.txt:1` — `"PrismTask - Smart To-Do & Habits"` — **32 characters**. Play Console rejects titles over 30. Shorten to e.g. `"PrismTask: Tasks & Habits"` (25) or `"PrismTask — Smart Tasks"` (23).

### 9b. "To-Do" in title clashes with in-app "Task" (HIGH)

The listing title uses `"To-Do"` but the short description, full description, every screen, the widget descriptions in `strings.xml`, and every in-code label call this a `task`. Pick one. Recommend standardizing on `Task(s)` because every in-app string already uses it; fixing the title also fixes 9a.

- `title.txt:1` — `"Smart To-Do & Habits"` — rename.
- `short-description.txt:1` — `"Smart tasks, habits & AI focus planning."` — already uses "tasks".

### 9c. Full-description length / char count (INFO — within limit)

- `full-description.txt` — 2,590 chars, well under the 4,000 Play Store limit. No action.
- `short-description.txt` — 70 chars, under the 80 limit. No action.

### 9d. PRO pricing (CONSISTENT with in-app, no action)

`full-description.txt:52` lists `"PRO ($7.99/month or $4.99/month billed annually; annual plan includes a 7-day free trial)"`. This matches `SubscriptionSection.kt:50–51` and `ProUpgradePrompt.kt:93–108`. Note `CLAUDE.md` still describes a three-tier `Free/Pro $3.99/Premium $7.99` model that no longer matches the UI — this is doc drift, not a listing issue, but worth correcting the CLAUDE.md before next audit.

### 9e. Feature list duplication (LOW)

- `full-description.txt:28` describes the AI Briefing & Weekly Planner section, then adds `"AI time blocking automatically schedules your day for optimal productivity."` — `time blocking` is already enumerated in the PRO bullet list at line 54. Harmless but redundant.

### 9f. Ambiguous "All Views" in FREE tier (MEDIUM)

- `full-description.txt:47` — FREE lists `"All views and widgets"`. But `"AI EISENHOWER MATRIX (PRO)"` at line 21 is gated. Users will reasonably read "all views" as including Eisenhower Matrix. Either clarify "All non-AI views" or break out the list.

### 9g. Missing trailing newline (LOW)

- `full-description.txt` ends mid-line at line 59 without a trailing newline. Doesn't affect Play Console upload but is a POSIX nit.

## 10. Debug / test strings shown to users

### 10a. No leaked debug markers in production UI (good)

Zero matches for `"Debug:"`, `"TEMP:"`, `"FIXME"`, `"xxx"`, `"Hello World"`, `"Android Studio"`, or `"Lorem ipsum"` inside `Text(` / `snackbarHostState.showSnackbar` / `Toast` call sites in production code.

The only `Text("Debug …")` string is the legitimate Debug Log screen title (`DebugLogScreen.kt:90`), which is gated behind a developer setting.

### 10b. Raw enum `.name` / `.toString()` not shown (good)

No user-facing `Text(...)` interpolates a raw enum's `.name` or a status enum's `.toString()` without a `when` mapping. All seven `.name` references found (`tag.name`, `project.name`, `habit.name`, `profile.name`, `cal.name`) are **entity display names**, not enum labels.

### 10c. Stack traces not shown (good)

Only one `.stackTraceToString()` / `.toString()` site near a `Text(`, and it was in `RecurrenceSelector.kt` debug output behind a flag. No stack traces reach the user.

### 10d. `fromScreen ?: "Unknown"` fallback (LOW)

- `BugReportViewModel.kt:58` — if the originating screen can't be determined, the bug report is tagged `"Unknown"`. This is shown in the generated report body. Fine for an internal/report field, but a user who inspects their own submission will see `Unknown` — harmless.

### 10e. The real debug leakage — §1a personal name (already CRITICAL)

The closest thing to a "debug string" leaking to users is the hardcoded `"Avery"` name in `SelfCareScreen.kt` and `LeisureComponents.kt`, captured in §1a. That's the one to fix.

## Prioritized fix list

### Critical (fix before launch — user trust at risk)

- `app/src/main/java/com/averycorp/prismtask/ui/screens/selfcare/SelfCareScreen.kt:293` — `"All done — go get it, Avery."` — drop the name or substitute user display name.
- `app/src/main/java/com/averycorp/prismtask/ui/screens/selfcare/SelfCareScreen.kt:295` — `"All done — lights out. Sleep well, Avery."` — drop the name.
- `app/src/main/java/com/averycorp/prismtask/ui/screens/leisure/components/LeisureComponents.kt:162` — `"✓ Leisure day complete. Nice work, Avery."` — drop the name.
- `store/listing/en-US/title.txt:1` — `"PrismTask - Smart To-Do & Habits"` (32 chars) — shorten under 30 AND replace "To-Do" with "Tasks". Suggest `"PrismTask: Tasks & Habits"` (25 chars).

### High (this week)

- **Replace all 43 `"Something went wrong"` snackbars** with action-specific copy. Files: `TaskListViewModel.kt` (17), `TaskListViewModelBulk.kt` (7), `TodayViewModel.kt` (4), `AddEditTaskViewModel.kt` (4), `AddEditHabitViewModel.kt` (2), `AddEditTemplateViewModel.kt` (2), `AddEditProjectViewModel.kt` (2), `ProjectListViewModel.kt` (1), `MonthViewModel.kt` (1), `WeekViewModel.kt` (1), `TimelineViewModel.kt` (1), `CoachingRepository.kt` (1).
- **Stop interpolating raw `e.message` into UI strings** — wrap in an `exceptionToUserMessage()` helper. ~20 call sites, concentrated in `SettingsViewModel.kt` (9), `TaskListViewModel.kt` (2), `ProjectListViewModel.kt` (2), `SchoolworkViewModel.kt` (2), `DailyBriefingViewModel.kt` (2), plus single sites in `EisenhowerViewModel`, `SmartPomodoroViewModel`, `OnboardingViewModel`, `AuthViewModel`, `AuthScreen`, `SettingsViewModelExportImport`.
- `store/listing/en-US/full-description.txt:47` — `"All views and widgets"` under FREE is ambiguous given `"AI EISENHOWER MATRIX (PRO)"` earlier; clarify scope.
- `"Sign In"` (button Title Case) vs `"Sign-in"` (error copy) — unify noun-form spelling across `AuthManager.kt:61`, `OnboardingViewModel.kt:64`, `AuthViewModel.kt:64`, `AuthScreen.kt:184,187`.
- `TaskAnalyticsScreen.kt:93` and `HabitAnalyticsScreen.kt:95` — replace `"Loading..."` with contextual copy.

### Medium (v1.0.1)

- Accessibility sweep: fill in or deliberately decorate-out the **105 `contentDescription = null`** sites flagged in §8a, prioritizing the 25+ actionable icons in `QuickAddBar`, `SubtaskSection`, `AddEditTaskScreen`, `OrganizeTab`, `BatchEditComponents`, `MoveToProjectSheet`.
- Harmonize delete-confirmation quoting (§4a) across `ProjectListScreen`, `TagManagementScreen`, `HabitListScreen`, `OrganizeTab`.
- `full-description.txt:28` vs `:54` — remove duplicate mention of `"time blocking"` in the briefing section.
- Decide on Material 3 Sentence-case vs project-standard Title Case (§6a). Current convention is Title Case per `CLAUDE.md`; confirm and move on.
- Correct `CLAUDE.md` three-tier pricing description — in-app UI and store listing both show only $4.99 / $7.99 Pro; no `$3.99` tier is visible.
- Backend-hostname rename from `averytask-*.up.railway.app` to a `prismtask-*` equivalent (docs drift, not user-facing; post-launch).

### Low (whenever)

- 24 ASCII ellipses (`...`) → single-character `…` for Material 3 polish (19 files).
- `OnboardingScreen.kt:895` — decide whether `"Task created!"` (with `!`) should match neutral `"Task created"` used elsewhere.
- `store/listing/en-US/full-description.txt` — add trailing newline.
- `BugReportViewModel.kt:58` — optional: replace the `"Unknown"` origin-screen fallback with a clearer hint.
- No `<plurals>` resources exist; adopt `@plurals/` when the first translation lands.
- Terms/Privacy HTML and Markdown twins are duplicated — not a copy issue, a maintenance one; out of scope.

---

Audit complete. Report at COPY_STRINGS_AUDIT.md. Total: 3 CRITICAL, 5 HIGH, 6 MEDIUM, 6 LOW.
