# AI Endpoint task_id Drift — Audit + Fan-Out Proposal

**Date:** 2026-04-30
**Phase:** Pre-F (planning batch)
**Trigger:** Smart Focus / `/ai/pomodoro-plan` Long→String drift fix.

---

## ⚠ Premise correction (added 2026-04-30, mid-audit)

Original prompt asserted "Smart Focus PR shipped" with `TaskDao.getIdByCloudId`
in HEAD. **It isn't.** The Smart Focus fix is sitting uncommitted in the
working tree, with no backing branch or stash:

- `git show HEAD:app/src/main/java/com/averycorp/prismtask/data/local/dao/TaskDao.kt | grep getIdByCloudId` → not in HEAD.
- HEAD's `ApiModels.kt` still has `SessionTaskResponse.taskId: Long` and `SkippedTaskResponse.taskId: Long` with the original Smart Focus TODO markers (lines 167, 184 in HEAD).
- `git diff HEAD --stat` for the four files matches the Smart Focus PR shape: `TaskDao.kt +3`, `ApiModels.kt +11/-?`, `SmartPomodoroViewModel.kt +37`, `SmartPomodoroViewModelTest.kt +62`.
- No stash, no `feat/...smart-focus...` branch — purely working-tree state.

**Findings remain valid** — the *pattern* exists in the working tree, the
backend contract is correct, the 5 drift endpoints are real. **The fan-out
plan needs sequencing** because PRs branched from `main` would not have
`getIdByCloudId` to call.

### Three viable paths (deferred to operator):

- **A. Smart-Focus-first** *(recommended)*: ship the working-tree changes
  as PR 0 first, then fan out the 2 follow-up PRs off `main` once
  Smart Focus merges. Cleanest, matches audit-first conventions, no PR
  stacking. Adds one merge cycle.
- **B. Single bundled PR**: combine Smart Focus + the 5 follow-up endpoints
  into one ~250 LOC PR. Fastest wall-clock, single review, single CI.
  Risk: bigger blast radius; review attention diluted across 6 endpoints.
- **C. Stacked PRs**: PR 1 = Smart Focus on a feature branch; PR 2
  (Daily Briefing) and PR 3 (WeeklyPlan+TimeBlock) branched off PR 1.
  More coordination, but parallel review.

Audit halts here pending operator pick. Phase 2 (worktree fan-out) does
NOT auto-fire until the path is chosen — branching off `main` without
the Smart Focus baseline would produce non-compiling PRs.

**Operator picked Path B (single bundled PR).** Phase 2 fan-out collapses
to one branch / one worktree containing Smart Focus + all 5 endpoints.
See "Phase 2 — Fan-out execution plan" at the end of this doc for the
revised execution.

---
The remaining 4 AI endpoints carry the same drift, flagged with explicit
`TODO(weekly-followup)` markers in `data/remote/api/ApiModels.kt`. Phase F
testers will exercise these flows; each will throw `NumberFormatException`
the moment its corresponding response hits the wire.

This audit confirms scope, verifies the backend contract, identifies the
one structural exception (Daily Briefing has no skipped-tasks analog), and
proposes a 2-PR fan-out shape.

---

## Item 1 — Endpoint inventory verification (GREEN)

`grep "taskId: Long" app/src/main/java/com/averycorp/prismtask/data/remote/api/ApiModels.kt`
returns 7 hits. After de-duping outbound request fields, **5 response
classes match the original drift set**:

| # | Response class | Line | Endpoint | Container |
|---|---|---|---|---|
| 1 | `BriefingPriorityResponse` | 227 | `/ai/daily-briefing` | `DailyBriefingResponse.topPriorities` |
| 2 | `SuggestedTaskResponse` | 234 | `/ai/daily-briefing` | `DailyBriefingResponse.suggestedOrder` |
| 3 | `PlannedTaskResponse` | 266 | `/ai/weekly-plan` | `DayPlanResponse.tasks` |
| 4 | `UnscheduledTaskResponse` | 283 | `/ai/weekly-plan` + `/ai/time-block` | `WeeklyPlanResponse.unscheduled`, `TimeBlockResponse.unscheduledTasks` |
| 5 | `ScheduleBlockResponse` | 382 | `/ai/time-block` | `TimeBlockResponse.schedule` |

