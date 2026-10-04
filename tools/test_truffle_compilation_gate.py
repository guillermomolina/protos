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


"""TEST009-F self-tests for tools/truffle_compilation_gate.py (synthetic logs only, no real Graal)."""

from __future__ import print_function

import io
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))

import java_generated_bytecode_bci_compilation_check as bci_check  # noqa: E402
import truffle_compilation_gate as gate  # noqa: E402

DONE = "[engine] opt done   id=12   work@1a2b |Tier 2|Time   120(  90+30  )ms|AST   40"
SUMMARY = "412 passed, 0 failed"
CLEAN = DONE + "\n" + SUMMARY + "\n"
PE_FAILURE = """[engine] opt failed id=7    work                    |Tier 2|Reason: PermanentBailoutException: Partial evaluation did not reduce value to a constant
\tat com.oracle.truffle.api.CompilerAsserts.partialEvaluationConstant(CompilerAsserts.java:111)"""
WARNING_FAILURE = """[engine] opt failed id=8    hot                     |Tier 2|Reason: Performance warning detected and is treated as a compilation error.
\tat somewhere"""
GRAPH_TOO_BIG = """[engine] opt failed id=9    big                     |Tier 2|Reason: GraphTooBigBailoutException: Graph too big to safely compile
\tat somewhere"""
CASCADE = ("[engine] opt failed engine=5  id=47656 work@7b52 |Tier 2|Reason: "
           "com.oracle.truffle.runtime.hotspot.libgraal.DestroyedIsolateException: Handle[2]")
WARNING_TRACE ="[engine] perf warn  id=10   hot   |Kind: call|Partial evaluation could not inline the virtual runtime call"


def option_names(options):
    return [option.split("=", 1)[0] for option in options]


class OptionTest(unittest.TestCase):

    def test_sync_compiles_immediately_and_synchronously(self):
        self.assertIn("-Dpolyglot.engine.CompileImmediately=true", gate.MODES[gate.SYNC])
        self.assertIn("-Dpolyglot.engine.BackgroundCompilation=false", gate.MODES[gate.SYNC])

    def test_background_does_not_force_synchronous_compilation(self):
        self.assertIn("-Dpolyglot.engine.CompileImmediately=true", gate.MODES[gate.BACKGROUND])
        self.assertNotIn("-Dpolyglot.engine.BackgroundCompilation", option_names(gate.MODES[gate.BACKGROUND]))

    def test_both_modes_are_strict(self):
        for mode in (gate.SYNC, gate.BACKGROUND):
            self.assertIn("-Dpolyglot.engine.CompilationFailureAction=ExitVM", gate.MODES[mode])
            self.assertIn("-Dpolyglot.compiler.TreatPerformanceWarningsAsErrors=all", gate.MODES[mode])
            self.assertIn("-Dpolyglot.engine.TraceCompilation=true", gate.MODES[mode])

    def test_failure_dumps_stay_under_target(self):
        for options in list(gate.MODES.values()) + [gate.DIAGNOSTIC_OPTIONS]:
            dumps = [option for option in options if option.startswith("-Djdk.graal.DumpPath=")]
            self.assertEqual(dumps, ["-Djdk.graal.DumpPath=target/truffle-compilation/graal_dumps"])

    def test_diagnostic_options_differ_from_strict(self):
        self.assertNotIn("-Dpolyglot.compiler.TreatPerformanceWarningsAsErrors=all", gate.DIAGNOSTIC_OPTIONS)
        self.assertIn("-Dpolyglot.engine.CompilationFailureAction=Print", gate.DIAGNOSTIC_OPTIONS)
        for option in ("TracePerformanceWarnings=all", "TraceMethodExpansion=truffleTier",
                       "MethodExpansionStatistics=truffleTier", "TraceNodeExpansion=truffleTier",
                       "NodeExpansionStatistics=truffleTier", "TraceInlining=true"):
            self.assertIn("-Dpolyglot.compiler." + option, gate.DIAGNOSTIC_OPTIONS)
        for options in gate.MODES.values():
            self.assertNotEqual(set(options), set(gate.DIAGNOSTIC_OPTIONS))


