# Check Matrix

Use this matrix to choose a verification depth after code changes.

## Quick Mode

Command:

```bash
python skills/cliptune-change-guard/scripts/run_checks.py --mode quick
```

Tasks:
- `:app:lintDebug`
- `:app:testDebugUnitTest`
- `:app:assembleDebug`

Use for:
- routine feature updates
- bug fixes in existing flows
- UI adjustments with limited architecture impact

## Full Mode

Command:

```bash
python skills/cliptune-change-guard/scripts/run_checks.py --mode full
```

Tasks:
- `:app:lintDebug`
- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- `:app:lintRelease`
- `:app:assembleRelease`

Use for:
- release readiness checks
- dependency / Gradle / plugin updates
- broad refactors that touch playback, storage, or app initialization

## Custom Mode

Command:

```bash
python skills/cliptune-change-guard/scripts/run_checks.py --tasks :app:testDebugUnitTest
```

Use for:
- fast reruns of one failing task
- focused troubleshooting during iterative fixes

## Failure Triage

1. Keep the first failing task as the primary signal.
2. Re-run only that task with `--tasks`.
3. Add `--gradle-arg=--stacktrace` if logs are incomplete.
4. Add `--clean` only when local cache/state issues are suspected.
