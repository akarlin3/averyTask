# PrismTask

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-orange.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.115-009688.svg)](https://fastapi.tiangolo.com)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791.svg)](https://postgresql.org)
[![Android CI](https://github.com/akarlin3/prismTask/actions/workflows/android-ci.yml/badge.svg)](https://github.com/akarlin3/prismTask/actions/workflows/android-ci.yml)
[![Backend CI](https://github.com/akarlin3/prismTask/actions/workflows/ci.yml/badge.svg)](https://github.com/akarlin3/prismTask/actions/workflows/ci.yml)

A native Android task manager with a Python API backend featuring AI-powered natural language processing, voice input, full accessibility support, deep customization, productivity analytics, and first-class integrations with Gmail, Slack, and Google Calendar. Built with Kotlin/Jetpack Compose for the client and FastAPI/PostgreSQL for the server.

## Download

<!-- TODO: Replace with actual Play Store link once published -->
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=com.averycorp.prismtask)

## Free vs Pro vs Premium

PrismTask v1.3.0 introduces a three-tier pricing model.

| Feature | Free | Pro ($3.99/mo) | Premium ($7.99/mo) |
|---------|------|----------------|--------------------|
| Task management, projects, tags, subtasks | Yes | Yes | Yes |
| Habit tracking with streaks & analytics | Yes | Yes | Yes |
| Task templates (built-in + local) | Yes | Yes | Yes |
| Google Calendar sync | Yes | Yes | Yes |
| All 7 home screen widgets with per-instance config | Yes | Yes | Yes |
| Voice input, accessibility, customization | Yes | Yes | Yes |
| NLP quick-add (local parser) | Yes | Yes | Yes |
| All views (Today, Tasks, Week, Month, Timeline, Eisenhower) | Yes | Yes | Yes |
| Drag-to-reorder, bulk edit, quick reschedule | Yes | Yes | Yes |
| JSON/CSV export/import | Yes | Yes | Yes |
| Cloud sync across devices (tasks + templates) | -- | Yes | Yes |
| AI Eisenhower auto-categorization | -- | Yes | Yes |
| AI Smart Pomodoro focus planning | -- | Yes | Yes |
| Basic analytics & time tracking | -- | Yes | Yes |
| Smart defaults engine | -- | Yes | Yes |
| Notification profiles & quiet hours | -- | Yes | Yes |
| Unlimited saved filters, custom templates | -- | Yes | Yes |
| AI daily briefing + weekly planner + time blocking | -- | -- | Yes |
| Collaboration (shared projects) | -- | -- | Yes |
| Integrations: Gmail, Slack, Webhooks/Zapier | -- | -- | Yes |
| Full analytics dashboard (burndown, heatmap, correlation) | -- | -- | Yes |
| Google Drive backup/restore | -- | -- | Yes |

Debug builds expose a tier override in Settings for local development.

## Screenshots

<p align="center">
  <img src="screenshots/today-view.jpg" width="250" alt="Today View" />
  &nbsp;&nbsp;
  <img src="screenshots/habits-view.jpg" width="250" alt="Habits" />
  &nbsp;&nbsp;
  <img src="screenshots/timer-view.jpg" width="250" alt="Focus Timer" />
</p>

## Features

### Task Management
- Create, edit, and delete tasks with titles, descriptions, due dates, times, and priority levels (None/Low/Medium/High/Urgent)
- Organize tasks into projects with custom colors and emoji icons
- Nested subtasks with completion tracking and cascade delete
- Flexible tagging system (many-to-many) with color-coded tags
- Notes and file/link attachments per task
- Swipe-to-complete and swipe-to-delete gestures with undo snackbars
- Multi-select mode with batch complete, delete, and move operations
- Bulk edit: batch change priority, due date, tags, and move to project for multi-selected tasks
- Drag-to-reorder tasks in "Custom" sort mode with persistent order
- Quick reschedule via long-press popup (Today, Tomorrow, Next Week, Pick Date)
- Duplicate task from context menu or editor with optional subtask copying
- Move tasks between projects via long-press menu and drag in grouped-by-project view
- Per-screen sort preference memory (DataStore-persisted)
- Urgency scoring (0-1) based on due date proximity, priority, age, and subtask progress

### Recurrence
- Daily, weekly (multi-day), monthly, and yearly patterns with configurable intervals
- Skip-weekends option for daily recurrence
- End conditions: max occurrences or end date
- Completing a recurring task auto-creates the next occurrence

### Reminders and Notifications
- Per-task reminders with configurable offset before due date
- AlarmManager scheduling with BroadcastReceiver delivery
- "Complete" action directly from the notification
- Reminders re-scheduled after device reboot

### NLP Quick-Add
- Natural language task creation from a single text input
- Extracts dates ("today", "tomorrow", "next Monday", "in 3 days", "Jan 15", "5/20", "2026-05-15"), times ("at 3pm", "at 15:00", "at noon"), tags (#work), projects (@home), priority (!urgent, !!), and recurrence hints (daily, weekly)
- Parsed results resolved against existing tags and projects with unmatched item feedback
- Smart suggestions for tags and projects based on usage keyword matching

### Views
- **Today** -- Focus screen with compact progress header bar, collapsible sections (expand/collapse state persisted), overdue section with red urgency tint, horizontal habit chips with tap-to-complete, "All Caught Up" celebration state, "Plan for Today" bottom sheet with inline quick-add, search filter, batch planning, and sort options
- **Task Editor** -- Full-screen modal bottom sheet with three-tab layout (Details / Schedule / Organize), title and priority always visible in header, subtask drag-to-reorder, swipe-to-complete/delete gestures, unsaved changes detection
- **Task List** -- Grouped or flat list with sorting (priority, date, urgency, alphabetical, custom), advanced filtering (tags, priorities, projects, date range), search with highlighted results
- **Week View** -- 7-day column layout with task cards per day and week navigation
- **Month View** -- Calendar grid with density dots and day detail panel
- **Timeline** -- Daily scheduled view with time blocks, duration management, and current-time indicator
- **Projects** -- Project list with task counts and full CRUD
- **Habits** -- Habit list with streak badges, weekly progress dots, and circular completion checkboxes
- **Archive** -- Archived tasks with search and restore/permanent-delete options

### Habit Tracking
- Create habits with name, description, icon (16 emoji options), color (12 options), and category
- Daily or weekly frequency with configurable target count and active day selection
- Streak engine: current streak, longest streak, completion rates (7/30/90 day), best/worst day analysis
- Analytics screen: GitHub-style contribution grid (12 weeks), weekly trend line chart, day-of-week bar chart, stat cards
- Habits integrated into Today screen with combined progress ring
- Optional daily reminder and auto-create-task features
- Weekly habit summary notification via WorkManager (Sunday 7PM)

### Task Templates
- Create reusable task blueprints with pre-filled title, description, priority, project, tags, subtasks, recurrence, and duration
- 6 built-in templates (Morning Routine, Weekly Review, Meeting Prep, Grocery Run, Assignment, Deep Clean)
- Quick-use from template list or NLP shortcut (`/templatename`)
- Save any existing task as a template from editor overflow menu
- Template categories, search, and usage tracking (count and last used date)

### AI Productivity (Pro)
- **Eisenhower Matrix** -- AI auto-categorization of tasks into Urgent/Important quadrants via Claude Haiku
- **Smart Pomodoro** -- AI-planned focus sessions based on deadlines, priorities, and work style preferences (Deep Work, Quick Wins, Balanced, Deadline-Driven)
- **Backend NLP** -- Enhanced natural language parsing via backend Claude Haiku API (free users use local regex parser)

### Cloud Sync
- Firebase Authentication with Google Sign-In via Credential Manager
- Firestore bidirectional sync for tasks, projects, tags, habits, and habit completions
- Offline queue with pending action tracking and retry logic
- Real-time snapshot listeners for cross-device updates

### Voice Input & Accessibility
- **Speech-to-Task** -- Dictate tasks via Android SpeechRecognizer; NLP parser extracts dates/priorities/tags from voice
- **Voice Commands** -- Hands-free completion, rescheduling, navigation via `VoiceCommandParser`
- **Hands-Free Mode** -- Continuous-listening mode for a spoken task session
- **Text-to-Speech** -- Readback of the Today list, individual tasks, and daily briefings
- **TalkBack/Screen Reader** -- Semantic labels throughout all screens
- **Font Scaling & High Contrast** -- Respects system accessibility settings and offers a built-in high-contrast mode
- **Keyboard Navigation** -- Full focus traversal for all interactive elements
- **Reduced Motion** -- Opt-out of decorative animations

### Customization & Personalization
- Centralized `UserPreferencesDataStore` for all customization settings
- Configurable swipe actions (7 options per direction) and flagged-task filter
- Custom accent color picker (hex input + recent colors), compact mode, card corner radius
- Toggleable task card metadata fields (12 fields) + minimal card style
- User-configurable urgency scoring weights with live preview
- Configurable task defaults + smart defaults engine
- Custom NLP shortcuts/aliases with quick-add suggestion chips
- Saved filter presets with quick-apply chips
- Reorderable, toggleable long-press context menu
- Customizable Today-screen section order and visibility
- Advanced recurrence: weekday, biweekly, custom month days, after-completion
- Notification profiles with multi-reminder bundles, escalation, quiet hours, daily digest
- Project and habit template systems with built-in templates

### Analytics & Time Tracking (Pro)
- Productivity dashboard with daily/weekly/monthly views
- Task completion burndown charts
- Habit-productivity correlation analysis
- Per-task time tracking with start/stop logging
- Heatmap visualization for activity patterns
- Time-tracked badge on task cards (configurable)

### Integrations (Premium)
- **Gmail** -- Auto-create tasks from starred emails
- **Slack** -- Create tasks from Slack messages
- **Google Calendar prep tasks** -- Auto-generate prep tasks before meetings
- **Webhook/Zapier endpoint** -- Bring external automations into PrismTask
- **Suggestion inbox** -- Review and accept/reject auto-created tasks before they land in the task list

### Home Screen Widgets (7 total)
- **Today Widget** -- Combined progress count + top task names + habit completion count
- **Habit Streak Widget** -- Up to 6 habits with icons and completion status
- **Quick-Add Widget** -- Minimal tap-to-launch bar for fast task creation
- **Calendar Widget** -- Upcoming events with PrismTask task overlays
- **Productivity Widget** -- At-a-glance burndown and completion stats
- **Timer Widget** -- Start, pause, and check the Pomodoro timer from the home screen
- **Upcoming Widget** -- Next N tasks across today and tomorrow
- Per-instance configuration activities with background opacity and section toggles
- Built with Glance for Compose

### Data Management
- JSON export (full backup: tasks with tag/project names, projects, tags)
- CSV export (tasks only with proper escaping)
- JSON import with merge (skip duplicates) or replace (delete-all-first) modes
- Customizable dashboard section ordering and visibility

### Theming
- Material 3 with dynamic colors on Android 12+
- Light, Dark, and System theme modes with 12 accent color options
- Edge-to-edge display

## Architecture Overview

```
┌─────────────────────────┐         ┌──────────────────────────┐
│   Android App (Kotlin)  │  HTTPS  │   FastAPI Backend         │
│   Jetpack Compose        │◄───────►│   Python 3.12            │
│   Room + Firebase        │         │   SQLAlchemy + Alembic   │
│   Glance Widgets         │         │   JWT Auth               │
└─────────────────────────┘         └──────────┬───────────────┘
                                               │
                                    ┌──────────▼───────────────┐
                                    │   PostgreSQL 16          │
                                    └──────────────────────────┘
                                               │
                                    ┌──────────▼───────────────┐
                                    │   Claude Haiku (NLP)     │
                                    └──────────────────────────┘
```

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.2.10 |
| UI | Jetpack Compose + Material 3 | BOM 2024.12.01 |
| Navigation | Jetpack Navigation Compose | 2.8.5 |
| DI | Hilt (Dagger) + KSP | 2.59.2 |
| Database | Room + KSP | 2.8.4 |
| Preferences | DataStore | 1.1.1 |
| Background | WorkManager | 2.9.1 |
| Cloud | Firebase Auth + Firestore + Storage | BOM 33.6.0 |
| Auth | Credential Manager + Google Identity | 1.3.0 / 1.1.1 |
| Drag-to-Reorder | sh.calvin.reorderable | 2.4.3 |
| Widgets | Glance for Compose | 1.1.0 |
| Billing | Google Play Billing | 7.1.1 |
| Serialization | Gson | 2.11.0 |
| Async | Kotlin Coroutines | 1.9.0 |
| Testing | JUnit, coroutines-test, Turbine, MockK, Robolectric, Hilt Testing | 4.13.2 / 1.9.0 / 1.1.0 / 1.13.13 / 4.13 / 2.59.2 |
| Build | Gradle (Kotlin DSL) | 8.13 |

**Target:** Android 8.0+ (API 26) through Android 15 (API 35)

## Backend API

The FastAPI backend provides REST endpoints for cross-device sync, AI-powered task parsing, and self-updating.

**Live API docs:** https://averytask-production.up.railway.app/docs

| Layer | Technology | Version |
|-------|-----------|---------|
| Framework | FastAPI | 0.115.6 |
| Database | PostgreSQL + SQLAlchemy | 16 / 2.0 |
| Migrations | Alembic | - |
| Auth | JWT (python-jose) + Firebase token bridge | - |
| NLP | Anthropic Claude Haiku API | - |
| Deployment | Railway + Docker | - |
| CI | GitHub Actions | - |

### Key Endpoints
- `POST /api/v1/auth/register` — Create account
- `POST /api/v1/auth/login` — JWT authentication
- `POST /api/v1/tasks/parse` — AI-powered natural language task parsing
- `GET /api/v1/dashboard/summary` — Dashboard statistics
- `POST /api/v1/sync/push` / `GET /api/v1/sync/pull` — Cross-device sync
- `GET /api/v1/app/version` — Self-update check

## Requirements

- Android Studio Ladybug (2024.2.1) or later
- JDK 17
- Android SDK 35
- Device or emulator running Android 8.0+ (API 26)

## Getting Started

### Android

```bash
# Clone the repository
git clone https://github.com/akarlin3/prismTask.git
cd prismTask

# Build debug APK
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug
```

Replace `app/google-services.json` with your Firebase project configuration for cloud sync and authentication features. The app works fully offline without Firebase.

### Backend (optional — app works fully offline)

```bash
cd backend
cp .env.example .env  # Edit with your settings
docker compose up -d
# API docs at http://localhost:8000/docs
```

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (R8 minification + resource shrinking)
./gradlew assembleRelease

# Run unit tests (~490 tests)
./gradlew testDebugUnitTest

# Run instrumentation tests (requires device/emulator)
./gradlew connectedDebugAndroidTest

# Clean
./gradlew clean
```

## Architecture

Single-activity MVVM with Hilt dependency injection:

- **UI layer**: Jetpack Compose screens with Material 3, connected to ViewModels via `hiltViewModel()`
- **ViewModel layer**: Exposes `StateFlow` from repositories via `stateIn(WhileSubscribed)`, handles user actions in `viewModelScope`
- **Repository layer**: Single source of truth wrapping Room DAOs with business logic (recurrence completion, date grouping, streak calculation, duplicate prevention)
- **Data layer**: Room database with reactive `Flow` queries, Firebase Firestore + FastAPI backend for cloud sync, DataStore for preferences (centralized via `UserPreferencesDataStore`)
- **Domain layer**: Pure use-case objects -- RecurrenceEngine, NaturalLanguageParser, ParsedTaskResolver, UrgencyScorer (with configurable weights), StreakCalculator, SuggestionEngine, SmartDefaultsEngine, NlpShortcutExpander, QuietHoursDeferrer, VoiceInputManager, VoiceCommandParser, TextToSpeechManager, ProFeatureGate (three-tier)
- **Notifications**: AlarmManager + BroadcastReceiver for task reminders, WorkManager for weekly habit summaries
- **Widgets**: Glance for Compose with direct Room queries via WidgetDataProvider

```
UI (Compose Screens + ViewModels)
        |
        v
  Repositories
        |
   +---------+---------+
   |         |         |
Room DAOs  SyncService  DataStore
   |         |
SQLite    Firestore
```

## Test Coverage

**~654 tests total** across the repo:

| Suite | Count | Scope |
|-------|-------|-------|
| Unit tests (`app/src/test/`) | ~490 | Parsers, recurrence engine, urgency scorer + weights, streak calculator, sync mapper, repositories (Task, Habit, Project, Tag, Template, Coaching, ReminderProfile, SavedFilter), use cases (ParsedTaskResolver, ChecklistParser, TodoListParser, VoiceCommandParser, SmartDefaults, NlpShortcutExpander, QuietHoursDeferrer, AdvancedRecurrence, TimeBlock, WeeklyPlanner, DailyBriefing, Eisenhower, SmartPomodoro, BookableHabit), data import/export (DataImporter, DataExporter, EntityJsonMerger), DataStore preferences, notification/reminder scheduling, ViewModels (Today, AddEditTask, TaskList, HabitList, Eisenhower, SmartPomodoro, Onboarding), widget data + config defaults, accessibility, theming, calendar manager |
| Instrumentation tests (`app/src/androidTest/`) | ~100 | Room DAO tests (Task, Project, Habit, Tag), recurrence integration, and smoke suites for navigation, QoL, editor, templates, Today, data export/import, views, search/archive, tags/projects, settings, recurrence, multi-select, habits, and offline edge cases |
| Backend tests (`backend/tests/`) | ~60+ | Router tests (dashboard, export, search, app_update, projects), service tests (recurrence, urgency, NLP edge cases), and integration workflows / stress tests |

## Accessibility

PrismTask is built to be usable by everyone. All screens are fully navigable with TalkBack and other screen readers, interactive elements expose semantic labels and roles, and text size scales with the system font-scale setting. A built-in high-contrast mode and a reduced-motion toggle let you opt out of decorative animations. Hardware-keyboard focus traversal is supported across every screen, and the Voice Input and Text-to-Speech features provide hands-free task capture and readback. If you hit an accessibility gap, please open an issue — it will be treated as a bug.

## Database

Room database with full task, habit, template, self-care, leisure, schoolwork, calendar-sync, sync-metadata, reminder-profile, saved-filter, NLP-shortcut, project-template, habit-template, and attachment entities. Migrations are grouped in `data/local/database/Migrations.kt`. Representative tables:

| Table | Purpose |
|-------|---------|
| `tasks` | Core task data with FKs to projects and parent tasks, sortOrder column |
| `projects` | Project grouping with color and icon |
| `tags` | Tag definitions with color |
| `task_tags` | Many-to-many junction (task-tag) |
| `attachments` | File and link attachments per task |
| `usage_logs` | Keyword-entity frequency for smart suggestions |
| `sync_metadata` | Local-to-cloud ID mapping with pending action queue |
| `calendar_sync` | Task-to-Google Calendar event mapping |
| `habits` | Habit definitions: frequency, color, icon, category |
| `habit_completions` | Per-day habit completion records |
| `leisure_logs` | Leisure activity tracking |
| `courses` | Schoolwork course definitions |
| `assignments` | Course assignment tracking |
| `study_logs` | Study session logs |
| `course_completions` | Course completion records |
| `self_care_logs` | Self-care activity logs |
| `self_care_steps` | Self-care routine step tracking |
| `task_templates` | Reusable task blueprints with category, icon, and usage stats |
| `project_templates` | Reusable project blueprints |
| `habit_templates` | Reusable habit blueprints |
| `habit_logs` | Bookable habit logs with booking state |
| `nlp_shortcuts` | User-defined NLP aliases |
| `saved_filters` | Saved filter presets for quick-apply |
| `reminder_profiles` | Multi-reminder notification bundles |

## Project Structure

```
prismTask/
├── app/                                    # Android app (Kotlin / Jetpack Compose)
│   └── src/main/java/com/averycorp/prismtask/
│       ├── MainActivity.kt                 # Single-activity entry point
│       ├── PrismTaskApplication.kt         # @HiltAndroidApp
│       ├── data/
│       │   ├── billing/                    # Google Play Billing (three-tier)
│       │   ├── calendar/                   # Device calendar manager + preferences
│       │   ├── export/                     # JSON/CSV export, import, entity merger
│       │   ├── local/                      # Room entities, DAOs, grouped migrations
│       │   ├── preferences/                # DataStore (+ centralized UserPreferencesDataStore)
│       │   ├── remote/                     # Firebase, Drive, calendar, backend sync (api/, mapper/, sync/)
│       │   ├── repository/                 # All repositories (task, habit, template, saved filter, ...)
│       │   └── seed/                       # Built-in content seeders
│       ├── di/                             # Hilt modules (Database, Billing, ...)
│       ├── domain/
│       │   ├── model/                      # RecurrenceRule, TaskFilter, TodayLayoutResolver, ...
│       │   └── usecase/                    # RecurrenceEngine, NLP parser, UrgencyScorer, voice,
│       │                                     smart defaults, quiet hours, shortcut expander
│       ├── notifications/                  # Reminders, boot, weekly/evening summary, briefings
│       ├── widget/                         # 7 Glance widgets + config datastore
│       ├── workers/                        # WorkManager background workers
│       └── ui/
│           ├── a11y/                       # Accessibility helpers
│           ├── components/                 # Reusable composables (+ settings/)
│           ├── navigation/                 # NavGraph, feature route groups
│           ├── screens/                    # Today, Tasks (+ components/), AddEdit (+ tabs/),
│           │                                 Settings (+ sections/), habits, templates,
│           │                                 leisure, medication, self-care, schoolwork,
│           │                                 Eisenhower, pomodoro, planner, briefing, chat,
│           │                                 coaching, timer, onboarding, ...
│           └── theme/                      # Color, Theme, Type, PriorityColors
└── backend/                                # FastAPI backend (Python 3.12)
    ├── app/                                # FastAPI application
    │   ├── api/                            # REST routers (auth, tasks, sync, dashboard)
    │   ├── core/                           # Config, security, JWT
    │   ├── db/                             # SQLAlchemy models, session
    │   ├── schemas/                        # Pydantic request/response models
    │   └── services/                       # Claude Haiku NLP, sync logic
    ├── alembic/                            # Database migrations
    ├── tests/                              # Pytest suite
    ├── Dockerfile
    └── requirements.txt
```

## Deployment

- **Android:** Firebase App Distribution via GitHub Actions CI
- **Backend:** Railway (Docker) with auto-deploy from main branch
- **Database:** Railway PostgreSQL

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code conventions, and pull request guidelines.

## Security

See [SECURITY.md](SECURITY.md) for security considerations and how to report vulnerabilities.

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).

---

## PrismTask Web Backend + React Native App

In addition to the native Android app, PrismTask includes a full-stack web backend and cross-platform React Native mobile app.

### Why I Built This

I wanted a hierarchical task management system that maps how I actually think about work — career goals broken into projects, projects broken into tasks. Most task apps are flat lists. PrismTask gives me Goal → Project → Task hierarchy with an NLP parser powered by Claude that lets me create tasks from natural language.

### Web Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Backend | FastAPI (Python 3.11+) | Async REST API with auto-docs |
| ORM | SQLAlchemy 2.0 (async) | Models + migrations via Alembic |
| Database | PostgreSQL | Production-grade relational DB |
| Auth | JWT (PyJWT + bcrypt) | Stateless auth with refresh tokens |
| NLP | Claude Haiku (Anthropic API) | Natural language task parsing |
| Mobile | React Native (Expo) | Cross-platform with file-based routing |
| State | Zustand | Lightweight state management |
| CI | GitHub Actions | Automated tests + linting |
| Deploy | Docker + Railway | Containerized deployment |

### Architecture

```
Android/iOS Device (React Native + Expo)
        │ HTTPS
        ▼
FastAPI Server (Railway)
├── Auth (JWT)
├── CRUD Routes (Goals → Projects → Tasks)
├── NLP Parser (Claude Haiku)
└── SQLAlchemy ORM + Alembic
        │
        ▼
    PostgreSQL
```

### Backend API Endpoints

All endpoints under `/api/v1`:

- **Auth**: POST `/auth/register`, `/auth/login`, `/auth/refresh`
- **Goals**: GET/POST `/goals`, GET/PATCH/DELETE `/goals/{id}`
- **Projects**: GET/POST `/goals/{id}/projects`, GET/PATCH/DELETE `/projects/{id}`
- **Tasks**: GET/POST `/projects/{id}/tasks`, GET/PATCH/DELETE `/tasks/{id}`, POST `/tasks/{id}/subtasks`
- **Dashboard**: GET `/tasks/today`, `/tasks/overdue`, `/tasks/upcoming`, `/dashboard/summary`
- **NLP**: POST `/tasks/parse` — natural language → structured task suggestion
- **Search**: GET `/search?q=query` — full-text search across tasks

### Getting Started (Backend)

```bash
# Prerequisites: Docker, Node.js 18+

# Start backend + PostgreSQL
docker compose up -d

# API docs
open http://localhost:8000/docs

# Run tests
docker compose exec backend pytest -v

# Start mobile app
cd mobile && npm install && npx expo start
```

### Environment Variables

Copy `backend/.env.example` to `backend/.env`:

```env
DATABASE_URL=postgresql+asyncpg://averytask:averytask@localhost:5432/averytask
JWT_SECRET_KEY=change-me-in-production
JWT_ALGORITHM=HS256
ANTHROPIC_API_KEY=sk-ant-your-key-here
ENVIRONMENT=dev
```

### Backend Test Coverage

21+ tests covering auth, goals CRUD, tasks/subtasks, depth constraints, and NLP parsing.

## Author

Avery Karlin