All 5 carry the inline TODO marker `// TODO(weekly-followup): flip to String
when ... is audited.` (lines 226, 233, 265, 282, 381).

**Item 4 is shared between two endpoints.** Type-flipping
`UnscheduledTaskResponse.taskId` simultaneously breaks both
`WeeklyPlannerViewModel` and `TimelineViewModel` callsites — they must ship
together.

**Two outbound `taskId: Long` survivors are out of scope** (request, not
response — Gson encodes Long as a JSON number, no `NumberFormatException`):

- `CoachingTaskSummary.taskId: Long` (line 547)
- `CoachingRequest.taskId: Long?` (line 589)

Backend `PomodoroCoachingTask.task_id` / `CoachingRequest.task_id` are
`Optional[str]` (per `backend/app/schemas/ai.py`). Pydantic v2 may coerce
or 422 — needs separate investigation. **Flagged as anti-pattern below;
not part of this audit's PR scope.**

---

## Item 2 — Per-endpoint ViewModel analysis

### 2a. DailyBriefingViewModel (YELLOW — structural exception)

**File:** `app/src/main/java/com/averycorp/prismtask/ui/screens/briefing/DailyBriefingViewModel.kt`

**Flow:** `api.getDailyBriefing(...)` → maps `response.topPriorities` and
`response.suggestedOrder` directly into UI models with `taskId: Long`.
`applyOrder()` (line 117) consumes the Long via
`taskDao.updatePlannedDateAndSortOrder(taskId, today, sortOrder)`.

**Existing fallback shape:** None. The UI model has no `skippedTasks`,
`unscheduled`, or `deferred` analog. `headsUp: List<String>` is a textual
backend-emitted list — semantically distinct from a sync-state surface.

**Recommendation:** Introduce a new field on `DailyBriefing`:

```kotlin
data class DailyBriefing(
    ...,
    val pendingSyncTitles: List<String> = emptyList(),
)
```

Render as a small footer under `headsUp`:
"3 priorities pending sync from another device". Matches Smart Focus's
"not synced to this device" textual approach (`SmartPomodoroViewModel.kt:518`).

**LOC estimate:** ~80 (data class flips + VM resolution + new UI surface).

### 2b. WeeklyPlannerViewModel (GREEN — fits Smart Focus pattern cleanly)

**File:** `app/src/main/java/com/averycorp/prismtask/ui/screens/planner/WeeklyPlannerViewModel.kt`

**Flow:** Maps `response.plan[dayName].tasks` and `response.unscheduled`
directly into UI models. `applyPlan()` (line 225) and `moveTaskToDay()`
(line 191) consume the Long.

**Existing fallback shape:** `WeeklyPlan.unscheduled: List<UnscheduledTask>`
already exists with a `reason: String` field — perfect for "not synced to
this device". Demote unresolved planned tasks into `unscheduled` with
that reason.

**LOC estimate:** ~50 (paired data class flips + VM resolution loop).

### 2c. TimelineViewModel + AiTimeBlockUseCase (GREEN — fits cleanly)

**Files:**
- `app/src/main/java/com/averycorp/prismtask/ui/screens/timeline/TimelineViewModel.kt`
- `app/src/main/java/com/averycorp/prismtask/domain/usecase/AiTimeBlockUseCase.kt`

**Flow:** Use case returns `TimeBlockResponse` unchanged → VM maps
`block.taskId` (`Long?`) directly into `AiScheduleBlock.taskId: Long?`.
`commitProposedSchedule()` (line 521) reads `block.taskId` and calls
`taskDao.getTaskByIdOnce(block.taskId)`.

**Existing fallback shape:** `AiSchedule.unscheduledTasks: List<Pair<Long, String>>`.
Demote unresolved schedule-block tasks into this list. The use case is the
right resolution boundary (already injects `TaskDao`); keeps the VM thin.

**LOC estimate:** ~60 (data class flips + use-case resolution + VM Long? mapping).

### Cross-references between endpoints

`UnscheduledTaskResponse` is the only shared type. No other cross-VM
coupling. Daily Briefing is fully independent.

---

