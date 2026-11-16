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


"""Self-tests for tools/native_runtime_compilation_gate.py (synthetic GraalVM output, simulated processes; never runs Maven or Native Image)."""

from __future__ import print_function

import io
import json
import os
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from unittest import mock
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import native_runtime_compilation_gate as gate  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]

PRELUDE = [
    "[INFO] --- native:1.1.14:compile-no-fork (build-stage1-native) @ protos ---",
    "GraalVM Native Image: Generating 'protos-native-analysis' (executable)...",
    "[1/8] Initializing...",
    "[2/8] Performing analysis...  [******]",
]
METHODS = [
    "****" + gate.METHODS_START + "***",
    "com.guillermomolina.protos.bytecode.ProtosBytecodeRootNodeGen.execute(VirtualFrame)",
    "com.guillermomolina.protos.runtime.ProtosObject.lookup(Object)",
    "****" + gate.METHODS_END + "***",
]
TREE = [
    "****" + gate.TREE_START + "***",
    "com.guillermomolina.protos.bytecode.ProtosBytecodeRootNodeGen.executeInternal",
    "  -> com.guillermomolina.protos.runtime.ProtosObject.lookup",
    "****" + gate.TREE_END + "***",
]
SUCCESS = ["[INFO] BUILD SUCCESS"]
FAILURE = ["[INFO] BUILD FAILURE", "[ERROR] Failed to execute goal native-maven-plugin"]


def violation(method, trace, target=None):
    return ["Blocklisted method", "   %s (target: %s)" % (method, target or method), "trace:"] + \
        ["  " + frame for frame in trace] + [""]


def header(count):
    return ["=== Found %s compilation blocklist violations ===" % count]


def violations(count):
    lines = header("{:,}".format(count))
    for index in range(count):
        lines += violation("com.example.Blocked.m%d()" % index,
                           ["com.example.Caller%d.call()" % index,
                            "com.guillermomolina.protos.bytecode.ProtosBytecodeRootNodeGen.execute"])
    return lines


ABORT = ["Error: Blocklisted methods are reachable for runtime compilation"]

STRICT_COMMAND = ["mvn", "-Pnative,native-analysis", "-DskipTests", "package"]
GRAPH_COMMAND = ["mvn", "-Pnative,native-analysis,native-analysis-graph", "-DskipTests", "package"]
BUILD_REQUEST = ["Error: Image build request for 'protos-native-analysis' (pid: 42) failed "
                 "with exit status 1"]


def npoc(count, blocks=None):
    blocks = count if blocks is None else blocks
    lines = ["Error: CompilerAsserts.neverPartOfCompilation reachable for runtime compilation "
             "from {:,} places:".format(count)]
    for index in range(blocks):
        lines += ["called from: com.guillermomolina.protos.runtime.Site%d.slowPath()" % index,
                  "runtime trace:",
                  "  com.guillermomolina.protos.runtime.Site%d.slowPath()" % index,
                  "  com.guillermomolina.protos.bytecode.ProtosBytecodeRootNodeGen.execute"]
    return lines


def text(*parts):
    lines = []
    for part in parts:
        lines.extend(part)
    return "\n".join(lines) + "\n"


CLEAN = text(PRELUDE, METHODS, TREE, SUCCESS)
TRACE_A = ["java.util.HashMap.put(Object,Object)", "com.guillermomolina.protos.runtime.Table.store",
           "com.guillermomolina.protos.bytecode.ProtosBytecodeRootNodeGen.execute"]
TRACE_B = ["java.lang.Thread.sleep(long)", "com.guillermomolina.protos.runtime.Clock.pause"]


class FakeStdout(object):
    """Yields lines; checks that each previous line already reached the terminal."""

    def __init__(self, lines, terminal, observed):
        self._lines = lines
        self._terminal = terminal
        self._observed = observed

    def __iter__(self):
        for line in self._lines:
            self._observed.append(self._terminal.getvalue())
            yield line


class FakeProcess(object):
    def __init__(self, lines, code, terminal, observed, hang=False):
        self.stdout = FakeStdout(lines, terminal, observed)
        self.returncode = None
        self._code = code
        self._hang = hang
        self.terminated = False

    def wait(self, timeout=None):
        if self._hang and not self.terminated:
            raise subprocess.TimeoutExpired("mvn", timeout)
        self.returncode = -15 if self.terminated else self._code
        return self.returncode

    def terminate(self):
        self.terminated = True

    def kill(self):
        self.terminated = True


class Runner(object):
    """Simulated processes, one scripted (output, code, hang) per launched phase."""

    def __init__(self, output="", code=0, hang=False, start_error=None, phases=None):
        self.phases = list(phases) if phases is not None else [(output, code, hang)]
        self.start_error = start_error
        self.terminal = io.StringIO()
        self.observed = []
        self.calls = []
        self.process = None

    def popen(self, command, **kwargs):
        self.calls.append((command, kwargs))
        if self.start_error is not None:
            raise self.start_error
        if not self.phases:
            raise AssertionError("unexpected extra process launch: %s" % (command,))
        output, code, hang = self.phases.pop(0)
        self.process = FakeProcess(output.splitlines(True), code,
                                   self.terminal, self.observed, hang)
        return self.process


class GateCase(unittest.TestCase):

    def setUp(self):
        self._dir = tempfile.TemporaryDirectory()
        self.root = Path(self._dir.name) / "target" / "native-analysis"
        self.report = self.root / "report.json"
        self.log = self.root / "analysis.log"
        self.strict_log = self.root / "strict-analysis.log"
        self.graph_log = self.root / "graph-analysis.log"

    def tearDown(self):
        self._dir.cleanup()

    def run_check(self, runner, timeout=60):
        code = gate.check(str(self.report), str(self.log), timeout, STRICT_COMMAND,
                          graph_command=GRAPH_COMMAND,
                          artifact_root=str(self.root), terminal=runner.terminal,
                          popen=runner.popen)
        report = json.loads(self.report.read_text(encoding="utf-8"))
        return code, report

    def summary(self, runner):
        tail = runner.terminal.getvalue().splitlines()[-7:]
        self.assertEqual([line.split("=")[0] for line in tail], [
            "NATIVE_RUNTIME_DETECTOR_OPERATIONAL", "NATIVE_RUNTIME_ANALYSIS_COMPLETE",
            "NATIVE_RUNTIME_GLOBAL_INVENTORY_COMPLETE", "NATIVE_RUNTIME_COMPILATION_BLOCKLIST",
            "NATIVE_RUNTIME_NEVER_PART_OF_COMPILATION", "NATIVE_RUNTIME_COMPILATION_CALL_TREE",
            "NATIVE_RUNTIME_COMPILATION_CHECK"])
        return dict(line.split("=", 1) for line in tail)

    def assertSummary(self, runner, complete, count, status, never=None, tree=None):
        summary = self.summary(runner)
        self.assertEqual(summary["NATIVE_RUNTIME_ANALYSIS_COMPLETE"], complete)
        self.assertEqual(summary["NATIVE_RUNTIME_COMPILATION_BLOCKLIST"], count)
        self.assertEqual(summary["NATIVE_RUNTIME_COMPILATION_CHECK"], status)
        if never is not None:
            self.assertEqual(summary["NATIVE_RUNTIME_NEVER_PART_OF_COMPILATION"], never)
        if tree is not None:
            self.assertEqual(summary["NATIVE_RUNTIME_COMPILATION_CALL_TREE"], tree)


