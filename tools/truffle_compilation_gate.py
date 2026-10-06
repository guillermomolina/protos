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

`diagnose` is manual escalation only: synchronous runs with textual
compilation, performance-warning and inlining traces, retained under the
artifact directory. It changes no product code and never
tries patches or boundaries; a gate failure is classified from its evidence.

BUG016-B: those traces and expansion statistics are published through
JVM-wide synchronized sinks, so one JVM serializes every logical Case however
large `--jobs` is. `diagnose` therefore first asks the real Test Tool for the
authoritative CasePlan (`protos test ... --list-cases`, D185
protos.test.cases/v1), then runs each opaque CaseRef in its own diagnostic JVM
(`protos test ... --case <ref>`), at most --shard-workers JVMs at a time.
CaseRefs are never decoded or derived here; the report keeps CasePlan order.

TEST009-V: `diagnose` is per-Case triage only (report schema /v4). It no longer
enables expansion traces or expansion statistics, and the TEST009-T global causal
correlation it once carried is retired: a per-compilation expansion tree is only
attributable inside a run that compiles one selected root. That single-root
escalation (stable root catalog, CompileOnly, one expansion view, Dump=Truffle:1
BGV under target/) is tools/truffle_root_diagnostic.py.
"""

from __future__ import print_function

import argparse
import concurrent.futures
import json
import re
import subprocess
import sys
import time
from collections import OrderedDict
from pathlib import Path
from typing import Callable, List, Optional, Sequence

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
             timeout: int, artifacts: Path, log_name: Optional[str] = None) -> "OrderedDict[str, object]":
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
    log = artifacts / (log_name or "%s.log" % mode.lower())
    log.write_text(output, encoding="utf-8")
    entry["log"] = str(log)
    entry["output_tail"] = output.splitlines()[-TAIL_LINES:]
    return entry


def write_report(report: Optional[Path], entries: List["OrderedDict[str, object]"]) -> None:
    if report is not None:
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(OrderedDict([("modes", entries)]), indent=2) + "\n", encoding="utf-8")


def print_entry(entry, out, label: Optional[str] = None) -> None:
    label = label or entry["mode"]
    print("%s=%s" % (label, entry["status"]), file=out)
    if entry.get("elapsed_seconds") is not None:
        print("  %s_SECONDS=%.1f" % (label, entry["elapsed_seconds"]), file=out)
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


CASES_SCHEMA = "protos.test.cases/v1"
DIAGNOSE_REPORT_SCHEMA = "protos.truffle-compilation.diagnose/v4"
DIAGNOSE_DIR = "diagnose"
DEFAULT_SHARD_WORKERS = 1
CASE_OPTION = "--case"
LIST_CASES_OPTION = "--list-cases"


class DiscoveryError(Exception):
    pass


def parse_case_listing(output: str) -> List["OrderedDict[str, str]"]:
    """The ordered Cases of the single protos.test.cases/v1 document in output; fail-closed.

    Only a line that starts with '{' and names a "schema" member is a candidate; exactly one
    candidate must exist. Each ref is kept verbatim and never decoded; display is presentation only.
    """
    candidates = [line.strip() for line in output.splitlines()
                  if line.lstrip().startswith("{") and '"schema"' in line]
    if not candidates:
        raise DiscoveryError("no %s document in --list-cases output" % CASES_SCHEMA)
    if len(candidates) > 1:
        raise DiscoveryError("%d candidate case-listing documents; expected exactly one" % len(candidates))
    try:
        document = json.loads(candidates[0])
    except ValueError as error:
        raise DiscoveryError("invalid case-listing JSON: %s" % error)
    if not isinstance(document, dict) or document.get("schema") != CASES_SCHEMA:
        raise DiscoveryError("unsupported case-listing schema %r" %
                             (document.get("schema") if isinstance(document, dict) else None))
    entries = document.get("cases")
    if not isinstance(entries, list):
        raise DiscoveryError("case-listing 'cases' is not an array")
    cases, seen = [], set()
    for position, entry in enumerate(entries):
        ref = entry.get("ref") if isinstance(entry, dict) else None
        if not isinstance(ref, str) or not ref:
            raise DiscoveryError("case-listing entry %d has no non-empty string 'ref'" % position)
        display = entry.get("display")
        if display is not None and not isinstance(display, str):
            raise DiscoveryError("case-listing entry %d has a non-string 'display'" % position)
        if ref in seen:
            raise DiscoveryError("case-listing repeats ref %s" % ref)
        seen.add(ref)
        cases.append(OrderedDict([("ref", ref), ("display", display)]))
    if not cases:
        raise DiscoveryError("case listing selected no logical Case")
    return cases


def without_case_selection(test_arguments: Sequence[str]) -> List[str]:
    """The original Test Tool arguments minus any --case selection, which discovery already applied."""
    kept, skip = [], False
    for argument in test_arguments:
        if skip:
            skip = False
        elif argument == CASE_OPTION:
            skip = True
        elif not argument.startswith(CASE_OPTION + "="):
            kept.append(argument)
    return kept


def shard_arguments(test_arguments: Sequence[str], ref: str) -> List[str]:
    """Original scope arguments (--jobs, --file, ...) unchanged, plus exactly one exact CaseRef."""
    return without_case_selection(test_arguments) + [CASE_OPTION, ref]


def shard_refs(arguments: Sequence[str]) -> List[str]:
    return [arguments[i + 1] for i, argument in enumerate(arguments[:-1]) if argument == CASE_OPTION]


def discover_cases(root: Path, java: str, test_arguments: Sequence[str], timeout: int, artifacts: Path
                   ) -> "OrderedDict[str, object]":
    """Authoritative CasePlan from the real Test Tool on the packaged JVM, without diagnostic options."""
    arguments = list(test_arguments) + [LIST_CASES_OPTION]
    entry = OrderedDict([("test_arguments", arguments), ("cases", [])])
    output, exit_code = "", None
    try:
        command = cli_command(root, java, (), ["test"] + arguments)
        exit_code, output, elapsed = run_cli(root, command, timeout)
        entry["elapsed_seconds"] = round(elapsed, 1)
        if exit_code != 0:
            raise DiscoveryError("--list-cases exited %d" % exit_code)
        entry["cases"] = parse_case_listing(output)
        entry["status"], entry["failures"] = "PASS", []
    except CheckError as error:
        entry["status"], entry["failures"] = "ERROR", ["ARTIFACT %s" % error]
    except subprocess.TimeoutExpired as expired:
        output = (expired.output or b"").decode("utf-8", errors="replace")
        entry["status"], entry["failures"] = "ERROR", ["DISCOVERY TIMEOUT after %d s" % timeout]
    except (DiscoveryError, OSError) as error:
        entry["status"], entry["failures"] = "ERROR", ["DISCOVERY %s" % error]
    entry["exit_code"] = exit_code
    artifacts.mkdir(parents=True, exist_ok=True)
    log = artifacts / "discovery.log"
    log.write_text(output, encoding="utf-8")
    entry["log"] = str(log)
    return entry


def shard_label(index: int) -> str:
    return "%04d" % index


def run_diagnostic_shard(root: Path, java: str, test_arguments: Sequence[str], timeout: int, artifacts: Path,
                         index: int, ref: str) -> "OrderedDict[str, object]":
    """One logical Case in one fresh diagnostic JVM with exactly DIAGNOSTIC_OPTIONS; the timeout is its own."""
    return run_mode(root, java, DIAGNOSE, DIAGNOSTIC_OPTIONS, shard_arguments(test_arguments, ref), timeout,
                    artifacts, log_name="shard-%s.log" % shard_label(index))


def run_diagnostic_shards(cases: Sequence["OrderedDict[str, str]"], workers: int, test_arguments: Sequence[str],
                          run_shard: Callable[[int, str], "OrderedDict[str, object]"]
                          ) -> List["OrderedDict[str, object]"]:
    """Runs every Case once with at most `workers` concurrent shards; results are in CasePlan order.

    Completion order never matters: each result is stored at its CasePlan index, and a shard that
    raised is recorded as ERROR evidence without abandoning the shards still running.
    """
    if workers < 1:
        raise ValueError("shard workers must be >= 1")
    results = [None] * len(cases)
    with concurrent.futures.ThreadPoolExecutor(max_workers=min(workers, len(cases))) as pool:
        futures = {pool.submit(run_shard, index, case["ref"]): index for index, case in enumerate(cases)}
        for future in concurrent.futures.as_completed(futures):
            index = futures[future]
            try:
                results[index] = future.result()
            except Exception as error:  # noqa: BLE001 - fail-closed evidence, never a coordinator abort
                results[index] = OrderedDict([
                    ("mode", DIAGNOSE), ("options", list(DIAGNOSTIC_OPTIONS)),
                    ("test_arguments", shard_arguments(test_arguments, cases[index]["ref"])),
                    ("status", "ERROR"), ("failures", ["HARNESS %s: %s" % (type(error).__name__, error)]),
                    ("exit_code", None)])
    shards = []
    for index, (case, result) in enumerate(zip(cases, results)):
        shard = OrderedDict([("shard", shard_label(index)), ("case_ref", case["ref"]),
                             ("display", case.get("display"))])
        shard.update(result)
        shards.append(shard)
    return shards


def coverage_problems(discovered: Sequence[str], shards: Sequence["OrderedDict[str, object]"]) -> List[str]:
    """Exact-once coverage: the --case actually passed to the shards must equal the discovered refs."""
    problems, executed = [], []
    for shard in shards:
        refs = shard_refs(shard.get("test_arguments", []))
        if len(refs) != 1 or refs[0] != shard.get("case_ref"):
            problems.append("SHARD %s does not select exactly its own CaseRef" % shard.get("shard"))
        executed.extend(refs)
    for ref in discovered:
        if ref not in executed:
            problems.append("MISSING %s" % ref)
    for ref in sorted(set(executed)):
        if executed.count(ref) > 1:
            problems.append("DUPLICATE %s" % ref)
        if ref not in discovered:
            problems.append("EXTRA %s" % ref)
    return problems


def shard_acquired(shard) -> bool:
    """The pre-sharding diagnose rule per JVM: an ERROR or a timeout (no exit code) is not acquired."""
    return shard.get("status") != "ERROR" and shard.get("exit_code") is not None


def aggregate_diagnostic_shards(discovery, shards, problems) -> "OrderedDict[str, object]":
    """A compiler failure stays FAIL evidence; an unobtained shard or coverage gap makes it ERROR."""
    counters = ("COMPILATIONS_DONE", "COMPILATION_FAILURES", "SHUTDOWN_CASCADE_FAILURES", "PERFORMANCE_WARNINGS",
                "PE_CONSTANT_FAILURES", "OTHER_PERMANENT_FAILURES")
    acquired = discovery["status"] == "PASS" and not problems and bool(shards) and all(
        shard_acquired(shard) for shard in shards)
    aggregate = OrderedDict()
    aggregate["status"] = "PASS" if acquired and all(shard["status"] == "PASS" for shard in shards) else (
        "FAIL" if acquired else "ERROR")
    aggregate["acquisition"] = "COMPLETE" if acquired else "INCOMPLETE"
    for status in ("PASS", "FAIL", "ERROR"):
        aggregate["SHARDS_%s" % status] = sum(1 for shard in shards if shard["status"] == status)
    aggregate["SHARDS_TIMEOUT"] = sum(1 for shard in shards if shard["status"] != "ERROR"
                                      and shard.get("exit_code") is None)
    for counter in counters:
        aggregate[counter] = sum(shard.get(counter, 0) for shard in shards)
    return aggregate


def diagnose(root: Path, java: str, test_arguments: Sequence[str], timeout: int, artifacts: Path,
             report: Optional[Path], out=sys.stdout, shard_workers: int = DEFAULT_SHARD_WORKERS) -> int:
    """Evidence collection only: exit status reflects whether every diagnostic shard itself was obtained."""
    print("truffle-compilation-diagnose: textual compilation diagnostics (no product change)", file=out)
    started = time.monotonic()
    directory = artifacts / DIAGNOSE_DIR
    if LIST_CASES_OPTION in test_arguments:
        discovery = OrderedDict([("test_arguments", list(test_arguments)), ("cases", []), ("status", "ERROR"),
                                 ("failures", ["DISCOVERY %s is owned by the harness" % LIST_CASES_OPTION]),
                                 ("exit_code", None)])
    else:
        discovery = discover_cases(root, java, test_arguments, timeout, directory)
    cases = discovery["cases"]
    shards = []
    if discovery["status"] == "PASS":
        shards = run_diagnostic_shards(
            cases, shard_workers, test_arguments,
            lambda index, ref: run_diagnostic_shard(root, java, test_arguments, timeout, directory, index, ref))
    refs = [case["ref"] for case in cases]
    problems = coverage_problems(refs, shards) if discovery["status"] == "PASS" else []
    aggregate = aggregate_diagnostic_shards(discovery, shards, problems)
    wall = round(time.monotonic() - started, 1)
    if report is not None:
        document = OrderedDict([
            ("schema", DIAGNOSE_REPORT_SCHEMA), ("mode", DIAGNOSE), ("options", list(DIAGNOSTIC_OPTIONS)),
            ("test_arguments", list(test_arguments)), ("shard_workers", shard_workers),
            ("discovery", discovery), ("case_refs", refs), ("coverage_problems", problems),
            ("shards", shards), ("aggregate", aggregate), ("wall_seconds", wall)])
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
    print("DIAGNOSTIC_DISCOVERY=%s CASES=%d" % (discovery["status"], len(cases)), file=out)
    for failure in discovery["failures"]:
        print("  " + failure, file=out)
    if "log" in discovery:
        print("DIAGNOSTIC_DISCOVERY_LOG=%s" % discovery["log"], file=out)
    print("DIAGNOSTIC_SHARD_WORKERS=%d" % shard_workers, file=out)
    for shard in shards:
        label = "%s_SHARD_%s" % (DIAGNOSE, shard["shard"])
        print_entry(shard, out, label)
        print("  CASE_REF=%s" % shard["case_ref"], file=out)
        if shard.get("display"):
            print("  CASE_DISPLAY=%s" % shard["display"], file=out)
        if "log" in shard:
            print("  DIAGNOSTIC_LOG=%s" % shard["log"], file=out)
    for problem in problems:
        print("COVERAGE " + problem, file=out)
    for key, value in aggregate.items():
        if key != "status":
            print("DIAGNOSTIC_%s=%s" % (key.upper(), value), file=out)
    print("DIAGNOSTIC_WALL_SECONDS=%.1f" % wall, file=out)
    print("%s=%s" % (DIAGNOSE, aggregate["status"]), file=out)
    return 0 if aggregate["acquisition"] == "COMPLETE" else 1


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=("check", "diagnose"))
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--java", default=None)
    parser.add_argument("--mode", choices=tuple(MODES), action="append",
                        help="check only: run selected modes (default: both)")
    parser.add_argument("--timeout", type=int, default=3600, help="per-JVM (check mode or diagnose shard) timeout in seconds")
    parser.add_argument("--artifacts", type=Path, default=Path("target/truffle-compilation"))
    parser.add_argument("--report", type=Path)
    parser.add_argument("--shard-workers", type=int, default=DEFAULT_SHARD_WORKERS,
                        help="diagnose only: maximum concurrent per-Case diagnostic JVMs (default: %(default)s); "
                             "independent of the Test Tool's --jobs")
    parser.epilog = "Arguments after -- are passed to `protos test` (e.g. -- --jobs 8)."
    argv = list(sys.argv[1:] if argv is None else argv)
    split = argv.index("--") if "--" in argv else len(argv)
    args = parser.parse_args(argv[:split])
    if args.shard_workers < 1:
        parser.error("--shard-workers must be >= 1")
    test_arguments = argv[split + 1:]
    root = args.root.resolve()
    java = select_java(args.java)
    artifacts = args.artifacts if args.artifacts.is_absolute() else root / args.artifacts
    if args.command == "diagnose":
        return diagnose(root, java, test_arguments, args.timeout, artifacts, args.report,
                        shard_workers=args.shard_workers)
    return check(root, java, args.mode or list(MODES), test_arguments, args.timeout, artifacts, args.report)


if __name__ == "__main__":
    sys.exit(main())
