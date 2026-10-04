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


"""TEST008 Java slow-test admission guard (PLAT047 Candidate H).

Subcommands:

  reset    --reports DIR --log FILE --state DIR
      Remove the generated Surefire reports and guard state, and truncate the
      retained log, so that no earlier invocation can participate.
  run      --log FILE [--timing FILE --phase NAME] -- COMMAND...
      Run COMMAND, mirroring its combined output live to stdout and appending
      it to FILE. With --timing, append the phase's wall time. Returns
      COMMAND's exit status unchanged.
  controls --state DIR --jobs N
      Measure the Protos-independent machine controls (pre-phase sample).
  check    --reports DIR --baseline FILE --state DIR --jobs N --log FILE
           --confirm-command CMD
      Measure the post-phase control sample, run exactly one confirmation
      for all suspects, and decide PASS (0), WARN (0), FAIL (1) or ERROR (2).
      With --warn-regressions a slow-test FAIL verdict is surfaced as WARN
      and exits 0, while guard ERROR remains fail-closed. With --advisory every
      non-PASS verdict is reported but exits 0 for CI.

Decision math and the baseline format live in java_slow_test_policy.py.
Java-test policy (phases, parallelism, exclusions) stays in the Makefile.
"""

from __future__ import print_function

import argparse
import hashlib
import json
import math
import os
import re
import shlex
import shutil
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Callable, Dict, List, Optional, Tuple

import java_slow_test_policy as policy

# Control workload definitions. The reviewed control references in the
# baseline are only valid for exactly these workloads: changing any of them
# requires re-approving the references.
CONTROL_REPETITIONS = 3
CPU_ROUNDS = 1000           # sha256 over 1 MiB per round, per worker
FS_FILES = 4000             # 4 KiB files: write, read, stat, rename, delete
FS_SPAWNS = 200             # trivial `sh -c :` processes

# The in-run probe: a tiny CPU+filesystem workload sampled during the Java
# phases and compared with the same probe measured before and after them,
# so load that starts and stops between the bracketing control samples is
# still seen. It needs no reference value of its own.
PROBE_INTERVAL_SECONDS = 5.0
PROBE_CPU_ROUNDS = 40
PROBE_FILES = 200

ANSI_ESCAPE = re.compile(rb"\x1b\[[0-9;?]*[A-Za-z]")

CPU_WORKER = """\
import hashlib, subprocess, sys
data = bytes(range(256)) * 4096
digest = hashlib.sha256()
for _ in range(int(sys.argv[2])):
    digest.update(data)
subprocess.check_call([sys.argv[1], "-version"],
                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
"""

PRE_CONTROLS = "controls-pre.json"
POST_CONTROLS = "controls-post.json"
PHASES = "phases.txt"
PROBES = "probes.txt"
CONFIRMATION_REPORTS = "confirmation-reports"


class GuardError(Exception):
    pass


def reset(reports: Path, log: Path, state: Path) -> None:
    for directory in (reports, state):
        if directory.exists():
            shutil.rmtree(str(directory))
    log.parent.mkdir(parents=True, exist_ok=True)
    log.write_bytes(b"")


def probe_once(work: Path) -> float:
    if work.exists():
        shutil.rmtree(str(work))
    work.mkdir(parents=True)
    data = bytes(range(256)) * 4096
    start = time.monotonic()
    digest = hashlib.sha256()
    for _ in range(PROBE_CPU_ROUNDS):
        digest.update(data)
    for i in range(PROBE_FILES):
        path = work / "p{}".format(i)
        path.write_bytes(data[:4096])
        path.read_bytes()
        os.remove(str(path))
    elapsed = time.monotonic() - start
    shutil.rmtree(str(work))
    return elapsed


def best_probe(state: Path) -> float:
    return min(probe_once(state / "probe") for _ in range(CONTROL_REPETITIONS))