class AcceptanceTest(GateCase):

    def test_01_complete_zero_violations_exit_zero_passes(self):
        runner = Runner(CLEAN, 0)
        code, report = self.run_check(runner)
        self.assertEqual(code, 0)
        self.assertEqual(report["status"], "PASS")
        self.assertEqual(report["schema"], "protos.native-runtime-compilation/v1")
        self.assertEqual(report["violations"], 0)
        self.assertTrue(report["analysis_complete"])
        self.assertTrue(report["call_tree_complete"])
        self.assertTrue(report["protos_roots_detected"])
        self.assertEqual(report["exit_code"], 0)
        self.assertEqual(report["log"], str(self.log))
        self.assertEqual(report["command"][1], "-Pnative,native-analysis")
        self.assertEqual(report["failures"], [])
        self.assertSummary(runner, "true", "0", "PASS")

    def test_02_one_violation_fails_and_keeps_trace(self):
        output = text(PRELUDE, METHODS, TREE,
                      ["=== Found 1 compilation blocklist violations ==="],
                      violation("java.util.HashMap.put(Object,Object)", TRACE_A), FAILURE)
        runner = Runner(output, 1)
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertEqual(report["status"], "FAIL")
        self.assertEqual(report["violations"], 1)
        self.assertEqual(report["blocklisted_methods"],
                         ["java.util.HashMap.put(Object,Object) (target: java.util.HashMap.put(Object,Object))"])
        log = self.log.read_text(encoding="utf-8")
        for frame in TRACE_A:
            self.assertIn(frame, log)
        self.assertSummary(runner, "true", "1", "FAIL")

    def test_03_several_violations_keep_all_methods_and_traces(self):
        output = text(PRELUDE, METHODS, TREE,
                      ["=== Found 2 compilation blocklist violations ==="],
                      violation("java.util.HashMap.put(Object,Object)", TRACE_A),
                      violation("java.lang.Thread.sleep(long)", TRACE_B), FAILURE)
        code, report = self.run_check(Runner(output, 1))
        self.assertEqual(code, 1)
        self.assertEqual(report["violations"], 2)
        self.assertEqual(report["blocklisted_method_count"], 2)
        self.assertEqual([m.split(" (target:")[0] for m in report["blocklisted_methods"]],
                         ["java.util.HashMap.put(Object,Object)", "java.lang.Thread.sleep(long)"])
        log = self.log.read_text(encoding="utf-8")
        for frame in TRACE_A + TRACE_B:
            self.assertIn(frame, log)

    def test_04_declared_count_differs_from_blocks(self):
        output = text(PRELUDE, METHODS, TREE,
                      ["=== Found 3 compilation blocklist violations ==="],
                      violation("java.lang.Thread.sleep(long)", TRACE_B), FAILURE)
        code, report = self.run_check(Runner(output, 1))
        self.assertEqual(code, 1)
        self.assertTrue(any("inconsistent" in f for f in report["failures"]))

    def test_05_nonzero_exit_without_blocklist(self):
        output = text(PRELUDE, ["Error: Unsupported features in 2 methods"], FAILURE)
        runner = Runner(output, 1)
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertIsNone(report["violations"])
        self.assertSummary(runner, "false", "unknown", "FAIL")

    def test_06_exit_zero_without_analysis(self):
        runner = Runner("[INFO] Building protos\n[INFO] BUILD SUCCESS\n", 0)
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertFalse(report["analysis_ran"])
        self.assertIsNone(report["violations"])
        self.assertSummary(runner, "false", "unknown", "FAIL")

    def test_07_missing_methods_start_or_end(self):
        for drop in (gate.METHODS_START, gate.METHODS_END):
            methods = [line for line in METHODS if drop not in line]
            code, report = self.run_check(Runner(text(PRELUDE, methods, TREE, SUCCESS), 0))
            self.assertEqual(code, 1, drop)
            self.assertFalse(report["runtime_compile_methods_complete"], drop)

    def test_08_missing_tree_start_or_end(self):
        for drop in (gate.TREE_START, gate.TREE_END):
            tree = [line for line in TREE if drop not in line]
            code, report = self.run_check(Runner(text(PRELUDE, METHODS, tree, SUCCESS), 0))
            self.assertEqual(code, 1, drop)
            self.assertFalse(report["call_tree_complete"], drop)

    def test_09_complete_sections_without_protos_roots(self):
        methods = [gate.METHODS_START, "java.lang.String.length()", gate.METHODS_END]
        tree = [gate.TREE_START, "java.lang.String.length()", gate.TREE_END]
        code, report = self.run_check(Runner(text(PRELUDE, methods, tree, SUCCESS), 0))
        self.assertEqual(code, 1)
        self.assertFalse(report["protos_roots_detected"])

    def test_10_truncated_log(self):
        truncated = text(PRELUDE, METHODS, TREE[:2])
        code, report = self.run_check(Runner(truncated, 0))
        self.assertEqual(code, 1)
        self.assertFalse(report["analysis_complete"])
        self.assertIsNone(report["violations"])

    def test_11_process_cannot_start(self):
        runner = Runner("", start_error=FileNotFoundError("mvn"))
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertIsNone(report["exit_code"])
        self.assertIsNone(report["violations"])
        self.assertSummary(runner, "false", "unknown", "FAIL")

    def test_12_timeout(self):
        runner = Runner(text(PRELUDE), 0, hang=True)
        code, report = self.run_check(runner, timeout=1)
        self.assertEqual(code, 1)
        self.assertTrue(report["timed_out"])
        self.assertTrue(runner.process.terminated)
        self.assertSummary(runner, "false", "unknown", "FAIL")

    def test_13_previous_pass_is_not_reused(self):
        code, report = self.run_check(Runner(CLEAN, 0))
        self.assertEqual(report["status"], "PASS")
        code, report = self.run_check(Runner(text(PRELUDE, FAILURE), 1))
        self.assertEqual(code, 1)
        self.assertEqual(report["status"], "FAIL")
        self.assertNotIn(gate.METHODS_START, self.log.read_text(encoding="utf-8"))

    def test_14_combined_output_kept_complete_and_in_order(self):
        runner = Runner(CLEAN, 0)
        self.run_check(runner)
        self.assertEqual(self.strict_log.read_text(encoding="utf-8"), CLEAN)
        self.assertIn(CLEAN, self.log.read_text(encoding="utf-8"))
        self.assertIn(CLEAN, runner.terminal.getvalue())
        _, kwargs = runner.calls[0]
        self.assertIs(kwargs["stderr"], subprocess.STDOUT)
        self.assertNotIn("shell", kwargs)

    def test_15_output_streams_live(self):
        runner = Runner(CLEAN, 0)
        self.run_check(runner)
        lines = CLEAN.splitlines(True)
        for index in range(1, len(lines)):
            self.assertTrue(runner.observed[index].endswith("".join(lines[:index])))

    def test_16_thousands_separators(self):
        for spelled in ("1,234", "1.234", "1_234", "1234"):
            self.assertEqual(gate.parse_count(
                gate.HEADER_RE.search("=== Found %s compilation blocklist violations ===" % spelled)
                .group(1)), 1234, spelled)

    def test_17_maven_status_contradicts_graalvm(self):
        output = text(PRELUDE, METHODS, TREE, ["[INFO] BUILD SUCCESS", "[INFO] BUILD FAILURE"])
        code, report = self.run_check(Runner(output, 0))
        self.assertEqual(code, 1)
        self.assertTrue(any("contradiction" in f for f in report["failures"]))
        code, report = self.run_check(Runner(CLEAN, 1))
        self.assertEqual(code, 1)
        self.assertTrue(any("contradiction" in f for f in report["failures"]))

    def test_18_fake_zero_exit_process_cannot_bypass(self):
        for output in ("", "[INFO] BUILD SUCCESS\n",
                       "=== Found 0 compilation blocklist violations ===\n[INFO] BUILD SUCCESS\n",
                       text(METHODS, TREE, SUCCESS)):
            code, report = self.run_check(Runner(output, 0))
            self.assertEqual(code, 1, output)
            self.assertEqual(report["status"], "FAIL", output)

    def test_artifacts_outside_root_are_rejected(self):
        with self.assertRaises(SystemExit):
            gate.check(str(Path(self._dir.name) / "report.json"), str(self.log), 1, ["mvn"],
                       artifact_root=str(self.root), terminal=io.StringIO(),
                       popen=Runner(CLEAN).popen)



class CollectionModeRegressionTest(GateCase):
    """-TruffleCheckBlockListMethods + PrintRuntimeCompileMethods: GraalVM prints
    the full violation inventory and completes the analysis; the gate enforces zero."""

    def test_r01_251_violations_complete_analysis_exit_zero(self):
        runner = Runner(text(PRELUDE, violations(251), METHODS, TREE, SUCCESS), 0)
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertTrue(report["analysis_complete"])
        self.assertEqual(report["blocklist_violations"], 251)
        self.assertEqual(report["blocklisted_method_count"], 251)
        self.assertEqual(len(set(report["blocklisted_methods"])), 251)
        self.assertEqual(report["result"], "FAIL")
        self.assertEqual(report["schema"], "protos.native-runtime-compilation/v1")
        self.assertIn("com.example.Caller250.call()", self.log.read_text(encoding="utf-8"))
        self.assertSummary(runner, "true", "251", "FAIL")

    def test_r02_one_violation_complete_analysis(self):
        output = text(PRELUDE, header(1), violation("java.lang.Thread.sleep(long)", TRACE_B),
                      METHODS, TREE, SUCCESS)
        runner = Runner(output, 0)
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertTrue(report["analysis_complete"])
        self.assertSummary(runner, "true", "1", "FAIL")

    def test_r03_complete_analysis_zero_violations(self):
        runner = Runner(CLEAN, 0)
        code, report = self.run_check(runner)
        self.assertEqual(code, 0)
        self.assertEqual(report["result"], "PASS")
        self.assertEqual(report["blocklist_violations"], 0)

    def test_r04_251_violations_incomplete_tree_keeps_count(self):
        runner = Runner(text(PRELUDE, violations(251), METHODS, TREE[:2], SUCCESS), 0)
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertFalse(report["analysis_complete"])
        self.assertEqual(report["blocklist_violations"], 251)
        self.assertSummary(runner, "false", "251", "FAIL")

    def test_r05_251_violations_maven_error_keeps_diagnostic(self):
        output = text(PRELUDE, violations(251), ABORT, FAILURE)
        code, report = self.run_check(Runner(output, 1))
        self.assertEqual(code, 1)
        self.assertEqual(report["blocklist_violations"], 251)
        self.assertEqual(report["blocklisted_method_count"], 251)
        self.assertEqual(self.strict_log.read_text(encoding="utf-8"), output)
        self.assertEqual(report["phases"]["graph"]["status"], "not-run")

    def test_r06_no_compile_methods_listing(self):
        code, report = self.run_check(Runner(text(PRELUDE, TREE, SUCCESS), 0))
        self.assertEqual(code, 1)
        self.assertFalse(report["analysis_complete"])
        self.assertIsNone(report["blocklist_violations"])

    def test_r07_no_call_tree(self):
        code, report = self.run_check(Runner(text(PRELUDE, METHODS, SUCCESS), 0))
        self.assertEqual(code, 1)
        self.assertFalse(report["call_tree_complete"])
        empty_tree = [TREE[0], TREE[-1]]
        code, report = self.run_check(Runner(text(PRELUDE, METHODS, empty_tree, SUCCESS), 0))
        self.assertEqual(code, 1)
        self.assertFalse(report["analysis_complete"])

    def test_r08_declared_count_mismatch(self):
        output = text(PRELUDE, header(251), violations(250)[1:], METHODS, TREE, SUCCESS)
        code, report = self.run_check(Runner(output, 0))
        self.assertEqual(code, 1)
        self.assertTrue(any("inconsistent" in f for f in report["failures"]))

    def test_r09_collection_mode_cannot_turn_violations_into_pass(self):
        for count in (1, 2, 251, 1234):
            output = text(PRELUDE, violations(count), METHODS, TREE, SUCCESS)
            code, report = self.run_check(Runner(output, 0))
            self.assertEqual((code, report["result"]), (1, "FAIL"), count)
            self.assertEqual(report["blocklist_violations"], count)

    def test_r10_later_run_does_not_reuse_zero_report(self):
        code, report = self.run_check(Runner(CLEAN, 0))
        self.assertEqual(report["blocklist_violations"], 0)
        code, report = self.run_check(Runner(text(PRELUDE, ["[INFO] BUILD FAILURE"]), 1))
        self.assertEqual(code, 1)
        self.assertIsNone(report["blocklist_violations"])
        self.assertEqual(report["result"], "FAIL")


