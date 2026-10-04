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


"""TEST009-F strict Truffle compilation gate over the real Protos Test Tool corpus.

`check` runs the bundled Test Tool (`protos test`) through the packaged JVM
CLI, never the Native binary, in two modes whose problems differ:

  TRUFFLE_COMPILATION_SYNC        CompileImmediately, BackgroundCompilation=false
  TRUFFLE_COMPILATION_BACKGROUND  CompileImmediately, background compilation enabled

both with CompilationFailureAction=ExitVM and
compiler.TreatPerformanceWarningsAsErrors=all. A mode passes only when the
process exits 0, the Test Tool reports "<n> passed, 0 failed", at least one
Truffle compilation completed, and no compilation failure or performance
warning was traced. Timeouts and an incomplete package/runtime plane fail.

`diagnose` is manual escalation only: one synchronous run with textual
compilation, performance-warning, method/node-expansion and inlining traces,
retained under the artifact directory. It changes no product code and never
tries patches or boundaries; a gate failure is classified from its evidence.
"""

from __future__ import print_function

import argparse
import json
import re
import subprocess
import sys
from collections import OrderedDict
from pathlib import Path
from typing import List, Optional, Sequence

sys.path.insert(0, str(Path(__file__).resolve().parent))

from truffle_jvm_launch import (CheckError, cli_command, compilations_done, failure_records,  # noqa: E402
                                is_done_line, is_failure_line, run_cli, select_java)

ENGINE = "-Dpolyglot.engine."
COMPILER = "-Dpolyglot.compiler."
# Failure diagnostics (written on ExitVM/Print failures) stay under target/, not the checkout root.
GRAAL_DUMPS = "-Djdk.graal.DumpPath=target/truffle-compilation/graal_dumps"
STRICT_COMMON = (
    GRAAL_DUMPS,
    ENGINE + "AllowExperimentalOptions=true",
    ENGINE + "CompileImmediately=true",
    ENGINE + "CompilationFailureAction=ExitVM",
    COMPILER + "TreatPerformanceWarningsAsErrors=all",
    # Evidence only: TraceCompilation proves compilation ran; TracePerformanceWarnings
    # names any warning that TreatPerformanceWarningsAsErrors turns into a failure.
    ENGINE + "TraceCompilation=true",
    COMPILER + "TracePerformanceWarnings=all",
)
SYNC = "TRUFFLE_COMPILATION_SYNC"
BACKGROUND = "TRUFFLE_COMPILATION_BACKGROUND"
MODES = OrderedDict([
    (SYNC, STRICT_COMMON + (ENGINE + "BackgroundCompilation=false",)),
    (BACKGROUND, STRICT_COMMON),
])
DIAGNOSE = "TRUFFLE_COMPILATION_DIAGNOSE"
DIAGNOSTIC_OPTIONS = (
    GRAAL_DUMPS,
    ENGINE + "AllowExperimentalOptions=true",
    ENGINE + "CompileImmediately=true",
    ENGINE + "BackgroundCompilation=false",
    ENGINE + "CompilationFailureAction=Print",
    ENGINE + "TraceCompilation=true",
    COMPILER + "TracePerformanceWarnings=all",
    COMPILER + "TraceMethodExpansion=truffleTier",
    COMPILER + "MethodExpansionStatistics=truffleTier",
    COMPILER + "TraceNodeExpansion=truffleTier",
    COMPILER + "NodeExpansionStatistics=truffleTier",
    COMPILER + "TraceInlining=true",
)

SUMMARY = re.compile(r"(?:^|\s)([0-9][0-9,]*) passed, ([0-9][0-9,]*) failed\s*$")
PERFORMANCE_WARNING_MARKERS = ("performance warning",)
PE_CONSTANT_MARKERS = ("partialevaluationconstant", "reduce value to a constant")

PERFORMANCE_WARNING = "PERFORMANCE_WARNING"
PE_CONSTANT = "PE_CONSTANT_FAILURE"
OTHER_PERMANENT = "OTHER_PERMANENT_FAILURE"
# ExitVM destroys the libgraal isolate; compilations still queued then fail with
# this exception. They are a consequence of the primary failure, not independent
# findings, and are counted apart; alone (no primary failure) they still fail.
SHUTDOWN_CASCADE = "SHUTDOWN_CASCADE"
SHUTDOWN_CASCADE_MARKERS = ("destroyedisolateexception",)
TAIL_LINES = 200


