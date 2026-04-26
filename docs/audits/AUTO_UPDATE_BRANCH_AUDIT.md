# Auto-update-branch workflow audit

Phase 1 audit for `feat/auto-update-branch` — keep open PRs current with
`main` automatically, using `AUTOFIX_PAT` so the resulting "Update
branch" commit retriggers required-status workflows.

Companion to PR #777 (which fixed the same shape inside `auto-merge.yml`
for PRs that have auto-merge enabled and are receiving fresh pushes).

> **Status:** Phase 1 complete. **Stopping for review before Phase 2
> (workflow implementation).** See §7.

---

## 0. Wrong-premise flag (read first)

The plan's framing of the gap has an inaccuracy worth surfacing:

> *"PR #777's auto-merge.yml update-branch step uses AUTOFIX_PAT
> correctly but only fires when auto-merge is enabled. Drafts +
> work-in-progress PRs without auto-merge don't get updated."*

Tighter constraint than that. The PR #777 step lives inside
`auto-merge.yml`, which triggers on `pull_request` events (`opened,
reopened, ready_for_review, synchronize`) — i.e. when the **PR head SHA
changes**. It does **not** fire when **`main` advances**. So even
auto-merge-enabled PRs sit `BEHIND` on a main push until somebody
re-pushes the PR.

GitHub's *native* auto-merge re-evaluates the PR on every main push and
will run an update-branch under `GITHUB_TOKEN` because the repo has
`allow_update_branch: true` (verified via `gh api repos/akarlin3/
prismTask --jq '.allow_update_branch'`). But that update is
`GITHUB_TOKEN`-driven, so the resulting commit does **not** trigger
required-check workflows — same shape as the bug PR #777 fixed in a
different surface.

**Implication for the new workflow:** the design choice "no-op on
auto-merge-enabled PRs" leaves the *main-advances* case broken for
auto-merge-enabled PRs. The §3 + §4 recommendations below pick the
broader scope: update **all** `BEHIND` PRs on main push regardless of
auto-merge state. The auto-merge.yml path remains responsible for
PR-event-driven updates; the new workflow remains responsible for
main-event-driven updates. Different events, no race.

This is flagged here per the audit-first STOP rule — it is not a hard
blocker, but it changes the recommended design from "narrowly fill the
non-auto-merge gap" to "fill the entire main-advance gap." Confirm or
override before Phase 2.

---

## 1. Existing branch-update surface

Workflow inventory under `.github/workflows/`:

