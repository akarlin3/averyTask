# CLAUDE.md

## Project Overview

**AveryTask** (`com.averykarlin.averytask`) is a native Android todo list app built with Kotlin and Jetpack Compose. v0.1.0 MVP is complete with full task management, projects, subtasks, recurrence, reminders, and notifications.

## Tech Stack

- **Language**: Kotlin 2.2.10 (JVM target 17)
- **UI**: Jetpack Compose with Material 3 (BOM 2024.12.01)
- **DI**: Hilt (Dagger) 2.59.2
- **Database**: Room 2.8.4 with KSP
- **Navigation**: Jetpack Navigation Compose 2.9.7
- **Serialization**: Gson 2.11.0 (for RecurrenceRule JSON)
- **Build**: Gradle 8.13 with Kotlin DSL
- **Min SDK**: 26 (Android 8.0) / **Target SDK**: 35 (Android 15)

## Project Structure

```
app/src/main/java/com/averykarlin/averytask/
├── MainActivity.kt                     # Single-activity entry point, notification permission
├── AveryTaskApplication.kt             # @HiltAndroidApp
├── data/
│   ├── local/
│   │   ├── converter/
│   │   │   └── RecurrenceConverter.kt  # Gson JSON ↔ RecurrenceRule
│   │   ├── dao/
│   │   │   ├── TaskDao.kt             # Room DAO with Flow queries
│   │   │   └── ProjectDao.kt          # Room DAO with task count join
│   │   ├── database/
│   │   │   └── AveryTaskDatabase.kt   # Room DB (v1, Task + Project entities)
│   │   └── entity/
│   │       ├── TaskEntity.kt          # Tasks table with FKs, indices
│   │       └── ProjectEntity.kt       # Projects table
│   └── repository/
│       ├── TaskRepository.kt          # Task CRUD, recurrence completion, date grouping
│       └── ProjectRepository.kt       # Project CRUD
├── di/
│   └── DatabaseModule.kt              # Hilt module: Room DB, DAOs
├── domain/
│   ├── model/
│   │   └── RecurrenceRule.kt          # RecurrenceRule data class + RecurrenceType enum
│   └── usecase/
│       └── RecurrenceEngine.kt        # Next-date calculation for all recurrence types
├── notifications/
│   ├── NotificationHelper.kt          # Channel creation, notification builder
│   ├── ReminderScheduler.kt           # AlarmManager scheduling
│   ├── ReminderBroadcastReceiver.kt   # Fires notification on alarm
│   ├── CompleteTaskReceiver.kt        # Marks task complete from notification action
│   └── BootReceiver.kt               # Reschedules reminders after reboot
└── ui/
    ├── components/
    │   ├── SubtaskSection.kt          # Expandable subtask list with inline add
    │   ├── RecurrenceSelector.kt      # Recurrence config dialog
    │   └── EmptyState.kt             # Reusable empty state composable
    ├── navigation/
    │   └── NavGraph.kt               # NavHost with 4 routes, slide transitions
    ├── screens/
    │   ├── tasklist/
    │   │   ├── TaskListScreen.kt      # Main screen: grouped/list views, swipe, filter
    │   │   └── TaskListViewModel.kt   # Sort, filter, group, subtask map, undo
    │   ├── addedittask/
    │   │   ├── AddEditTaskScreen.kt   # Task form with date/time/priority/recurrence/reminder
    │   │   └── AddEditTaskViewModel.kt
    │   └── projects/
    │       ├── ProjectListScreen.kt
    │       ├── ProjectListViewModel.kt
    │       ├── AddEditProjectScreen.kt
    │       └── AddEditProjectViewModel.kt
    └── theme/
        ├── Color.kt                   # Material 3 color tokens
        ├── Theme.kt                   # Dynamic color theme
        ├── Type.kt                    # Typography scale
        └── PriorityColors.kt         # Centralized priority color definitions
```

## Architecture

- **Single Activity**: `MainActivity` with `@AndroidEntryPoint`, notification permission request
- **MVVM**: ViewModels → Repositories → Room DAOs, all connected via Hilt
- **Compose-only UI**: No XML layouts; entire UI is Jetpack Compose
- **Material 3 theming**: Dynamic colors on Android 12+, static light/dark fallback
- **Edge-to-edge**: Uses `enableEdgeToEdge()`
- **Reactive data**: Room returns `Flow<T>`, ViewModels expose `StateFlow<T>` via `stateIn()`
- **Recurrence**: On task completion, `RecurrenceEngine` calculates next due date; a new task is inserted automatically
- **Reminders**: `AlarmManager` schedules `BroadcastReceiver` triggers; notifications have "Complete" action

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (R8 minification + resource shrinking enabled)
./gradlew assembleRelease

# Run unit tests
./gradlew testDebugUnitTest

# Run instrumentation tests (requires device/emulator)
./gradlew connectedDebugAndroidTest

# Clean
./gradlew clean
```

## Key Conventions

- **Theme**: Use `AveryTaskTheme` as the root composable wrapper
- **No XML layouts**: All UI must be Jetpack Compose
- **JVM target**: 17 — do not change without updating both `compileOptions` and `kotlinOptions`
- **Entity fields**: Use `@ColumnInfo` with snake_case column names
- **Recurrence**: Stored as JSON string in `TaskEntity.recurrenceRule`, parsed via `RecurrenceConverter` (Gson)
- **Reminders**: `reminderOffset` is millis before due date; scheduling handled by `ReminderScheduler`
- **Priority levels**: 0=None, 1=Low, 2=Medium, 3=High, 4=Urgent; colors in `PriorityColors`
- **Error handling**: ViewModels catch exceptions and surface via `SnackbarHostState` or `SharedFlow<String>`

## Important Files

- `build.gradle.kts` — Root build file with plugin versions (AGP 9.1.0, Kotlin 2.2.10)
- `app/build.gradle.kts` — App module dependencies, build config, ProGuard/R8 settings
- `app/proguard-rules.pro` — Keep rules for Room, Gson, domain models
- `app/src/main/AndroidManifest.xml` — Activity, receivers, permissions
- `app/src/test/` — RecurrenceEngine unit tests (18 tests)
- `app/src/androidTest/` — DAO + recurrence integration tests
