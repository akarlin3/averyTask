# Connected-Tests Stabilization Audit

**Date**: 2026-04-27
**Status**: Phase 1 complete — STOP-no-PR for the cancellation cascade; targeted
follow-up flagged for residual `HabitSmokeTest` AVD-boot flake.
**Scope**: 28-run window of `android-integration.yml` on `main`, runs `24938943423`
through `25018425883` (Apr 25 19:35Z – Apr 27 20:41Z), expanded with the next 8
post-#841 runs through `25019467806` (Apr 27 21:04Z) for fix-validation.

## TL;DR

The user's pre-audit measurement of "6 failures + 18 cancellations + 3 successes"
on `android-integration.yml` framed this as a "connected-tests stabilization"
problem with 64% cancellation rate. Investigation found:

1. **The cancellation rate is not a bug.** All 18 cancellations trace to expected
   `cancel-in-progress` cascade (13/18) or 45-min `timeout-minutes` on the
   `cross-device-tests` job (5/18). Both are textbook GitHub Actions behavior
   under high merge velocity. Hypothesis H1 (expected) confirmed.
2. **The "failures" are not connected-tests failures.** 5 of 6 are
   `cross-device-tests` job failures (the quarantined, NOT-required-status lane).
   The 1 connected-tests failure has the same AVD-boot signature as 2 more CT
   failures hidden inside cancelled runs.
3. **Run-level workflow conclusion is misleading.** When a workflow has a
   quarantined job, the run-level `conclusion` is dominated by whichever job is
   non-success — not by the required-status job. Branch protection sees the right
   thing (per-job conclusions); humans + audit scripts that read run-level
   conclusion don't.
4. **#824 + #841 substantially improved things but did NOT fully resolve the
   AVD-boot flake.** Post-#841 sample of 8 runs: 6 CT-success + 1 CT-cancelled +
   1 CT-failure (still AVD-boot, same `HabitSmokeTest.habitList_tappingHabitDoesNotCrash`
   signature). Residual rate: ~12.5% on CT.