class TwoPhaseRegressionTest(GateCase):
    """Phase A strict inventory (neverPartOfCompilation checked); Phase B diagnostic graph."""

    def strict_rejected(self, blocklist=251, never=14, blocks=None):
        body = violations(blocklist) if blocklist else []
        return (text(PRELUDE, body, npoc(never, blocks), BUILD_REQUEST, FAILURE), 1, False)

    def graph_ok(self, blocklist=251):
        body = violations(blocklist) if blocklist else []
        return (text(PRELUDE, body, METHODS, TREE, SUCCESS), 0, False)

    def test_t01_blocklist_and_never_part_then_complete_graph(self):
        runner = Runner(phases=[self.strict_rejected(), self.graph_ok()])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertEqual(report["result"], "FAIL")
        self.assertTrue(report["analysis_complete"])
        self.assertTrue(report["call_tree_complete"])
        self.assertTrue(report["graph_phase_ran"])
        self.assertTrue(report["blocklist_phases_consistent"])
        self.assertEqual(report["blocklist_violations"], 251)
        self.assertEqual(report["never_part_of_compilation_violations"], 14)
        self.assertEqual(report["phases"]["strict"]["status"],
                         "never-part-of-compilation-rejection")
        self.assertEqual(report["phases"]["strict"]["exit_code"], 1)
        self.assertEqual(report["phases"]["graph"]["status"], "completed")
        self.assertEqual(report["phases"]["graph"]["exit_code"], 0)
        self.assertEqual(report["errors"], [])
        self.assertSummary(runner, "true", "251", "FAIL", never="14", tree="COMPLETE")

    def test_t02_zero_violations_passes_without_graph_phase(self):
        runner = Runner(phases=[(CLEAN, 0, False)])
        code, report = self.run_check(runner)
        self.assertEqual(code, 0)
        self.assertEqual(len(runner.calls), 1)
        self.assertFalse(report["graph_phase_ran"])
        self.assertEqual(report["phases"]["graph"]["status"], "not-run")
        self.assertEqual(report["never_part_of_compilation_violations"], 0)
        self.assertSummary(runner, "true", "0", "PASS", never="0", tree="COMPLETE")

    def test_t03_blocklist_only_complete_strict_analysis(self):
        runner = Runner(phases=[self.graph_ok(251)])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertEqual(len(runner.calls), 1)
        self.assertEqual(report["never_part_of_compilation_violations"], 0)
        self.assertSummary(runner, "true", "251", "FAIL", never="0", tree="COMPLETE")

    def test_t04_never_part_only_then_complete_graph(self):
        runner = Runner(phases=[self.strict_rejected(blocklist=0), self.graph_ok(0)])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertEqual(report["never_part_of_compilation_violations"], 14)
        self.assertEqual(report["blocklist_violations"], 0)
        self.assertSummary(runner, "true", "0", "FAIL", never="14", tree="COMPLETE")

    def test_t05_declared_14_but_13_blocks(self):
        runner = Runner(phases=[self.strict_rejected(never=14, blocks=13), self.graph_ok()])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertEqual(report["never_part_of_compilation_entry_count"], 13)
        self.assertTrue(any("neverPartOfCompilation violations but 13" in f
                            for f in report["failures"]))

    def test_t06_graph_phase_blocklist_count_differs(self):
        runner = Runner(phases=[self.strict_rejected(), self.graph_ok(250)])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertFalse(report["blocklist_phases_consistent"])
        self.assertEqual(report["blocklist_violations"], 251)

    def test_t07_graph_phase_without_tree(self):
        graph = (text(PRELUDE, violations(251), METHODS, SUCCESS), 0, False)
        runner = Runner(phases=[self.strict_rejected(), graph])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertFalse(report["call_tree_complete"])
        self.assertSummary(runner, "false", "251", "FAIL", never="14", tree="INCOMPLETE")

    def test_t08_graph_phase_timeout(self):
        runner = Runner(phases=[self.strict_rejected(), (text(PRELUDE), 0, True)])
        code, report = self.run_check(runner, timeout=1)
        self.assertEqual(code, 1)
        self.assertEqual(report["phases"]["graph"]["status"], "timeout")
        self.assertTrue(report["timed_out"])
        self.assertTrue(runner.process.terminated)
        self.assertSummary(runner, "false", "251", "FAIL", never="14", tree="INCOMPLETE")

    def test_t09_graph_phase_exit_zero_without_native_image(self):
        runner = Runner(phases=[self.strict_rejected(), ("[INFO] BUILD SUCCESS\n", 0, False)])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertTrue(any("graph phase: Native Image analysis did not run" in f
                            for f in report["failures"]))

    def test_t10_other_strict_error_is_preserved_and_stops(self):
        output = text(PRELUDE, violations(251), npoc(14),
                      ["Error: Unsupported features in 3 methods"], BUILD_REQUEST, FAILURE)
        runner = Runner(phases=[(output, 1, False)])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertEqual(len(runner.calls), 1)
        self.assertEqual(report["phases"]["strict"]["status"], "failed")
        self.assertEqual(report["phases"]["strict"]["unexpected_errors"],
                         ["Error: Unsupported features in 3 methods"])
        self.assertTrue(any("Unsupported features" in f for f in report["failures"]))

    def test_t11_graph_phase_required_but_not_run(self):
        runner = Runner(phases=[self.strict_rejected()])
        code = gate.check(str(self.report), str(self.log), 60, STRICT_COMMAND,
                          artifact_root=str(self.root), terminal=runner.terminal,
                          popen=runner.popen)
        report = json.loads(self.report.read_text(encoding="utf-8"))
        self.assertEqual(code, 1)
        self.assertFalse(report["graph_phase_ran"])
        self.assertFalse(report["analysis_complete"])
        self.assertTrue(any("graph phase required" in f for f in report["failures"]))

    def test_t12_previous_phase_results_are_not_combined(self):
        code, report = self.run_check(Runner(phases=[self.strict_rejected(), self.graph_ok()]))
        self.assertTrue(self.graph_log.exists())
        code, report = self.run_check(Runner(phases=[self.strict_rejected()[:1] + (1, True)]),
                                      timeout=1)
        self.assertEqual(code, 1)
        self.assertFalse(self.graph_log.exists())
        self.assertFalse(report["graph_phase_ran"])
        self.assertFalse(report["analysis_complete"])

    def test_t13_report_keeps_all_traces(self):
        code, report = self.run_check(Runner(phases=[self.strict_rejected(), self.graph_ok()]))
        self.assertEqual(len(report["blocklist_entries"]), 251)
        self.assertTrue(all(len(e["trace"]) == 2 for e in report["blocklist_entries"]))
        self.assertEqual(report["blocklist_entries"][250]["trace"][0], "com.example.Caller250.call()")
        self.assertEqual(len(report["never_part_of_compilation_entries"]), 14)
        self.assertTrue(all(len(e["runtime_trace"]) == 2
                            for e in report["never_part_of_compilation_entries"]))
        log = self.log.read_text(encoding="utf-8")
        self.assertIn("strict phase", log)
        self.assertIn("graph phase", log)
        self.assertIn("Site13.slowPath", self.strict_log.read_text(encoding="utf-8"))
        self.assertIn(gate.TREE_END, self.graph_log.read_text(encoding="utf-8"))

    def test_t14_both_phases_stream_live(self):
        phases = [self.strict_rejected(), self.graph_ok()]
        expected = [p[0].splitlines(True) for p in phases]
        runner = Runner(phases=phases)
        self.run_check(runner)
        offset = 0
        for lines in expected:
            for index in range(1, len(lines)):
                self.assertTrue(runner.observed[offset + index].endswith("".join(lines[:index])))
            offset += len(lines)

    def test_t15_phase_commands(self):
        runner = Runner(phases=[self.strict_rejected(), self.graph_ok()])
        code, report = self.run_check(runner)
        self.assertEqual([call[0] for call in runner.calls], [STRICT_COMMAND, GRAPH_COMMAND])
        self.assertEqual(report["commands"], {"strict": STRICT_COMMAND, "graph": GRAPH_COMMAND})
        for _, kwargs in runner.calls:
            self.assertNotIn("shell", kwargs)
            self.assertIs(kwargs["stderr"], subprocess.STDOUT)

    def test_t18_no_violation_keeps_pass(self):
        code, report = self.run_check(Runner(CLEAN, 0))
        self.assertEqual((code, report["result"]), (0, "PASS"))

    def test_t19_single_violation_of_either_category_fails(self):
        code, report = self.run_check(Runner(phases=[self.graph_ok(1)]))
        self.assertEqual((code, report["blocklist_violations"]), (1, 1))
        code, report = self.run_check(Runner(phases=[self.strict_rejected(0, 1), self.graph_ok(0)]))
        self.assertEqual((code, report["never_part_of_compilation_violations"]), (1, 1))

    def test_never_part_header_thousands_and_exit_zero_contradiction(self):
        self.assertEqual(gate.parse_count(gate.NPOC_HEADER_RE.search(npoc(1234, 0)[0]).group(1)),
                         1234)
        output = text(PRELUDE, npoc(1), METHODS, TREE, SUCCESS)
        code, report = self.run_check(Runner(output, 0))
        self.assertEqual(code, 1)
        self.assertTrue(any("contradiction" in f for f in report["failures"]))


