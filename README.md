# AveryTask

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-orange.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)

A native Android todo list app built with Kotlin and Jetpack Compose. Supports task management with projects, subtasks, recurring schedules, and reminders.

## Features

- **Task management** — create, edit, delete, and complete tasks with titles, descriptions, due dates, times, and priority levels
- **Projects** — organize tasks into color-coded projects with custom emoji icons
- **Subtasks** — add subtasks inline with completion tracking and parent-child cascade delete
- **Recurring tasks** — daily, weekly (multi-day), monthly, and yearly recurrence with configurable intervals and end conditions; completing a recurring task auto-creates the next occurrence
- **Reminders** — schedule notifications at due time or 15 min / 30 min / 1 hour / 1 day before; persists across device reboots
- **Upcoming view** — tasks grouped by Overdue, Today, Tomorrow, This Week, Later, No Date
- **Sorting** — by due date, priority, date created, or alphabetical
- **Project filtering** — horizontal chip row to filter by project
- **Overdue detection** — red-tinted cards, left border accent, and badge count in the top bar
- **Swipe gestures** — swipe right to complete, swipe left to delete, with undo snackbars
- **Quick date picker** — Today / Tomorrow / +1 Week chips plus full Material 3 date picker

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.2.10 |
| UI | Jetpack Compose + Material 3 | BOM 2024.12.01 |
| DI | Hilt (Dagger) | 2.59.2 |
| Database | Room | 2.8.4 |
| Navigation | Jetpack Navigation Compose | 2.9.7 |
| Build | Gradle (Kotlin DSL) | 8.13 |

**Target:** Android 8.0+ (API 26) through Android 15 (API 35)

## Requirements

- Android Studio Ladybug (2024.2.1) or later
- JDK 17
- Android SDK 35
- Device or emulator running Android 8.0+ (API 26)

## Getting Started

```bash
# Clone the repository
git clone https://github.com/akarlin3/averyTask.git
cd averyTask

# Build debug APK
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug
```

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (R8 minification + resource shrinking)
./gradlew assembleRelease

# Run unit tests
./gradlew testDebugUnitTest

# Run instrumentation tests
./gradlew connectedDebugAndroidTest

# Clean
./gradlew clean
```

## Architecture

The app follows MVVM with a single-activity Compose architecture:

- **UI layer**: Jetpack Compose screens with Material 3, connected to ViewModels via `hiltViewModel()`
- **ViewModel layer**: Exposes `StateFlow` from repositories, handles user actions in `viewModelScope`
- **Data layer**: Room database with DAOs returning `Flow`, repositories as the single source of truth
- **DI**: Hilt provides database, DAOs, and repositories as singletons
- **Notifications**: `AlarmManager` + `BroadcastReceiver` for scheduled reminders, surviving reboots via `BOOT_COMPLETED`

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code conventions, and pull request guidelines.

## Security

See [SECURITY.md](SECURITY.md) for security considerations and how to report vulnerabilities.

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).