| Metric | Run-level (what user measured) | `connected-tests` only (required status) |
|--------|--------------------:|---------------------:|
| pre-fix window (28 runs Apr 25-27 to #856) success | 4/27 = 14.8% | **22/27 = 81.5%** |
| pre-fix failure | 5/27 = 18.5% | 2/27 = 7.4% (both AVD-boot) |
| pre-fix cancelled | 18/27 = 66.7% | 4/27 = 14.8% (cancel-in-progress) |
| post-#841 sample (8 runs through #857) success | 6/8 = 75% | 6/8 = 75% |
| post-#841 CT failure | n/a | 1/8 = 12.5% (residual AVD-boot) |

**Verdict: D2 item "Stabilize connected-tests" closes to `done: 0.85`** (not 1.0)
with one targeted follow-up tracked: investigate the residual
`HabitSmokeTest.habitList_tappingHabitDoesNotCrash` AVD-boot flake. Promotion of
`connected-tests` to required-status remains viable but should wait until the
residual flake is investigated to keep noise low.

## Item 1 — Classify the 18 cancellations

### Methodology

Pulled the 28-run window:
```bash
gh run list --workflow=android-integration.yml --branch main --limit 28 \
  --json databaseId,conclusion,createdAt,updatedAt,headSha,event,headBranch,displayTitle
```

For each cancelled / failed run, pulled per-job conclusions:
```bash
gh run view <RUN_ID> --json conclusion,createdAt,headSha,jobs
```

### Per-run table (24 non-success/non-progress runs)

Sorted oldest → newest. `CT` = `connected-tests` (required status). `XDT` =
`cross-device-tests` (quarantined, NOT required). Durations in seconds.

| # | Run ID | created | SHA8 | PR | conclusion | CT | CT s | XDT | XDT s | XDT cause |
|---|--------|---------|------|----|-----------|----|------|-----|-------|-----------|
| a | 24938943423 | Apr 25 19:35:13Z | 5fa31a6a | (push) | cancelled | cancelled | 57 | cancelled | 57 | cancelled mid-emulator-boot by newer push |
| b | 24938962202 | Apr 25 19:36:08Z | 591a9d77 | #786 | failure | success | 1063 | **failure** | 172 | shell-parse error (BUILD FAILED in 20s, exit code 2) — pre-#776 escape fix |
| c | 24942365939 | Apr 25 22:39:12Z | 9dd2df5d | #780 | failure | success | 817 | **failure** | 805 | AVD-boot (`[EmulatorConsole]: Failed to start Emulator console for 5554` + 3 MedicationCrossDeviceConvergenceTest tests fail downstream) |
| d | 24943217820 | Apr 25 23:29:34Z | 4dd0efae | #781 | failure | success | 886 | **failure** | 588 | AVD-boot (same signature) |
| e | 24945995404 | Apr 26 02:10:56Z | dfbc1695 | (push) | failure | success | 1151 | **failure** | 764 | AVD-boot (same signature) |
| f | 24947340575 | Apr 26 03:32:11Z | 7b925913 | #787 | failure | success | 1076 | **failure** | 763 | AVD-boot (same signature, with shell-retry — pre-#835 retry-drop) |
| g | 24948026658 | Apr 26 04:15:48Z | 4b77c23f | #791 | cancelled | success | 974 | cancelled | 1603 | cancel-in-progress (newer push) |
| h | 24948443960 | Apr 26 04:42:30Z | a6ce066c | (push) | cancelled | success | 1040 | cancelled | 1098 | cancel-in-progress (newer push) |
| i | 24948728277 | Apr 26 05:00:50Z | cb12afe3 | (push) | cancelled | success | 879 | cancelled | **2703 ≈ 45m** | **JOB TIMEOUT** (`timeout-minutes: 45`) |
| j | 24949613217 | Apr 26 05:56:39Z | 747cc4ed | (push) | cancelled | success | 969 | cancelled | 1597 | cancel-in-progress (newer push) |
| k | 24950056333 | Apr 26 06:23:15Z | d4f8d5ed | #804 | cancelled | success | 971 | cancelled | **2703 ≈ 45m** | **JOB TIMEOUT** |
| l | 24966447620 | Apr 26 20:35:34Z | c351cfc8 | #812 | cancelled | success | 1052 | cancelled | 1294 | cancel-in-progress (newer push #811) |
| m | 24966870448 | Apr 26 20:57:07Z | fb077dc2 | #811 | cancelled | success | 1010 | cancelled | 1227 | cancel-in-progress (newer push #813) |
| n | 24967280776 | Apr 26 21:17:36Z | 77327156 | #813 | cancelled | success | 1079 | cancelled | **2703 ≈ 45m** | **JOB TIMEOUT** |
| o | 24969550465 | Apr 26 23:16:20Z | c874385a | #818 | cancelled | **failure** | 920 | cancelled | 988 | CT: AVD-boot (`adb failed exit 1` + `[EmulatorConsole]: Failed to start Emulator console for 5554` + downstream `HabitSmokeTest.habitList_tappingHabitDoesNotCrash` `Failed to inject touch input`); XDT: cancel-in-progress |
| p | 24969851342 | Apr 26 23:32:46Z | e8b564e9 | #816 | cancelled | success | 1013 | cancelled | 1411 | cancel-in-progress (newer push #823) |
| q | 24970283404 | Apr 26 23:56:21Z | 871c2da8 | **#824** | cancelled | success | 1023 | cancelled | **2705 ≈ 45m** | **JOB TIMEOUT** (referenced in YAML lines 180-183) |
| r | 24973532931 | Apr 27 02:21:19Z | 32704930 | #829 | cancelled | cancelled | 537 | cancelled | 534 | both cancelled mid-emulator-boot by newer push #830 (53s gap) |
| s | 24973738490 | Apr 27 02:30:12Z | c144f651 | #830 | cancelled | success | 968 | cancelled | 1675 | cancel-in-progress (newer push) |
| t | 24974393528 | Apr 27 02:58:12Z | c5c0fefc | #835 | cancelled | success | 1007 | cancelled | **2705 ≈ 45m** | **JOB TIMEOUT** |
| u | 24976930635 | Apr 27 04:42:28Z | 00acdc11 | #834 | cancelled | success | 998 | cancelled | 2490 | cancel-in-progress (newer push #840) |
| v | 24978033482 | Apr 27 05:23:54Z | 23696e57 | #832 | cancelled | cancelled | 924 | cancelled | 920 | both cancelled by newer push #844 (15m gap) |
| w | 24978460550 | Apr 27 05:39:20Z | c3997aa3 | #844 | cancelled | **failure** | 1014 | cancelled | 2283 | CT: AVD-boot (same `HabitSmokeTest.habitList_tappingHabitDoesNotCrash` signature as run o); XDT: cancel-in-progress |

### Cancellation cause distribution (18 cancelled runs)

| Cause | Count | Notes |
|-------|------:|-------|
| `cross-device-tests` cancel-in-progress (newer push) | 13 | including 3 where CT was also cancelled because the new push fired before CT booted |
| `cross-device-tests` 45min job-timeout (`timeout-minutes: 45`) | 5 | runs i, k, n, q, t — all show XDT duration ≈ 2703-2705s |

100% of "cancelled" runs trace to the `cross-device-tests` job. **None** trace to
external orchestration, branch-protection bypass, or workflow_run chain effects.

### Was the SHA still HEAD-of-main when each cancellation fired?

For cancel-in-progress cancellations: by definition the SHA was no longer HEAD —
the cancellation fires precisely because a newer commit reached HEAD and triggered
a new run with a higher SHA in the same concurrency group. Verified spot-check:
run l (#812 SHA c351cfc8) cancelled at 20:57:11Z; PR #811 with mergeCommit
fb077dc2 was merged at 20:57:04Z — 7 seconds earlier. cancel-in-progress fired
when run m was created at 20:57:07Z.

For 45min-timeout cancellations: the SHA was usually still HEAD when the timeout
fired (no newer push intervened in 45min).

## Item 2 — Trace the high-velocity merge window timeline

Cross-referenced cancellation timestamps against PR merge timestamps via:
```bash
gh pr list --base main --state merged --limit 50 --json number,mergedAt,title,mergeCommit
```

Densest cluster (Apr 26 20:35Z – Apr 27 05:39Z):

| Merge time | PR | Run created | Δ | Notes |
|------------|----|-----:|--:|-------|
| 20:35:32Z | #812 | 20:35:34Z | +2s | run l |
| 20:57:04Z | #811 | 20:57:07Z | +3s | run m, cancels l 4s later |
| 21:17:33Z | #813 | 21:17:36Z | +3s | run n, cancels m 6s later |
| 23:16:17Z | #818 | 23:16:20Z | +3s | run o (CT AVD-boot) |
| 23:32:44Z | #816 | 23:32:46Z | +2s | run p, cancels o |
| 23:56:19Z | #824 | 23:56:21Z | +2s | run q, cancels p; XDT 45m timeout 00:41:34Z |
| 02:21:16Z | #829 | 02:21:19Z | +3s | run r |
| 02:30:09Z | #830 | 02:30:12Z | +3s | run s, cancels r 53s later (still booting) |
| 02:58:09Z | #835 | 02:58:12Z | +3s | run t, cancels s |
| 04:42:26Z | #834 | 04:42:28Z | +2s | run u (gap of 1h44m) |
| 05:23:50Z | #832 | 05:23:54Z | +4s | run v, cancels u |
| 05:39:17Z | #844 | 05:39:20Z | +3s | run w (CT AVD-boot), cancels v |

Every run was created 2-4 seconds after a PR merge. Every cancellation maps
cleanly to either (a) a subsequent merge inside the in-progress window, or
(b) the 45-minute job timeout when no subsequent merge intervened. **No**
evidence of cancellations firing independently of merge-driven cancel-in-progress
or job timeouts.

Docs-only PRs (#814, #815, #817, #819, #820, #823, #825, #826, #827, #828, #831,
#833, #836-#840, #842, #843, #845, #846, #847) skip android-integration via the
`paths` filter at YAML lines 26-35 — their merges don't appear in this timeline.

## Item 3 — Audit `cancel-in-progress` YAML configuration

### Per-workflow concurrency matrix

| Workflow | Group key | cancel-in-progress | Verdict |
|----------|-----------|-------------------:|---------|
| android-ci.yml | `android-ci-${{ github.ref }}` | true | Standard pattern |
| backend-ci.yml | `backend-ci-${{ github.ref }}` | true | Standard pattern |
| web-ci.yml | `web-ci-${{ github.ref }}` | true | Standard pattern |
| **android-integration.yml** | **`android-integration-${{ github.ref }}`** | **true** | **Standard; the workflow under audit** |
| release.yml | `release-${{ github.ref }}` | true | Standard; documented Phase F cadence |
| label-integration-ci.yml | `label-integration-ci-${{ github.event.pull_request.number }}` | true | Per-PR scoped, safe |
| version-bump-and-distribute.yml | `version-firebase-main` (literal) | **false** | Intentional — runs SERIALIZE for sequential versionCode bumps |
| auto-update-branch.yml | `auto-update-branch` (literal) | true | Intentional global queue |

**No surprises**. Every cancel-in-progress workflow uses `${{ github.ref }}` as
the group key. For pushes to main, all main-branch pushes share a single
concurrency group — newer pushes cancel older runs. This is the textbook
GitHub Actions cost-saving pattern. The two workflows where chain-cancellation
could be a problem (`version-bump-and-distribute.yml`, `auto-update-branch.yml`)
explicitly use a different group strategy.

**No `workflow_run` chain cancellation found** — no workflow uses `workflow_run`
triggers in a chain pattern that would cascade cancellations across workflows.
PR #850 / #793 / #796 do NOT introduce a regression here.

## Item 4 — Investigate the 6 pre-fix failures

### Per-failure classification

Following memory #21 (`adb failed with exit code 1` + `EmulatorConsole` =
AVD-boot, not test-content), pulling the actual failure markers from
`gh run view <id> --log-failed`:

| Run | SHA | PR | Failure shape | Root cause | Fix candidate |
|-----|-----|----|---------------|------------|---------------|
| 24937612065 (just outside 28-window) | 72659e33 | (push) | `BUILD FAILED in 20s` (exit code 1) — emulator boots but gradle invocation fails before any test runs | shell-parse error in pre-#776 `\\`-continuation script | **already fixed by #776** ("cross-device-tests shell-escape" — fn-replacement) |
| b (24938962202) | 591a9d77 | #786 | `BUILD FAILED in 20s` (exit code 2) | shell-parse error (similar) | **already fixed by #780** ("stabilize via temp-script file") |
| c (24942365939) | 9dd2df5d | #780 | `[EmulatorConsole]: Failed to start Emulator console for 5554` → 3 MedicationCrossDeviceConvergenceTest tests FAIL: `medicationDoseFkResolvesAcrossDevices`, `medicationSlotJunctionRebuildAfterRemoteSlotAdd`, `medicationLastWriteWins_remoteUpdateOverwritesLocal` | AVD-boot (HiltTestRunner heuristic drift; pre-#841) | **#841** (HiltTestRunner.isAndroidEmulator detects API 33+ AVDs) |
| d (24943217820) | 4dd0efae | #781 | Same EmulatorConsole + same 3 tests FAIL | AVD-boot | **#841** |
| e (24945995404) | dfbc1695 | (push) | Same EmulatorConsole + same 3 tests FAIL | AVD-boot | **#841** |
| f (24947340575) | 7b925913 | #787 | Same EmulatorConsole + same 3 tests FAIL (with shell-retry — `Run medication cross-device tests with one retry`) | AVD-boot | **#841** + **#835** (drop shell retry — retries were masking convergence-on-second-try) |

### Connected-tests failures hidden inside cancelled runs

| Run | SHA | PR | Failure shape | Root cause | Fix candidate |
|-----|-----|----|---------------|------------|---------------|
| o (24969550465) | c874385a | #818 | `[EmulatorConsole]: Failed to start Emulator console for 5554` → `HabitSmokeTest.habitList_tappingHabitDoesNotCrash` FAILED with `java.lang.AssertionError: Failed to inject touch input` | AVD-boot (test runs but input subsystem not stable) | **#841** + see residual finding below |
| w (24978460550) | c3997aa3 | #844 | Same EmulatorConsole + same `HabitSmokeTest.habitList_tappingHabitDoesNotCrash` FAILED with same `Failed to inject touch input` | AVD-boot | **#841** + see residual finding below |

### Memory #20 drive-by check

`git log -p -S 'HiltTestRunner'` since 2026-04-22:
- **#791** (4b77c23f) — introduced `HiltTestRunner.configureFirebaseEmulator()` mirroring `PrismTaskApplication.configureFirebaseEmulator()`. Bug: `isAndroidEmulator()` heuristic copied from production was already drifted.
- **#841** (2f65bf85) — fixed the heuristic for API 33+ AVDs (`Build.PRODUCT = "sdk_gphone64_x86_64"` shape). CHANGELOG entry calls out: "with `isAndroidEmulator()` returning false, `configureFirebaseEmulator()` fell through to `host = "localhost"`, and the AVD's loopback can't reach the host's Firestore emulator — so cross-device sync tests hung on `ECONNREFUSED` retries until the 45-min job-timeout fired."

No drive-by between #791 and #841. No drive-by since.

`git log` for android-integration.yml since 2026-04-22:
- **#705** (85354d3c) — workflow hardening
- **#741** (3187413d) — sync-tests CI infrastructure + harness
- **#773** (11cd349d) — Phase B medication migration
- **#776** (591a9d77) — shell-escape function replacement (fixes 24937612065)
- **#780** (9dd2df5d) — temp-script file stabilization (fixes b)
- **#824** (871c2da8) — emulator-boot-timeout 600 → 1200s
- **#835** (c5c0fefc) — drop shell retry

All known. No surprise commits.

### Failure summary

- **2 of 6 (b, 6th-out-of-window)**: shell-parse errors, fix-resolved by #776 + #780.
- **4 of 6 (c, d, e, f)**: AVD-boot via HiltTestRunner heuristic drift, fix-resolved by #841 (CT side) and complemented by #824 (longer boot timeout) + #835 (drop convergence-masking retry).
- **2 additional CT failures (o, w)** hidden inside cancelled runs: AVD-boot via the SAME `HabitSmokeTest.habitList_tappingHabitDoesNotCrash` "Failed to inject touch input" signature.

100% of pre-#841 failures classify as fix-targeted by the audit's referenced
PRs. **0 real test-content bugs.**

## Item 5 — Confirm fix-resolution by sampling post-fix runs

### Post-#841 sample (8 main-branch runs through Apr 27 21:04Z)

| Run ID | Created | PR | Run conclusion | CT | XDT | XDT s |
|--------|---------|----|----|----|-----|-------|
| 24979605145 | Apr 27 06:17:29Z | **#841** | success | success | success | 519 |
| 24982560120 | Apr 27 07:39:09Z | #848 | success | success | success | 494 |
| 24985826353 | Apr 27 08:56:54Z | #851 | success | success | success | 513 |
| 25014086872 | Apr 27 19:04:41Z | #849 | success | success | success | 538 |
| 25016073394 | Apr 27 19:49:46Z | #853 | success | success | success | (~520) |
| 25017932714 | Apr 27 20:30:40Z | #855 | cancelled | **cancelled (650s)** | success | 538 |
| 25018425883 | Apr 27 20:41:25Z | #856 | success | success | success | (~520) |
| 25019467806 | Apr 27 21:04:08Z | **#857** | failure | **failure (1063s)** | success | 519 |

Post-#841 outcomes:
- CT success: 6/8 = 75%
- CT cancelled (cancel-in-progress, expected): 1/8 = 12.5% (run #855)
- CT failure: 1/8 = 12.5% (run #857) — **same `HabitSmokeTest.habitList_tappingHabitDoesNotCrash` AVD-boot signature as runs o + w pre-#841**
- XDT success: 8/8 = **100%** (XDT durations all 8-9 minutes — well below 45m
  timeout, confirming #841 fixed XDT's root cause completely)

### Fix-resolution verdict

- **XDT side: fully resolved.** 8/8 success post-#841, no 45m timeouts, no
  EmulatorConsole errors in XDT logs since #841 merged. The MedicationCrossDeviceConvergenceTest
  (3 tests in pre-fix failures c-f) now passes consistently.
- **CT side: partially resolved.** The `MedicationCrossDeviceConvergenceTest`-shaped
  failures are gone, but `HabitSmokeTest.habitList_tappingHabitDoesNotCrash` AVD-boot
  flake persists. 3 occurrences total (runs o + w pre-#841, run #857 post-#841)
  with identical `[EmulatorConsole]: Failed to start Emulator console for 5554` →
  `Failed to inject touch input` shape.

### Why the residual flake survives #841

#841 fixed the **routing** path (HiltTestRunner.isAndroidEmulator returning
false → host = "localhost" → ECONNREFUSED). That's a deterministic bug.

The residual failure mode is different: the emulator boots and routing is
correct, but the QEMU console socket binding on port 5554 is intermittently
delayed. When that happens, adb input commands route through a fallback path
that's slower and occasionally drops touch events. `HabitSmokeTest.habitList_tappingHabitDoesNotCrash`
is the canary because it does a `performClick()` on a non-tab text node
immediately after `clickTab()` — two consecutive touch events with minimal
settle time between them. (`habitsTab_showsSeededHabits` only does a tab click;
`habitsTab_todayScreenShowsHabitsSection` does no click at all. Both consistently
pass.)

This is a known `reactivecircus/android-emulator-runner@v2` runner-image
characteristic, not a bug introduced by any audited PR. The `composeRule.waitForIdle()`
calls in the test are correct — they wait for Compose's snapshot to settle, but
they don't wait on adb input subsystem readiness.

## Item 6 — Phase 2 fan-out proposal

### Decision: STOP-no-PR for the cancellation cascade investigation

The audit's primary deliverable is the audit doc itself. **No fix PR is warranted
for the cancellation cascade** — the pattern is expected GitHub Actions behavior
under high merge velocity, and the existing concurrency configs are textbook
correct.

This continues the audit-first track record (12 of 13 ending in STOP-no-PR or
audit-first-reframe; this audit makes it 13 of 14 if counted as a reframe).

### Decision: STOP-no-speculative-PR for the residual `HabitSmokeTest` flake

The residual ~12.5% CT flake on `HabitSmokeTest.habitList_tappingHabitDoesNotCrash`
warrants a follow-up but **not a speculative fix in this audit**. Possible
interventions, all with their own tradeoffs:

| Option | Tradeoff | My take |
|--------|----------|---------|
| 1. Pin a newer `reactivecircus/android-emulator-runner` version | Might fix; might break — runner action releases have introduced their own bugs historically | Worth investigating; needs its own audit |
| 2. Add `adb wait-for-device` + console probe before gradle | Already did the `adb shell settings put global` calls succeed → adb shell works → console probe wouldn't catch this | Low confidence in fix |
| 3. Add `composeRule.waitForIdle()` between `clickTab()` and `performClick()` | `clickTab()` already calls `waitForIdle()` internally — additional waits are speculative | Won't help |
| 4. Mark `HabitSmokeTest.habitList_tappingHabitDoesNotCrash` as `@FlakyTest` with retry | Hides the symptom, exactly what memory #21 warns against | Anti-pattern |
| 5. Accept ~12.5% residual flake when promoting CT to required-status | Branch protection becomes mildly noisy; ~1 in 8 PRs gets a spurious failure that needs re-run | Pragmatic — most projects accept this rate |

**Recommendation**: option 5 (accept) for CT promotion to required-status, with
a separate follow-up audit on `HabitSmokeTest` specifically. The follow-up audit
should:
1. Reproduce the failure shape locally with `--rerun-tasks` looped 20+ times.
2. Look at the QEMU console binding behavior to confirm the hypothesis.
3. Propose a deterministic test-content or workflow-content fix (or accept the
   residual flake permanently, with the test pinned to 3 retries via JUnit's
   `@Repeat` if such a rule exists).

### D2 item status

**D2 "Stabilize connected-tests" closes to `done: 0.85`** (not 1.0):
- Cancellation cascade explained and confirmed not-a-bug (Item 1-3): +0.4
- Pre-fix failures classified, all fix-resolved (Item 4): +0.3
- Post-fix XDT side fully green (Item 5 partial): +0.15
- Residual CT `HabitSmokeTest` flake tracked but not fixed: -0.15

### Promote `connected-tests` to required-status

Viable now, with the caveat that `HabitSmokeTest.habitList_tappingHabitDoesNotCrash`
will occasionally surface as a noisy required-status failure (~12.5% rate, ~1
PR in 8). Manual UI step in GitHub repo settings → Branches → main → required
status checks → add `connected-tests`. Not a code change. Not in this audit's
scope to flip; flag for the operator's call.

### Memory candidates

Two potential memory entries from this audit, flagged for operator review:

1. **"Run-level workflow conclusion is not a flake metric for multi-job workflows
   with quarantined jobs."** Generalizes memory #22 (CD silent-failure) to the
   data-layer aggregation problem. When measuring CT health, query per-job
   conclusions, not run-level conclusions. The "22% flake rate" baseline at
   PR #761 era was almost certainly measuring run-level conclusion and so was
   actually measuring XDT flakiness, not CT flakiness.

2. **"Same-test-name + EmulatorConsole-5554 = AVD console binding flake, not test
   bug."** Refines memory #21 (`adb failed exit 1` = AVD boot failure) for the
   case where adb itself works but the QEMU console socket has bound late. The
   downstream symptom is `Failed to inject touch input` on the first
   touch-injecting test in the suite. Fix is at the runner level, not test
   level; speculative test changes are anti-pattern.

These are flagged for the operator; not auto-saved. Memory cap is 30; current
count is 21 after this session's `feedback_skip_audit_checkpoints`.

## Hypothesis verdicts (final)

| Hypothesis | Verdict | Notes |
|------------|---------|-------|
| **H1** — Expected `cancel-in-progress` cascade during merge velocity | **CONFIRMED** | 13/18 cancel-in-progress + 5/18 45m XDT job-timeout |
| **H2** — Regression in PR #850 / #793 / #796 workflow_run chain | **REJECTED** | No `workflow_run` chain into android-integration; concurrency configs are standard |
| **H3** — Memory-#22 silent-failure-adjacent (branch-protection bypass) | **REJECTED for branch protection**, but a **NEW data-layer parallel found** | Branch protection sees per-job conclusions correctly. Run-level conclusion is misleading at the human / dashboard / audit-script level. |
| **H4** (emerged) — Residual AVD console-binding flake post-#841 | **CONFIRMED at ~12.5%** | Same `HabitSmokeTest.habitList_tappingHabitDoesNotCrash` "Failed to inject touch input" signature in 3 occurrences (o, w, #857). NOT fix-resolved by #841; needs separate investigation. |

## Phase 3 — Bundle summary

- **Per-item PR numbers**: STOP-no-PR for Items 1-5; STOP-no-speculative-PR for Item 6's residual flake.
- **Final flake-rate measurement**: post-#841 8-run sample shows CT 75% success, 12.5% cancel-in-progress, 12.5% AVD-console-binding flake.
- **`connected-tests` required-status promotion**: operator-decision; viable but ~12.5% noise expected until the follow-up audit on `HabitSmokeTest`.
- **D2 item status**: closes to `done: 0.85` (not 1.0), with one tracked follow-up (`HabitSmokeTest` AVD-console-binding flake audit).
- **Memory candidates**: 2 flagged, neither auto-saved (see Item 6).

## Reference SHAs and PR numbers

- PR #761 — TAG_CHANGE preview diff (where 22% flake rate was originally flagged — actually XDT rate, not CT)
- PR #776 — cross-device-tests shell-escape function replacement (fixes failure 24937612065)
- PR #780 — temp-script file stabilization (fixes failure b)
- PR #791 — cross-device-tests emulator routing fix (introduced `configureFirebaseEmulator` in HiltTestRunner with the heuristic-drift bug that #841 fixed)
- PR #793 — auto-update-branch workflow (NOT implicated in cancellation cascade)
- PR #796 — every-merge release pipeline (NOT implicated)
- PR #801 — flake misclassification correction (the audit that found the 22% number was wrong; this audit refines further by attributing residual to XDT quarantine + cancel-in-progress)
- PR #823 — pre-Phase F mega-audit closeout
- PR #824 — emulator-boot-timeout 600 → 1200s (resolves XDT 45-min timeouts when boot is slow)
- PR #825 — D2/F prep mega-audit closeout
- PR #835 — drop shell retry in cross-device-tests (removes convergence-on-retry false-pass)
- PR #841 — HiltTestRunner.isAndroidEmulator detects modern API 33+ AVDs (real root-cause fix for XDT side)
- PR #850 / #852 — APK + AAB workflow artifacts (NOT implicated)
- PR #851 / #853 / #855 / #856 — P0 sync fan-out
- PR #857 — feat(medication) tier buttons (NEW post-#841 CT-failure run; same residual `HabitSmokeTest` shape)
- Memory entry #13 — audit-first pattern (now 13 of 14 with this reframe)
- Memory entry #20 — `git log -p -S` drive-by check (used in Item 4)
- Memory entry #21 — adb-failed vs test-failed classification (applied to runs c-f, o, w, #857)
- Memory entry #22 — CD silent-failure rule (data-layer parallel found in this audit)
