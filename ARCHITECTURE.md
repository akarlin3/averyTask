# PrismTask — Architecture & Data Model

## 1. System Overview

```
┌─────────────────────────────────────────────────────┐
│                  Android Device                      │
│  ┌───────────────────────────────────────────────┐  │
│  │        Kotlin / Jetpack Compose                │  │
│  │  ┌─────────┐ ┌──────────┐ ┌───────────────┐  │  │
│  │  │ Screens │ │ ViewModels│ │    Room DB    │  │  │
│  │  │ & Nav   │ │ (StateFlow)│ │  (SQLite)    │  │  │
│  │  └─────────┘ └──────────┘ └───────────────┘  │  │
│  │  ┌───────────────────────────────────────────┐│  │
│  │  │  Firebase Auth + Firestore Sync + Hilt DI ││  │
│  │  └───────────────────┬───────────────────────┘│  │
│  └──────────────────────┼────────────────────────┘  │
└─────────────────────────┼───────────────────────────┘
                          │ HTTPS
                          ▼
┌─────────────────────────────────────────────────────┐
│                     Browser                          │
│  ┌───────────────────────────────────────────────┐  │
│  │       React + TypeScript + Vite                │  │
│  │  ┌─────────┐ ┌──────────┐ ┌───────────────┐  │  │
│  │  │ Screens │ │  State   │ │  API Client   │  │  │
│  │  │ (Router)│ │ (Zustand)│ │  (Axios)      │  │  │
│  │  └─────────┘ └──────────┘ └───────┬───────┘  │  │
│  └────────────────────────────────────┼──────────┘  │
└───────────────────────────────────────┼─────────────┘
                                        │ HTTPS
                                        ▼
┌─────────────────────────────────────────────────────┐
│              Railway / Render                        │
│  ┌───────────────────────────────────────────────┐  │
│  │              FastAPI Server                    │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────┐  │  │
│  │  │  Auth    │ │  CRUD    │ │  NLP Parser  │  │  │
│  │  │  (JWT)   │ │  Routes  │ │  (Claude)    │  │  │
│  │  └──────────┘ └────┬─────┘ └──────────────┘  │  │
│  │                     │                          │  │
│  │  ┌──────────────────▼─────────────────────┐   │  │
│  │  │         SQLAlchemy ORM + Alembic       │   │  │
│  │  └──────────────────┬─────────────────────┘   │  │
│  └─────────────────────┼─────────────────────────┘  │
│                        │                             │
│  ┌─────────────────────▼─────────────────────────┐  │
│  │              PostgreSQL                        │  │
│  └────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### Tech Stack Summary

| Layer        | Technology             | Why                                                    |
|--------------|------------------------|--------------------------------------------------------|
| Android      | Kotlin + Jetpack Compose | Native performance, Material 3, offline-first with Room |
| Web          | React 19 + TypeScript + Vite | Fast iteration, shared API, responsive SPA             |
| Web Styling  | TailwindCSS 4          | Utility-first, rapid prototyping, consistent design     |
| Web State    | Zustand 5              | Lightweight, no boilerplate (Redux is overkill for MVP) |
| HTTP Client  | Axios (web) / Retrofit (Android) | Interceptors for auth tokens, clean error handling |
| Backend      | FastAPI (Python 3.11+) | Auto-docs, async, type hints — reinforces Python resume |
| ORM          | SQLAlchemy 2.0         | Industry standard, pairs with Alembic for migrations    |
| Migrations   | Alembic                | Schema versioning — shows production discipline         |
| Database     | PostgreSQL             | Production-grade, free tier on Railway/Render           |
| Auth         | JWT (python-jose)      | Stateless, simple, well-understood                      |
| NLP Feature  | Anthropic API (Haiku)  | Fast, cheap, high-quality parsing                       |
| Deployment   | Docker + Railway       | Simple, free/cheap tier, auto-deploy from GitHub        |
| Web Tests    | Vitest + Playwright    | Unit tests and E2E browser automation                   |
| CI           | GitHub Actions         | Auto-test on push, free for public repos                |

---

## 2. Data Model

### Entity Relationship Diagram

```
┌──────────┐
│   User   │
│──────────│
│ id (PK)  │
│ email    │
│ password │
│ name     │
│ created  │
└────┬─────┘
     │ 1:many
     ▼
