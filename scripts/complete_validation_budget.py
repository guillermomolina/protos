#!/usr/bin/env python3
# THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
# ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
# DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
# DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
# OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
# THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
# OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
# THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
# FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
# https://github.com/guillermomolina/protos
#
# Software distributed under the License is distributed on an "AS IS" basis,
# WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
# the specific language governing rights and limitations under the License.

"""Fail-visible wall-clock budget guard for complete repository validation."""

import argparse
import os
import signal
import subprocess
import sys
import time


BUDGET_EXIT_CODE = 124


def terminate_process_group(process):
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return

    try:
        process.wait(timeout=5)
        return
    except subprocess.TimeoutExpired:
        pass

    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        return

    process.wait()


def run(command, budget_seconds, environment):
    print(f"VALIDATION_BUDGET_ENVIRONMENT={environment}", flush=True)
    print(f"VALIDATION_BUDGET_SECONDS={budget_seconds:g}", flush=True)
    print("VALIDATION_BUDGET_COMMAND=" + " ".join(command), flush=True)

    started = time.monotonic()
    process = subprocess.Popen(
        command,
        start_new_session=True,
    )

    try:
        returncode = process.wait(timeout=budget_seconds)
    except subprocess.TimeoutExpired:
        elapsed = time.monotonic() - started
        terminate_process_group(process)
        print(f"VALIDATION_WALL_SECONDS={elapsed:.3f}", flush=True)
        print("VALIDATION_BUDGET=FAIL", flush=True)
        print("VALIDATION_BUDGET_REASON=TIME_LIMIT_EXCEEDED", flush=True)
        return BUDGET_EXIT_CODE

    elapsed = time.monotonic() - started
    print(f"VALIDATION_WALL_SECONDS={elapsed:.3f}", flush=True)

    if returncode != 0:
        print("VALIDATION_BUDGET=NOT_EVALUATED", flush=True)
        print(f"VALIDATION_COMMAND_EXIT_CODE={returncode}", flush=True)
        return returncode

    if elapsed > budget_seconds:
        print("VALIDATION_BUDGET=FAIL", flush=True)
        print("VALIDATION_BUDGET_REASON=TIME_LIMIT_EXCEEDED", flush=True)
        return BUDGET_EXIT_CODE

    print("VALIDATION_BUDGET=PASS", flush=True)
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--budget-seconds", type=float, required=True)
    parser.add_argument("--environment", required=True)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args(argv)

    if args.budget_seconds <= 0:
        parser.error("--budget-seconds must be greater than zero")

    command = list(args.command)
    if command and command[0] == "--":
        command.pop(0)

    if not command:
        parser.error("a validation command is required after --")

    return run(
        command,
        args.budget_seconds,
        args.environment,
    )


if __name__ == "__main__":
    sys.exit(main())