def run(command: List[str], log: Path, timing: Optional[Path] = None,
        phase: Optional[str] = None) -> int:
    log.parent.mkdir(parents=True, exist_ok=True)
    out = getattr(sys.stdout, "buffer", None)
    env = dict(os.environ)
    if sys.stdout.isatty():
        # Maven disables colour when its output is a pipe; keep the
        # terminal coloured and the retained log plain.
        env["MAVEN_ARGS"] = (env.get("MAVEN_ARGS", "") + " -Dstyle.color=always").strip()
    stop = threading.Event()
    sampler = None
    if timing is not None:
        sampler = threading.Thread(target=_sample_probes,
                                   args=(timing.parent, stop), daemon=True)
        sampler.start()
    start = time.monotonic()
    pending = b""
    with log.open("ab") as sink:
        process = subprocess.Popen(command, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, env=env)
        assert process.stdout is not None
        fd = process.stdout.fileno()
        while True:
            chunk = os.read(fd, 65536)
            if not chunk:
                break
            # Strip escapes per complete line so none is split across reads.
            pending += chunk
            cut = pending.rfind(b"\n") + 1
            if cut:
                sink.write(ANSI_ESCAPE.sub(b"", pending[:cut]))
                sink.flush()
                pending = pending[cut:]
            if out is not None:
                out.write(chunk)
                out.flush()
            else:
                sys.stdout.write(chunk.decode("utf-8", "replace"))
                sys.stdout.flush()
        sink.write(ANSI_ESCAPE.sub(b"", pending))
        process.stdout.close()
        status = process.wait()
    stop.set()
    if sampler is not None:
        sampler.join()
    if timing is not None:
        # The phases run one after another, so their wall times add up to
        # the real Java phase makespan (unlike overlapping class times).
        timing.parent.mkdir(parents=True, exist_ok=True)
        with timing.open("a", encoding="utf-8") as sink:
            sink.write("{} {:.3f} {}\n".format(phase, time.monotonic() - start, status))
    return status


def _sample_probes(state: Path, stop: threading.Event) -> None:
    state.mkdir(parents=True, exist_ok=True)
    while not stop.wait(PROBE_INTERVAL_SECONDS):
        try:
            elapsed = probe_once(state / "probe-run")
        except OSError:
            continue
        with (state / PROBES).open("a", encoding="utf-8") as sink:
            sink.write("{:.4f}\n".format(elapsed))


def read_in_run_load(path: Path, bracket: float) -> Optional[float]:
    """Median in-run probe relative to the best bracketing probe; the median
    ignores the natural spikes of test load (JVM start-up, JIT)."""
    if not path.is_file():
        return None
    values = sorted(float(v) for v in path.read_text(encoding="utf-8").split())
    if not values:
        return None
    middle = len(values) // 2
    median = values[middle] if len(values) % 2 else (values[middle - 1] + values[middle]) / 2
    return median / bracket


def java_executable() -> str:
    home = os.environ.get("JAVA_HOME")
    if home and (Path(home) / "bin" / "java").is_file():
        return str(Path(home) / "bin" / "java")
    found = shutil.which("java")
    if not found:
        raise GuardError("no java executable for the CPU/JVM control")
    return found


def cpu_jvm_once(jobs: int, java: str) -> float:
    """Wall time of `jobs` concurrent CPU+JVM-startup workers, matching the
    class-parallel lane's concurrency."""
    start = time.monotonic()
    workers = [subprocess.Popen([sys.executable, "-c", CPU_WORKER, java, str(CPU_ROUNDS)])
               for _ in range(jobs)]
    failed = [w for w in workers if w.wait() != 0]
    if failed:
        raise GuardError("CPU/JVM control worker failed")
    return time.monotonic() - start


def fs_process_once(work: Path) -> float:
    if work.exists():
        shutil.rmtree(str(work))
    work.mkdir(parents=True)
    payload = bytes(range(256)) * 16
    start = time.monotonic()
    paths = [work / "f{}".format(i) for i in range(FS_FILES)]
    for path in paths:
        path.write_bytes(payload)
    for path in paths:
        path.read_bytes()
        os.stat(str(path))
    for path in paths:
        os.replace(str(path), str(path) + ".r")
        os.remove(str(path) + ".r")
    for _ in range(FS_SPAWNS):
        if subprocess.call(["sh", "-c", ":"]) != 0:
            raise GuardError("filesystem/process control spawn failed")
    elapsed = time.monotonic() - start
    shutil.rmtree(str(work))
    return elapsed


