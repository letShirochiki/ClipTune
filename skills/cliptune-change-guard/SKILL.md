---
name: cliptune-change-guard
description: Post-change quality and build verification for the ClipTune Android project. Use whenever Kotlin/Compose/Gradle code changes and you need to confirm the workspace is still healthy by running lint, unit tests, and debug/release builds, then reporting pass/fail and concrete next fixes.
---

# ClipTune Change Guard

Run repeatable health checks after every code change in `ClipTune` to quickly answer:
- Does lint still pass?
- Do debug unit tests still pass?
- Can debug/release artifacts still build?

## Quick Start

1. Run fast baseline checks:

```bash
python skills/cliptune-change-guard/scripts/run_checks.py --mode quick
```

2. Run full gate checks before merge/release:

```bash
python skills/cliptune-change-guard/scripts/run_checks.py --mode full
```

3. Run custom task list when needed:

```bash
python skills/cliptune-change-guard/scripts/run_checks.py --tasks :app:lintDebug,:app:testDebugUnitTest,:app:assembleDebug
```

## Standard Workflow

1. Confirm workspace root is `ClipTune` and `gradlew`/`gradlew.bat` exists.
2. Run `--mode quick` after any non-trivial code change.
3. If quick mode fails, stop and fix root cause before running broader checks.
4. Run `--mode full` for risky refactors, dependency updates, playback pipeline changes, or before release.
5. Report:
- exact commands executed
- failing task (if any)
- log path produced by the script
- pass/fail conclusion and next action

See task details in [references/check-matrix.md](references/check-matrix.md).

## Failure Handling

1. Keep the first failing task as the primary signal.
2. Re-run only the failing task for focused diagnosis:

```bash
python skills/cliptune-change-guard/scripts/run_checks.py --tasks :app:testDebugUnitTest
```

3. If failure suggests infra or dependency drift, add `--clean` once:

```bash
python skills/cliptune-change-guard/scripts/run_checks.py --mode quick --clean
```

4. If Gradle output is insufficient, append extra args:

```bash
python skills/cliptune-change-guard/scripts/run_checks.py --mode quick --gradle-arg=--stacktrace --gradle-arg=--warning-mode=all
```

## Skill Collaboration

- Use `$cliptune-android-dev` to implement or fix Android/Kotlin code before running checks.
- Use `$gh-fix-ci` when pull request checks fail and CI logs are needed.
- Use `$security-best-practices` when changes affect permissions, storage boundaries, or service exposure.