SEAL_TRACE = ["  at com.oracle.graal.pointsto.flow.TypeFlow.seal(TypeFlow.java:100)",
              "  at com.oracle.svm.hosted.NativeImageGenerator.runPointsToAnalysis"
              "(NativeImageGenerator.java:200)"]


def sealed(trace=True):
    lines = ["Error: Should not reach here: Sealed problem:"]
    if trace:
        return lines + ["stack trace:"] + SEAL_TRACE + ["end trace:"]
    return lines + ["stack trace is unknown"]


class SealedProblemRegressionTest(GateCase):
    """GraalVM 'Should not reach here: Sealed problem' in the diagnostic graph phase."""

    def strict_rejected(self, blocklist=251):
        return (text(PRELUDE, violations(blocklist), npoc(14), BUILD_REQUEST, FAILURE), 1, False)

    def sealed_graph(self, trace=True, sections=True, blocklist=251):
        body = [METHODS, TREE] if sections else []
        return (text(PRELUDE, violations(blocklist), *(body + [sealed(trace), BUILD_REQUEST,
                                                              FAILURE])), 1, False)

    def test_s01_sealed_problem_with_full_trace(self):
        runner = Runner(phases=[self.strict_rejected(), self.sealed_graph(trace=True)])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertTrue(report["graph_sealed_failure"])
        self.assertTrue(report["graph_seal_trace_available"])
        self.assertEqual(report["graph_seal_stack_trace"], SEAL_TRACE)
        self.assertFalse(report["analysis_complete"])
        self.assertIn(SEAL_TRACE[0], self.log.read_text(encoding="utf-8"))
        self.assertIn(SEAL_TRACE[1], self.graph_log.read_text(encoding="utf-8"))
        self.assertTrue(any("Sealed problem" in f for f in report["failures"]))
        self.assertSummary(runner, "false", "251", "FAIL", never="14", tree="INCOMPLETE")

    def test_s02_sealed_problem_with_unknown_trace(self):
        runner = Runner(phases=[self.strict_rejected(), self.sealed_graph(trace=False)])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertTrue(report["graph_sealed_failure"])
        self.assertFalse(report["graph_seal_trace_available"])
        self.assertIsNone(report["graph_seal_stack_trace"])
        self.assertIn("stack trace is unknown", self.log.read_text(encoding="utf-8"))

    def test_s03_sealed_problem_before_analysis_finishes(self):
        runner = Runner(phases=[self.strict_rejected(), self.sealed_graph(sections=False)])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertFalse(report["analysis_complete"])
        self.assertFalse(report["call_tree_complete"])
        self.assertEqual(report["phases"]["graph"]["status"], "failed")

    def test_s04_strict_violations_kept_when_graph_phase_fails(self):
        code, report = self.run_check(Runner(phases=[self.strict_rejected(),
                                                     self.sealed_graph()]))
        self.assertEqual(report["blocklist_violations"], 251)
        self.assertEqual(len(report["blocklist_entries"]), 251)
        self.assertEqual(report["never_part_of_compilation_violations"], 14)
        self.assertEqual(len(report["never_part_of_compilation_entries"]), 14)
        self.assertEqual(report["phases"]["strict"]["status"],
                         "never-part-of-compilation-rejection")

    def test_s05_phase_inventory_difference_is_diagnostic_evidence(self):
        graph = (text(PRELUDE, violations(250), METHODS, TREE, SUCCESS), 0, False)
        code, report = self.run_check(Runner(phases=[self.strict_rejected(), graph]))
        self.assertEqual(code, 1)
        comparison = report["blocklist_phase_comparison"]
        self.assertFalse(comparison["equal"])
        self.assertEqual((comparison["strict_count"], comparison["graph_count"]), (251, 250))
        self.assertEqual(comparison["only_in_strict"], ["com.example.Blocked.m250() "
                                                        "(target: com.example.Blocked.m250())"])
        self.assertEqual(comparison["only_in_graph"], [])
        self.assertIn("different", comparison["note"])
        self.assertFalse(any("differs between phases" in f for f in report["failures"]))
        self.assertEqual(report["blocklist_violations"], 251)
        self.assertEqual(report["errors"], [])

    def test_s07_no_false_pass_on_any_sealed_problem(self):
        clean_sealed = text(PRELUDE, METHODS, TREE, sealed(True), SUCCESS)
        variants = [
            [(clean_sealed, 0, False)],
            [(text(PRELUDE, METHODS, TREE, sealed(False), SUCCESS), 0, False)],
            [self.strict_rejected(0)[:1] + (1, False), self.sealed_graph(blocklist=0)],
            [self.strict_rejected(), self.sealed_graph(trace=False, sections=False)],
        ]
        for phases in variants:
            code, report = self.run_check(Runner(phases=phases))
            self.assertEqual((code, report["result"]), (1, "FAIL"))
            self.assertFalse(report["analysis_complete"])


HOSTED_CAUSE = ['Error: java.lang.NullPointerException: Cannot invoke '
                '"jdk.vm.ci.meta.JavaConstant.getJavaKind()"',
                'because "hostedConstant" is null']
HOSTED_FRAMES = [
    "\tat com.oracle.svm.graal.SubstrateGraalUtils.hostedToRuntime(SubstrateGraalUtils.java:80)",
    "\tat com.oracle.svm.graal.hosted.runtimecompilation.RuntimeCompilationGraphEncoder"
    ".replaceObjectForEncoding(RuntimeCompilationGraphEncoder.java:120)",
    "\tat jdk.graal.compiler.nodes.GraphEncoder.prepare(GraphEncoder.java:300)",
    "\tat com.oracle.svm.graal.hosted.runtimecompilation.RuntimeCompilationParsingSupport"
    ".validateGraph(RuntimeCompilationParsingSupport.java:60)",
]
CONTEXTS = [
    "parsing com.guillermomolina.protos.compiler.CanonicalToBytecodeLowerer"
    ".primitiveNumberLiteralOrNull(CanonicalToBytecodeLowerer.java:900)",
    "parsing com.guillermomolina.protos.compiler.CanonicalBindingAnalyzer"
    ".analyze(CanonicalBindingAnalyzer.java:40)",
]


def hosted_failure(contexts=CONTEXTS, single_line=False):
    cause = [" ".join(HOSTED_CAUSE)] if single_line else list(HOSTED_CAUSE)
    lines = cause + HOSTED_FRAMES
    if contexts:
        lines += ["Parsing context:"] + ["   " + context for context in contexts]
    return lines


