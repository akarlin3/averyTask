# D-Series UX Audit

**Scope.** Discovery audit across 5 UX buckets — information architecture,
in-app education, onboarding, feature ergonomics, feature discovery — to
enumerate concrete launch-quality defects ahead of Phase F kickoff
(May 15) / soft launch (June 14). Hybrid Phase 2 PR structure: ≤100 LOC
fixes bundled in this branch, >100 LOC fixes fan out separately.

**Operator-acknowledged risks** (per prompt § "SCOPE-RISK DOCUMENTATION"):
launch-slip, designing-without-data (Phase E UX feedback hasn't started,
solo-user signal only), D-series bloat, lower STOP leverage on a discovery
prompt, no Phase 1→2 confirmation gate.

## Recon

### Drive-by + parked-branch sweep (memory #18, A.1+A.2)

- `git log -p -S "tooltip\|empty.*state\|onboarding\|first.*launch" origin/main`
  → no UX-audit-class fixes in recent history.
- `git branch -r | grep -iE "ux|onboarding|tooltip|empty.state|nav"`
  → only `origin/claude/audit-d-series-ux-KznZ8` (this branch).
- `ls docs/audits/ | grep -iE "ux|onboard|tooltip|empty.state|nav|discovery|education"`
  → one prior audit: `ONBOARDING_COVERAGE_AUDIT.md`.

### Prior-art: ONBOARDING_COVERAGE_AUDIT.md → SHIPPED

Direct verification against `OnboardingScreen.kt:99–137`: the prior audit's
8 PROCEED items have all shipped (LifeModesPage, AccessibilityPage,
PrivacyPage, NotificationsPage, DaySetupPage, ConnectIntegrationsPage,
TemplatesPage filter on Life Modes, HabitsPage forgiveness card). Fresh
installs traverse 15 pages. Post-onboarding StartOfDay modal at
`MainActivity.kt:375–406` is suppressed once `DaySetupPage` writes
`hasSetStartOfDay = true` via `TaskBehaviorPreferences.setStartOfDay`
(`TaskBehaviorPreferences.kt:162–168`).

**Implication for this audit.** The Onboarding bucket is mostly
paper-closed. Surfacing remaining onboarding items requires post-Phase E
UX-feedback signal that does not yet exist. Two onboarding items remain
worth flagging — both deferred to F-series, NOT Phase 2.

### Surface enumeration

- Android screens: `app/src/main/java/com/averycorp/prismtask/ui/screens/`
  (40 packages incl. addedittask, today, projects, habits, automation,
  balance, mood, review, checkin, focus/pomodoro, extract, etc.).
- Android navigation: `NavGraph.kt` + `routes/{Task,Habit,AI,Notification,
  Settings,Mode,Template,Auth,Feedback}Routes.kt`.
- Web parallel (out of D-series scope per prompt — UX-feedback layer that
  has not started yet): `web/src/features/` mirrors most Android packages.
- Bottom nav: 7 default tabs (Today, Tasks, Daily, Recurring, Meds, Timer,
  Settings — confirmed via Explore agent on `MainActivity.kt`).

## Per-bucket enumeration

