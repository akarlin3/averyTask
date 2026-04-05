# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-04-17

### Added

- Task management with create, edit, delete, and completion tracking
- Project organization with custom colors and emoji icons
- Subtask support with inline add, nested display, and completion counts
- Recurring tasks (daily, weekly, monthly, yearly) with configurable intervals, day-of-week selection, end conditions, and automatic next-occurrence creation on completion
- Task reminders via Android notifications with quick-select offsets (at due time, 15 min, 30 min, 1 hour, 1 day before)
- Notification actions: tap to open app, "Complete" button to mark done from the notification
- Boot persistence for scheduled reminders
- Upcoming grouped view (Overdue / Today / Tomorrow / This Week / Later / No Date)
- Flat list view with sorting (due date, priority, date created, alphabetical)
- Project filtering via horizontal chip row
- Overdue detection with red card styling, left border, and badge count in the top bar
- Quick-select date chips (Today, Tomorrow, +1 Week, Pick Date) in the task editor
- Smart date labels (Today / Tomorrow / Overdue + formatted date)
- Swipe-to-complete (right, green) and swipe-to-delete (left, red) with undo snackbars
- Priority system (None / Low / Medium / High / Urgent) with colored dots and centralized PriorityColors theme
- Material 3 DatePicker and TimePicker dialogs
- Recurrence selector UI with type chips, interval picker, day-of-week multi-select, and end condition options
- Reminder picker dialog with preset offset options
- Reusable EmptyState component across task list, project list, and filtered views
- Slide + fade navigation transitions (300ms)
- Custom typography scale
- animateContentSize on subtask sections
- Hilt dependency injection throughout
- Room database with TaskEntity, ProjectEntity, TaskDao, ProjectDao
- Foreign keys: task-to-project (SET_NULL), task-to-parent (CASCADE delete)
- Indices on projectId, parentTaskId, dueDate, isCompleted, priority
- POST_NOTIFICATIONS permission request on Android 13+
- SCHEDULE_EXACT_ALARM and RECEIVE_BOOT_COMPLETED permissions
- ProGuard/R8 rules for Room, Gson, and domain models
- Release build with minification and resource shrinking enabled
- Unit tests for RecurrenceEngine (18 tests)
- Integration tests for DAO operations and recurrence completion flow

### Infrastructure

- Single-activity Compose architecture with Jetpack Navigation
- MVVM with ViewModels, Repositories, and Room DAOs
- Material 3 theming with dynamic color support (Android 12+)
- Edge-to-edge display
- Kotlin 2.2.10, Compose BOM 2024.12.01, Gradle 8.13
- Min SDK 26 (Android 8.0), Target SDK 35 (Android 15)

[0.1.0]: https://github.com/akarlin3/averyTask/releases/tag/v0.1.0-mvp