## Item 3 — Backend contract sanity check (GREEN)

`grep "task_id" backend/app/schemas/ai.py` confirms all 5 response classes
emit `str`:

| Schema class | Line | Field |
|---|---|---|
| `BriefingPriority` | 95 | `task_id: str` |
| `SuggestedTask` | 101 | `task_id: str` |
| `PlannedTask` | 131 | `task_id: str` |
| `UnscheduledTask` | 147 | `task_id: str` |
| `ScheduleBlock` | 221 | `task_id: Optional[str] = None` |

Backend is consistent. Single Long→String type flip is correct
architecturally for all 5. No backend changes needed.

`int`-typed `task_id` survivors elsewhere
(`collaboration.py:68`, `integration.py:44`, `template.py:59`) are outside
the AI router and unrelated.

---

## Item 4 — Test surface inventory

### 4a. DailyBriefingTest (domain test, no VM mock)

**File:** `app/src/test/java/com/averycorp/prismtask/domain/DailyBriefingTest.kt`

Fixtures use `BriefingPriorityResponse(1L, ...)` and
`SuggestedTaskResponse(1L, ...)`. Tests do not mock `TaskDao`. **Type flip
breaks compilation — fixtures need `"cloud-1"` strings.**

**No `DailyBriefingViewModelTest` exists.** Need one new file with at
least:
- Resolution path: real cloud_id maps to local Long → fixtures appear in `topPriorities` / `suggestedOrder`.
- Unresolved path: cloud_id with no local match → demoted into `pendingSyncTitles`.

**Estimated new tests:** 2 (resolution + unresolved). The existing domain
test only needs fixture string updates, not new cases.

### 4b. WeeklyPlannerTest (domain test, no VM mock)

**File:** `app/src/test/java/com/averycorp/prismtask/domain/WeeklyPlannerTest.kt`

Fixtures use `PlannedTaskResponse(1L, ...)` and
`UnscheduledTaskResponse(5L, ...)`. **Type flip breaks compilation.** No VM
mock; no `TaskDao` injection currently.

**No `WeeklyPlannerViewModelTest` exists.** Need one new file with:
- Resolution path: planned tasks resolve to local Long.
- Unresolved path: planned tasks demoted into `unscheduled` with "not synced" reason.

**Estimated new tests:** 2.

### 4c. TimelineViewModelTest (existing VM test)

**File:** `app/src/test/java/com/averycorp/prismtask/ui/screens/timeline/TimelineViewModelTest.kt`

Fixtures use `ScheduleBlockResponse(..., taskId = 42L, ...)` (lines 119,
155, 209). `TaskDao` already mocked. Need to:
- Update fixture `taskId` to strings: `"cloud-7"`, `"cloud-42"`.
- Add `coEvery { taskDao.getIdByCloudId("cloud-7") } returns 7L` etc. in `setUp`.
- Add 1 test for unresolved demotion → `unscheduledTasks`.

**Estimated new tests:** 1 (unresolved-skip path). Existing 5 tests need
fixture + mock updates.

### 4d. AiTimeBlockUseCaseTest (existing use-case test)

**File:** `app/src/test/java/com/averycorp/prismtask/domain/usecase/AiTimeBlockUseCaseTest.kt`

Fixtures use `TaskEntity.id` Long for outbound `TimeBlockTaskSignal.taskId`
(already string, since `id.toString()`). Stub `TimeBlockResponse` is
empty — no inbound `taskId` exercise. The resolution logic moves into
this use case → need 2 new tests (resolution + unresolved demotion at
the use-case boundary).

**Estimated new tests:** 2.

### 4e. TimeBlockTest (domain test, no VM mock)

**File:** `app/src/test/java/com/averycorp/prismtask/domain/TimeBlockTest.kt`

Fixtures use `ScheduleBlockResponse(..., 1L, ...)` and
`UnscheduledTaskResponse(7L, ...)`. **Type flip breaks compilation.**
Pure mapping test — fixture string updates only, no new cases needed.

**Total per fan-out PR (see Item 7):**

