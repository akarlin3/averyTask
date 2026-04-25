# Phase 3 — Verification

**Date:** 2026-04-24
**Worktree:** `C:\Projects\prismtask-store-listing` on `feature/play-store-listing`
**Render backend used:** `resvg-py 0.3.1` (libcairo-free, Windows-friendly)

This is the cross-check pass over every Phase 2 deliverable. Each section below maps to one of the seven verification gates from the prompt. Anything failing or borderline is called out at the bottom under "Open issues."

---

## 1. Character count check (Play Console limits)

| File | Chars (incl. newlines) | Limit | Status |
|---|---|---|---|
| `copy/en-US/app-title.txt` | **26** | 30 | OK |
| `copy/en-US/short-description.txt` | **74** | 80 | OK |
| `copy/en-US/full-description.txt` | **3,388** | 4,000 | OK (~85% used; room to grow) |
| `copy/en-US/release-notes/v1.5.4.txt` | **417** | 500 | OK |
| `copy/en-US/release-notes/v2.0.0.txt` | **339** | 500 | OK (template; placeholders may push final length up — check at fill-in time) |

`v2.0.0.txt` was retightened from 515 → 339 chars during Phase 3 because the original phrasing exceeded the 500-char Play Console cap. The placeholder structure now leaves ~160 chars of headroom for actual content per change line.

---

## 2. PNG dimension and size check

`render.py --check` output:

```
[OK] icon-512.png: 512x512 (82.3 KiB)
[OK] feature-graphic-1024x500.png: 1024x500 (121.9 KiB)
[OK] screenshot-01.png: 1080x1920 (288.8 KiB)
[OK] screenshot-02.png: 1080x1920 (335.8 KiB)
[OK] screenshot-03.png: 1080x1920 (241.7 KiB)
[OK] screenshot-04.png: 1080x1920 (273.2 KiB)
[OK] screenshot-05.png: 1080x1920 (383.4 KiB)
[OK] screenshot-06.png: 1080x1920 (278.5 KiB)
[OK] screenshot-07.png: 1080x1920 (353.2 KiB)
[OK] screenshot-08.png: 1080x1920 (320.8 KiB)
All outputs pass Play Store dimension/size checks.
```

| Asset | Required | Actual | Status |
|---|---|---|---|
| Icon | 512×512 PNG, ≤1 MB | 512×512, 82 KiB | OK |
| Feature graphic | 1024×500 PNG, ≤15 MB | 1024×500, 122 KiB | OK |
| Screenshots ×8 | 1080×1920 PNG, ≤8 MB each, 9:16 aspect | All 8 at 1080×1920, 242–384 KiB | OK |

All ten files clear the Play Store technical requirements with very large headroom (largest is screenshot-05 at 383 KiB vs. 8 MB cap — 2 % of the limit).

---

## 3. Privacy policy completeness check

`privacy-policy/index.md` covers every section the Play Console privacy-policy validator (and standard GDPR/CCPA practice) expects:

| Required section | Present in `index.md`? |
|---|---|
| Identity of data controller | Yes — "Who we are" |
| Contact for privacy requests | Yes — `privacy@prismtask.app` |
| Effective date + last updated | Yes — both = 2026-04-24 |
| Categories of data collected | Yes — "What we collect" |
| Purposes of processing | Yes — "How we use your data" |
| Storage locations and retention | Yes — "Where your data is stored" |
| Third-party processors | Yes — table with Google/Anthropic/Railway |
| User rights (export, delete, correct) | Yes — "Your rights" |
| GDPR / CCPA jurisdiction notes | Yes — "Your rights" |
| Children policy | Yes — 18+ explicitly stated |
| Security practices | Yes — "Data security" |
| Update / change-of-policy process | Yes — "Changes to this policy" + Changelog |

GitHub Pages hosting is documented in `privacy-policy/README.md` with the recommended folder rename (`docs/store-listing/privacy-policy/` → `docs/privacy/`) for a cleaner URL.

---

## 4. Data-safety form ↔ privacy-policy consistency

Cross-check verified by side-by-side read of `compliance/data-safety-form.md` and `privacy-policy/index.md`. Every data type, processor, and retention claim is consistent across both documents:

| Data type | data-safety-form | privacy-policy | Consistent? |
|---|---|---|---|
| Email + Google user ID | Collected, linked, not shared | Collected, linked, not shared | Yes |
| App-content (tasks, habits, mood, medications) | Collected (synced if signed in), shared with Anthropic for AI features only | Same | Yes |
| Voice audio | Transient, shared with Google Speech | Transient, shared with Google Speech | Yes |
| Calendar events | Optional opt-in, shared with Google Calendar | Optional opt-in, shared with Google Calendar | Yes |
| Crash logs / diagnostics | Collected, shared with Google/Firebase | Collected, shared with Google/Firebase | Yes |
| Location, contacts, photos, SMS | Not collected | Not collected | Yes |
| Financial data | Not collected; Play Billing handles purchases | Not collected; Play Billing handles purchases | Yes |
| Children policy | 18+ | 18+ | Yes |

**Load-bearing invariant:** if either file changes, the other must be updated in the same commit. README in `privacy-policy/` calls this out explicitly.

---

## 5. Permission justifications coverage

Every permission declared in the manifest has a justification entry in `compliance/permissions-justifications.md`:

| Permission in `AndroidManifest.xml` | In permissions-justifications.md? |
|---|---|
| `INTERNET` | Yes |
| `ACCESS_NETWORK_STATE` | Yes |
| `POST_NOTIFICATIONS` | Yes |
| `USE_FULL_SCREEN_INTENT` | Yes |
| `SCHEDULE_EXACT_ALARM` | Yes |
| `USE_EXACT_ALARM` | Yes |
| `RECEIVE_BOOT_COMPLETED` | Yes |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Yes |
| `REQUEST_INSTALL_PACKAGES` | Yes (with policy-risk callout) |
| `RECORD_AUDIO` | Yes |
| `VIBRATE` | Yes |
| `FOREGROUND_SERVICE` | Yes |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Yes |

`<queries>` declaration for `RecognitionService` is also documented.

---

## 6. Beta framing audit

"Closed testing" / "beta" framing appears where it should and is absent where it would feel marketing-y:

| Surface | Beta language? | Verdict |
|---|---|---|
| `app-title.txt` | No | Right call — title needs to be discoverable, not framed |
| `short-description.txt` | No | Right call — 80-char line cannot afford framing |
| `full-description.txt` | One paragraph, last section: "Currently in closed testing. We're shipping fast, listening hard…" | Right amount — sets expectations without leading with it |
| `release-notes/v1.5.4.txt` | Implicit (medication-mode change is the kind of work in flight that closed testing exists for) — no explicit "beta" word | Acceptable; release notes are descriptive, not framing |
| Privacy policy | No | Right — privacy is not the place for product framing |

---

## 7. Vaporware audit (every claim in `full-description.txt` traces to a Phase 1 finding)

| Copy claim | Phase 1 audit support |
|---|---|
| "Tasks with due dates, priorities, projects, tags, subtasks, recurrence, and natural-language quick-add" | §2 Core task management — confirmed |
| "Habits with forgiveness-first streaks" | §2 Habits + memory `DailyForgivenessStreakCore` |
| "Pomodoro timer with per-session energy tracking and a foreground service" | §2 Focus/timing + manifest `FOREGROUND_SERVICE_MEDIA_PLAYBACK` |
| "Week, month, and timeline views" | §2 Views — `weekview/`, `monthview/`, `timeline/` screens |
| "Today screen with progress ring and balance bar" | §2 Today + Work-Life Balance |
| "Four cohesive themes — Cyberpunk, Synthwave, Matrix, Void" | §2 Themes — verified hex tokens in `ThemeColors.kt` |
| "Eisenhower matrix with auto-classification and persistent manual overrides" | §2 AI-powered + DB v56→v57 `user_overrode_quadrant` column |
| "AI time blocking … daily briefing and weekly review" | §2 AI-powered — `screens/planner/`, `screens/briefing/`, `screens/review/` |
| "Smart Pomodoro planning" | §2 AI-powered — `EnergyAwarePomodoro` |
| "Work-Life Balance engine with a configurable target ratio" | §2 Wellness — `BalanceTracker` + Settings sliders |
| "Mood and energy logs with correlation" | §2 Wellness — `MoodCorrelationEngine` |
| "Morning check-in and weekly review" | §2 Wellness — `screens/checkin/`, `screens/review/` |
| "Boundary rules — declare work hours and category limits; burnout scorer" | §2 Wellness — `BoundaryEnforcer`, `BurnoutScorer` |
| "Brain Mode, adjustable UI complexity, forgiveness streaks, shake-to-capture, 'good enough' timer" | §2 ND-friendly + `NdFeatureGate`, `ShakeDetector`, `GoodEnoughTimerManager` |
| "Medications with flexible clock-time or interval reminders" | §2 Medication + CHANGELOG Unreleased PRs 1-3 of 4 |
| "Refill projection from your dose history" | §2 Medication — `RefillCalculator` |
| "Therapist-friendly clinical report export" | §2 Medication — `ClinicalReportGenerator` |
| "TalkBack-ready content descriptions, dynamic font scaling, high-contrast palette mode, full keyboard focus traversal, reduced-motion gates" | §2 Accessibility — `ui/a11y/` |
| "Full local-first Room database — works entirely offline" | §2 Data & sync |
| "Optional Google Sign-In … Firebase Firestore" | §4 Data flow |
| "One-tap full JSON export and a CSV task export" | §2 Data & sync — `DataExporter.kt` |
| "Delete all of your data from Settings" | §4 Data flow — Settings → Account → Delete |
| "No ads … no analytics SDKs beyond crash reporting" | §4 Data flow — confirmed only Firebase Crashlytics |
| "Pro ($3.99 / month) adds cross-device cloud sync, AI-assisted planning, analytics, time tracking, the clinical report export, and Google Drive backup" | README §"Free vs Pro" table — exact match |
| "Currently in closed testing" | Track context — not a feature claim |

