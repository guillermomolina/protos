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


"""Self-test for the TEST008 guard: `java_slow_test_guard.py --self-test`.

All policy cases use synthetic timings and fixture baselines; nothing here
depends on real sleeps or machine speed. The fixture constants are test
values only, not the repository policy.
"""

from __future__ import print_function

import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Dict, List, Optional

import java_slow_test_guard as guard
import java_slow_test_policy as policy

FIXTURE = """\
# fixture
status UNAPPROVED_CANDIDATE_VALUES
policy CONTROL_FACTOR_MIN 0.5
policy CONTROL_FACTOR_MAX 4
policy CONTROL_COHERENCE_LIMIT 1.5
policy CLASS_REGRESSION_FACTOR 2
policy GLOBAL_REGRESSION_FACTOR 1.3
policy PARALLEL_INTERACTION_LIMIT 3
policy PATHOLOGICAL_CEILING_SECONDS 300
control cpu_jvm 2
control fs_process 1
probe load_ratio 2
global java_phase 6 100
class a.FooTest 5
class a.BarTest 20
"""

REFERENCE = {"cpu_jvm": 2.0, "fs_process": 1.0}


def scaled(cpu: float, fs: float) -> Dict[str, float]:
    return {"cpu_jvm": REFERENCE["cpu_jvm"] * cpu, "fs_process": REFERENCE["fs_process"] * fs,
            "probe": 0.05}


def suite_xml(name: str, seconds: str) -> str:
    return ('<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="{}" '
            'time="{}" tests="1" errors="0" skipped="0" failures="0"/>\n'
            ).format(name, seconds)


class GuardSelfTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.reports = self.root / "surefire-reports"
        self.reports.mkdir()
        self.state = self.root / "state"
        self.state.mkdir()
        self.baseline = self.root / "baseline.txt"
        self.baseline.write_text(FIXTURE, encoding="utf-8")
        self.confirm_calls = []  # type: List[List[str]]

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def report(self, directory: Path, name: str, seconds: str) -> None:
        directory.mkdir(parents=True, exist_ok=True)
        (directory / "TEST-{}.xml".format(name)).write_text(
            suite_xml(name, seconds), encoding="utf-8")

    def scenario(self, timings: Dict[str, float], makespan: float = 100.0,
                 pre: Optional[Dict[str, float]] = None,
                 post: Optional[Dict[str, float]] = None,
                 confirmation: Optional[Dict[str, float]] = None,
                 confirm_status: int = 0, jobs: int = 6,
                 probes: Optional[List[float]] = None):
        pre = pre or scaled(1, 1)
        post = post or pre
        confirmation = confirmation or {}
        for name, seconds in timings.items():
            self.report(self.reports, name, repr(seconds))
        guard.write_controls(self.state / guard.PRE_CONTROLS, pre)
        if probes is not None:
            (self.state / guard.PROBES).write_text(
                "".join("{}\n".format(p) for p in probes), encoding="utf-8")
        (self.state / guard.PHASES).write_text(
            "test-java-parallel {:.3f} 0\ntest-java-serial 0 0\n".format(makespan),
            encoding="utf-8")

        def measure(state: Path, n: int) -> Dict[str, float]:
            return post

        def confirm(classes: List[str], reports: Path) -> int:
            self.confirm_calls.append(list(classes))
            for name in classes:
                if name in confirmation:
                    self.report(reports, name, repr(confirmation[name]))
            return confirm_status

        return guard.check(self.reports, self.baseline, self.state, jobs, measure, confirm)

    def assertVerdict(self, result, status: int, *classifications: str) -> List[str]:
        code, lines = result
        word = {0: "PASS", 1: "FAIL", 2: "ERROR"}[status]
        self.assertEqual(status, code, "\n".join(lines))
        if policy.TRANSIENT_CONTENTION in classifications:
            word = "WARN"
            self.assertTrue(lines[1].startswith("JAVA_SLOW_TEST_WARNING="))
        self.assertIn("JAVA_SLOW_TEST_GUARD=" + word, lines)
        shown = [l for l in lines if l.startswith("CLASSIFICATION=")][0]
        for name in classifications:
            self.assertIn(name, shown.split("=", 1)[1].split(","), "\n".join(lines))
        return lines

    # Mandatory adversarial cases.

    def test_coherent_machine_slowdown_no_false_fail(self) -> None:
        result = self.scenario({"a.FooTest": 10.0, "a.BarTest": 40.0, "c.PlainTest": 18.0},
                               makespan=200.0, pre=scaled(2, 2))
        lines = self.assertVerdict(result, 0, policy.NORMAL)
        self.assertIn("MACHINE_FACTOR=2.000", lines)
        self.assertEqual([], self.confirm_calls)

    def test_isolated_large_regression_fails(self) -> None:
        result = self.scenario({"a.FooTest": 20.0, "c.PlainTest": 2.0},
                               confirmation={"a.FooTest": 20.0})
        self.assertVerdict(result, 1, policy.ISOLATED_TEST_REGRESSION)
        self.assertEqual([["a.FooTest"]], self.confirm_calls)

    def test_global_runtime_regression_fails(self) -> None:
        for makespan in (140.0, 160.0):
            result = self.scenario({"c.PlainTest": 3.0}, makespan=makespan)
            lines = self.assertVerdict(result, 1, policy.GLOBAL_RUNTIME_REGRESSION)
            self.assertIn("GLOBAL_PHASE_SIGNAL={:.3f}".format(makespan), lines)

    def test_global_slowdown_not_hidden_by_slow_tests(self) -> None:
        # Every class 1.5x slower: the suite cannot become its own denominator.
        result = self.scenario({"a.FooTest": 7.5, "a.BarTest": 30.0, "c.PlainTest": 6.0},
                               makespan=150.0)
        self.assertVerdict(result, 1, policy.GLOBAL_RUNTIME_REGRESSION)

    def test_filesystem_contention_transient_classified(self) -> None:
        result = self.scenario({"c.FsTest": 30.0}, pre=scaled(1, 3), post=scaled(1, 1),
                               confirmation={"c.FsTest": 6.0})
        lines = self.assertVerdict(result, 0, policy.TRANSIENT_CONTENTION)
        self.assertIn("CONTROL_SAMPLE_DRIFT=YES", lines)
        self.assertEqual(1, len(self.confirm_calls))

    def test_mid_run_load_warns_instead_of_global_fail(self) -> None:
        # Load present only between the bracketing samples: the in-run probe
        # sees it beyond normal test load, so the inflated makespan is a
        # warning, not a regression.
        result = self.scenario({"c.PlainTest": 3.0}, makespan=160.0,
                               probes=[0.10, 0.24, 0.24])
        lines = self.assertVerdict(result, 0, policy.TRANSIENT_CONTENTION)
        self.assertIn("IN_RUN_LOAD_RATIO=4.800,expected:2.000", lines)

    def test_steady_probe_keeps_global_fail(self) -> None:
        result = self.scenario({"c.PlainTest": 3.0}, makespan=160.0,
                               probes=[0.10, 0.25, 0.11, 0.10, 0.09])
        self.assertVerdict(result, 1, policy.GLOBAL_RUNTIME_REGRESSION)

    def test_incoherent_controls_error(self) -> None:
        result = self.scenario({"c.FsTest": 30.0}, pre=scaled(1, 3), post=scaled(1, 3),
                               confirmation={"c.FsTest": 6.0})
        lines = self.assertVerdict(result, 2, policy.ENVIRONMENT_NOT_COMPARABLE)
        self.assertIn("MACHINE_FACTOR=N/A", lines)
        self.assertNotIn(policy.TRANSIENT_CONTENTION, "\n".join(lines))

    def test_controls_outside_domain_error(self) -> None:
        result = self.scenario({"c.PlainTest": 1.0}, makespan=500.0, pre=scaled(5, 5))
        self.assertVerdict(result, 2, policy.ENVIRONMENT_NOT_COMPARABLE)

    def test_new_suspicious_class_cannot_self_baseline(self) -> None:
        before = self.baseline.read_bytes()
        for _ in range(2):
            result = self.scenario({"c.NewTest": 25.0}, confirmation={"c.NewTest": 24.0})
            self.assertVerdict(result, 1, policy.NEW_UNBASELINED_EXPENSIVE_TEST)
        self.assertEqual(before, self.baseline.read_bytes())

    def test_new_class_slow_only_from_contention_passes(self) -> None:
        result = self.scenario({"c.NewTest": 12.0}, confirmation={"c.NewTest": 6.0})
        self.assertVerdict(result, 0, policy.NORMAL)

    def test_pathological_ceiling_is_post_execution(self) -> None:
        # Raw seconds against the ceiling, even on a coherent 3x-slow host.
        result = self.scenario({"a.BarTest": 400.0}, makespan=300.0, pre=scaled(3, 3),
                               confirmation={"a.BarTest": 150.0})
        self.assertVerdict(result, 1, policy.PATHOLOGICAL_COST)
        # A reviewed class far below its envelope but above the ceiling is
        # still a suspect: the ceiling never depends on normalization.
        self.assertIn("a.BarTest", policy.suspects(
            {"a.BarTest": 301.0}, 3.9, policy.parse_baseline(self.baseline)))

    def test_cheap_reviewed_class_not_always_suspect(self) -> None:
        baseline = policy.parse_baseline(self.baseline)
        baseline.classes["a.TinyTest"] = 1.0
        self.assertEqual([], policy.suspects({"a.TinyTest": 9.0}, 1.0, baseline))
        self.assertEqual(["a.TinyTest"], policy.suspects({"a.TinyTest": 11.0}, 1.0, baseline))

    def test_parallel_only_regression_not_erased(self) -> None:
        result = self.scenario({"a.FooTest": 30.0}, confirmation={"a.FooTest": 5.0})
        lines = self.assertVerdict(result, 1, policy.PARALLEL_INTERACTION_REGRESSION)
        self.assertTrue(any("parallel:30.00 confirmation:5.00" in l for l in lines))

    def test_exactly_one_confirmation_for_all_suspects(self) -> None:
        self.scenario({"a.FooTest": 30.0, "c.NewTest": 12.0},
                      confirmation={"a.FooTest": 5.0, "c.NewTest": 6.0})
        self.assertEqual([["a.FooTest", "c.NewTest"]], self.confirm_calls)

    def test_failed_or_missing_confirmation_errors(self) -> None:
        result = self.scenario({"c.NewTest": 12.0}, confirm_status=1)
        self.assertVerdict(result, 2, policy.CONFIGURATION_ERROR)
        shutil.rmtree(str(self.state / guard.CONFIRMATION_REPORTS), ignore_errors=True)
        result = self.scenario({"c.NewTest": 12.0, "c.OtherTest": 12.0},
                               confirmation={"c.OtherTest": 6.0})
        self.assertVerdict(result, 2, policy.CONFIGURATION_ERROR)

    def test_pending_baseline_errors_with_evidence(self) -> None:
        self.baseline.write_text(FIXTURE.replace("control fs_process 1",
                                                 "control fs_process PENDING"),
                                 encoding="utf-8")
        result = self.scenario({"c.NewTest": 12.0}, confirmation={"c.NewTest": 6.0})
        lines = self.assertVerdict(result, 2, policy.BASELINE_PENDING)
        self.assertTrue(any(l.startswith("SUSPECT_CLASS=c.NewTest parallel:12.00 "
                                         "confirmation:6.00") for l in lines))

    def test_unknown_jobs_is_configuration_error(self) -> None:
        result = self.scenario({"c.PlainTest": 1.0}, jobs=3)
        self.assertVerdict(result, 2, policy.CONFIGURATION_ERROR)

    def test_no_automatic_baseline_growth(self) -> None:
        before = self.baseline.read_bytes()
        self.scenario({"c.NewTest": 25.0, "a.FooTest": 3.0},
                      confirmation={"c.NewTest": 24.0})
        self.scenario({"c.PlainTest": 1.0})
        self.assertEqual(before, self.baseline.read_bytes())
        self.assertNotIn("c.NewTest", policy.parse_baseline(self.baseline).classes)

    def test_advisory_mode_never_fails(self) -> None:
        for status in (1, 2):
            code, lines = guard.advisory(status, ["JAVA_SLOW_TEST_GUARD=FAIL", "CLASSIFICATION=X"])
            self.assertEqual(0, code)
            self.assertTrue(lines[0].startswith("JAVA_SLOW_TEST_GUARD_MODE=ADVISORY"))
            self.assertIn("JAVA_SLOW_TEST_GUARD=FAIL", lines)
        self.assertEqual((0, ["ok"]), guard.advisory(0, ["ok"]))

    def test_makefile_keeps_slow_test_telemetry_non_authoritative(self) -> None:
        makefile = (
            Path(__file__).resolve().parent.parent / "Makefile"
        ).read_text(encoding="utf-8")

        self.assertNotIn("JAVA_SLOW_TEST_MODE", makefile)
        self.assertIn("test: test-java test-protos", makefile)

        self.assertIn(
            "\t-$(JAVA_SLOW_TEST_GUARD) reset "
            "--reports $(JAVA_SUREFIRE_REPORTS)",
            makefile,
        )
        self.assertIn(
            "\t-$(JAVA_SLOW_TEST_GUARD) controls "
            "--state $(JAVA_SLOW_TEST_STATE)",
            makefile,
        )
        self.assertIn(
            "\t-$(JAVA_SLOW_TEST_GUARD) check "
            "--reports $(JAVA_SUREFIRE_REPORTS)",
            makefile,
        )
        self.assertIn(
            '--confirm-command "$(MAKE) --no-print-directory '
            'test-java-confirm" --advisory',
            makefile,
        )

        self.assertIn(
            "\t@$(MAKE) --no-print-directory java-test-phase "
            "JAVA_TEST_PHASE=test-java-parallel",
            makefile,
        )
        self.assertIn(
            "\t@$(MAKE) --no-print-directory java-test-phase "
            "JAVA_TEST_PHASE=test-java-serial",
            makefile,
        )

        check_line = next(
            line for line in makefile.splitlines()
            if line.startswith("check:")
        )
        prerequisites = check_line.split(":", 1)[1].split()
        self.assertNotIn("test", prerequisites)
        self.assertNotIn("test-java", prerequisites)
        self.assertNotIn("test-protos", prerequisites)
        self.assertNotIn("check-truffle-compilation", prerequisites)

    def test_warning_mode_downgrades_only_slow_test_failures(self) -> None:
        fail_lines = [
            "JAVA_SLOW_TEST_GUARD=FAIL",
            "CLASSIFICATION=ISOLATED_TEST_REGRESSION",
        ]
        code, lines = guard.warn_regressions(1, fail_lines)
        self.assertEqual(0, code)
        self.assertEqual("JAVA_SLOW_TEST_GUARD=WARN", lines[0])
        self.assertIn(
            "JAVA_SLOW_TEST_WARNING=slow-test regression detected; validation continues",
            lines,
        )
        self.assertIn("JAVA_SLOW_TEST_POLICY_VERDICT=FAIL", lines)
        self.assertIn("CLASSIFICATION=ISOLATED_TEST_REGRESSION", lines)

        error_lines = [
            "JAVA_SLOW_TEST_GUARD=ERROR",
            "CLASSIFICATION=CONFIGURATION_ERROR",
        ]
        self.assertEqual((2, error_lines), guard.warn_regressions(2, error_lines))

        mixed_lines = [
            "JAVA_SLOW_TEST_GUARD=FAIL",
            "CLASSIFICATION=CONFIGURATION_ERROR,ISOLATED_TEST_REGRESSION",
        ]
        self.assertEqual((1, mixed_lines), guard.warn_regressions(1, mixed_lines))

        malformed_lines = ["JAVA_SLOW_TEST_GUARD=FAIL"]
        self.assertEqual((1, malformed_lines), guard.warn_regressions(1, malformed_lines))

        self.assertEqual((0, ["ok"]), guard.warn_regressions(0, ["ok"]))

    # Baseline parsing.

    def test_explicit_baseline_parsing_is_exact(self) -> None:
        parsed = policy.parse_baseline(self.baseline)
        self.assertEqual({"a.FooTest": 5.0, "a.BarTest": 20.0}, parsed.classes)
        self.assertEqual({6: 100.0}, parsed.global_phase)
        # A prefix entry never covers a longer name.
        self.baseline.write_text(FIXTURE.replace("class a.FooTest 5", "class a.Foo 5"),
                                 encoding="utf-8")
        result = self.scenario({"a.FooTest": 25.0}, confirmation={"a.FooTest": 24.0})
        self.assertVerdict(result, 1, policy.NEW_UNBASELINED_EXPENSIVE_TEST)
        bad = [
            FIXTURE + "class a.* 5\n",
            FIXTURE + "class FooTest 5\n",
            FIXTURE + "class a.FooTest 6\n",
            FIXTURE + "class a.Q 0\n",
            FIXTURE + "class a.Q abc\n",
            FIXTURE + "class a.Q 5 extra\n",
            FIXTURE + "policy UNKNOWN 1\n",
            FIXTURE + "status APPROVED\n",
            FIXTURE.replace("status UNAPPROVED_CANDIDATE_VALUES\n", ""),
            FIXTURE.replace("policy GLOBAL_REGRESSION_FACTOR 1.3\n", ""),
            FIXTURE.replace("control cpu_jvm 2\n", ""),
            FIXTURE.replace("probe load_ratio 2\n", ""),
            FIXTURE.replace("UNAPPROVED_CANDIDATE_VALUES", "APPROVED")
                   .replace("policy CLASS_REGRESSION_FACTOR 2", "policy CLASS_REGRESSION_FACTOR PENDING"),
        ]
        for text in bad:
            self.baseline.write_text(text, encoding="utf-8")
            with self.assertRaises(policy.PolicyError, msg=text):
                policy.parse_baseline(self.baseline)

    def test_repository_baseline_parses(self) -> None:
        policy.parse_baseline(Path(__file__).resolve().parent / "java_slow_test_baseline.txt")

    # Current-run evidence, reports, and failure propagation.

    def test_stale_surefire_reports_excluded(self) -> None:
        self.report(self.reports, "stale.SlowTest", "99")
        self.report(self.state / guard.CONFIRMATION_REPORTS, "stale.SlowTest", "99")
        (self.state / guard.PHASES).write_text("old 1 0\n", encoding="utf-8")
        log = self.root / "out" / "test-java.log"
        log.parent.mkdir()
        log.write_text("old run\n", encoding="utf-8")
        guard.reset(self.reports, log, self.state)
        self.assertFalse(self.reports.exists())
        self.assertFalse(self.state.exists())
        self.assertEqual(b"", log.read_bytes())
        self.reports.mkdir()
        self.state.mkdir()
        self.assertVerdict(self.scenario({"fresh.FastTest": 1.0}), 0, policy.NORMAL)

    def test_child_failure_status_preserved(self) -> None:
        log = self.root / "test-java.log"
        timing = self.state / guard.PHASES
        code = ("import sys; print('phase-out'); "
                "sys.stderr.write('phase-err\\n'); sys.exit(7)")
        self.assertEqual(7, guard.run([sys.executable, "-c", code], log, timing, "p1"))
        self.assertEqual(0, guard.run([sys.executable, "-c", "print('second')"], log))
        text = log.read_text(encoding="utf-8")
        self.assertIn("phase-out", text)
        self.assertIn("phase-err", text)
        self.assertIn("second", text)
        colored = "import sys; sys.stdout.write('\\x1b[1;32mgreen\\x1b[0m\\nend')"
        self.assertEqual(0, guard.run([sys.executable, "-c", colored], log))
        text = log.read_text(encoding="utf-8")
        self.assertIn("green\nend", text)
        self.assertNotIn("\x1b", text)
        self.assertTrue(timing.read_text(encoding="utf-8").rstrip().endswith(" 7"))
        with self.assertRaises(guard.GuardError):
            guard.read_makespan(timing)

    def test_report_and_state_errors(self) -> None:
        (self.reports / "TEST-x.xml").write_text("<testsuite", encoding="utf-8")
        self.assertVerdict(self.scenario({}), 2, policy.CONFIGURATION_ERROR)
        for content in ('<testsuite name="x"/>', '<testsuite name="x" time="abc"/>'):
            (self.reports / "TEST-x.xml").write_text(content, encoding="utf-8")
            self.assertVerdict(self.scenario({}), 2, policy.CONFIGURATION_ERROR)
        (self.reports / "TEST-x.xml").unlink()
        self.assertVerdict(self.scenario({}), 2, policy.CONFIGURATION_ERROR)
        self.report(self.reports, "a.X", "1")
        self.baseline.unlink()
        self.assertVerdict(self.scenario({}), 2, policy.CONFIGURATION_ERROR)

    # Bounded smoke of the real controls (sub-second workloads).

    def test_fs_process_control_smoke(self) -> None:
        self.assertGreater(guard.fs_process_once(self.root / "fs"), 0.0)
        self.assertFalse((self.root / "fs").exists())

    def test_cpu_jvm_control_smoke(self) -> None:
        try:
            java = guard.java_executable()
        except guard.GuardError:
            self.skipTest("no java executable")
        self.assertGreater(guard.cpu_jvm_once(1, java), 0.0)


def main() -> int:
    result = unittest.TextTestRunner(verbosity=2).run(
        unittest.defaultTestLoader.loadTestsFromTestCase(GuardSelfTest))
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    sys.exit(main())