| PR | Compile-fix updates | New tests |
|---|---|---|
| Daily Briefing | DailyBriefingTest.kt (~6 fixtures) | + DailyBriefingViewModelTest.kt: 2 |
| Weekly Plan + Time-Block | WeeklyPlannerTest.kt + TimeBlockTest.kt + TimelineViewModelTest.kt + AiTimeBlockUseCaseTest.kt (~15 fixtures total) | + WeeklyPlannerViewModelTest.kt: 2; + 1 TimelineVM unresolved test; + 2 AiTimeBlockUseCase resolution tests |

---

## Item 5 — Web parity (GREEN)

`web/src/types/briefingPlanner.ts` and `web/src/api/ai.ts` already declare
`task_id: string` for `BriefingPriority`, `SuggestedTask`, `PlannedTask`,
`UnscheduledTask`, `ScheduleBlock` (and the Time-Block variant). **No web
type drift.**

A stale TODO comment exists at `web/src/api/ai.ts:61-66` referencing the
"Long → String audit" — pure documentation; no behavioral implication.
**Bundle the comment removal with the Time-Block PR** (or skip — it's a
no-op fix).

Web doesn't need its own PR.

---

## Item 6 — Cross-device fallback UX

Smart Focus's existing behavior (`SmartPomodoroViewModel.kt:518`) puts
`"${title}: not synced to this device"` into `skippedTasks`. The skipped
section is already rendered — user sees a "Skipped: X title (not synced)"
list inline.

**Per endpoint:**

- **Daily Briefing:** New `pendingSyncTitles: List<String>` rendered as a
  small footer below `headsUp`. "3 priorities pending sync from another
  device" — match Smart Focus's textual phrasing.
- **Weekly Plan:** Append unresolved tasks to existing `unscheduled` with
  reason "Not synced to this device". User sees them in the existing
  unscheduled section.
- **Time-Block:** Append unresolved schedule-block tasks to existing
  `unscheduledTasks` with reason "Not synced to this device". User sees
  them in the existing unscheduled list.

**No silent demotion.** All three surfaces are user-visible. Matches the
spirit of Smart Focus's pattern; no follow-up UX PR needed.

---

## Item 7 — Fan-out proposal

`UnscheduledTaskResponse` shared between Weekly Plan + Time-Block forces
those two endpoints to ship together. Daily Briefing is independent.

**Recommended shape: 2 PRs.**

### PR 1 — `fix/ai-briefing-task-id-drift`

**Touches:**
- `data/remote/api/ApiModels.kt`: `BriefingPriorityResponse.taskId` Long → String, `SuggestedTaskResponse.taskId` Long → String. Remove the two `TODO(weekly-followup)` markers.
- `ui/screens/briefing/DailyBriefingViewModel.kt`: add `taskDao.getIdByCloudId` resolution loop; demote unresolved into new `DailyBriefing.pendingSyncTitles: List<String>`.
- `ui/screens/briefing/DailyBriefing(.kt)`: add new field; surface as footer text.
- `ui/screens/briefing/DailyBriefingScreen.kt`: render the new footer.
- `app/src/test/.../DailyBriefingTest.kt`: fixture string updates.
- `app/src/test/.../DailyBriefingViewModelTest.kt`: NEW — resolution + unresolved tests.

**Estimated:** ~80 LOC, 2 new tests, 1 new UI surface, ~3 hours.

### PR 2 — `fix/ai-weeklyplan-timeblock-task-id-drift`

**Touches:**
- `data/remote/api/ApiModels.kt`: `PlannedTaskResponse.taskId` Long → String, `UnscheduledTaskResponse.taskId` Long → String, `ScheduleBlockResponse.taskId` Long? → String?. Remove the three `TODO(weekly-followup)` markers.
- `ui/screens/planner/WeeklyPlannerViewModel.kt`: resolution loop in `generatePlan()`; demote unresolved into existing `unscheduled` list with "Not synced to this device" reason.
- `domain/usecase/AiTimeBlockUseCase.kt`: resolution loop; demote unresolved schedule blocks into the response's `unscheduledTasks` (still typed as backend response — promote to a wrapper type if cleaner — implementation call).
- `ui/screens/timeline/TimelineViewModel.kt`: keep `AiScheduleBlock.taskId: Long?` UI shape (already correct); just consume the use-case's already-resolved IDs.
- `web/src/api/ai.ts`: remove stale TODO comment at line 61-66 (no code change).
- `app/src/test/.../WeeklyPlannerTest.kt`: fixture string updates.
- `app/src/test/.../TimeBlockTest.kt`: fixture string updates.
- `app/src/test/.../TimelineViewModelTest.kt`: fixture string updates + `getIdByCloudId` mock setup + 1 new unresolved-demotion test.
- `app/src/test/.../AiTimeBlockUseCaseTest.kt`: 2 new tests (resolution + unresolved demotion at use-case boundary).
- `app/src/test/.../WeeklyPlannerViewModelTest.kt`: NEW — resolution + unresolved tests.