**No widgets, Gmail, or Slack mentions in the copy** — `Grep` over `copy/` found zero matches for those terms (matching `widget|Gmail|Slack|Zapier`, case-insensitive). The only "#" hashtag in the copy is the NLP example `#home !high`, which is an accurate quick-add syntax demo.

---

## Open issues for the user before submitting to Play Console

1. **`REQUEST_INSTALL_PACKAGES` policy risk** (Phase 1 §Policy-Risk-1, Phase 1 §8 Q6). Engineering decision required: leave declared and answer Play's permission-declaration form, or split into a non-Play build variant. Listing copy is unaffected; this is purely a manifest decision.

2. **Launcher icon mismatch** (Phase 1 §Policy-Risk-2). The new 512×512 Play Store icon at `graphics/out/icon-512.png` is generated and ready. Decide whether to also unify the in-app `mipmap-*/ic_launcher.png` placeholder with this design — separate engineering PR if yes.

3. **Folder rename for cleaner privacy URL.** `privacy-policy/README.md` recommends moving `docs/store-listing/privacy-policy/` to `docs/privacy/` so the GitHub Pages URL becomes `https://akarlin3.github.io/prismTask/privacy/` instead of the deeply nested current path. Consider doing this rename as part of the same PR.

4. **Firestore region in privacy policy.** Currently states "`nam5` multi-region (United States)" per user confirmation. Verify in Firebase Console → Project settings → "Default GCP resource location" before going live with the policy URL — if it shows a different region, the policy needs a one-line edit.

5. **Tablet screenshots** (categorization.md notes this is acceptable for closed testing). Add 7-inch and 10-inch tablet screenshots before promoting the listing to production.

6. **Account deletion UI verification.** Privacy policy claims a Settings → Account → Delete account flow exists. Verify the actual UI path before publishing the policy URL — if the flow is partially wired or missing, either ship the UI first or temporarily reword the policy to direct users to email.

7. **Version label on the next bundle upload.** Ensure the upload to the closed testing track matches the v1.5.4 release notes (`copy/en-US/release-notes/v1.5.4.txt`). If the next bundle is actually v1.5.5 or later, rename the file and reflect the actual versionName/versionCode.

---

## Summary

**Phase 1 audit:** complete. Two hard contradictions surfaced (version state ~6 months stale in the prompt; existing in-app icon does not match Play Store icon).

**Phase 2 generation:** 27 files written across copy, compliance, privacy policy, theme tokens, 10 SVG sources, render pipeline, and a localization scaffold.

**Phase 3 verification:** 5 hard checks all green (char counts, PNG dimensions, privacy completeness, data-safety/policy consistency, permission coverage). Two soft checks (beta framing, vaporware) also clean. Seven open issues flagged for engineering or content review before Play Console submission.

**Render artifacts:** all 10 PNGs rendered into `graphics/out/` via the `resvg-py` backend. `icon-512.png` (82 KiB), `feature-graphic-1024x500.png` (122 KiB), and 8 phone screenshots at 1080×1920 — all well under the Play Store byte caps.