| File | Updates PR branches? | Token | Trigger |
|------|----------------------|-------|---------|
| `auto-merge.yml` | **Yes** (`gh pr update-branch`, line 130) | `AUTOFIX_PAT \|\| GITHUB_TOKEN` | `pull_request: [opened, reopened, ready_for_review, synchronize]` |
| `android-ci.yml` | No (autofix pushes auto-format commits to PR head, doesn't update from main) | `AUTOFIX_PAT \|\| GITHUB_TOKEN` | `pull_request`, `push: main` |
| `android-integration.yml` | No | n/a | `pull_request`, `push`, `schedule` |
| `backend-ci.yml` | No | n/a | `pull_request`, `push` |
| `capture-failure.yml` | No | n/a | `workflow_call` |
| `label-integration-ci.yml` | No | `GITHUB_TOKEN` | label events |
| `release.yml` | No | `GITHUB_TOKEN` | tag push |
| `version-bump-and-distribute.yml` | Pushes to `main`, not PR branches | `GITHUB_TOKEN` | `workflow_run` of auto-merge.yml |
| `web-ci.yml` | No | n/a | `pull_request`, `push` |

**Only `auto-merge.yml` performs PR branch updates.** Nothing else
competes.

### auto-merge.yml's update-branch step in detail (lines 93–137)

Fires only when:

1. **Job filter:** `event_name == 'pull_request' && draft == false &&
   head.repo.full_name == github.repository && sender.type != 'Bot'`
2. **Step filter:** `steps.ci_check.outputs.has_ci == 'true'` (≥1
   non-Auto-merge check run posted on the PR head SHA)
3. **Inner case:** `mergeStateStatus == 'BEHIND'`

Token chosen via `${{ secrets.AUTOFIX_PAT || secrets.GITHUB_TOKEN }}`.
The comment at lines 100–117 documents the rationale (GITHUB_TOKEN-CI
bug observed on PRs #772 + #774) and warns that AUTOFIX_PAT must have
`Pull Requests: Write` scope.

### Coverage gaps the new workflow fills

| Scenario | Covered by auto-merge.yml? | Covered by new workflow? |
|----------|---------------------------|--------------------------|
| PR head re-pushed → auto-merge on → `BEHIND` | Yes | n/a (out of scope) |
| PR head re-pushed → draft | No (`draft == false` filter) | Yes |
| PR head re-pushed → sender is bot | No (intentional — prevents autofix loops) | No (preserve that intent) |
| **`main` advances → any open PR `BEHIND`** | **No (no event fires)** | **Yes — the headline fix** |
| `main` advances → many open PRs `BEHIND` (fan-out batches like Phase D #780–#786) | No | Yes |

The "main advances" rows are the high-value gap, and they include
auto-merge-enabled PRs.

---

## 2. AUTOFIX_PAT scope verification

`AUTOFIX_PAT` is present in repo secrets (verified via `gh api
repos/akarlin3/prismTask/actions/secrets --jq '.secrets[].name'`).
Memory entry #21 records its scope as **Pull Requests: Write +
Contents: Read+Write**, set during PR #777 to fix PRs #772 + #774.

`gh pr update-branch` calls `PUT
/repos/{owner}/{repo}/pulls/{n}/update-branch`. GitHub REST docs
require:

- **Permission:** `Pull requests (write)` on the repository.
- **Auth scope:** must be able to update the head branch — same-repo
  only (fork PRs filtered out by `head.repo.full_name == repository`).

The PAT scope per memory entry #21 is **sufficient**. No PAT-scope
expansion needed.

**Caveat for smoke test (§6.2):** the auto-merge.yml comment at lines
113–117 warns that if AUTOFIX_PAT was originally provisioned with `Pull
Requests: Read` only (its pre-PR-#777 autofix-only scope), update-branch
will 403. Memory entry #21 confirms the upgrade to Read+Write was
applied. The smoke test must verify this end-to-end — if the probe PR's
update-branch call 403s, that's a **STOP** and we expand the PAT scope
(separate decision).

No premise here is shaky enough to block Phase 2 on its own; flagging
for the smoke step.

---

## 3. Trigger design

**Recommend 3b — `push: branches: [main]`.**

| Option | Pros | Cons |
|--------|------|------|
| 3a `pull_request: [synchronize, opened, reopened]` | Already the auto-merge.yml path | Doesn't fire when *main* advances → doesn't solve the actual gap |
| **3b `push: branches: [main]`** | Fires exactly when PRs go BEHIND. ~1 run per merged PR; cheap. Semantics match the gap precisely. | Slight runner-minute cost (one workflow run + one API call per open PR per main push). Trivial at this repo's cadence. |
| 3c cron (e.g. `*/30 * * * *`) | Cheapest if API rate-limit concerns existed | Up to 30-min latency; runs even on idle days |

Pick 3b. Optionally also include `pull_request: types:
[ready_for_review]` so that flipping a draft → ready re-evaluates
`BEHIND` once (handles the case where main advanced while the PR was
drafty and never received a ready-for-review event).

Don't add the `synchronize` trigger — that's auto-merge.yml's territory
and would double-fire.

---

## 4. Opt-out mechanism

**Recommend 4a — label-based opt-out (`no-auto-update`).**

| Option | Pros | Cons |
|--------|------|------|
| **4a label `no-auto-update`** | Explicit, visible in PR UI, reversible by removing the label, trivially documentable | Requires creating the label once |
| 4b draft + opt-in label | More principled if drafts are long-running | Most drafts in this repo are short-lived iteration branches → opt-in friction would skip work that benefits from auto-update |
| 4c path-based (`wip/*`, `experiment/*`) | Implicit | Brittle, hard to discover, doesn't compose with topic-style branch naming |

The new workflow:

- Creates the `no-auto-update` label on first run if missing (idempotent
  `gh label create … --force` or guarded check).
- Skips PRs carrying the label.
- **Does** auto-update drafts by default (counter to plan's option 4b).
  Rationale: this repo's drafts are short-lived iteration branches; the
  long-running design-branch case is rare and the label covers it.

Document the label name in `CHANGELOG.md` Unreleased entry and (as a
follow-up suggestion) in CONTRIBUTING.md or the workflow file's header
comment. The header-comment route avoids an extra file change in the PR
and keeps the rule next to its enforcement.

---

## 5. Failure-mode handling

### 5.1 Merge conflict during update-branch

`gh pr update-branch` returns non-zero (and `mergeStateStatus` flips to
`DIRTY`) when the resulting merge would conflict. **Loud-fail** with a
PR comment recommending manual rebase. Do **not** silently leave the
PR `BEHIND`.

Comment template (kept short — no signature/branding):

```
Auto-update-branch hit a merge conflict against `main` and could not
update this branch. Please rebase or merge `main` manually. Re-running
the workflow will not help until the conflict is resolved.
(mergeStateStatus=DIRTY)
```

Workflow step exits non-zero so the run shows red, but only for the
affected PR — the loop should `continue` so other open PRs still get
their updates.

### 5.2 AUTOFIX_PAT expiration

`gh pr update-branch` returns 401 if the PAT is expired or revoked.
**Loud-fail** with a workflow log error and a PR comment on the first
affected PR per run:

```
auto-update-branch could not authenticate with AUTOFIX_PAT (HTTP 401).
The PAT may have expired or been revoked. Auto-update is disabled
until the PAT is rotated.
```

Memory entry #21 carries the calendar-reminder concern. This loud-fail
surface makes the next missed update self-evident rather than silent.

### 5.3 Force-push race

If a PR is force-pushed between enumeration and the update-branch call,
the endpoint either succeeds against the new SHA (fine — branch is
fresh) or returns 422 (fine — branch is now up-to-date or moved). No
special handling required.

### 5.4 PR closed/merged between enumeration and update

`gh pr update-branch` returns 404 or 422. Treat as benign skip + log
line; do **not** comment or fail the run.

---

## 6. Proposed PR shape

### 6.1 Files touched

- `.github/workflows/auto-update-branch.yml` — new, ~70–100 LOC
- `docs/audits/AUTO_UPDATE_BRANCH_AUDIT.md` — this file (written first)
- `CHANGELOG.md` — one-line `### Added` entry under `## Unreleased`,
  trailing newline preserved (PR #761 mitigation)

Total PR: ~80–120 LOC. Single squash merge.

### 6.2 Smoke verification procedure (mandatory)

After Phase 2 lands on `main`:

1. Open a probe PR with a trivial change (e.g. one-line README edit on
   a `chore/auto-update-probe` branch). Confirm it goes green and
   shows `mergeStateStatus=CLEAN`.
2. Push a commit to `main` — e.g. land another small PR ahead of the
   probe. The probe should now show `mergeStateStatus=BEHIND`.
3. Watch `gh run list --workflow=auto-update-branch.yml --limit=5`.
   Expect a run within ~60s of the main push.
4. Confirm the workflow updated the probe PR's branch (head SHA on the
   PR moves; an "Update branch" merge commit appears in the PR
   timeline).
5. **Critical check:** confirm the four required-status checks
   (`lint-and-test`, `connected-tests`, `test`, `web-lint-and-test`)
   actually **fire** on the new commit. Look for fresh runs against the
   new head SHA, not stale results.
6. If downstream checks stay stale → **STOP**. AUTOFIX_PAT scope or
   trigger semantics are wrong; debug before merging the probe PR or
   declaring success. Debug paths:
   - Re-verify `AUTOFIX_PAT` scope is `Pull Requests: Write +
     Contents: Read+Write` via Settings → Developer settings → PATs.
   - Check the resulting commit's `committer` field — it should be the
     PAT owner, not `github-actions[bot]`. If it's the bot, the token
     fell through to `GITHUB_TOKEN`.

Document the smoke result in the Phase 2 PR description.

### 6.3 First-run race

The auto-update-branch workflow's own first run will fire on the merge
commit of its own PR. At that point the PR is already merged, so no
open PRs reference it. The workflow walks open PRs and updates any
`BEHIND` ones — same as any other main push. Benign.

If there are open PRs at merge time that are already `BEHIND`, the
first run will update them. That's the intended behavior, but worth
calling out so the PR author isn't surprised by extra workflow runs
right after merge.

---

## 7. Phase 1 deliverable + STOP

This audit doc is the Phase 1 deliverable.

**Awaiting confirmation on:**

1. **Wrong-premise call (§0):** confirm the new workflow should update
   *all* `BEHIND` PRs on main push (including auto-merge-enabled ones),
   rather than no-op on auto-merge-enabled PRs as the plan suggested.
   Recommend: yes, broaden scope.
2. **Trigger (§3):** confirm 3b (`push: main`) + optional
   `pull_request: ready_for_review`. Recommend: yes.
3. **Opt-out (§4):** confirm 4a (`no-auto-update` label) and that
   drafts are auto-updated by default. Recommend: yes.
4. **Failure-mode comment templates (§5.1, §5.2):** confirm tone /
   wording acceptable before they show up on real PRs.
5. **Smoke gating (§6.2):** confirm the workflow ships with auto-merge
   on, but the smoke verification is mandatory before declaring the
   workflow trusted (i.e. observed once before relying on it for the
   next BEHIND PR).

Once confirmed, Phase 2 begins on this same branch
(`feat/auto-update-branch`) — no new worktree.

---

## 8. Phase 3 — post-merge summary (appended after PRs landed)

### 8.1 PR shipping log

| PR | Branch | Merge SHA | Merged | Notes |
|----|--------|-----------|--------|-------|
| #792 | `feat/auto-update-branch` | `2d84ee28` | 2026-04-26 04:18:02Z | Phase 1 audit doc only — auto-merged unexpectedly while Phase 2 was being written, see §8.2 |
| #793 | `feat/auto-update-branch-workflow` | `23ec2c4d` | 2026-04-26 04:30:36Z | Phase 2 — workflow + CHANGELOG entry, on a fresh branch since #792's branch was auto-deleted |
| #794 | `fix/version-bump-trigger` | `ea03b751` | 2026-04-26 04:43:24Z | Adjacent unrelated regression surfaced during Phase 3 prep — see §8.4 |

### 8.2 Final scope deviations vs the plan

- **Phase 1 + Phase 2 wound up across two PRs instead of one.** PR #792
  (originally opened as a draft for Phase 1 review only) auto-flipped to
  ready and got auto-merged by `auto-merge.yml` while Phase 2 was being
  written in the same worktree. Phase 2 then shipped on a fresh branch
  (`feat/auto-update-branch-workflow`) cherry-picked off the post-Phase-1
  main. Functionally equivalent to the planned single-PR shape but the
  audit doc and the workflow file are recorded as separate squash
  commits on main. Doesn't affect the workflow's behavior; flagged here
  so the git log makes sense.
- **Workflow file landed at ~250 LOC**, vs §6.1's estimate of 70–100.
  The extra came from defensive code we agreed on in §0/§3/§5: the
  `resolve_state` retry on `mergeStateStatus=UNKNOWN`, the
  `has_marker` dedup function for conflict + auth-failure comments,
  pre-built body strings (forced by a YAML literal-block / bash heredoc
  collision discovered during implementation — original draft used
  `cat <<EOF` heredocs that broke the YAML parser; fix was to build the
  comment bodies as bash variables before the loop). Comments + the
  job-level `if:` filter for fork PRs also added line count.
- **Broader scope per §0 wrong-premise flag confirmed by user.** The
  workflow updates ALL `BEHIND` PRs on `push: main` regardless of
  auto-merge state, not just non-auto-merge ones.
- **`workflow_run` trigger on "Version Bump & Firebase Distribute"
  added to cover the `[skip ci]` versionCode-bump push to main** that
  the `push:` trigger can't see. Not in the original §3 design; surfaced
  during implementation review.
- **Job-level `if:` extended to skip fork-PR `pull_request` events**
  (PAT can't push to forks; native filter inside the loop is belt-and-
  suspenders).

### 8.3 Smoke verification result

| Check | Result | Evidence |
|-------|--------|----------|
| Workflow runs end-to-end via `workflow_dispatch` | **Pass** | Run [#24948334254](https://github.com/akarlin3/prismTask/actions/runs/24948334254) — walked open PR #790, saw `mergeStateStatus=BLOCKED` (correctly NOT `BEHIND`), skipped, exited 0. No spurious comments posted. |
| `no-auto-update` label created idempotently | **Pass** | `gh label list` shows the label with color `B60205` and the documented description. |
| YAML parses cleanly | **Pass** | `python -c "yaml.safe_load(...)"` + `bash -n` on each `run:` block. |
| **AUTOFIX_PAT-driven update-branch fires downstream required checks** | **Not directly observed in this run** — see §8.4 | The `BEHIND` code path didn't execute because no PR was `BEHIND` at the moment of dispatch. GitHub's NATIVE auto-update fired on PR #790 BEFORE our workflow could (cae62397, "Merge branch 'main' into feat/pii-disclose-and-gate", committed by `web-flow`, `triggering_actor=akarlin3`). |
| Native auto-update path triggers downstream CI | **Pass** | Required checks (`lint-and-test`, `connected-tests`, `test`, `web-lint-and-test` family) ran on cae62397 with `event=pull_request, actor=akarlin3`. |

### 8.4 Premise refinement (informs design + memory candidates)

The plan's framing — *"GitHub native auto-update uses GITHUB_TOKEN, so
the resulting commit doesn't trigger downstream workflows"* — is at
least partially incorrect for THIS repo. Observed evidence:

When `allow_update_branch: true` + `gh pr merge --auto` is enabled on a
PR, and `main` advances, GitHub's NATIVE auto-update produces a merge
commit on the PR head (committed by `web-flow`, but the
`triggering_actor` is the user who enabled auto-merge). That `actor` is
a real user, and the resulting `synchronize` event fires downstream
workflows normally. The check runs on cae62397 confirm this.

The PR #777 case is genuinely different: it's a WORKFLOW (auto-merge.yml)
calling `gh pr update-branch` under `GITHUB_TOKEN`. The resulting commit
*is* attributed to a workflow, *is* subject to GitHub Actions'
anti-recursion rule, and *does* fail to trigger downstream workflows.
That's why PR #777 switched to `AUTOFIX_PAT`.

Resulting model:

| Update path | Fires downstream workflows? |
|-------------|-----------------------------|
| GitHub native auto-update (user enabled auto-merge) | **Yes** — `triggering_actor` is the user |
| Workflow calling `gh pr update-branch` under `GITHUB_TOKEN` | No — anti-recursion suppression |
| Workflow calling `gh pr update-branch` under `AUTOFIX_PAT` | **Yes** — PAT owner is a real user |
| User clicking "Update branch" in the PR UI | **Yes** — actor is the user |

**Implication for the new workflow's value:** the cases it covers that
GitHub native does NOT are drafts and PRs without auto-merge enabled
that go `BEHIND` when `main` advances. Auto-merge-enabled PRs were
already covered by GitHub native (in this repo's config) — but it
doesn't hurt for the new workflow to fire on them too, since the
update-branch endpoint is idempotent.

### 8.5 Adjacent regression: `version-bump-and-distribute.yml`

While checking which workflows had fired on the merge of #793, I found
that **`Version Bump & Firebase Distribute` had run zero times since
PR #771 (2026-04-25)**. Last `chore: bump to v…` on main was
`5e5404c8` (v1.5.2 build 689), produced by the OLD inline job inside
`auto-merge.yml` before PR #771 split it out.

Root cause: PR #771's split added `branches: [main]` to the
`workflow_run:` trigger. For `workflow_run`, that filter matches the
SOURCE workflow's HEAD branch — but `Auto-merge & Release` runs on
`pull_request` events whose HEAD branch is the PR's feature branch,
never `main`. The filter therefore rejected every Auto-merge completion.
Net effect: every PR merge in the regression window shipped without a
versionCode bump or a Firebase App Distribution upload.

Fix shipped in PR #794 (`ea03b751`): drop the `branches:` filter; the
job-level `if: workflow_run.conclusion == 'success'` already handles
failed-source-run gating. Inline IMPORTANT comment added to the
workflow file documenting the gotcha.

Verification: after #794 merged at 04:43:24Z, the FIRST EVER run of
`version-bump-and-distribute.yml` queued at 04:43:53Z on `ea03b751`.
Because #794 itself doesn't touch `app/`, the run skipped the bump +
Firebase steps (by design — `Detect Android-related changes` step). The
NEXT non-trivial PR merge (touching `app/`, `gradle*`, etc.) will
produce both a new `chore: bump to v…` commit on main and a Firebase
App Distribution release.

### 8.6 Memory entry candidates (flagged, not auto-saved)

For Daisy to review and decide whether to persist:

1. **`feedback_workflow_run_branches_filter_gotcha.md`** — the
   `branches:` filter on a `workflow_run` trigger matches the SOURCE
   workflow's HEAD branch, not the PR base or merge target. Combined
   with a source workflow that runs on `pull_request`, the filter
   silently rejects every event. Reason: cost real-world Firebase
   regression for ~24h before detection.
2. **`feedback_native_auto_update_does_trigger_ci.md`** — refines the
   premise that GITHUB_TOKEN-driven update-branch suppresses downstream
   CI. Specifically: GitHub's NATIVE auto-update path (triggered by user
   enabling auto-merge + main advancing) DOES fire downstream workflows
   because `triggering_actor` is the user. Only WORKFLOW-driven
   `gh pr update-branch` under `GITHUB_TOKEN` is suppressed. PR #777's
   fix and the PR #793 design rationale both relate but address the
   workflow-driven path; the native path was already working.
3. **`reference_no_auto_update_label.md`** — opt-out label name is
   `no-auto-update`, color `B60205`, description "Skip
   auto-update-branch on this PR". Created automatically by the
   workflow's first step. Apply to draft PRs that should NOT be
   auto-rebased onto main (e.g. long-running design branches).
4. **`reference_workflow_dispatch_smoke_for_auto_update_branch.md`** —
   `gh workflow run auto-update-branch.yml` exercises the enumeration +
   state logic without needing a real `BEHIND` PR. Use when validating
   future edits to the workflow.

Items 1 + 2 are the load-bearing ones for future CI work. Items 3 + 4
are reference-only.