┌───────────┐
│   Goal    │
│───────────│
│ id (PK)   │
│ user_id   │──→ User
│ title     │
│ description│
│ status    │  (active / achieved / archived)
│ target_date│
│ color     │  (for UI grouping)
│ sort_order│
│ created   │
│ updated   │
└────┬──────┘
     │ 1:many
     ▼
┌────────────┐
│  Project   │
│────────────│
│ id (PK)    │
│ goal_id    │──→ Goal
│ user_id    │──→ User
│ title      │
│ description│
│ status     │  (active / completed / on_hold / archived)
│ due_date   │
│ sort_order │
│ created    │
│ updated    │
└────┬───────┘
     │ 1:many
     ▼
┌──────────────┐
│    Task      │
│──────────────│
│ id (PK)      │
│ project_id   │──→ Project
│ user_id      │──→ User
│ parent_id    │──→ Task (nullable, self-referential for subtasks)
│ title        │
│ description  │
│ status       │  (todo / in_progress / done / cancelled)
│ priority     │  (1=urgent, 2=high, 3=medium, 4=low)
│ due_date     │
│ completed_at │
│ sort_order   │
│ depth        │  (0=task, 1=subtask — enforced max depth of 1)
│ created      │
│ updated      │
└──────────────┘
```

### Design Decisions

**Why self-referential Tasks instead of a separate Subtask table?**
- Simpler queries — one table, one endpoint, one set of CRUD logic
- `parent_id` is null for top-level tasks, points to another Task for subtasks
- `depth` column enforced at max 1 (no sub-sub-tasks — keeps UI clean)
- Same data model pattern used by Todoist, Linear, and Asana

**Why `sort_order` everywhere?**
- Lets you drag-and-drop reorder within each level
- Integer field, rebalanced on move (e.g., 100, 200, 300 → insert at 150)

**Why `user_id` on every entity (not just Goal)?**
- Enables direct queries without joins for common operations ("all my tasks due today")
- Slight denormalization, but worth it for query performance and API simplicity

### SQLAlchemy Models (Python)

```python
# models.py

from datetime import datetime, date
from enum import Enum as PyEnum
from sqlalchemy import (
    Column, Integer, String, Text, DateTime, Date,
    ForeignKey, Enum, CheckConstraint
)
from sqlalchemy.orm import relationship, DeclarativeBase
from sqlalchemy.sql import func


class Base(DeclarativeBase):
    pass


class GoalStatus(str, PyEnum):
    ACTIVE = "active"
    ACHIEVED = "achieved"
    ARCHIVED = "archived"


class ProjectStatus(str, PyEnum):
    ACTIVE = "active"
    COMPLETED = "completed"
    ON_HOLD = "on_hold"
    ARCHIVED = "archived"


class TaskStatus(str, PyEnum):
    TODO = "todo"
    IN_PROGRESS = "in_progress"
    DONE = "done"
    CANCELLED = "cancelled"


class TaskPriority(int, PyEnum):
    URGENT = 1
    HIGH = 2
    MEDIUM = 3
    LOW = 4


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True)
    email = Column(String(255), unique=True, nullable=False, index=True)
    hashed_password = Column(String(255), nullable=False)
    name = Column(String(255), nullable=False)
    created_at = Column(DateTime, server_default=func.now())

    goals = relationship("Goal", back_populates="user", cascade="all, delete-orphan")


class Goal(Base):
    __tablename__ = "goals"

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    title = Column(String(255), nullable=False)
    description = Column(Text, nullable=True)
    status = Column(Enum(GoalStatus), default=GoalStatus.ACTIVE, nullable=False)
    target_date = Column(Date, nullable=True)
    color = Column(String(7), nullable=True)  # hex color, e.g. "#3B82F6"
    sort_order = Column(Integer, default=0)
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())

    user = relationship("User", back_populates="goals")
    projects = relationship("Project", back_populates="goal", cascade="all, delete-orphan")


