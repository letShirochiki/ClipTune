# ClipTune Agent Routing

This repository is an Android app built with Kotlin, Jetpack Compose, Media3, Room, and FFmpegKit.

## Default Routing

- Use `skills/cliptune-android-dev/SKILL.md` for product work, bug fixes, playback changes, SAF directory handling, and data flow changes.
- Use `skills/cliptune-change-guard/SKILL.md` after non-trivial changes to run repeatable health checks and report pass/fail cleanly.

## Key Paths

- `app/src/main/java/` for app code.
- `app/src/main/AndroidManifest.xml` for permissions and service declarations.
- `build.gradle` and `settings.gradle` for Gradle configuration.
- `skills/` for repo-local workflow guidance.

## Common Commands

- Debug build: `.\gradlew assembleDebug`
- Unit tests: `.\gradlew testDebugUnitTest`
- Quick guardrail run: `python skills/cliptune-change-guard/scripts/run_checks.py --mode quick`
- Full guardrail run: `python skills/cliptune-change-guard/scripts/run_checks.py --mode full`

## Working Rules

- Protect the three main chains: directory authorization, media scanning, and playback control.
- Keep data flow changes coherent across repository, ViewModel, and UI or service layers.
- Record manual verification points when playback behavior changes.
- Use UTF-8 and avoid unrelated refactors.