class EvaluateTest(unittest.TestCase):

    def test_clean_run_passes(self):
        result = gate.evaluate(CLEAN, 0)
        self.assertEqual(result["failures"], [])
        self.assertEqual(result["SEMANTIC_CORPUS"], "PASS")
        self.assertEqual(result["COMPILATIONS_DONE"], 1)
        self.assertEqual(result["CORPUS_PASSED"], 412)

    def test_nonzero_exit_fails(self):
        self.assertTrue(any(f.startswith("EXIT_CODE") for f in gate.evaluate(CLEAN, 1)["failures"]))

    def test_corpus_failure_fails(self):
        result = gate.evaluate(DONE + "\n410 passed, 2 failed\n", 0)
        self.assertEqual(result["SEMANTIC_CORPUS"], "FAIL")
        self.assertTrue(result["failures"])

    def test_missing_summary_fails(self):
        result = gate.evaluate(DONE + "\n", 0)
        self.assertTrue(any(f.startswith("SEMANTIC_CORPUS") for f in result["failures"]))

    def test_no_compilation_fails(self):
        self.assertTrue(any(f.startswith("NO_COMPILATION") for f in gate.evaluate(SUMMARY + "\n", 0)["failures"]))

    def test_any_compilation_failure_fails(self):
        for record, kind in ((PE_FAILURE, gate.PE_CONSTANT), (GRAPH_TOO_BIG, gate.OTHER_PERMANENT)):
            result = gate.evaluate("\n".join([DONE, record, SUMMARY]), 0)
            self.assertEqual(result["COMPILATION_FAILURES"], 1)
            self.assertTrue(any(f.startswith(kind) for f in result["failures"]))

    def test_performance_warning_as_error_fails(self):
        result = gate.evaluate("\n".join([DONE, WARNING_FAILURE, SUMMARY]), 0)
        self.assertEqual(result["PERFORMANCE_WARNINGS"], 1)
        self.assertTrue(any(f.startswith(gate.PERFORMANCE_WARNING) for f in result["failures"]))

    def test_traced_performance_warning_fails(self):
        result = gate.evaluate("\n".join([DONE, WARNING_TRACE, SUMMARY]), 0)
        self.assertEqual(result["PERFORMANCE_WARNINGS"], 1)
        self.assertTrue(result["failures"])

    def test_isolate_teardown_cascade_is_counted_apart_from_the_primary_failure(self):
        result = gate.evaluate("\n".join([WARNING_FAILURE, CASCADE, CASCADE, SUMMARY]), 255)
        self.assertEqual(result["COMPILATION_FAILURES"], 1)
        self.assertEqual(result["SHUTDOWN_CASCADE_FAILURES"], 2)
        self.assertFalse(any(f.startswith(gate.SHUTDOWN_CASCADE) for f in result["failures"]))
        self.assertTrue(any(f.startswith(gate.PERFORMANCE_WARNING) for f in result["failures"]))

    def test_isolate_teardown_cascade_alone_still_fails(self):
        result = gate.evaluate("\n".join([DONE, CASCADE, SUMMARY]), 0)
        self.assertTrue(any(f.startswith(gate.SHUTDOWN_CASCADE) for f in result["failures"]))

    def test_innocuous_text_is_not_a_failure(self):
        text = "\n".join([DONE, "case 'opt failed handling' printed: opt failed", "  note: opt done later",
                          "a performance warning was mentioned", SUMMARY])
        result = gate.evaluate(text, 0)
        self.assertEqual(result["COMPILATION_FAILURES"], 0)
        self.assertEqual(result["PERFORMANCE_WARNINGS"], 0)
        self.assertEqual(result["COMPILATIONS_DONE"], 1)
        self.assertEqual(result["failures"], [])


def fake_root(directory):
    root = Path(directory)
    (root / "target" / "maven-archiver").mkdir(parents=True)
    (root / "target" / "maven-archiver" / "pom.properties").write_text("artifactId=protos\nversion=1\n")
    (root / "target" / "protos-1.jar").write_text("")
    (root / "target" / "runtime").mkdir()
    (root / "target" / "runtime" / "x.truffle-runtime-1.jar").write_text("")
    (root / "target" / "runtime" / "x.truffle-compiler-1.jar").write_text("")
    return root