class Project(Base):
    __tablename__ = "projects"

    id = Column(Integer, primary_key=True)
    goal_id = Column(Integer, ForeignKey("goals.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    title = Column(String(255), nullable=False)
    description = Column(Text, nullable=True)
    status = Column(Enum(ProjectStatus), default=ProjectStatus.ACTIVE, nullable=False)
    due_date = Column(Date, nullable=True)
    sort_order = Column(Integer, default=0)
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())

    goal = relationship("Goal", back_populates="projects")
    user = relationship("User")
    tasks = relationship("Task", back_populates="project", cascade="all, delete-orphan")


class Task(Base):
    __tablename__ = "tasks"
    __table_args__ = (
        CheckConstraint("depth >= 0 AND depth <= 1", name="check_depth_range"),
    )

    id = Column(Integer, primary_key=True)
    project_id = Column(Integer, ForeignKey("projects.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    parent_id = Column(Integer, ForeignKey("tasks.id", ondelete="CASCADE"), nullable=True, index=True)
    title = Column(String(500), nullable=False)
    description = Column(Text, nullable=True)
    status = Column(Enum(TaskStatus), default=TaskStatus.TODO, nullable=False)
    priority = Column(Integer, default=TaskPriority.MEDIUM)
    due_date = Column(Date, nullable=True)
    completed_at = Column(DateTime, nullable=True)
    sort_order = Column(Integer, default=0)
    depth = Column(Integer, default=0)
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())

    project = relationship("Project", back_populates="tasks")
    user = relationship("User")
    parent = relationship("Task", remote_side=[id], back_populates="subtasks")
    subtasks = relationship("Task", back_populates="parent", cascade="all, delete-orphan")
```

---

## 3. API Design

### Base URL
```
https://averytask-api.up.railway.app/api/v1
```

### Authentication

| Endpoint          | Method | Description              |
|-------------------|--------|--------------------------|
| `/auth/register`  | POST   | Create account           |
| `/auth/login`     | POST   | Get JWT access + refresh |
| `/auth/refresh`   | POST   | Refresh access token     |

All other endpoints require `Authorization: Bearer <token>` header.

### Goals

| Endpoint           | Method | Description          |
|--------------------|--------|----------------------|
| `/goals`           | GET    | List all goals       |
| `/goals`           | POST   | Create a goal        |
| `/goals/{id}`      | GET    | Get goal + projects  |
| `/goals/{id}`      | PATCH  | Update a goal        |
| `/goals/{id}`      | DELETE | Delete goal (cascade)|

### Projects

| Endpoint                      | Method | Description               |
|-------------------------------|--------|---------------------------|
| `/goals/{goal_id}/projects`   | GET    | List projects under goal  |
| `/goals/{goal_id}/projects`   | POST   | Create project under goal |
| `/projects/{id}`              | GET    | Get project + tasks       |
| `/projects/{id}`              | PATCH  | Update a project          |
| `/projects/{id}`              | DELETE | Delete project (cascade)  |

### Tasks

| Endpoint                          | Method | Description                |
|-----------------------------------|--------|----------------------------|
| `/projects/{project_id}/tasks`    | GET    | List tasks under project   |
| `/projects/{project_id}/tasks`    | POST   | Create task under project  |
| `/tasks/{id}`                     | GET    | Get task + subtasks        |
| `/tasks/{id}`                     | PATCH  | Update a task              |
| `/tasks/{id}`                     | DELETE | Delete task (cascade)      |
| `/tasks/{id}/subtasks`            | POST   | Create subtask             |

### Cross-Cutting Queries (Dashboard)

| Endpoint                | Method | Description                          |
|-------------------------|--------|--------------------------------------|
| `/tasks/today`          | GET    | All tasks due today                  |
| `/tasks/overdue`        | GET    | All overdue tasks                    |
| `/tasks/upcoming?days=7`| GET    | Tasks due within N days              |
| `/dashboard/summary`    | GET    | Counts: overdue, today, this week, completed this week |

### NLP Endpoint

| Endpoint       | Method | Description                                        |
|----------------|--------|----------------------------------------------------|
| `/tasks/parse` | POST   | NL input → structured task (project, due date, priority) |

**Request:**
```json
{
  "text": "finish pancData3 paper by Friday, high priority"
}
```

**Response:**
```json
{
  "parsed": {
    "title": "Finish pancData3 paper",
    "project_suggestion": "pancData3",
    "due_date": "2026-04-10",
    "priority": 2
  },
  "confidence": 0.92,
  "needs_confirmation": true
}
```

The client always shows a confirmation screen before creating — the NLP endpoint suggests, the user confirms.

---

## 4. NLP Task Parser Design

### How It Works

1. User types or speaks a natural language string
2. Client sends string to `/tasks/parse`
3. Backend calls Claude Haiku with a structured prompt
4. Claude returns JSON with parsed fields
5. Backend validates and returns to client
6. Client shows pre-filled task creation form for user confirmation

### Prompt Template

```python
PARSE_PROMPT = """You are a task parser for a hierarchical task management app.

The user's existing projects are:
{project_list}

Parse the following natural language input into a structured task.
Return ONLY valid JSON with these fields:
- title: string (cleaned up task title)
- project_suggestion: string or null (best matching project name from the list above)
- due_date: string or null (ISO date, interpreting relative dates from today: {today})
- priority: int or null (1=urgent, 2=high, 3=medium, 4=low)
- parent_task_suggestion: string or null (if this sounds like a subtask of something)

Input: "{user_input}"
"""
```

### Why Claude Haiku?
- ~$0.25 per million input tokens / ~$1.25 per million output tokens
- At ~200 tokens per parse call, even 100 tasks/day = ~$0.006/day
- Sub-second latency — feels instant on mobile
- Handles ambiguity well ("Friday" → next Friday, "EOD" → today, "ASAP" → priority 1)

---

## 5. Auth Design

### Flow
1. User registers with email + password
2. Password hashed with bcrypt
3. Login returns JWT access token (15 min) + refresh token (7 days)
4. Access token sent in header for all API calls
5. Refresh token stored securely (Android Keystore on mobile, httpOnly cookie or localStorage on web)
6. On 401, client auto-refreshes and retries

### JWT Payload
```json
{
  "sub": 1,
  "email": "avery@example.com",
  "exp": 1717200000
}
```

---

## 6. Project Structure

```
prismTask/
├── backend/
│   ├── app/
│   │   ├── __init__.py
│   │   ├── main.py              # FastAPI app, CORS, lifespan
│   │   ├── config.py            # Settings (env vars)
│   │   ├── database.py          # Engine, session factory
│   │   ├── models.py            # SQLAlchemy models
│   │   ├── schemas/             # Pydantic request/response models
│   │   │   ├── auth.py
│   │   │   ├── goal.py
│   │   │   ├── project.py
│   │   │   ├── task.py
│   │   │   └── dashboard.py
│   │   ├── routers/             # API route handlers
│   │   │   ├── auth.py
│   │   │   ├── goals.py
│   │   │   ├── projects.py
│   │   │   ├── tasks.py
│   │   │   └── dashboard.py
│   │   ├── services/            # Business logic
│   │   │   ├── auth.py
│   │   │   ├── nlp_parser.py   # Claude integration
│   │   │   └── task_service.py
│   │   └── middleware/
│   │       └── auth.py          # JWT dependency
│   ├── alembic/                 # Database migrations
│   ├── tests/
│   │   ├── test_auth.py
│   │   ├── test_goals.py
│   │   ├── test_tasks.py
│   │   └── test_nlp_parser.py
│   ├── Dockerfile
│   ├── requirements.txt
│   └── alembic.ini
│
├── app/                             # Android app module (Kotlin / Jetpack Compose)
│   ├── src/main/java/com/averycorp/prismtask/
│   │   ├── data/                    # Room DB, DAOs, entities, repositories
│   │   ├── domain/                  # Use cases and business logic
│   │   ├── ui/                      # Compose screens and components
│   │   ├── di/                      # Hilt DI modules
│   │   ├── notifications/           # Reminders, workers, receivers
│   │   └── widget/                  # Glance home-screen widgets
│   └── src/test/                    # ~490 unit tests
│
├── web/                             # Web client (React + TypeScript + Vite)
│   ├── src/
│   │   ├── api/                     # Axios API client modules
│   │   ├── components/              # Layout, shared, and UI primitives
│   │   ├── features/                # Feature screens (auth, today, tasks,
│   │   │                            #   projects, habits, calendar, etc.)
│   │   ├── hooks/                   # Custom React hooks
│   │   ├── routes/                  # React Router definitions
│   │   ├── stores/                  # Zustand state stores
│   │   ├── types/                   # TypeScript type definitions
│   │   └── utils/                   # Helpers and utility tests
│   ├── package.json
│   ├── vite.config.ts
│   └── playwright.config.ts        # E2E test config
│
├── .github/
│   └── workflows/
│       ├── android-ci.yml       # Android build + unit tests
│       ├── ci.yml               # Backend pytest + lint
│       ├── web-ci.yml           # Web lint + Vitest + Playwright
│       └── release.yml          # Release AAB build
│
├── docker-compose.yml           # Local dev (backend + postgres)
├── README.md
└── ARCHITECTURE.md              # This document
```

---

## 7. Deployment Architecture

### Local Development
```
docker-compose up
# Starts: FastAPI (hot reload) + PostgreSQL

# Web dev server (separate terminal):
cd web && npm run dev
# Opens at http://localhost:5173, proxies /api to backend

# Android: open in Android Studio and run on device/emulator
```

### Production
- **Backend:** Docker container on Railway (free tier: 500 hrs/month, more than enough)
- **Database:** Railway PostgreSQL (free tier: 1GB, plenty for single-user)
- **Android:** Built via Gradle, distributed through Google Play Store
- **Web:** Static build (`npm run build`) deployable to any CDN/hosting
- **CI:** GitHub Actions runs three pipelines — Android CI, Backend CI (pytest + lint), Web CI (Vitest + Playwright)

### Environment Variables
```env
DATABASE_URL=postgresql://user:pass@host:5432/averytask
JWT_SECRET_KEY=<random-256-bit>
JWT_ALGORITHM=HS256
ANTHROPIC_API_KEY=<your-key>
```

---

## 8. Build History

The original MVP roadmap targeted an Expo / React Native mobile client. That
plan was abandoned in favor of a native Kotlin / Jetpack Compose Android
app plus a React / TypeScript / Vite web app. Both clients share the FastAPI
+ PostgreSQL backend. See the [README](README.md) and [CHANGELOG](CHANGELOG.md)
for the shipping feature set.

---

## 9. Key Technical Decisions Log

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Android UI | Jetpack Compose (Kotlin) | Native performance, Material 3, offline-first with Room |
| Web UI | React + TypeScript + Vite | Fast iteration, large ecosystem, shared backend API |
| Web styling | TailwindCSS | Utility-first, rapid prototyping, small CSS bundles |
| Web state | Zustand | Minimal boilerplate, hooks-native, sufficient for SPA |
| ORM vs raw SQL | SQLAlchemy | Portfolio signal, migration support, type safety |
| JWT vs session auth | JWT | Stateless, standard for mobile + web APIs |
| PostgreSQL vs SQLite (server) | PostgreSQL | Production-grade, shows DB skills, free on Railway |
| Monorepo vs separate repos | Monorepo | Single GitHub link for portfolio, easier to review |
| REST vs GraphQL | REST | Simpler, FastAPI auto-docs, sufficient for this data model |
| Subtask depth limit | Max 1 | Prevents UI complexity explosion, matches most task apps |
| Web bundler | Vite | Fast HMR, native ESM, optimized production builds |
| Web E2E testing | Playwright | Cross-browser support, reliable CI execution |