class GraphFailureClassificationTest(GateCase):
    """Sealed problem vs null hosted constant in runtime-compilation graph encoding."""

    def strict_rejected(self):
        return (text(PRELUDE, violations(142), npoc(4), BUILD_REQUEST, FAILURE), 1, False)

    def graph(self, failure, sections=False):
        body = [METHODS, TREE] if sections else []
        return (text(PRELUDE, violations(142), *(body + [failure, BUILD_REQUEST, FAILURE])),
                1, False)

    def run_graph(self, failure, sections=False):
        runner = Runner(phases=[self.strict_rejected(), self.graph(failure, sections)])
        code, report = self.run_check(runner)
        return runner, code, report

    def test_c01_sealed_problem_with_trace(self):
        _, code, report = self.run_graph(sealed(True))
        self.assertEqual(code, 1)
        self.assertEqual(report["graph_failure_kind"], "sealed-problem")
        self.assertIn("Sealed problem", report["graph_failure_cause"])
        self.assertEqual(report["graph_failure_stack_trace"], SEAL_TRACE)
        self.assertTrue(report["graph_seal_trace_available"])

    def test_c02_sealed_problem_without_trace(self):
        _, code, report = self.run_graph(sealed(False))
        self.assertEqual(code, 1)
        self.assertEqual(report["graph_failure_kind"], "sealed-problem")
        self.assertIsNone(report["graph_failure_stack_trace"])
        self.assertFalse(report["graph_seal_trace_available"])

    def test_c03_hosted_constant_null(self):
        for single_line in (False, True):
            _, code, report = self.run_graph(hosted_failure(single_line=single_line))
            self.assertEqual(code, 1)
            self.assertEqual(report["graph_failure_kind"], "hosted-constant-null")
            self.assertIn('"hostedConstant" is null', report["graph_failure_cause"])
            self.assertIn("getJavaKind()", report["graph_failure_cause"])
            self.assertEqual(report["graph_failure_stack_trace"], HOSTED_FRAMES)
            self.assertTrue(all(report["graph_failure_call_chain"].values()))
            self.assertFalse(report["graph_sealed_failure"])
            self.assertFalse(report["graph_seal_trace_available"])

    def test_c04_several_parsing_contexts_kept_as_context(self):
        _, code, report = self.run_graph(hosted_failure())
        self.assertEqual(report["graph_failure_parsing_contexts"], CONTEXTS)
        trace = "\n".join(report["graph_failure_stack_trace"])
        self.assertNotIn("CanonicalToBytecodeLowerer", trace)
        self.assertNotIn("CanonicalBindingAnalyzer", trace)
        self.assertNotIn("CanonicalToBytecodeLowerer", report["graph_failure_cause"])

    def test_c05_encoder_failure_is_not_a_blocklist_violation(self):
        _, code, report = self.run_graph(hosted_failure())
        self.assertEqual(report["blocklist_violations"], 142)
        self.assertFalse(any("hostedToRuntime" in e["method"] for e in report["blocklist_entries"]))
        self.assertTrue(any("graph encoding failed (hosted-constant-null)" in f
                            for f in report["errors"]))
        self.assertFalse(any("hostedConstant" in f for f in report["failures"]
                             if f.startswith("GraalVM reported")))

    def test_c06_fail_with_incomplete_tree(self):
        for sections in (False, True):
            runner, code, report = self.run_graph(hosted_failure(), sections)
            self.assertEqual((code, report["result"]), (1, "FAIL"))
            self.assertFalse(report["analysis_complete"])
            self.assertFalse(report["call_tree_complete"])
            self.assertSummary(runner, "false", "142", "FAIL", never="4", tree="INCOMPLETE")

    def test_c07_strict_inventory_kept_on_encoder_failure(self):
        _, code, report = self.run_graph(hosted_failure())
        self.assertEqual(report["blocklist_violations"], 142)
        self.assertEqual(len(report["blocklist_entries"]), 142)
        self.assertEqual(report["never_part_of_compilation_violations"], 4)
        self.assertEqual(len(report["never_part_of_compilation_entries"]), 4)
        self.assertIsNone(report["strict_failure_kind"])
        self.assertEqual(report["phases"]["strict"]["status"],
                         "never-part-of-compilation-rejection")

    def test_c08_c09_c10_assertion_scoping_in_profiles(self):
        profiles = PomProfileTest("test_s06_builder_assertions_only_in_graph_profile")
        tree = ET.parse(str(ROOT / "pom.xml"))
        ns = PomProfileTest.NS
        for profile in tree.getroot().findall("m:profiles/m:profile", ns):
            args = [a.text for a in profile.iter("{%s}buildArg" % ns["m"])]
            self.assertNotIn("-J-ea", args, profile.findtext("m:id", namespaces=ns))
            self.assertNotIn("-J-enableassertions", args)
        graph_args = [a.text for a in profiles.profile_plugin("native-analysis-graph").findall(
            "m:configuration/m:buildArgs/m:buildArg", ns)]
        self.assertIn("-J-ea:jdk.graal.compiler.debug.Assertions", graph_args)
        native = profiles.profile_plugin("native")
        native_args = [a.text for a in native.findall("m:configuration/m:buildArgs/m:buildArg", ns)]
        self.assertEqual([a for a in native_args if not a.startswith("@")],
                         ["-march=compatibility", "--enable-native-access=ALL-UNNAMED"])
        self.assertFalse(any((a or "").startswith("-J-ea") for a in native_args))
        self.assertFalse(any((a or "").startswith("-H:") for a in native_args))


LOWERER = "com.guillermomolina.protos.compiler.CanonicalToBytecodeLowerer"
REACH_FRAMES = [
    LOWERER + ".primitiveNumberLiteralOrNull%%R(CanonicalToBytecodeLowerer.java:900)",
    LOWERER + ".lower(CanonicalToBytecodeLowerer.java:100)",
    "com.guillermomolina.protos.bytecode.ProtosBytecodeRootNodeGen.execute(Gen.java:10)",
]


def parsing_failure(causes=()):
    lines = ["Fatal error: com.oracle.graal.pointsto.util.AnalysisError$ParsingError: "
             "Parsing failed on a special method version: " + LOWERER
             + ".primitiveNumberLiteralOrNull%%R(CanonicalNode)",
             "Method reachability trace"]
    lines += ["\tat " + frame for frame in REACH_FRAMES]
    lines += ["Error encountered while parsing " + LOWERER + ".primitiveNumberLiteralOrNull%%R",
              "Parsing context:",
              "\tat " + LOWERER + ".primitiveNumberLiteralOrNull(CanonicalToBytecodeLowerer.java:905)"]
    for exception, frames in causes:
        lines += ["Caused by: " + exception] + ["\tat " + frame for frame in frames]
    return lines


NESTED_CAUSES = [
    ("com.oracle.svm.core.util.VMError$HostedError: graph validation failed",
     ["com.oracle.svm.hosted.Validator.validate(Validator.java:1)"]),
    ('java.lang.NullPointerException: Cannot invoke "jdk.vm.ci.meta.JavaConstant.getJavaKind()" '
     'because "hostedConstant" is null',
     [f.strip()[3:] for f in HOSTED_FRAMES]),
]


class ReportRunner(Runner):
    """Runner whose launches may create files (e.g. svm_err_b_*.md) like the real builder."""

    def __init__(self, phases, on_launch=()):
        Runner.__init__(self, phases=phases)
        self.on_launch = list(on_launch)

    def popen(self, command, **kwargs):
        if self.on_launch:
            hook = self.on_launch.pop(0)
            if hook is not None:
                hook()
        return Runner.popen(self, command, **kwargs)


def kinds(report, phase=None):
    return [f["kind"] for f in report["native_image_failures"]
            if phase is None or f["phase"] == phase]


class InventoryCase(GateCase):

    def strict_rejected(self, blocklist=177, never=6, blocks=None):
        body = violations(blocklist) if blocklist else []
        return (text(PRELUDE, body, npoc(never, blocks), BUILD_REQUEST, FAILURE), 1, False)

    def graph_ok(self, blocklist=177):
        body = violations(blocklist) if blocklist else []
        return (text(PRELUDE, body, METHODS, TREE, SUCCESS), 0, False)

    def graph_parsing(self, blocklist=169, causes=()):
        return (text(PRELUDE, violations(blocklist), parsing_failure(causes), BUILD_REQUEST,
                     FAILURE), 1, False)

    def assertProperties(self, runner, report, detector, analysis, inventory, status):
        summary = self.summary(runner)
        self.assertEqual(summary["NATIVE_RUNTIME_DETECTOR_OPERATIONAL"], detector)
        self.assertEqual(summary["NATIVE_RUNTIME_ANALYSIS_COMPLETE"], analysis)
        self.assertEqual(summary["NATIVE_RUNTIME_GLOBAL_INVENTORY_COMPLETE"], inventory)
        self.assertEqual(summary["NATIVE_RUNTIME_COMPILATION_CHECK"], status)
        self.assertEqual(report["detector_operational"], detector == "true")
        self.assertEqual(report["graalvm_analysis_complete"], analysis == "true")
        self.assertEqual(report["global_inventory_complete"], inventory == "true")
        self.assertEqual(report["checks"]["POLICY_PASS"], status == "PASS")
        self.assertEqual(report["result"], status)
        for key in ("strict_phase", "graph_phase", "blocklist", "never_part_of_compilation",
                    "observed_reachability_graph", "runtime_compilation_call_tree",
                    "phase_comparison", "native_image_failures", "diagnostic_artifacts",
                    "failure_reasons", "checks"):
            self.assertIn(key, report)
        self.assertEqual(set(report["checks"]), {"PARSER_SUCCESS", "GRAALVM_ANALYSIS_SUCCESS",
                                                 "GLOBAL_INVENTORY_COMPLETE", "POLICY_PASS"})
        if status == "FAIL":
            self.assertTrue(report["failure_reasons"])
            self.assertTrue(report["failures"])


class CompleteExecutionTest(InventoryCase):

    def test_e01_zero_violations_complete_tree_passes(self):
        runner = Runner(CLEAN, 0)
        code, report = self.run_check(runner)
        self.assertEqual(code, 0)
        self.assertProperties(runner, report, "true", "true", "true", "PASS")
        self.assertSummary(runner, "true", "0", "PASS", never="0", tree="COMPLETE")
        self.assertTrue(all(report["checks"].values()))
        self.assertEqual(report["runtime_compilation_call_tree"]["status"], "COMPLETE")
        self.assertEqual(report["runtime_compilation_call_tree"]["call_tree"]["roots"],
                         [TREE[1]])
        self.assertTrue(report["blocklist"]["exhaustive"])
        self.assertTrue(report["never_part_of_compilation"]["exhaustive"])
        self.assertEqual(report["native_image_failures"], [])
        self.assertEqual(report["failure_reasons"], [])
        self.assertEqual(report["strict_phase"]["termination"], "exited-success")
        self.assertEqual(report["strict_phase"]["command"], STRICT_COMMAND)
        self.assertTrue(report["strict_phase"]["started_at"])
        self.assertFalse(report["graph_phase"]["executed"])

    def test_e02_one_blocklist_violation_complete_tree_fails(self):
        runner = Runner(phases=[self.graph_ok(1)])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertProperties(runner, report, "true", "true", "true", "FAIL")
        self.assertSummary(runner, "true", "1", "FAIL", never="0", tree="COMPLETE")

    def test_e03_one_never_part_violation_complete_tree_fails(self):
        runner = Runner(phases=[self.strict_rejected(0, 1), self.graph_ok(0)])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertProperties(runner, report, "true", "true", "true", "FAIL")
        self.assertSummary(runner, "true", "0", "FAIL", never="1", tree="COMPLETE")
        self.assertEqual(kinds(report, "strict"), ["NEVER_PART_OF_COMPILATION_ABORT"])

    def test_e04_both_categories_complete_tree_fails(self):
        runner = Runner(phases=[self.strict_rejected(3, 2), self.graph_ok(3)])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertProperties(runner, report, "true", "true", "true", "FAIL")
        self.assertSummary(runner, "true", "3", "FAIL", never="2", tree="COMPLETE")


