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


"""TEST008 Java slow-test regression guard.

Subcommands:

  reset  --reports DIR --log FILE
      Remove only the generated Surefire report directory and truncate the
      retained log so that no earlier invocation can participate.
  run    --log FILE -- COMMAND...
      Run COMMAND, mirroring its combined output live to stdout and appending
      it to FILE. Returns COMMAND's exit status unchanged.
  check  --reports DIR --allowlist FILE
      Fail when any Surefire test class took strictly more than the threshold
      and either its exact fully-qualified name is not allowlisted or it
      exceeded its allowlisted budget.

Java-test policy (phases, parallelism, exclusions) stays in the Makefile.
"""

from __future__ import print_function

import argparse
import math
import os
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, List, Tuple

THRESHOLD_SECONDS = 10.0


class GuardError(Exception):
    pass


def reset(reports: Path, log: Path) -> None:
    if reports.exists():
        shutil.rmtree(str(reports))
    log.parent.mkdir(parents=True, exist_ok=True)
    log.write_bytes(b"")


def run(command: List[str], log: Path) -> int:
    log.parent.mkdir(parents=True, exist_ok=True)
    out = getattr(sys.stdout, "buffer", None)
    with log.open("ab") as sink:
        process = subprocess.Popen(command, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT)
        assert process.stdout is not None
        fd = process.stdout.fileno()
        while True:
            chunk = os.read(fd, 65536)
            if not chunk:
                break
            sink.write(chunk)
            sink.flush()
            if out is not None:
                out.write(chunk)
                out.flush()
            else:
                sys.stdout.write(chunk.decode("utf-8", "replace"))
                sys.stdout.flush()
        process.stdout.close()
        return process.wait()


def load_allowlist(path: Path) -> Dict[str, float]:
    if not path.is_file():
        raise GuardError("allowlist not found: {}".format(path))
    entries = {}  # type: Dict[str, float]
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        fields = line.split()
        where = "{}:{}".format(path, number)
        if len(fields) != 2 or any(ch in fields[0] for ch in "*?[]"):
            raise GuardError("{}: entry must be '<exact fully-qualified test class> "
                             "<budget seconds>': {!r}".format(where, raw))
        name, raw_budget = fields
        try:
            budget = float(raw_budget)
        except ValueError:
            raise GuardError("{}: unparseable budget {!r}".format(where, raw_budget))
        if math.isnan(budget) or math.isinf(budget) or budget <= THRESHOLD_SECONDS:
            raise GuardError("{}: budget must be a finite number above {:g}: {!r}"
                             .format(where, THRESHOLD_SECONDS, raw_budget))
        if name in entries:
            raise GuardError("{}: duplicate entry {}".format(where, name))
        entries[name] = budget
    return entries


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


def check(reports: Path, allowlist: Path) -> Tuple[int, List[str]]:
    lines = []
    try:
        allowed = load_allowlist(allowlist)
        timings = load_timings(reports)
    except GuardError as error:
        return 2, ["JAVA_SLOW_TEST_GUARD=ERROR",
                   "THRESHOLD_SECONDS={:g}".format(THRESHOLD_SECONDS),
                   "ALLOWLIST={}".format(allowlist),
                   "REASON={}".format(error)]
    slow = sorted((name, t) for name, t in timings.items() if t > THRESHOLD_SECONDS)
    unlisted = [(name, t) for name, t in slow if name not in allowed]
    over_budget = [(name, t) for name, t in slow
                   if name in allowed and t > allowed[name]]
    offenders = sorted(unlisted + over_budget)
    status = "FAIL" if offenders else "PASS"
    lines.append("JAVA_SLOW_TEST_GUARD={}".format(status))
    lines.append("THRESHOLD_SECONDS={:g}".format(THRESHOLD_SECONDS))
    lines.append("ALLOWLIST={}".format(allowlist))
    lines.append("REPORTED_TEST_CLASSES={}".format(len(timings)))
    lines.append("ALLOWLISTED_SLOW_TESTS={}".format(len(slow) - len(unlisted)))
    lines.append("UNALLOWLISTED_SLOW_TESTS={}".format(len(unlisted)))
    lines.append("OVER_BUDGET_SLOW_TESTS={}".format(len(over_budget)))
    if offenders:
        lines.append("")
        width = max(len(name) for name, _ in offenders)
        for name, t in offenders:
            reason = ("not allowlisted" if name not in allowed
                      else "budget {:g} s".format(allowed[name]))
            lines.append("{}  {:.2f} s  ({})".format(name.ljust(width), t, reason))
    return (1 if offenders else 0), lines