def measure_controls(state: Path, jobs: int) -> Dict[str, float]:
    java = java_executable()
    return {
        "cpu_jvm": min(cpu_jvm_once(jobs, java) for _ in range(CONTROL_REPETITIONS)),
        "fs_process": min(fs_process_once(state / "fs-control")
                          for _ in range(CONTROL_REPETITIONS)),
        "probe": best_probe(state),
    }


def write_controls(path: Path, values: Dict[str, float]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(values, sort_keys=True) + "\n", encoding="utf-8")


def read_controls(path: Path) -> Dict[str, float]:
    if not path.is_file():
        raise GuardError("control sample not found: {}".format(path))
    try:
        values = json.loads(path.read_text(encoding="utf-8"))
    except ValueError as error:
        raise GuardError("malformed control sample {}: {}".format(path, error))
    for name in policy.CONTROLS + ("probe",):
        value = values.get(name)
        if not isinstance(value, (int, float)) or not value > 0 or math.isinf(value):
            raise GuardError("invalid control {} in {}".format(name, path))
    return {name: float(values[name]) for name in policy.CONTROLS + ("probe",)}


def read_makespan(path: Path) -> float:
    if not path.is_file():
        raise GuardError("Java phase timing not found: {}".format(path))
    total = 0.0
    lines = [l for l in path.read_text(encoding="utf-8").splitlines() if l.strip()]
    if not lines:
        raise GuardError("Java phase timing is empty: {}".format(path))
    for line in lines:
        fields = line.split()
        if len(fields) != 3 or fields[2] != "0":
            raise GuardError("Java phase did not complete successfully: {!r}".format(line))
        total += float(fields[1])
    return total


def load_timings(reports: Path) -> Dict[str, float]:
    if not reports.is_dir():
        raise GuardError("Surefire report directory not found: {}".format(reports))
    files = sorted(reports.glob("TEST-*.xml"))
    if not files:
        raise GuardError("no Surefire XML reports in {}".format(reports))
    timings = {}  # type: Dict[str, float]
    for report in files:
        try:
            root = ET.parse(str(report)).getroot()
        except ET.ParseError as error:
            raise GuardError("malformed Surefire report {}: {}".format(report, error))
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        if not suites:
            raise GuardError("no <testsuite> in {}".format(report))
        for suite in suites:
            name = suite.get("name")
            raw_time = suite.get("time")
            if not name:
                raise GuardError("<testsuite> without name in {}".format(report))
            if raw_time is None:
                raise GuardError("<testsuite name={!r}> without time in {}".format(name, report))
            try:
                elapsed = float(raw_time)
            except ValueError:
                raise GuardError("unparseable time {!r} for {} in {}".format(raw_time, name, report))
            if math.isnan(elapsed) or math.isinf(elapsed) or elapsed < 0:
                raise GuardError("invalid time {!r} for {} in {}".format(raw_time, name, report))
            timings[name] = max(elapsed, timings.get(name, 0.0))
    return timings


def confirm_with_make(command: str, log: Path) -> Callable[[List[str], Path], int]:
    def confirm(classes: List[str], reports: Path) -> int:
        argv = shlex.split(command) + ["JAVA_CONFIRM_TESTS=" + ",".join(classes),
                                       "JAVA_CONFIRM_REPORTS=" + str(reports)]
        return run(argv, log)
    return confirm