class InterruptedExecutionTest(InventoryCase):

    def test_i01_177_and_6_without_tree_fails(self):
        graph = (text(PRELUDE, violations(169), FAILURE), 1, False)
        runner = Runner(phases=[self.strict_rejected(), graph])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertProperties(runner, report, "true", "false", "false", "FAIL")
        self.assertSummary(runner, "false", "177", "FAIL", never="6", tree="INCOMPLETE")
        strict = report["blocklist"]["strict"]
        self.assertEqual((strict["reported_count"], strict["parsed_count"]), (177, 177))
        self.assertTrue(strict["report_consistent"])
        self.assertFalse(strict["exhaustive"])
        self.assertEqual(len(strict["entries"]), 177)
        self.assertNotEqual(report["runtime_compilation_call_tree"]["status"], "COMPLETE")

    def test_i02_169_graph_violations_and_parsing_failure(self):
        runner = Runner(phases=[self.strict_rejected(), self.graph_parsing()])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertProperties(runner, report, "true", "false", "false", "FAIL")
        self.assertSummary(runner, "false", "177", "FAIL", never="6", tree="INCOMPLETE")
        self.assertEqual(kinds(report, "graph"), ["RUNTIME_COMPILATION_PARSING_FAILURE"])
        failure = report["native_image_failures"][-1]
        self.assertIn("primitiveNumberLiteralOrNull%%R", failure["subject"])
        self.assertFalse(failure["subject_is_proven_cause"])
        self.assertFalse(failure["parsing_context_is_proven_cause"])
        details = failure["exception"]
        self.assertEqual(details["method_reachability_trace"]["frames"],
                         ["at " + frame for frame in REACH_FRAMES])
        self.assertIn("Error encountered while parsing",
                      details["error_encountered_while_parsing"]["heading"])
        self.assertEqual(len(details["parsing_context"]["entries"]), 1)
        self.assertEqual(report["blocklist"]["graph"]["reported_count"], 169)
        self.assertEqual(report["blocklist"]["strict"]["reported_count"], 177)
        self.assertFalse(report["checks"]["GRAALVM_ANALYSIS_SUCCESS"])
        self.assertTrue(report["checks"]["PARSER_SUCCESS"])
        self.assertIn("RUNTIME_COMPILATION_PARSING_FAILURE", report["blocking_failure_kinds"])

    def test_i03_sealed_problem_fails(self):
        graph = (text(PRELUDE, violations(169), sealed(True), BUILD_REQUEST, FAILURE), 1, False)
        runner = Runner(phases=[self.strict_rejected(), graph])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertProperties(runner, report, "true", "false", "false", "FAIL")
        failure = [f for f in report["native_image_failures"] if f["kind"] == "SEALED_PROBLEM"]
        self.assertEqual(len(failure), 1)
        self.assertEqual(failure[0]["seal_stack_trace"], SEAL_TRACE)
        self.assertEqual(failure[0]["phase"], "graph")

    def test_i04_hosted_constant_null_fails(self):
        graph = (text(PRELUDE, violations(169), hosted_failure(), BUILD_REQUEST, FAILURE), 1,
                 False)
        runner = Runner(phases=[self.strict_rejected(), graph])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertProperties(runner, report, "true", "false", "false", "FAIL")
        failure = [f for f in report["native_image_failures"]
                   if f["kind"] == "HOSTED_CONSTANT_ENCODING_FAILURE"]
        self.assertEqual(len(failure), 1)
        self.assertTrue(all(failure[0]["call_chain"].values()))
        self.assertEqual(failure[0]["parsing_contexts"], CONTEXTS)
        self.assertFalse(failure[0]["parsing_context_is_proven_cause"])

    def test_i05_maven_error_before_native_image(self):
        output = "[INFO] Scanning for projects...\n[ERROR] Could not resolve dependencies\n" \
                 "[INFO] BUILD FAILURE\n"
        runner = Runner(output, 1)
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertProperties(runner, report, "true", "false", "false", "FAIL")
        self.assertSummary(runner, "false", "unknown", "FAIL", never="unknown", tree="INCOMPLETE")
        self.assertEqual(kinds(report), ["MAVEN_FAILURE"])
        self.assertTrue(report["native_image_failures"][0]["before_native_image"])
        self.assertEqual(report["runtime_compilation_call_tree"]["status"], "NOT_PRODUCED")

    def test_i06_timeout_in_either_phase(self):
        runner = Runner(text(PRELUDE), 0, hang=True)
        code, report = self.run_check(runner, timeout=1)
        self.assertEqual(code, 1)
        self.assertProperties(runner, report, "true", "false", "false", "FAIL")
        self.assertEqual(kinds(report), ["NATIVE_IMAGE_TIMEOUT"])
        self.assertEqual(report["strict_phase"]["termination"], "timeout-terminated")
        runner = Runner(phases=[self.strict_rejected(), (text(PRELUDE), 0, True)])
        code, report = self.run_check(runner, timeout=1)
        self.assertEqual(code, 1)
        self.assertProperties(runner, report, "true", "false", "false", "FAIL")
        self.assertEqual(kinds(report, "graph"), ["NATIVE_IMAGE_TIMEOUT"])
        self.assertSummary(runner, "false", "177", "FAIL", never="6", tree="INCOMPLETE")

    def test_i07_native_image_starts_without_completing_sections(self):
        for output, code in ((text(PRELUDE, METHODS[:2]), 0), (text(PRELUDE, METHODS), 0),
                             (text(PRELUDE, METHODS, TREE[:2], SUCCESS), 0)):
            runner = Runner(output, code)
            exit_code, report = self.run_check(runner)
            self.assertEqual(exit_code, 1)
            self.assertProperties(runner, report, "true", "false", "false", "FAIL")
            self.assertIn("INCOMPLETE_OUTPUT", kinds(report))
            self.assertNotEqual(report["runtime_compilation_call_tree"]["status"], "COMPLETE")

    def test_i08_unknown_failure_is_kept_even_with_known_violations(self):
        output = text(PRELUDE, violations(2), METHODS, TREE,
                      ["Error: Something GraalVM never said before", "\tat x.Y.z(Y.java:1)"],
                      FAILURE)
        runner = Runner(output, 1)
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        unexpected = [f for f in report["native_image_failures"]
                      if f["kind"] == "NATIVE_IMAGE_UNEXPECTED_FAILURE"]
        self.assertEqual(len(unexpected), 1)
        self.assertEqual(unexpected[0]["block"], ["Error: Something GraalVM never said before",
                                                  "\tat x.Y.z(Y.java:1)"])
        self.assertFalse(report["graalvm_analysis_complete"])

    def test_i10_builder_unexpected_error_heading_is_classified(self):
        backend = "com.guillermomolina.protos.execution.ProtosNioCapturedTreeFilesystemBackend"
        failure = [
            "Failed generating 'protos-native-analysis' after 30.1s.",
            "",
            "The build process encountered an unexpected error:",
            "",
            "> com.oracle.graal.pointsto.util.ParallelExecutionException: Detected a "
            "java.lang.ref.Cleaner object in the image heap",
            "",
            "    '--trace-object-instantiation=java.lang.ref.Cleaner'",
            "",
            "Object was reached by",
            "  scanning root constant java.lang.ref.Cleaner@305cc2fe embedded in",
            "    " + backend + ".<init>(Backend.java:295)",
            "  parsing method " + backend + ".<init>(Backend.java:291) reachable via the "
            "parsing context",
            "    at " + backend + "$Builder.complete(Backend.java:179)",
            "    at com.guillermomolina.protos.cli.ProtosCli.dispatchCommand(ProtosCli.java:444)",
            "    at static root method.(Unknown Source)",
            "",
            "",
            "Please inspect the generated error report at: '/nonexistent/svm_err_b_x.md'",
        ]
        output = text(PRELUDE, violations(12), failure, FAILURE)
        runner = Runner(output, 1)
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertProperties(runner, report, "true", "false", "false", "FAIL")
        self.assertSummary(runner, "false", "12", "FAIL", never="unknown", tree="INCOMPLETE")
        self.assertEqual(kinds(report), ["POINTS_TO_ANALYSIS_FAILURE"])
        classified = report["native_image_failures"][0]
        self.assertTrue(classified["message"].startswith(
            "com.oracle.graal.pointsto.util.ParallelExecutionException: Detected a "
            "java.lang.ref.Cleaner"))
        self.assertIn("    at static root method.(Unknown Source)", classified["block"])
        self.assertNotIn("Please inspect", "\n".join(classified["block"]))
        reach = classified["exception"]["method_reachability_trace"]["frames"]
        self.assertEqual(reach[0], backend + ".<init>(Backend.java:291)")
        edges = {(e["caller"], e["callee"])
                 for e in report["observed_reachability_graph"]["edges"]}
        self.assertIn((backend + "$Builder.complete(Backend.java:179)",
                       backend + ".<init>(Backend.java:291)"), edges)
        self.assertEqual([r["source"] for r in
                          report["diagnostic_artifacts"]["rejected_error_reports"]],
                         ["/nonexistent/svm_err_b_x.md"])

    def test_i09_detector_internal_failure_is_never_pass(self):
        runner = Runner(CLEAN, 0)
        with mock.patch.object(gate, "evaluate_phases", side_effect=RuntimeError("boom")):
            code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertProperties(runner, report, "false", "false", "false", "FAIL")
        self.assertSummary(runner, "false", "unknown", "FAIL", never="unknown", tree="INCOMPLETE")
        self.assertFalse(report["checks"]["PARSER_SUCCESS"])
        self.assertIn("boom", report["detector_error"])