def self_test() -> int:
    import tempfile
    import unittest

    def suite_xml(name: str, time: str) -> str:
        return ('<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="{}" '
                'time="{}" tests="1" errors="0" skipped="0" failures="0"/>\n'
                ).format(name, time)

    class GuardSelfTest(unittest.TestCase):
        def setUp(self) -> None:
            self.tmp = tempfile.TemporaryDirectory()
            self.root = Path(self.tmp.name)
            self.reports = self.root / "surefire-reports"
            self.reports.mkdir()
            self.allow = self.root / "allow.txt"
            self.allow.write_text("# comment\n\n", encoding="utf-8")

        def tearDown(self) -> None:
            self.tmp.cleanup()

        def report(self, name: str, time: str) -> None:
            (self.reports / "TEST-{}.xml".format(name)).write_text(
                suite_xml(name, time), encoding="utf-8")

        def test_all_within_threshold_passes(self) -> None:
            self.report("a.FastTest", "0.5")
            self.report("a.EdgeTest", "10.000")
            status, lines = check(self.reports, self.allow)
            self.assertEqual(0, status)
            self.assertIn("JAVA_SLOW_TEST_GUARD=PASS", lines)
            self.assertIn("THRESHOLD_SECONDS=10", lines)

        def test_allowlisted_slow_passes(self) -> None:
            self.report("a.SlowTest", "12.4")
            self.allow.write_text("# why\na.SlowTest 15\n", encoding="utf-8")
            status, lines = check(self.reports, self.allow)
            self.assertEqual(0, status)
            self.assertIn("ALLOWLISTED_SLOW_TESTS=1", lines)

        def test_unallowlisted_slow_fails(self) -> None:
            self.report("a.SlowTest", "10.001")
            self.allow.write_text("a.Slow 20\na.SlowTestX 20\n", encoding="utf-8")
            status, lines = check(self.reports, self.allow)
            self.assertEqual(1, status)
            self.assertIn("JAVA_SLOW_TEST_GUARD=FAIL", lines)
            self.assertIn("a.SlowTest  10.00 s  (not allowlisted)", lines)

        def test_all_offenders_reported_sorted(self) -> None:
            self.report("b.BarTest", "31.08")
            self.report("a.FooTest", "12.41")
            self.report("c.OkTest", "1")
            status, lines = check(self.reports, self.allow)
            self.assertEqual(1, status)
            self.assertIn("UNALLOWLISTED_SLOW_TESTS=2", lines)
            self.assertEqual(["a.FooTest  12.41 s  (not allowlisted)",
                              "b.BarTest  31.08 s  (not allowlisted)"], lines[-2:])

        def test_allowlisted_over_budget_fails(self) -> None:
            self.report("a.SlowTest", "15.01")
            self.report("b.OtherTest", "14")
            self.allow.write_text("a.SlowTest 15\nb.OtherTest 15\n", encoding="utf-8")
            status, lines = check(self.reports, self.allow)
            self.assertEqual(1, status)
            self.assertIn("UNALLOWLISTED_SLOW_TESTS=0", lines)
            self.assertIn("OVER_BUDGET_SLOW_TESTS=1", lines)
            self.assertEqual("a.SlowTest  15.01 s  (budget 15 s)", lines[-1])

        def test_allowlist_entry_without_budget_rejected(self) -> None:
            self.report("a.X", "1")
            self.allow.write_text("a.X\n", encoding="utf-8")
            self.assert_error()

        def test_allowlist_budget_not_above_threshold_rejected(self) -> None:
            self.report("a.X", "1")
            self.allow.write_text("a.X 10\n", encoding="utf-8")
            self.assert_error()

        def assert_error(self) -> None:
            status, lines = check(self.reports, self.allow)
            self.assertEqual(2, status)
            self.assertIn("JAVA_SLOW_TEST_GUARD=ERROR", lines)

        def test_missing_reports_dir_fails(self) -> None:
            shutil.rmtree(str(self.reports))
            self.assert_error()

        def test_empty_reports_dir_fails(self) -> None:
            self.assert_error()

        def test_malformed_xml_fails(self) -> None:
            (self.reports / "TEST-x.xml").write_text("<testsuite", encoding="utf-8")
            self.assert_error()

        def test_missing_time_fails(self) -> None:
            (self.reports / "TEST-x.xml").write_text('<testsuite name="x"/>', encoding="utf-8")
            self.assert_error()

        def test_unparseable_time_fails(self) -> None:
            self.report("a.X", "abc")
            self.assert_error()

        def test_missing_allowlist_fails(self) -> None:
            self.report("a.X", "1")
            self.allow.unlink()
            self.assert_error()

        def test_glob_allowlist_entry_rejected(self) -> None:
            self.report("a.X", "1")
            self.allow.write_text("a.* 20\n", encoding="utf-8")
            self.assert_error()

        def test_reset_removes_stale_reports_and_log(self) -> None:
            self.report("stale.SlowTest", "99")
            log = self.root / "out" / "test-java.log"
            log.parent.mkdir()
            log.write_text("old run\n", encoding="utf-8")
            reset(self.reports, log)
            self.assertFalse(self.reports.exists())
            self.assertEqual(b"", log.read_bytes())
            self.reports.mkdir()
            self.report("fresh.FastTest", "1")
            status, _ = check(self.reports, self.allow)
            self.assertEqual(0, status)

        def test_run_propagates_status_and_retains_output(self) -> None:
            log = self.root / "test-java.log"
            code = ("import sys; print('phase-out'); "
                    "sys.stderr.write('phase-err\\n'); sys.exit(7)")
            self.assertEqual(7, run([sys.executable, "-c", code], log))
            self.assertEqual(0, run([sys.executable, "-c", "print('second')"], log))
            text = log.read_text(encoding="utf-8")
            self.assertIn("phase-out", text)
            self.assertIn("phase-err", text)
            self.assertIn("second", text)

    result = unittest.TextTestRunner(verbosity=2).run(
        unittest.defaultTestLoader.loadTestsFromTestCase(GuardSelfTest))
    return 0 if result.wasSuccessful() else 1


def main(argv: List[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--self-test", action="store_true")
    sub = parser.add_subparsers(dest="command")
    p_reset = sub.add_parser("reset")
    p_reset.add_argument("--reports", required=True, type=Path)
    p_reset.add_argument("--log", required=True, type=Path)
    p_run = sub.add_parser("run")
    p_run.add_argument("--log", required=True, type=Path)
    p_run.add_argument("child", nargs=argparse.REMAINDER)
    p_check = sub.add_parser("check")
    p_check.add_argument("--reports", required=True, type=Path)
    p_check.add_argument("--allowlist", required=True, type=Path)
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()
    if args.command == "reset":
        reset(args.reports, args.log)
        print("JAVA_TEST_LOG={}".format(args.log))
        return 0
    if args.command == "run":
        child = args.child[1:] if args.child[:1] == ["--"] else args.child
        if not child:
            parser.error("run requires a command after --")
        status = run(child, args.log)
        print("JAVA_TEST_LOG={}".format(args.log))
        return status
    if args.command == "check":
        status, lines = check(args.reports, args.allowlist)
        print("\n".join(lines))
        return status
    parser.error("a subcommand or --self-test is required")
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