def check(reports: Path, baseline_path: Path, state: Path, jobs: int,
          measure: Callable[[Path, int], Dict[str, float]],
          confirm: Callable[[List[str], Path], int]) -> Tuple[int, List[str]]:
    """Measure the post sample, confirm suspects once, and decide."""
    try:
        baseline = policy.parse_baseline(baseline_path)
        timings = load_timings(reports)
        makespan = read_makespan(state / PHASES)
        pre = read_controls(state / PRE_CONTROLS)
        post = measure(state, jobs)
        write_controls(state / POST_CONTROLS, post)
        in_run_load = read_in_run_load(state / PROBES, min(pre["probe"], post["probe"]))
    except (GuardError, policy.PolicyError) as error:
        return 2, ["JAVA_SLOW_TEST_GUARD=ERROR",
                   "CLASSIFICATION=" + policy.CONFIGURATION_ERROR,
                   "BASELINE={}".format(baseline_path),
                   "REASON={}".format(error)]
    norm = policy.normalize(baseline, pre, post, in_run_load)
    suspect_names = policy.suspects(timings, norm.factor, baseline)
    confirmation = {}  # type: Dict[str, float]
    confirmation_result = "NOT_REQUIRED"
    if suspect_names:
        # Exactly one bounded reduced-contention confirmation; never repeated.
        confirm_reports = state / CONFIRMATION_REPORTS
        if confirm_reports.exists():
            shutil.rmtree(str(confirm_reports))
        status = confirm(suspect_names, confirm_reports)
        confirmation_result = "EXIT_{}".format(status)
        if status == 0:
            try:
                confirmation = load_timings(confirm_reports)
            except GuardError as error:
                confirmation_result = "UNREADABLE: {}".format(error)
    outcomes = []
    for name in suspect_names:
        if not confirmation_result == "EXIT_0":
            outcomes.append(policy.ClassOutcome(name, timings[name], None,
                                                policy.CONFIGURATION_ERROR,
                                                "confirmation " + confirmation_result))
        else:
            outcomes.append(policy.classify_class(name, timings[name], confirmation.get(name),
                                                  norm, baseline))
    global_class, global_signal, global_detail = policy.classify_global(
        makespan, jobs, norm, baseline)
    status_word, classes = policy.verdict(global_class, outcomes)

    def fmt(value: Optional[float]) -> str:
        return "N/A" if value is None else "{:.3f}".format(value)

    lines = [
        "JAVA_SLOW_TEST_GUARD={}".format(status_word),
        "CLASSIFICATION={}".format(",".join(classes)),
        "POLICY_STATUS={}".format(baseline.status),
        "BASELINE={}".format(baseline_path),
        "JAVA_TEST_JOBS={}".format(jobs),
    ]
    for name in policy.CONTROLS:
        lines.append("CONTROL_{}_SECONDS=pre:{:.3f},post:{:.3f},factor:{}".format(
            name.upper(), pre[name], post[name], fmt(norm.factors.get(name))))
    lines += [
        "CONTROL_COHERENCE={}".format(fmt(norm.coherence)),
        "CONTROL_SAMPLE_DRIFT={}".format("YES" if norm.transient else "NO"),
        "CONTROL_PROBE_SECONDS=pre:{:.4f},post:{:.4f}".format(pre["probe"], post["probe"]),
        "IN_RUN_LOAD_RATIO={},expected:{}".format(
            fmt(in_run_load), fmt(baseline.probe_load)),
        "MACHINE_FACTOR={}".format(fmt(norm.factor)),
        "NORMALIZATION={}".format(norm.reason),
        "JAVA_PHASE_MAKESPAN_SECONDS={:.3f}".format(makespan),
        "GLOBAL_PHASE_SIGNAL={}".format(fmt(global_signal)),
        "GLOBAL_RESULT={} ({})".format(global_class, global_detail),
        "REPORTED_TEST_CLASSES={}".format(len(timings)),
        "SUSPECT_CLASSES={}".format(len(suspect_names)),
        "CONFIRMATION_RESULT={}".format(confirmation_result),
    ]
    for outcome in outcomes:
        lines.append("SUSPECT_CLASS={} parallel:{:.2f} confirmation:{} {} ({})".format(
            outcome.name, outcome.parallel,
            "N/A" if outcome.confirmation is None else "{:.2f}".format(outcome.confirmation),
            outcome.classification, outcome.detail))
    if status_word == "WARN":
        lines.insert(1, "JAVA_SLOW_TEST_WARNING=machine load changed during the run; "
                        "affected timings were not judged as Protos regressions")
    return {"PASS": 0, "WARN": 0, "FAIL": 1, "ERROR": 2}[status_word], lines