class RunTest(unittest.TestCase):

    def test_missing_artifact_is_error(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "report.json"
            status = gate.check(Path(directory), "java", list(gate.MODES), [], 10, Path(directory) / "a",
                                report, out=io.StringIO())
            self.assertEqual(status, 1)
            modes = json.loads(report.read_text())["modes"]
            self.assertEqual([entry["status"] for entry in modes], ["ERROR", "ERROR"])

    def test_missing_compiler_jar_is_error(self):
        with tempfile.TemporaryDirectory() as directory:
            root = fake_root(directory)
            (root / "target" / "runtime" / "x.truffle-compiler-1.jar").unlink()
            entry = gate.run_mode(root, "java", gate.SYNC, gate.MODES[gate.SYNC], [], 10, root / "a")
            self.assertEqual(entry["status"], "ERROR")

    def test_timeout_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = fake_root(directory)
            expired = subprocess.TimeoutExpired(["java"], 10, output=b"partial\n")
            with mock.patch.object(gate, "run_cli", side_effect=expired):
                entry = gate.run_mode(root, "java", gate.SYNC, gate.MODES[gate.SYNC], [], 10, root / "a")
            self.assertEqual(entry["status"], "FAIL")
            self.assertTrue(entry["failures"][0].startswith("TIMEOUT"))

    def test_report_keeps_modes_separate(self):
        with tempfile.TemporaryDirectory() as directory:
            root = fake_root(directory)
            report = root / "report.json"
            seen = []

            def fake_run(_root, command, _timeout):
                seen.append(command)
                return (0, CLEAN, 1.0) if "-Dpolyglot.engine.BackgroundCompilation=false" in command \
                    else (1, PE_FAILURE, 2.0)
            with mock.patch.object(gate, "run_cli", side_effect=fake_run):
                status = gate.check(root, "java", list(gate.MODES), ["--jobs", "2"], 10, root / "a", report,
                                    out=io.StringIO())
            self.assertEqual(status, 1)
            modes = json.loads(report.read_text())["modes"]
            self.assertEqual([entry["mode"] for entry in modes], [gate.SYNC, gate.BACKGROUND])
            self.assertEqual([entry["status"] for entry in modes], ["PASS", "FAIL"])
            self.assertEqual(modes[1]["exit_code"], 1)
            self.assertNotEqual(modes[0]["options"], modes[1]["options"])
            self.assertEqual(seen[0][-3:], ["test", "--jobs", "2"])
            self.assertTrue((root / "a" / "truffle_compilation_sync.log").is_file())

    def test_passing_check_prints_compact_summary(self):
        with tempfile.TemporaryDirectory() as directory:
            root = fake_root(directory)
            out = io.StringIO()
            with mock.patch.object(gate, "run_cli", return_value=(0, CLEAN, 1.0)):
                status = gate.check(root, "java", list(gate.MODES), [], 10, root / "a", None, out=out)
            self.assertEqual(status, 0)
            for line in ("TRUFFLE_COMPILATION_SYNC=PASS", "TRUFFLE_COMPILATION_BACKGROUND=PASS",
                         "COMPILATION_FAILURES=0", "PERFORMANCE_WARNINGS=0", "SEMANTIC_CORPUS=PASS",
                         "SYSTEMATIC_TRUFFLE_COMPILATION_GATE=PASS"):
                self.assertIn(line, out.getvalue().splitlines())


class MainTest(unittest.TestCase):

    def test_arguments_after_separator_reach_the_test_tool(self):
        with mock.patch.object(gate, "check", return_value=0) as fake_check:
            self.assertEqual(gate.main(["check", "--timeout", "5", "--mode", gate.BACKGROUND,
                                        "--", "--jobs", "8"]), 0)
        args = fake_check.call_args[0]
        self.assertEqual(args[2], [gate.BACKGROUND])
        self.assertEqual(args[3], ["--jobs", "8"])
        self.assertEqual(args[4], 5)

    def test_diagnose_uses_diagnostic_surface(self):
        with mock.patch.object(gate, "diagnose", return_value=0) as fake_diagnose, \
                mock.patch.object(gate, "check") as fake_check:
            gate.main(["diagnose", "--", "--jobs", "1"])
        fake_check.assert_not_called()
        self.assertEqual(fake_diagnose.call_args[0][2], ["--jobs", "1"])


class Test009DUnchangedTest(unittest.TestCase):

    def test_d_engine_options_are_unchanged(self):
        self.assertEqual(bci_check.ENGINE_OPTIONS, (
            "-Dpolyglot.engine.AllowExperimentalOptions=true",
            "-Dpolyglot.engine.CompileImmediately=true",
            "-Dpolyglot.engine.BackgroundCompilation=false",
            "-Dpolyglot.engine.CompilationFailureAction=Print",
            "-Dpolyglot.engine.TraceCompilation=true",
        ))

    def test_d_still_reports_other_permanent_failures_without_owning_them(self):
        done = ("[engine] opt done   id=1   ProtosBytecodeRootNodeGen@1 |Tier 2\n"
                "[engine] opt done   id=2   ProtosSemanticBytecodeRootNodeGen@2 |Tier 2")
        result = bci_check.evaluate("\n".join([done, GRAPH_TOO_BIG, "600", "200"]), 0)
        self.assertEqual(result["OTHER_PERMANENT_FAILURES"], 1)
        self.assertEqual(result["failures"], [])

    def test_d_command_runs_its_own_program(self):
        with tempfile.TemporaryDirectory() as directory:
            command = bci_check.launcher_command(fake_root(directory), "java")
            self.assertEqual(command[-2:], ["-e", bci_check.PROGRAM])
            self.assertIn("-Dpolyglot.engine.CompilationFailureAction=Print", command)


if __name__ == "__main__":
    unittest.main(verbosity=1)