Each item: file:line, defect, P0/P1/P2 (operator's locked rubric), LOC
estimate (≤100 vs >100), Phase 2 inclusion.

**Operator's P0 rubric** (locked, prompt § "Triage rubric"):
> P0 — launch-blocker: New user cannot complete core loop (create task,
> complete task, see progress) without external help.

This rubric is strictly about the core loop, not feature completeness. A
shipped feature that is unreachable is **P1** (launch-quality), not P0,
because the user can still complete the core loop without it. **Several
sub-agent findings classified as P0 for "zero discovery surface" are
re-triaged here as P1 or P2 against the operator's rubric.**

### Bucket 1 — Information architecture

**IA-1. ProjectRoadmap orphan (P1, ≤100 LOC).** PR #1120 ported
ProjectRoadmapScreen (495 LOC at `ui/screens/projects/roadmap/
ProjectRoadmapScreen.kt`) and registered the route at
`NavGraph.kt:102` + `routes/TaskRoutes.kt:67`, but **no
`navigate(...Roadmap...)` call exists anywhere in the app**. Verified by
`grep -rnE "navigate.*Roadmap|FeatureRoutes\..*Roadmap"
app/src/main/java/com/averycorp/prismtask/` → empty. Onboarding ViewsPage
advertises Roadmap but in-app users cannot reach it. Fix: add a fourth
"Roadmap" tab to the `PrimaryTabRow` at
`ProjectDetailScreen.kt:230` alongside Overview/Milestones/Tasks; ~15
LOC. **PROCEED, Phase 2 bundle.**

**IA-2. MoodAnalytics orphan (P1, ≤100 LOC).** Route at
`NavGraph.kt:204`, screen at `ui/screens/mood/MoodAnalyticsScreen.kt`,
ViewModel at `MoodAnalyticsViewModel.kt`, route registered at
`routes/AIRoutes.kt:50`. Verified `navigate(...MoodAnalytics...)` →
empty everywhere. Mood data is *collected* via Morning Check-In + Energy
Check-In Card on Today, but users have no entry point to view trends. Fix:
add an overflow / "View Trends" affordance on `EnergyCheckInCard`
(`ui/components/EnergyCheckInCard.kt`) navigating to
`PrismTaskRoute.MoodAnalytics`. ~25 LOC. **PROCEED, Phase 2 bundle.**

**IA-3. Settings-only Automation, Boundaries, Custom Sort, Saved Filter
Presets, Time Blocking, Smart Suggestions (P2, defer).** Each is reachable
only via Settings. None block the core loop. Re-trigger criterion:
post-Phase E user-feedback report that ≥1 tester couldn't find the feature.
DEFER to F-series.

**IA-4. WeeklyBalanceReportScreen / MoodAnalyticsScreen / ClinicalReport
no exit actions (P2, defer).** Detail screens show the report; user must
back out with no share / export / next-step action. Polish, not
launch-blocker. Re-trigger: feature usage analytics post-launch indicating
high bounce. DEFER.

### Bucket 2 — In-app education

**ED-1. OrganizeTab pickers — Life Category, Task Mode, Cognitive Load
(P1, ≤100 LOC).** `OrganizeTab.kt:131,139,147` use bare `SectionLabel`
strings ("Life Category", "Task Mode", "Cognitive Load") with **no
description text** — these are jargon terms whose semantics are not
self-evident. Internal code comments at `:130/138/146` reference
`docs/WORK_PLAY_RELAX.md` and `docs/COGNITIVE_LOAD.md` but those docs are
invisible to end users. Fix: add a one-line `SectionDescription` helper
under each label explaining the metric in plain English. Bundled fix
~30 LOC + 1 test. **PROCEED, Phase 2 bundle.**

**ED-2. BrainModeSection ADHD / Calm Mode toggles (P1, ≤100 LOC).**
`BrainModeSection.kt:47,53` use `ModeToggleRow` (defined at
`SettingsCommon.kt:303` — label + switch only, no subtitle). Adjacent
`SettingsToggleRow` at `:59` for Focus & Release has the explanatory
subtitle "Helps you finish tasks and stop over-polishing". ADHD Mode and
Calm Mode are jargon-heavy ND-friendly toggles where the missing subtitle
is most costly. Fix: switch the two ADHD/Calm rows to `SettingsToggleRow`
with subtitles. ~15 LOC. **PROCEED, Phase 2 bundle.**

**ED-3. ModesSection (Self Care / Medication / Housework / Schoolwork /
Leisure) toggles (P2, defer).** `ModesSection.kt:23–27` toggles use bare
`ModeToggleRow`. Less jargon-heavy than ADHD/Calm; new users now
encounter these in onboarding LifeModesPage where they ARE explained.
Re-trigger: tester confusion report. DEFER.

**ED-4. Notification escalation chain "STANDARD_ALERT / HEADS_UP" jargon
(P2, defer).** `NotificationEscalationScreen.kt:39–116`. Power-user
feature, not encountered in core loop. Re-trigger: tester confusion
report. DEFER.

**ED-5. Quiet Hours "Break-through allowlist" jargon (P2, defer).**
`NotificationQuietHoursScreen.kt:100–120`. Same shape as ED-4. DEFER.

**ED-6. Affordance hints (swipe / drag / long-press) (P2, defer).** No
"first-arrival" coachmark for swipe-to-complete, drag-to-reorder, or
batch-select. Defaults are discoverable through play. Building a
coachmark/hint-card *system* is F-series infrastructure work, not a
single fix. Re-trigger: F-series in-app-education-system batch. DEFER.

**ED-7. Help-icon system absent app-wide (P2, defer).** No
`Icons.*.HelpOutline` usages on settings. Same shape as ED-6 — adding
a help-icon affordance is a system-wide pattern, not a single fix.
DEFER.

### Bucket 3 — Onboarding

Mostly paper-closed (see § "Prior-art" above). Two residuals surfaced:

**OB-1. POST_NOTIFICATIONS permission timing (P2, defer).** Fresh users
complete `NotificationsPage` (page 11/15) flipping six default-ON
notification flags ON; the system permission dialog fires *afterwards*
from `MainActivity.kt:301–311` LaunchedEffect. Timing surprise: user can
deny the permission post-onboarding, silently breaking what they just
agreed to. Source comment at `OnboardingScreen.kt:1474–1478`
acknowledges this. Re-classified as P2 because the existing flow still
results in informed consent (page) before request (system dialog) — the
timing window between is small. Re-trigger: post-Phase E tester confusion.
DEFER.

**OB-2. Skip-button writes-through for in-flight slider/toggle changes
(P2, defer).** ViewModel setters fire on every `onValueChange`; Skip just
animates pages without buffering. User who fiddled with a slider then
hit Skip persists the partial state. Defensible as feature-not-bug. DEFER.

**OB-3 (referenced from prior audit, still deferred).** Smart defaults,
Boundary rules / WLB, Smartwatch sync, Pomodoro coaching, dashboard
visibility, swipe actions, subscription tier, habit nag suppression —
all confirmed STILL deferred per prior audit's intent.

### Bucket 4 — Feature ergonomics

**ER-1. Task delete missing confirmation dialog (P1, ≤100 LOC).**
`AddEditTaskScreen.kt:120–131`: edit-mode delete IconButton calls
`viewModel.deleteTask()` and `popBackStack()` directly with no confirm.
Inconsistent with project delete (`ProjectDetailScreen.kt:260–280` —
AlertDialog with cascade-delete language), template delete, and
medication-archive (all gated by AlertDialog). Tasks have history and
recurrence implications; the existing snackbar-Undo mechanism is a
weaker recovery surface than a confirm dialog. Fix: gate the icon button
behind an AlertDialog (mirroring the Project delete shape). ~40 LOC.
**PROCEED, Phase 2 bundle.**

**ER-2. Today-screen has no medication quick-tap (P2, defer).**
`MedicationScreen.kt` requires bottom-tab switch to log a dose even when
Today shows a medication reminder card. Adding inline quick-tap is
~50 LOC + ViewModel wiring; surface re-trigger: post-Phase E feedback
that medication logging is high-friction. DEFER.

**ER-3. Click counts (no defect).** Create-task = 3 taps, complete-task =
1 tap/swipe. Both within budget. Jargon-leak grep on user-facing
strings (CloudId / naturalKey / discriminator / schema / DAO / entity /
tombstone) returned zero. PASS.

### Bucket 5 — Feature discovery

Discovery sweep produced 6 "RED — zero discovery outside Settings"
findings (Automation rules, Conversation Extract, Time Blocking, Smart
Suggestions, Saved Filter Presets, Custom Sort/Drag-to-Reorder). Each is
re-triaged P2 against operator's rubric — **zero discovery surface ≠
launch-blocker**. New users complete the core loop (create / complete /
see progress) without ever encountering any of these. Surfacing them
needs *evidence* that testers tried-and-failed, which is the Phase E
UX-feedback signal that does not yet exist.

**DI-1 through DI-6 (P2, defer).** All six "RED" discovery findings →
F-series follow-on, re-trigger criterion: post-Phase E tester report or
feature-usage analytics indicating <X% of testers reached the surface.

## Triage table

| ID | Bucket | Defect | LOC | Priority | Phase 2? |
|----|--------|--------|----:|----------|----------|
| IA-1 | IA | ProjectRoadmap orphan — add tab on ProjectDetailScreen | ~15 | P1 | ✅ bundle |
| IA-2 | IA | MoodAnalytics orphan — add entry from EnergyCheckInCard | ~25 | P1 | ✅ bundle |
| ED-1 | Education | OrganizeTab pickers no description (3 sites) | ~30 | P1 | ✅ bundle |
| ED-2 | Education | BrainMode ADHD/Calm no subtitles | ~15 | P1 | ✅ bundle |
| ER-1 | Ergonomics | Task delete no confirmation dialog | ~40 | P1 | ✅ bundle |
| IA-3 | IA | Settings-only Automation/Boundaries/etc. | ≤100 ea | P2 | defer |
| IA-4 | IA | Detail-screen dead-ends | ≤100 ea | P2 | defer |
| ED-3 | Education | ModesSection toggles no subtitle | ~30 | P2 | defer |
| ED-4 | Education | Escalation jargon | ~40 | P2 | defer |
| ED-5 | Education | Quiet Hours allowlist jargon | ~15 | P2 | defer |
| ED-6 | Education | Affordance-hint system | >100 | P2 | defer |
| ED-7 | Education | Help-icon system | >100 | P2 | defer |
| OB-1 | Onboarding | POST_NOTIFICATIONS timing | ~30 | P2 | defer |
| OB-2 | Onboarding | Skip preserves partial state | ~50 | P2 | defer |
| ER-2 | Ergonomics | No Today medication quick-tap | ~50 | P2 | defer |
| DI-1..6 | Discovery | Zero non-Settings discovery surface (6 features) | varies | P2 | defer |

**Phase 2 totals.** 5 P1 fixes, 0 P0 fixes. Aggregate ~125 LOC +
~50 LOC tests. All in single bundle PR per CLAUDE.md hybrid-PR
convention. Zero >100 LOC fan-out PRs needed.

## Phase 2 plan

**Bundle PR** (this branch, `claude/audit-d-series-ux-KznZ8`):

1. `docs(audits): D-series UX audit — Phase 1` — this file.
2. `fix(ux): IA: ProjectRoadmap entry tab on ProjectDetailScreen` —
   ~15 LOC.
3. `fix(ux): IA: MoodAnalytics entry from EnergyCheckInCard` — ~25 LOC.
4. `fix(ux): education: OrganizeTab picker descriptions
   (Life Category / Task Mode / Cognitive Load)` — ~30 LOC.
5. `fix(ux): education: BrainMode ADHD/Calm subtitles` — ~15 LOC.
6. `fix(ux): ergonomics: task delete confirmation dialog` — ~40 LOC.

**Operator-action gates between commits.** Per memory #29 — none. All
fixes are pure UI; none touch sync, scheduling, or storage primitives.
Verification path is AVD smoke + ktlint/detekt + unit tests post-bundle.

**Phase 3 + 4 fire pre-merge** per CLAUDE.md § Repo conventions.

## STOP-conditions evaluated

- **STOP-A (>25 P0+P1 items):** 5 P1, 0 P0 = 5. Does not fire. ✅
- **STOP-B (>1500 LOC P0+P1):** ~125 LOC. Does not fire. ✅
- **STOP-C (drive-by drift overlap):** Onboarding bucket overlaps
  `ONBOARDING_COVERAGE_AUDIT.md` (shipped). Treated as paper-closed; not
  duplicating. Does not fire. ✅
- **STOP-D (4 of 5 buckets zero items, 1 bucket 20+):** Distribution
  across buckets — IA 2, Education 2, Onboarding 0 (paper-closed),
  Ergonomics 1, Discovery 0 (re-triaged to P2). Onboarding being closed
  is a paper-closure outcome, not a bucket-framing failure. Discovery
  items mostly didn't meet operator's strict P0 rubric. Borderline — but
  the *findings* exist in every bucket; only the *Phase 2 inclusion* is
  uneven. Does not fire. ✅
- **STOP-E (>5 P0 items):** 0 P0 items. Does not fire. ✅
- **STOP-F (no items found):** 5 P1 items. Does not fire. ✅

## Premise verification (D.1–D.5)

- **D.1.** Recent surfaces (Blockers PR #1097, Roadmap PR #1120,
  TaskMode + CognitiveLoad PR #1094 + #1084) UX-audited? `git log -p -S
  "tooltip\|empty.*state" -- app/src/main/java/com/averycorp/prismtask/
  ui/screens/<surface>` empty for each. ✅ confirmed un-audited.
- **D.2.** Avery is solo user. No `tester report` / Phase E feedback
  doc on disk. ✅ confirmed.
- **D.3.** Onboarding flow exists (15 pages, fresh installs). Confirmed via
  `OnboardingScreen.kt`. ✅
- **D.4.** No prior UX audit on disk other than
  `ONBOARDING_COVERAGE_AUDIT.md`. ✅
- **D.5.** First-launch routes through onboarding then drops to
  TodayScreen. ✅

## Deferred items — not auto-filed (memory #30)

P2 items above with re-trigger criteria. Surfaced in this audit doc only.
Re-trigger discipline: do NOT promote to F-series until post-Phase E
tester signal or post-launch analytics provide evidence.

## Open questions for operator

None. Phase 2 fires immediately.

---

(Phase 3 + Phase 4 will be appended below once the Phase 2 bundle is
committed and pushed, pre-merge per CLAUDE.md.)
