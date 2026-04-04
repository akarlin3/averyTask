# CLAUDE.md

## Project Overview

**AveryTask** (`com.averykarlin.averytask`) is a native Android todo list app built with Kotlin and Jetpack Compose. The project is in early development with the initial scaffold in place.

## Tech Stack

- **Language**: Kotlin 2.2.10 (JVM target 17)
- **UI**: Jetpack Compose with Material 3 (BOM 2024.12.01)
- **Build**: Gradle 8.13 with Kotlin DSL
- **Compose Compiler**: Kotlin Compiler Plugin (via `org.jetbrains.kotlin.plugin.compose`)
- **Min SDK**: 26 (Android 8.0) / **Target SDK**: 35 (Android 15)

## Project Structure

```
app/src/main/java/com/averykarlin/averytask/
├── MainActivity.kt              # Single-activity entry point (Compose)
└── ui/theme/
    ├── Color.kt                 # Material 3 color tokens (light + dark)
    ├── Theme.kt                 # Dynamic color theme with light/dark support
    └── Type.kt                  # Typography definitions
```

## Architecture

- **Single Activity**: `MainActivity` is the sole entry point, using `setContent` with Compose
- **Compose-only UI**: No XML layouts; the entire UI is built with Jetpack Compose
- **Material 3 theming**: Supports dynamic colors on Android 12+ with static light/dark fallback
- **Edge-to-edge**: Uses `enableEdgeToEdge()` for modern system bar handling

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Clean
./gradlew clean
```

There is no CI/CD pipeline or automated test suite configured yet.

## Key Conventions

- **Theme**: Use `AveryTaskTheme` as the root composable wrapper. It handles light/dark and dynamic colors.
- **No XML layouts**: All UI must be Jetpack Compose.
- **JVM target**: 17 — do not change without updating both `compileOptions` and `kotlinOptions` in `app/build.gradle.kts`.

## Important Files

- `build.gradle.kts` — Root build file with plugin versions (AGP, Kotlin, Compose compiler)
- `app/build.gradle.kts` — App module dependencies and build configuration
- `settings.gradle.kts` — Repository sources and project includes
- `gradle.properties` — Gradle JVM args, AndroidX flags
- `app/src/main/AndroidManifest.xml` — Activity declaration, theme reference
- `app/proguard-rules.pro` — R8/ProGuard rules (empty, minification disabled)