**Estimated:** ~120 LOC, 5 new tests, ~4 hours.

### Why not 3 PRs

Splitting `UnscheduledTaskResponse` from either WeeklyPlan or Time-Block
would require either: (a) a temporary intermediate type, or (b) breaking
one consumer for one merge cycle. Bundling avoids both — single uniform
type flip lands once, both VMs see the new type simultaneously.

### Why not 1 bundled PR

Daily Briefing carries the only structural exception (new
`pendingSyncTitles` UI surface). Reviewing it alongside the rote
WeeklyPlan/TimeBlock flips dilutes review attention on the one place
that has design judgement. Two PRs gives Daily Briefing focused review.

### Sequencing

PR 1 and PR 2 are **independent** — no shared files. Both can land in
parallel. Per memory `feedback_use_worktrees_for_features.md`: each gets
its own worktree, removed paired with merge.

CI gates per repo conventions: `compileDebugAndroidTestKotlin` must pass
on both. No DAO changes (`getIdByCloudId` already shipped with Smart
Focus), so DAO/test-module parity check (#20) doesn't apply here.

### Total estimate

~6-8 hours = ~1 dev day. Well under the >5d STOP threshold. PROCEED in
single Phase F session.

---

## Item 8 — Expected-outcome distribution

**Pre-investigation prediction:**
- PROCEED-as-written: 50%
- COMPROMISED-SCOPE-PROCEED: 35%
- STOP-defer-to-G.0: 10%
- STOP-architectural-rethink: 5%

**Actual outcome: COMPROMISED-SCOPE-PROCEED.**

The compromise: Daily Briefing has no skipped-tasks analog. The Smart
Focus pattern can't be applied verbatim — needs a new
`pendingSyncTitles` field + footer UI. Minor scope expansion (~30 extra
LOC over a pure type-flip), but stays within Phase F budget.

The other 4 endpoints fit the pattern cleanly. Backend is consistent.
Web is already fixed. No architectural rethink needed.

---

## Improvement table (sorted by wall-clock-savings ÷ implementation-cost)

| Rank | Improvement | Wall-clock saved | Implementation cost | Verdict |
|---|---|---|---|---|
| 1 | PR 1 — Daily Briefing fix | High (Phase F testers will trip the moment they tap "Generate briefing") | ~3 h | PROCEED |
| 2 | PR 2 — WeeklyPlan + Time-Block fix | High (same — both flows are gated on tier but live to Pro testers) | ~4 h | PROCEED |
| 3 | Coaching outbound type flip (lines 547, 589) | Low — flow may already 422 silently or pydantic-coerce | ~1 h investigation + ~2 h fix | DEFER (not in scope; separate audit) |
| 4 | Web stale TODO removal | None (cosmetic) | ~5 min | Bundle in PR 2 |

---

## Anti-patterns flagged (not necessarily fixed)

1. **Inconsistent inbound vs outbound `taskId` typing** — `CoachingRequest`,
   `CoachingTaskSummary`, `ChatRequest.taskContextId` (line 485), all
   outbound, still typed `Long` while the backend expects `Optional[str]`.
   Pydantic v2 behavior under Long-as-int input is implementation-defined
   (lenient coercion vs strict 422). **Recommend a separate request-side
   audit before Phase G** — different drift, different blast radius.

2. **Multi-tier mapping in DailyBriefingViewModel** — every field is
   re-mapped from response types into near-identical UI types (`taskId`,
   `title`, `reason`). This is fine for type isolation, but consider
   whether the wrapper layer earns its keep. Out of scope for this audit.

3. **Tests that bypass the ViewModel** — `DailyBriefingTest.kt` and
   `WeeklyPlannerTest.kt` reconstruct the VM's mapping logic inline rather
   than mock-injecting the real VM. The drift fix needs to add real VM
   tests anyway; consider whether the inline domain tests still earn
   their place after the VM tests land.

4. **`TimeBlockTest.kt` constructor lacks `date`** (line 21-23). Some
   `ScheduleBlockResponse` fixtures omit the `date` field which is
   nullable. Compile passes via the default, but inconsistent with the
   `TimelineViewModelTest.kt` fixtures that always set it. Cosmetic.

---

## Appendix — Reference SHAs / commits

- Smart Focus reference implementation: `SmartPomodoroViewModel.kt:509-539`,
  `SmartPomodoroViewModelTest.kt`. Type flip: `taskId: Long` → `taskId: String`
  on `SessionTaskResponse` and `SkippedTaskResponse` already shipped in HEAD.
- `TaskDao.getIdByCloudId(cloudId: String): Long?` shipped at `TaskDao.kt:19-20`.
- Originating drift commit: `018f2408` (Apr 18 2026,
  "Read AI-endpoint tasks from Firestore").
- Backend schemas: `backend/app/schemas/ai.py` lines 95, 101, 131, 147, 221.
- Web parity: `web/src/types/briefingPlanner.ts` (clean), `web/src/api/ai.ts:61`
  (stale TODO).

---

## Phase 2 — Fan-out execution plan (Path B: single bundled PR)

1. Create worktree `worktrees/fix-ai-endpoint-task-id-drift` off `main`.
   Inside the worktree, apply in order:
   - **Smart Focus baseline** (currently uncommitted in the main repo's
     working tree — bring over by re-applying the 4 file deltas):
     `TaskDao.getIdByCloudId`, `SessionTaskResponse`/`SkippedTaskResponse`
     Long → String, `SmartPomodoroViewModel` resolution loop,
     `SmartPomodoroViewModelTest` updates.
   - **Daily Briefing fix**: `BriefingPriorityResponse`/`SuggestedTaskResponse`
     Long → String; `DailyBriefingViewModel` resolution + new
     `pendingSyncTitles: List<String>` field; `DailyBriefingScreen`
     footer rendering; fixture updates in `DailyBriefingTest.kt`; new
     `DailyBriefingViewModelTest.kt`.
   - **Weekly Plan + Time-Block fix**: `PlannedTaskResponse`,
     `UnscheduledTaskResponse`, `ScheduleBlockResponse` Long → String/Long?
     → String?; `WeeklyPlannerViewModel` resolution into existing
     `unscheduled`; `AiTimeBlockUseCase` resolution into `unscheduledTasks`;
     fixture updates in `WeeklyPlannerTest.kt`, `TimeBlockTest.kt`,
     `TimelineViewModelTest.kt`, `AiTimeBlockUseCaseTest.kt`; new
     `WeeklyPlannerViewModelTest.kt`; new resolution + unresolved tests
     in `AiTimeBlockUseCaseTest.kt` and `TimelineViewModelTest.kt`.
   - **Web cleanup**: remove stale TODO comment at `web/src/api/ai.ts:61-66`.
   - **Audit doc**: include this file in the same PR for traceability.
2. Run `./gradlew testDebugUnitTest` in the worktree. Confirm green.
3. Push branch; open PR with summary linking to this audit doc;
   `gh pr merge <num> --auto --squash`.
4. After merge: remove worktree + delete branch the same session per
   `feedback_use_worktrees_for_features.md`.
5. Phase 4 session summary appended to this audit doc + Claude Chat
   handoff block emitted.

No checkpoint stops. No backend changes. DAO change limited to
`getIdByCloudId` (already in working tree, re-applied as part of Smart
Focus baseline; no new DAO surface beyond what Smart Focus added).
Memory #20 (DAO/test-module parity) does not apply because the new DAO
method does not need a corresponding fake-test-module override — it's a
plain `@Query` method auto-generated by Room.

**Estimated bundled PR size:** ~250 LOC (Smart Focus ~115 + 5 endpoints
~135) + this audit doc.