def warn_regressions(status: int, lines: List[str]) -> Tuple[int, List[str]]:
    """Downgrade pure slow-test regression failures to a non-failing warning.

    Guard/infrastructure errors remain fail-closed, including when they coexist
    with a proven slow-test regression.
    """
    if status != 1:
        return status, lines

    classification_line = next(
        (line for line in lines if line.startswith("CLASSIFICATION=")),
        None,
    )
    if classification_line is None:
        return status, lines

    classifications = set(classification_line.split("=", 1)[1].split(","))
    guard_errors = {
        policy.ENVIRONMENT_NOT_COMPARABLE,
        policy.BASELINE_PENDING,
        policy.CONFIGURATION_ERROR,
    }
    if classifications & guard_errors:
        return status, lines

    shown = [
        "JAVA_SLOW_TEST_GUARD=WARN",
        "JAVA_SLOW_TEST_WARNING=slow-test regression detected; validation continues",
        "JAVA_SLOW_TEST_POLICY_VERDICT=FAIL",
    ]
    return 0, shown + lines[1:]


def advisory(status: int, lines: List[str]) -> Tuple[int, List[str]]:
    """Report a non-PASS verdict as a warning without failing."""
    if status == 0:
        return 0, lines
    shown = ["JAVA_SLOW_TEST_GUARD_MODE=ADVISORY (exit status {} not enforced)".format(status)]
    if os.environ.get("GITHUB_ACTIONS") == "true":
        shown.append("::warning title=TEST008 Java slow-test guard::{}; {}".format(
            lines[0], lines[1]))
    return 0, shown + lines


def main(argv: List[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--self-test", action="store_true")
    sub = parser.add_subparsers(dest="command")
    p_reset = sub.add_parser("reset")
    p_reset.add_argument("--reports", required=True, type=Path)
    p_reset.add_argument("--log", required=True, type=Path)
    p_reset.add_argument("--state", required=True, type=Path)
    p_run = sub.add_parser("run")
    p_run.add_argument("--log", required=True, type=Path)
    p_run.add_argument("--timing", type=Path)
    p_run.add_argument("--phase")
    p_run.add_argument("child", nargs=argparse.REMAINDER)
    p_controls = sub.add_parser("controls")
    p_controls.add_argument("--state", required=True, type=Path)
    p_controls.add_argument("--jobs", required=True, type=int)
    p_check = sub.add_parser("check")
    p_check.add_argument("--reports", required=True, type=Path)
    p_check.add_argument("--baseline", required=True, type=Path)
    p_check.add_argument("--state", required=True, type=Path)
    p_check.add_argument("--jobs", required=True, type=int)
    p_check.add_argument("--log", required=True, type=Path)
    p_check.add_argument("--confirm-command", required=True)
    mode = p_check.add_mutually_exclusive_group()
    mode.add_argument("--warn-regressions", action="store_true")
    mode.add_argument("--advisory", action="store_true")
    args = parser.parse_args(argv)

    if args.self_test:
        import java_slow_test_guard_selftest
        return java_slow_test_guard_selftest.main()
    if args.command == "reset":
        reset(args.reports, args.log, args.state)
        print("JAVA_TEST_LOG={}".format(args.log))
        return 0
    if args.command == "run":
        child = args.child[1:] if args.child[:1] == ["--"] else args.child
        if not child:
            parser.error("run requires a command after --")
        if (args.timing is None) != (args.phase is None):
            parser.error("--timing and --phase go together")
        status = run(child, args.log, args.timing, args.phase)
        print("JAVA_TEST_LOG={}".format(args.log))
        return status
    if args.command == "controls":
        try:
            values = measure_controls(args.state, args.jobs)
        except GuardError as error:
            print("JAVA_SLOW_TEST_CONTROLS=ERROR\nREASON={}".format(error))
            return 2
        write_controls(args.state / PRE_CONTROLS, values)
        print("JAVA_SLOW_TEST_CONTROLS={}".format(json.dumps(values, sort_keys=True)))
        return 0
    if args.command == "check":
        def measure(state: Path, jobs: int) -> Dict[str, float]:
            return measure_controls(state, jobs)
        status, lines = check(args.reports, args.baseline, args.state, args.jobs,
                              measure, confirm_with_make(args.confirm_command, args.log))
        if args.warn_regressions:
            status, lines = warn_regressions(status, lines)
        elif args.advisory:
            status, lines = advisory(status, lines)
        print("\n".join(lines))
        return status
    parser.error("a subcommand or --self-test is required")
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