def classify_failure(record: str) -> str:
    lowered = record.lower()
    if any(marker in lowered for marker in SHUTDOWN_CASCADE_MARKERS):
        return SHUTDOWN_CASCADE
    if any(marker in lowered for marker in PERFORMANCE_WARNING_MARKERS):
        return PERFORMANCE_WARNING
    if any(marker in lowered for marker in PE_CONSTANT_MARKERS):
        return PE_CONSTANT
    return OTHER_PERMANENT


def is_performance_warning_line(line: str) -> bool:
    return line.lstrip().startswith("[engine] perf warn")


def corpus_summary(output: str):
    """(passed, failed) from the Test Tool's final summary line, or None when it never completed."""
    found = None
    for line in output.splitlines():
        match = SUMMARY.search(line)
        if match:
            found = (int(match.group(1).replace(",", "")), int(match.group(2).replace(",", "")))
    return found


def evaluate(output: str, exit_code: int) -> "OrderedDict[str, object]":
    records = failure_records(output, is_failure_line)
    kinds = [classify_failure(record) for record in records]
    warnings = [line.strip() for line in output.splitlines() if is_performance_warning_line(line)]
    compilations = compilations_done(output, is_done_line)
    summary = corpus_summary(output)
    failures = []
    if exit_code != 0:
        failures.append("EXIT_CODE %d" % exit_code)
    if summary is None:
        failures.append("SEMANTIC_CORPUS no Test Tool '<n> passed, <m> failed' summary")
    elif summary[1] != 0 or summary[0] == 0:
        failures.append("SEMANTIC_CORPUS %d passed, %d failed" % summary)
    if compilations == 0:
        failures.append("NO_COMPILATION no 'opt done' trace: real compilation did not run")
    primary = [(kind, record) for kind, record in zip(kinds, records) if kind != SHUTDOWN_CASCADE]
    for kind, record in primary:
        failures.append("%s %s" % (kind, record.splitlines()[0].strip()))
    if kinds.count(SHUTDOWN_CASCADE) and not primary:
        failures.append("%s %d compilations failed by isolate teardown without a primary failure"
                        % (SHUTDOWN_CASCADE, kinds.count(SHUTDOWN_CASCADE)))
    records = [record for _, record in primary]
    for warning in warnings:
        failures.append("%s %s" % (PERFORMANCE_WARNING, warning))
    result = OrderedDict()
    result["SEMANTIC_CORPUS"] = "PASS" if summary is not None and summary[1] == 0 and summary[0] > 0 else "FAIL"
    result["CORPUS_PASSED"] = summary[0] if summary else None
    result["CORPUS_FAILED"] = summary[1] if summary else None
    result["COMPILATIONS_DONE"] = compilations
    result["COMPILATION_FAILURES"] = len(records)
    result["SHUTDOWN_CASCADE_FAILURES"] = kinds.count(SHUTDOWN_CASCADE)
    result["PERFORMANCE_WARNINGS"] = len(warnings) + kinds.count(PERFORMANCE_WARNING)
    result["PE_CONSTANT_FAILURES"] = kinds.count(PE_CONSTANT)
    result["OTHER_PERMANENT_FAILURES"] = kinds.count(OTHER_PERMANENT)
    result["failures"] = failures
    result["records"] = records
    result["performance_warnings"] = warnings
    return result


def run_mode(root: Path, java: str, mode: str, options: Sequence[str], test_arguments: Sequence[str],
             timeout: int, artifacts: Path) -> "OrderedDict[str, object]":
    entry = OrderedDict([("mode", mode), ("options", list(options)),
                         ("test_arguments", list(test_arguments))])
    try:
        command = cli_command(root, java, options, ["test"] + list(test_arguments))
    except CheckError as error:
        entry.update([("status", "ERROR"), ("failures", ["ARTIFACT %s" % error])])
        return entry
    try:
        exit_code, output, elapsed = run_cli(root, command, timeout)
    except subprocess.TimeoutExpired as expired:
        output = (expired.output or b"").decode("utf-8", errors="replace")
        entry.update([("status", "FAIL"), ("elapsed_seconds", float(timeout)),
                      ("failures", ["TIMEOUT after %d s" % timeout])])
        exit_code, elapsed = None, None
    else:
        result = evaluate(output, exit_code)
        entry["status"] = "FAIL" if result["failures"] else "PASS"
        entry["elapsed_seconds"] = round(elapsed, 1)
        entry.update(result)
    entry["exit_code"] = exit_code
    artifacts.mkdir(parents=True, exist_ok=True)
    log = artifacts / ("%s.log" % mode.lower())
    log.write_text(output, encoding="utf-8")
    entry["log"] = str(log)
    entry["output_tail"] = output.splitlines()[-TAIL_LINES:]
    return entry