class IntegrityTest(InventoryCase):

    def test_n01_header_177_with_177_blocks_is_consistent(self):
        runner = Runner(phases=[self.graph_ok(177)])
        code, report = self.run_check(runner)
        inventory = report["blocklist"]["strict"]
        self.assertEqual((inventory["reported_count"], inventory["parsed_count"]), (177, 177))
        self.assertTrue(inventory["report_consistent"])
        self.assertTrue(inventory["exhaustive"])
        self.assertEqual([e["index"] for e in inventory["entries"]], list(range(177)))
        self.assertTrue(all(e["phase"] == "strict" for e in inventory["entries"]))
        self.assertEqual(inventory["entries"][5]["method_name"], "com.example.Blocked.m5()")
        self.assertEqual(inventory["entries"][5]["target"], "com.example.Blocked.m5()")
        self.assertFalse(any("inconsistent" in f for f in report["failures"]))

    def test_n02_header_177_with_176_blocks_fails(self):
        output = text(PRELUDE, header(177), violations(176)[1:], METHODS, TREE, SUCCESS)
        runner = Runner(output, 0)
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertFalse(report["blocklist"]["strict"]["report_consistent"])
        self.assertFalse(report["global_inventory_complete"])
        self.assertTrue(any("inconsistent" in f for f in report["failures"]))

    def test_n03_never_part_6_with_6_traces_is_consistent(self):
        code, report = self.run_check(Runner(phases=[self.strict_rejected(), self.graph_ok()]))
        inventory = report["never_part_of_compilation"]["strict"]
        self.assertEqual((inventory["reported_count"], inventory["parsed_count"]), (6, 6))
        self.assertTrue(inventory["report_consistent"])
        self.assertEqual(inventory["check_state"], "ENABLED")
        self.assertTrue(all(e["declared_count"] == 6 for e in inventory["entries"]))

    def test_n04_never_part_6_with_5_traces_fails(self):
        code, report = self.run_check(Runner(phases=[self.strict_rejected(blocks=5),
                                                     self.graph_ok()]))
        self.assertEqual(code, 1)
        self.assertFalse(report["never_part_of_compilation"]["strict"]["report_consistent"])
        self.assertFalse(report["global_inventory_complete"])

    def test_n05_category_not_run_is_unknown_never_zero(self):
        runner = Runner(text(PRELUDE), 0, hang=True)
        code, report = self.run_check(runner, timeout=1)
        never = report["never_part_of_compilation"]
        self.assertIsNone(never["accepted_count"])
        self.assertEqual(never["accepted_count_state"], "UNKNOWN")
        self.assertEqual(never["strict"]["count_state"], "UNKNOWN")
        self.assertEqual(never["graph"]["count_state"], "NOT_RUN")
        self.assertEqual(report["blocklist"]["graph"]["count_state"], "NOT_RUN")
        self.assertSummary(runner, "false", "unknown", "FAIL", never="unknown")
        code, report = self.run_check(Runner(phases=[self.strict_rejected(), self.graph_ok()]))
        graph = report["never_part_of_compilation"]["graph"]
        self.assertEqual(graph["check_state"], "DISABLED_FOR_DIAGNOSIS")
        self.assertEqual(graph["count_state"], "DISABLED")
        self.assertIsNone(graph["count"])

    def test_n06_previous_reports_are_not_reused(self):
        reports = Path(self._dir.name) / "builder"
        reports.mkdir()
        stale = reports / "svm_err_b_20200101T000000.000Z_pid1.md"
        stale.write_text("# old report\n", encoding="utf-8")
        os.utime(str(stale), (1000000000, 1000000000))
        errors = self.root / "errors"
        errors.mkdir(parents=True)
        (errors / "graph-svm_err_b_old.md").write_text("previous run\n", encoding="utf-8")
        output = text(PRELUDE, ["Error report: " + str(stale)], FAILURE)
        runner = Runner(output, 1)
        gate.check(str(self.report), str(self.log), 60, STRICT_COMMAND,
                   graph_command=GRAPH_COMMAND, artifact_root=str(self.root),
                   terminal=runner.terminal, popen=runner.popen, report_dirs=[reports])
        report = json.loads(self.report.read_text(encoding="utf-8"))
        artifacts = report["diagnostic_artifacts"]
        self.assertEqual(artifacts["error_reports"], [])
        self.assertEqual([r["source"] for r in artifacts["rejected_error_reports"]],
                         [os.path.realpath(str(stale))])
        self.assertFalse((errors / "graph-svm_err_b_old.md").exists())
        self.assertTrue(stale.exists())

    def test_n07_markdown_error_report_kept_whole_and_per_phase(self):
        reports = Path(self._dir.name) / "builder"
        reports.mkdir()
        content = "# GraalVM error report\n\n## Stack\n" + "frame line\n" * 500

        def produce():
            (reports / "svm_err_b_20261116T101010.123Z_pid42.md").write_text(
                content, encoding="utf-8")
        runner = ReportRunner([self.strict_rejected(), self.graph_parsing()],
                              on_launch=[None, produce])
        gate.check(str(self.report), str(self.log), 60, STRICT_COMMAND,
                   graph_command=GRAPH_COMMAND, artifact_root=str(self.root),
                   terminal=runner.terminal, popen=runner.popen, report_dirs=[reports])
        report = json.loads(self.report.read_text(encoding="utf-8"))
        preserved = report["diagnostic_artifacts"]["error_reports"]
        self.assertEqual(len(preserved), 1)
        self.assertEqual(preserved[0]["phase"], "graph")
        path = Path(preserved[0]["preserved_path"])
        self.assertTrue(gate._inside(str(path), str(self.root / "errors")))
        self.assertEqual(path.read_text(encoding="utf-8"), content)
        self.assertEqual(report["graph_phase"]["error_reports"], preserved)
        self.assertEqual(report["strict_phase"]["error_reports"], [])
        self.assertTrue((reports / "svm_err_b_20261116T101010.123Z_pid42.md").exists())

    def test_n08_all_nested_causes_are_kept(self):
        runner = Runner(phases=[self.strict_rejected(),
                                self.graph_parsing(causes=NESTED_CAUSES)])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        parsing = [f for f in report["native_image_failures"]
                   if f["kind"] == "RUNTIME_COMPILATION_PARSING_FAILURE"][0]
        causes = parsing["exception"]["causes"]
        self.assertEqual([c["exception"] for c in causes], [c[0] for c in NESTED_CAUSES])
        self.assertEqual(causes[1]["frames"], ["at " + f for f in NESTED_CAUSES[1][1]])
        self.assertIn("ParsingError", parsing["exception"]["head"])
        hosted = [f for f in report["native_image_failures"]
                  if f["kind"] == "HOSTED_CONSTANT_ENCODING_FAILURE"]
        self.assertEqual(len(hosted), 1)
        self.assertTrue(hosted[0]["nested"])
        self.assertTrue(all(hosted[0]["call_chain"].values()))


class PhaseComparisonTest(InventoryCase):

    def test_p01_strict_177_graph_169_difference_is_published_only(self):
        runner = Runner(phases=[self.strict_rejected(), self.graph_ok(169)])
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        comparison = report["phase_comparison"]
        self.assertEqual((comparison["strict_reported_count"],
                          comparison["graph_reported_count"]), (177, 169))
        self.assertEqual(len(comparison["common_methods"]), 169)
        self.assertEqual(len(comparison["strict_only_methods"]), 8)
        self.assertEqual(comparison["graph_only_methods"], [])
        self.assertEqual(comparison["interpretation"], "DIAGNOSTIC_ONLY")
        self.assertFalse(comparison["equivalence_claimed"])
        self.assertIsNone(comparison["merged_inventory"])
        self.assertFalse(any("inconsistent" in f or "parser" in f
                             for f in report["failure_reasons"]))
        self.assertEqual(report["blocklist_violations"], 177)
        self.assertEqual(report["blocklist"]["accepted_from"], "strict")

    def test_p02_phases_are_not_merged_into_a_complete_inventory(self):
        runner = Runner(phases=[self.strict_rejected(), self.graph_parsing()])
        code, report = self.run_check(runner)
        self.assertFalse(report["global_inventory_complete"])
        self.assertEqual(report["blocklist"]["accepted_count"], 177)
        self.assertFalse(report["blocklist"]["exhaustive"])
        self.assertIsNone(report["phase_comparison"]["merged_inventory"])

    def test_p03_diagnostic_phase_alone_never_passes(self):
        with self.assertRaises(SystemExit):
            gate.check(str(self.report), str(self.log), 60, GRAPH_COMMAND,
                       artifact_root=str(self.root), terminal=io.StringIO(),
                       popen=Runner(CLEAN).popen)
        with self.assertRaises(SystemExit):
            gate.check(str(self.report), str(self.log), 60,
                       ["mvn", "-Pnative,native-analysis", gate.NPOC_DISABLE_OPTION],
                       artifact_root=str(self.root), terminal=io.StringIO(),
                       popen=Runner(CLEAN).popen)
        facts = gate.analyze_phase(CLEAN, 0)
        facts["run"] = {"command": GRAPH_COMMAND}
        result = gate.evaluate_phases(facts)
        self.assertEqual(result["status"], "FAIL")
        self.assertFalse(result["checks"]["POLICY_PASS"])
        self.assertEqual(result["never_part_of_compilation"]["strict"]["check_state"],
                         "DISABLED_FOR_DIAGNOSIS")
        self.assertIsNone(result["never_part_of_compilation_violations"])
        self.assertTrue(any("diagnostic configuration" in f for f in result["failure_reasons"]))


