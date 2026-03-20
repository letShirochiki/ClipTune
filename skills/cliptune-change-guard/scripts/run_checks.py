#!/usr/bin/env python3
"""Run repeatable ClipTune quality/build checks after code changes."""

from __future__ import annotations

import argparse
import subprocess
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable, Sequence

MODE_TASKS = {
    "quick": [
        ":app:lintDebug",
        ":app:testDebugUnitTest",
        ":app:assembleDebug",
    ],
    "full": [
        ":app:lintDebug",
        ":app:testDebugUnitTest",
        ":app:assembleDebug",
        ":app:lintRelease",
        ":app:assembleRelease",
    ],
}


@dataclass
class TaskResult:
    task: str
    exit_code: int
    duration_s: float


def parse_args() -> argparse.Namespace:
    default_root = Path(__file__).resolve().parents[3]
    parser = argparse.ArgumentParser(
        description="Run ClipTune post-change verification tasks."
    )
    parser.add_argument(
        "--project-root",
        type=Path,
        default=default_root,
        help=f"Project root (default: {default_root})",
    )
    parser.add_argument(
        "--mode",
        choices=sorted(MODE_TASKS.keys()),
        default="quick",
        help="Task set to run when --tasks is not provided.",
    )
    parser.add_argument(
        "--tasks",
        help="Comma-separated Gradle tasks. Overrides --mode.",
    )
    parser.add_argument(
        "--clean",
        action="store_true",
        help="Run clean before selected tasks.",
    )
    parser.add_argument(
        "--gradle-arg",
        action="append",
        default=[],
        help="Extra argument to pass to every Gradle invocation. Repeatable.",
    )
    parser.add_argument(
        "--continue-on-failure",
        action="store_true",
        help="Continue remaining tasks after a failure.",
    )
    parser.add_argument(
        "--log-dir",
        type=Path,
        help="Override log directory. Default: <project>/build/reports/change-guard",
    )
    return parser.parse_args()


def pick_wrapper(project_root: Path) -> Path:
    candidates = (
        project_root / "gradlew.bat",
        project_root / "gradlew",
    )
    for candidate in candidates:
        if candidate.exists():
            return candidate
    raise FileNotFoundError(
        f"Could not find Gradle wrapper in {project_root}. Expected gradlew or gradlew.bat."
    )


def parse_tasks(args: argparse.Namespace) -> list[str]:
    if args.tasks:
        tasks = [task.strip() for task in args.tasks.split(",") if task.strip()]
        if not tasks:
            raise ValueError("--tasks was provided but no valid task names were found.")
    else:
        tasks = list(MODE_TASKS[args.mode])
    if args.clean:
        tasks = ["clean", *tasks]
    return tasks


def print_header(project_root: Path, tasks: Sequence[str], log_path: Path) -> None:
    print("== ClipTune Change Guard ==")
    print(f"Project root : {project_root}")
    print(f"Tasks        : {', '.join(tasks)}")
    print(f"Log file     : {log_path}")
    print()


def run_command(
    command: Sequence[str], cwd: Path, log_fp
) -> tuple[int, float]:
    start = time.perf_counter()
    process = subprocess.Popen(
        command,
        cwd=cwd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    assert process.stdout is not None
    for line in process.stdout:
        print(line, end="")
        log_fp.write(line)
    process.wait()
    duration = time.perf_counter() - start
    return process.returncode, duration


def build_gradle_command(
    wrapper: Path, task: str, gradle_args: Iterable[str]
) -> list[str]:
    cmd = [str(wrapper), task, "--console=plain"]
    cmd.extend(gradle_args)
    return cmd


def main() -> int:
    args = parse_args()
    project_root = args.project_root.resolve()
    wrapper = pick_wrapper(project_root)
    tasks = parse_tasks(args)

    log_dir = (
        args.log_dir.resolve()
        if args.log_dir
        else (project_root / "build" / "reports" / "change-guard").resolve()
    )
    log_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    log_path = log_dir / f"check-{timestamp}.log"

    print_header(project_root, tasks, log_path)

    results: list[TaskResult] = []
    with log_path.open("w", encoding="utf-8") as log_fp:
        log_fp.write(f"Project root: {project_root}\n")
        log_fp.write(f"Wrapper: {wrapper}\n")
        log_fp.write(f"Tasks: {', '.join(tasks)}\n\n")

        for task in tasks:
            command = build_gradle_command(wrapper, task, args.gradle_arg)
            banner = f"--- Running: {' '.join(command)} ---"
            print(banner)
            log_fp.write(banner + "\n")

            exit_code, duration = run_command(command, project_root, log_fp)
            results.append(TaskResult(task=task, exit_code=exit_code, duration_s=duration))

            status = "PASS" if exit_code == 0 else "FAIL"
            footer = f"--- {task}: {status} ({duration:.1f}s) ---"
            print(footer)
            print()
            log_fp.write(footer + "\n\n")

            if exit_code != 0 and not args.continue_on_failure:
                break

    print("Summary:")
    failed = [item for item in results if item.exit_code != 0]
    for item in results:
        status = "PASS" if item.exit_code == 0 else "FAIL"
        print(f"- {item.task}: {status} ({item.duration_s:.1f}s)")
    print(f"- Log: {log_path}")

    if failed:
        print("\nResult: FAILED")
        print(f"First failing task: {failed[0].task}")
        return 1

    print("\nResult: PASSED")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (FileNotFoundError, ValueError) as exc:
        print(f"Error: {exc}", file=sys.stderr)
        raise SystemExit(2)