def write_report(report: Optional[Path], entries: List["OrderedDict[str, object]"]) -> None:
    if report is not None:
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(OrderedDict([("modes", entries)]), indent=2) + "\n", encoding="utf-8")


def print_entry(entry, out) -> None:
    print("%s=%s" % (entry["mode"], entry["status"]), file=out)
    if entry.get("elapsed_seconds") is not None:
        print("  %s_SECONDS=%.1f" % (entry["mode"], entry["elapsed_seconds"]), file=out)
    for key in ("SEMANTIC_CORPUS", "CORPUS_PASSED", "CORPUS_FAILED", "COMPILATIONS_DONE", "COMPILATION_FAILURES",
                "SHUTDOWN_CASCADE_FAILURES", "PERFORMANCE_WARNINGS", "PE_CONSTANT_FAILURES",
                "OTHER_PERMANENT_FAILURES"):
        if key in entry:
            print("  %s=%s" % (key, entry[key]), file=out)
    for failure in entry.get("failures", []):
        print("  " + failure, file=out)
    for record in entry.get("records", []):
        print(record, file=out)


def check(root: Path, java: str, modes: Sequence[str], test_arguments: Sequence[str], timeout: int,
          artifacts: Path, report: Optional[Path], out=sys.stdout) -> int:
    print("truffle-compilation-gate: strict real Truffle compilation of the Protos Test Tool corpus", file=out)
    entries = []
    for mode in modes:
        entry = run_mode(root, java, mode, MODES[mode], test_arguments, timeout, artifacts)
        entries.append(entry)
        print_entry(entry, out)
        if entry["status"] != "PASS" and entry.get("exit_code") not in (None, 0):
            print("--- %s output (tail) ---" % entry["mode"], file=out)
            print("\n".join(entry["output_tail"][-60:]), file=out)
    write_report(report, entries)
    total = sum(entry.get("elapsed_seconds") or 0.0 for entry in entries)
    passed = all(entry["status"] == "PASS" for entry in entries)
    print("TOTAL_SECONDS=%.1f" % total, file=out)
    print("COMPILATION_FAILURES=%d" % sum(entry.get("COMPILATION_FAILURES", 0) for entry in entries), file=out)
    print("PERFORMANCE_WARNINGS=%d" % sum(entry.get("PERFORMANCE_WARNINGS", 0) for entry in entries), file=out)
    print("SEMANTIC_CORPUS=%s" % ("PASS" if all(entry.get("SEMANTIC_CORPUS") == "PASS" for entry in entries)
                                  else "FAIL"), file=out)
    print("SYSTEMATIC_TRUFFLE_COMPILATION_GATE=%s" % ("PASS" if passed else "FAIL"), file=out)
    return 0 if passed else 1


def diagnose(root: Path, java: str, test_arguments: Sequence[str], timeout: int, artifacts: Path,
             report: Optional[Path], out=sys.stdout) -> int:
    """Evidence collection only: exit status reflects whether the diagnostic run itself was obtained."""
    print("truffle-compilation-diagnose: textual compilation diagnostics (no product change)", file=out)
    entry = run_mode(root, java, DIAGNOSE, DIAGNOSTIC_OPTIONS, test_arguments, timeout, artifacts)
    write_report(report, [entry])
    print_entry(entry, out)
    if "log" in entry:
        print("DIAGNOSTIC_LOG=%s" % entry["log"], file=out)
    return 1 if entry["status"] == "ERROR" or entry.get("exit_code") is None else 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=("check", "diagnose"))
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--java", default=None)
    parser.add_argument("--mode", choices=tuple(MODES), action="append",
                        help="check only: run selected modes (default: both)")
    parser.add_argument("--timeout", type=int, default=3600, help="per-mode timeout in seconds")
    parser.add_argument("--artifacts", type=Path, default=Path("target/truffle-compilation"))
    parser.add_argument("--report", type=Path)
    parser.epilog = "Arguments after -- are passed to `protos test` (e.g. -- --jobs 8)."
    argv = list(sys.argv[1:] if argv is None else argv)
    split = argv.index("--") if "--" in argv else len(argv)
    args = parser.parse_args(argv[:split])
    test_arguments = argv[split + 1:]
    root = args.root.resolve()
    java = select_java(args.java)
    artifacts = args.artifacts if args.artifacts.is_absolute() else root / args.artifacts
    if args.command == "diagnose":
        return diagnose(root, java, test_arguments, args.timeout, artifacts, args.report)
    return check(root, java, args.mode or list(MODES), test_arguments, args.timeout, artifacts, args.report)


if __name__ == "__main__":
    sys.exit(main())