class ObservedGraphTest(InventoryCase):

    def test_g01_complete_traces_give_exactly_the_observed_edges(self):
        output = text(PRELUDE, header(1), violation("java.util.HashMap.put(Object,Object)",
                                                    ["com.x.A.a()", "com.x.B.b()"]),
                      METHODS, TREE, SUCCESS)
        code, report = self.run_check(Runner(output, 0))
        graph = report["observed_reachability_graph"]
        self.assertEqual({(e["caller"], e["callee"]) for e in graph["edges"]},
                         {("com.x.B.b()", "com.x.A.a()"),
                          ("com.x.A.a()", "java.util.HashMap.put(Object,Object)")})
        self.assertEqual(graph["status"], "TRACES_COMPLETE")
        self.assertEqual([r["method"] for r in graph["roots"]], ["com.x.B.b()"])
        self.assertEqual(graph["paths"][0]["path_root_to_sink"],
                         ["com.x.B.b()", "com.x.A.a()", "java.util.HashMap.put(Object,Object)"])
        self.assertEqual(graph["edges"][0]["evidence"], ["strict:blocklist#0"])
        self.assertFalse(graph["global_inventory"])

    def test_g02_partial_traces_give_a_partial_graph(self):
        output = text(PRELUDE, header(1), ["Blocklisted method", "   com.x.M.m()", ""],
                      npoc(1, 0), ["called from: com.x.S.s()"], BUILD_REQUEST, FAILURE)
        code, report = self.run_check(Runner(phases=[(output, 1, False), self.graph_ok(1)]))
        graph = report["observed_reachability_graph"]
        self.assertEqual(graph["status"], "PARTIAL")
        self.assertTrue(graph["partial"])
        strict_edges = {(e["caller"], e["callee"]) for e in graph["edges"]
                        if any(ref.startswith("strict:") for ref in e["evidence"])}
        self.assertEqual(strict_edges, {("com.x.S.s()", gate.NPOC_SINK)})
        self.assertFalse(all(p["complete"] for p in graph["paths"] if p["phase"] == "strict"))
        self.assertEqual(code, 1)

    def test_g03_observed_graph_never_substitutes_the_official_tree(self):
        runner = Runner(phases=[self.strict_rejected(), self.graph_parsing()])
        code, report = self.run_check(runner)
        graph = report["observed_reachability_graph"]
        self.assertTrue(graph["edges"])
        self.assertFalse(graph["substitutes_runtime_compilation_call_tree"])
        self.assertIn((REACH_FRAMES[1], REACH_FRAMES[0]),
                      {(e["caller"], e["callee"]) for e in graph["edges"]})
        self.assertEqual(report["runtime_compilation_call_tree"]["status"], "NOT_PRODUCED")
        self.assertEqual(self.summary(runner)["NATIVE_RUNTIME_COMPILATION_CALL_TREE"],
                         "INCOMPLETE")

    def test_g04_tree_without_end_marker_is_incomplete(self):
        runner = Runner(text(PRELUDE, METHODS, TREE[:-1], SUCCESS), 0)
        code, report = self.run_check(runner)
        tree = report["runtime_compilation_call_tree"]
        self.assertEqual(tree["status"], "INCOMPLETE")
        self.assertTrue(tree["call_tree"]["start_found"])
        self.assertFalse(tree["call_tree"]["end_found"])
        self.assertEqual(tree["call_tree"]["entries"], TREE[1:3])
        self.assertProperties(runner, report, "true", "false", "false", "FAIL")

    def test_g05_quoted_markers_are_not_delimiters(self):
        quoted = ["Error: unexpected state after '****%s***'" % gate.TREE_END,
                  "  note: %s was expected" % gate.TREE_END]
        lines = text(TREE[:2], quoted).splitlines()
        self.assertEqual(gate._section(lines, gate.TREE_START, gate.TREE_END), (False, []))
        self.assertEqual(gate._section(["prefix " + gate.TREE_START, "x", gate.TREE_END],
                                       gate.TREE_START, gate.TREE_END), (False, []))
        runner = Runner(text(PRELUDE, METHODS, TREE[:3], quoted, SUCCESS), 0)
        code, report = self.run_check(runner)
        self.assertEqual(code, 1)
        self.assertNotEqual(report["runtime_compilation_call_tree"]["status"], "COMPLETE")
        self.assertFalse(report["call_tree_complete"])


class PomProfileTest(unittest.TestCase):
    NS = {"m": "http://maven.apache.org/POM/4.0.0"}

    def test_graph_profile_keeps_blocklist_and_official_print_options(self):
        analysis = [a.text for a in self.profile_plugin("native-analysis").findall(
            "m:configuration/m:buildArgs/m:buildArg", self.NS)]
        graph = [a.text for a in self.profile_plugin("native-analysis-graph").findall(
            "m:configuration/m:buildArgs/m:buildArg", self.NS)]
        self.assertFalse(any("TruffleCheckBlockListMethods" in a for a in graph))
        self.assertFalse(any("PrintRuntimeCompile" in a for a in graph))
        self.assertFalse(any("ReturnAfterAnalysis" in a for a in graph))
        for option in ("-H:+ReturnAfterAnalysis", "-H:+PrintRuntimeCompileMethods",
                       "-H:+PrintRuntimeCompilationCallTree"):
            self.assertIn(option, analysis)
        self.assertEqual([a for a in graph if a.startswith("-H:")],
                         [gate.NPOC_DISABLE_OPTION])

    def profile_plugin(self, profile_id):
        tree = ET.parse(str(ROOT / "pom.xml"))
        for profile in tree.getroot().findall("m:profiles/m:profile", self.NS):
            if profile.findtext("m:id", namespaces=self.NS) == profile_id:
                for plugin in profile.findall("m:build/m:plugins/m:plugin", self.NS):
                    if plugin.findtext("m:artifactId", namespaces=self.NS) == "native-maven-plugin":
                        return plugin
        self.fail("no native-maven-plugin in profile %s" % profile_id)

    def test_analysis_profile_appends_required_options(self):
        plugin = self.profile_plugin("native-analysis")
        config = plugin.find("m:configuration", self.NS)
        args = config.find("m:buildArgs", self.NS)
        self.assertEqual(args.get("combine.children"), "append")
        self.assertEqual([a.text for a in args.findall("m:buildArg", self.NS)], [
            "-H:+ReturnAfterAnalysis", "-H:-TruffleCheckBlockListMethods",
            "-H:+PrintRuntimeCompileMethods", "-H:+PrintRuntimeCompilationCallTree",
            "-H:+TruffleCheckNeverPartOfCompilation"])
        self.assertEqual(config.findtext("m:imageName", namespaces=self.NS), "protos-native-analysis")
        self.assertEqual(config.findtext("m:outputDirectory", namespaces=self.NS),
                         "${project.build.directory}/native-analysis/image")
        self.assertIsNone(plugin.find("m:executions", self.NS))

    def test_native_profile_is_unchanged_by_analysis(self):
        plugin = self.profile_plugin("native")
        config = plugin.find("m:configuration", self.NS)
        args = [a.text for a in config.findall("m:buildArgs/m:buildArg", self.NS)]
        self.assertNotIn("-H:+ReturnAfterAnalysis", args)
        self.assertNotIn("-H:-TruffleCheckBlockListMethods", args)
        self.assertFalse(any("TruffleCheckBlockListMethods" in (a or "") for a in args))
        self.assertFalse(any("TruffleCheckNeverPartOfCompilation" in (a or "") for a in args))
        self.assertFalse(any("ReturnAfterAnalysis" in (a or "") for a in args))
        self.assertEqual(config.findtext("m:imageName", namespaces=self.NS), "protos")
        self.assertEqual(config.findtext("m:outputDirectory", namespaces=self.NS),
                         "${project.build.directory}/native")
        ids = [e.findtext("m:id", namespaces=self.NS)
               for e in plugin.findall("m:executions/m:execution", self.NS)]
        self.assertEqual(ids, ["build-stage1-native"])

    def test_graph_profile_only_disables_never_part_of_compilation(self):
        plugin = self.profile_plugin("native-analysis-graph")
        config = plugin.find("m:configuration", self.NS)
        args = config.find("m:buildArgs", self.NS)
        self.assertEqual(args.get("combine.children"), "append")
        self.assertEqual([a.text for a in args.findall("m:buildArg", self.NS)],
                         ["-H:-TruffleCheckNeverPartOfCompilation",
                          "-J-ea:jdk.graal.compiler.debug.Assertions"])
        self.assertEqual(config.findtext("m:imageName", namespaces=self.NS),
                         "protos-native-analysis-graph")
        self.assertEqual(config.findtext("m:outputDirectory", namespaces=self.NS),
                         "${project.build.directory}/native-analysis/graph-image")
        self.assertIsNone(plugin.find("m:executions", self.NS))
        self.assertIsNone(plugin.find("m:dependencies", self.NS))

    def test_s06_builder_assertions_only_in_graph_profile(self):
        for profile_id, expected in (("native", False), ("native-analysis", False),
                                     ("native-analysis-graph", True)):
            plugin = self.profile_plugin(profile_id)
            args = [a.text for a in plugin.findall(
                "m:configuration/m:buildArgs/m:buildArg", self.NS)]
            self.assertEqual("-J-ea:jdk.graal.compiler.debug.Assertions" in args, expected,
                             profile_id)
            self.assertNotIn("-J-ea", args, profile_id)

    def test_graph_profile_is_declared_after_analysis_profile(self):
        tree = ET.parse(str(ROOT / "pom.xml"))
        ids = [p.findtext("m:id", namespaces=self.NS)
               for p in tree.getroot().findall("m:profiles/m:profile", self.NS)]
        self.assertLess(ids.index("native"), ids.index("native-analysis"))
        self.assertLess(ids.index("native-analysis"), ids.index("native-analysis-graph"))


if __name__ == "__main__":
    unittest.main(verbosity=1)
