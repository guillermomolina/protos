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


"""TEST009-F/BUG016-B self-tests for tools/truffle_compilation_gate.py (synthetic logs only, no real Graal)."""

from __future__ import print_function

import io
import json
import subprocess
import sys
import tempfile
import threading
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))

import java_generated_bytecode_bci_compilation_check as bci_check  # noqa: E402
import truffle_compilation_gate as gate  # noqa: E402
import truffle_jvm_launch  # noqa: E402

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


REFS = ["v1.AAA", "v1.BBB", "v1.CCC"]


def listing(refs, schema="protos.test.cases/v1"):
    return json.dumps({"schema": schema, "cases": [{"ref": ref, "display": "case " + ref} for ref in refs]})


def cases(refs):
    return [gate.OrderedDict([("ref", ref), ("display", None)]) for ref in refs]


def shard_result(arguments, status="PASS", exit_code=0):
    return gate.OrderedDict([("mode", gate.DIAGNOSE), ("options", list(gate.DIAGNOSTIC_OPTIONS)),
                             ("test_arguments", list(arguments)), ("status", status),
                             ("exit_code", exit_code), ("COMPILATIONS_DONE", 1)])


class CaseListingTest(unittest.TestCase):

    def test_valid_listing_keeps_casePlan_order(self):
        output = "[engine] noise\n" + listing(["v1.C", "v1.A", "v1.B"]) + "\n"
        self.assertEqual([case["ref"] for case in gate.parse_case_listing(output)], ["v1.C", "v1.A", "v1.B"])

    def test_malformed_listings_fail_closed(self):
        bad = {
            "missing": "1 passed, 0 failed\n",
            "two documents": listing(REFS) + "\n" + listing(REFS) + "\n",
            "invalid json": '{"schema": "protos.test.cases/v1", "cases": [\n',
            "wrong schema": listing(REFS, schema="protos.test.cases/v2"),
            "cases not array": json.dumps({"schema": "protos.test.cases/v1", "cases": {"ref": "v1.A"}}),
            "missing ref": json.dumps({"schema": "protos.test.cases/v1", "cases": [{"display": "x"}]}),
            "empty ref": json.dumps({"schema": "protos.test.cases/v1", "cases": [{"ref": ""}]}),
            "non-string ref": json.dumps({"schema": "protos.test.cases/v1", "cases": [{"ref": 7}]}),
            "entry not object": json.dumps({"schema": "protos.test.cases/v1", "cases": ["v1.A"]}),
            "duplicate ref": listing(["v1.A", "v1.A"]),
            "empty": listing([]),
        }
        for name, output in bad.items():
            with self.subTest(name):
                with self.assertRaises(gate.DiscoveryError):
                    gate.parse_case_listing(output)

    def test_nonzero_discovery_exit_is_error(self):
        with tempfile.TemporaryDirectory() as directory:
            root = fake_root(directory)
            with mock.patch.object(gate, "run_cli", return_value=(2, listing(REFS), 1.0)):
                discovery = gate.discover_cases(root, "java", ["--jobs", "8"], 10, root / "d")
            self.assertEqual(discovery["status"], "ERROR")
            self.assertEqual(discovery["cases"], [])

    def test_discovery_uses_real_test_tool_without_diagnostic_options(self):
        with tempfile.TemporaryDirectory() as directory:
            root = fake_root(directory)
            with mock.patch.object(gate, "run_cli", return_value=(0, listing(REFS), 1.0)) as fake:
                discovery = gate.discover_cases(root, "java", ["--file", "X"], 10, root / "d")
            command = fake.call_args[0][1]
            self.assertEqual(command[-4:], ["test", "--file", "X", "--list-cases"])
            self.assertFalse([option for option in command if option.startswith("-Dpolyglot.")])
            self.assertEqual([case["ref"] for case in discovery["cases"]], REFS)
            self.assertTrue((root / "d" / "discovery.log").is_file())


class ShardArgumentTest(unittest.TestCase):

    def test_one_case_one_shard_with_original_arguments(self):
        original = ["--jobs", "8", "--file", "X"]
        self.assertEqual([gate.shard_arguments(original, ref) for ref in REFS],
                         [original + ["--case", ref] for ref in REFS])
        self.assertEqual(original, ["--jobs", "8", "--file", "X"])

    def test_focused_case_is_replaced_not_duplicated(self):
        self.assertEqual(gate.shard_arguments(["--case", "v1.BBB", "--jobs", "2", "--case=v1.CCC"], "v1.BBB"),
                         ["--jobs", "2", "--case", "v1.BBB"])


class ShardPoolTest(unittest.TestCase):

    def test_bounded_concurrency(self):
        entered = [threading.Event() for _ in REFS]
        release = [threading.Event() for _ in REFS]
        lock, active, peak = threading.Lock(), [0], [0]

        def run(index, ref):
            with lock:
                active[0] += 1
                peak[0] = max(peak[0], active[0])
            entered[index].set()
            self.assertTrue(release[index].wait(10))
            with lock:
                active[0] -= 1
            return shard_result(gate.shard_arguments([], ref))
        results = []
        coordinator = threading.Thread(target=lambda: results.extend(
            gate.run_diagnostic_shards(cases(REFS), 2, [], run)))
        coordinator.start()
        self.assertTrue(entered[0].wait(10) and entered[1].wait(10))
        self.assertFalse(entered[2].is_set())
        release[0].set()
        self.assertTrue(entered[2].wait(10))
        release[1].set()
        release[2].set()
        coordinator.join(10)
        self.assertEqual(peak[0], 2)
        self.assertEqual([shard["case_ref"] for shard in results], REFS)

    def test_out_of_order_completion_keeps_casePlan_order(self):
        release = [threading.Event() for _ in REFS]
        finished = []
        # Completion order C3, C1, C2: C3 runs first and releases C1, which releases C2.
        release[2].set()
        release_chain = {2: 0, 0: 1}

        def chained(index, ref):
            self.assertTrue(release[index].wait(10))
            finished.append(ref)
            if index in release_chain:
                release[release_chain[index]].set()
            return shard_result(gate.shard_arguments([], ref))
        shards = gate.run_diagnostic_shards(cases(REFS), 3, [], chained)
        self.assertEqual(finished, ["v1.CCC", "v1.AAA", "v1.BBB"])
        self.assertEqual([shard["case_ref"] for shard in shards], REFS)
        self.assertEqual([shard["shard"] for shard in shards], ["0000", "0001", "0002"])

    def test_unexpected_worker_exception_is_recorded_and_others_collected(self):
        def run(index, ref):
            if index == 1:
                raise RuntimeError("boom")
            return shard_result(gate.shard_arguments([], ref))
        shards = gate.run_diagnostic_shards(cases(REFS), 2, [], run)
        self.assertEqual([shard["status"] for shard in shards], ["PASS", "ERROR", "PASS"])
        self.assertIn("HARNESS RuntimeError: boom", shards[1]["failures"])
        self.assertEqual(gate.coverage_problems(REFS, shards), [])


class AggregationTest(unittest.TestCase):

    DISCOVERY = {"status": "PASS"}

    def aggregate(self, *outcomes):
        refs = REFS[:len(outcomes)]
        shards = [dict(shard_result(gate.shard_arguments([], ref), status, code), shard=str(i), case_ref=ref)
                  for i, (ref, (status, code)) in enumerate(zip(refs, outcomes))]
        return gate.aggregate_diagnostic_shards(self.DISCOVERY, shards, gate.coverage_problems(refs, shards))

    def test_failure_matrix(self):
        passed, failed, timeout, error = ("PASS", 0), ("FAIL", 1), ("FAIL", None), ("ERROR", None)
        expected = [((passed, passed), "PASS", "COMPLETE"), ((passed, failed), "FAIL", "COMPLETE"),
                    ((passed, timeout), "ERROR", "INCOMPLETE"), ((passed, error), "ERROR", "INCOMPLETE"),
                    ((failed, failed), "FAIL", "COMPLETE")]
        for outcomes, status, acquisition in expected:
            with self.subTest(outcomes):
                aggregate = self.aggregate(*outcomes)
                self.assertEqual((aggregate["status"], aggregate["acquisition"]), (status, acquisition))

    def test_failed_discovery_or_coverage_is_never_pass(self):
        self.assertEqual(gate.aggregate_diagnostic_shards({"status": "ERROR"}, [], [])["status"], "ERROR")
        shards = [dict(shard_result(gate.shard_arguments([], REFS[0])), shard="0", case_ref=REFS[0])]
        self.assertEqual(gate.aggregate_diagnostic_shards(self.DISCOVERY, shards, ["MISSING x"])["status"],
                         "ERROR")


class CoverageTest(unittest.TestCase):

    def shards(self, selected):
        return [{"shard": str(i), "case_ref": own, "test_arguments": gate.shard_arguments([], ref)}
                for i, (own, ref) in enumerate(selected)]

    def test_exact_once(self):
        self.assertEqual(gate.coverage_problems(REFS, self.shards(zip(REFS, REFS))), [])

    def test_missing_duplicate_extra_are_detected(self):
        missing = gate.coverage_problems(REFS, self.shards(zip(REFS[:2], REFS[:2])))
        self.assertIn("MISSING v1.CCC", missing)
        duplicate = gate.coverage_problems(REFS, self.shards(zip(REFS + ["v1.AAA"], REFS + ["v1.AAA"])))
        self.assertIn("DUPLICATE v1.AAA", duplicate)
        extra = gate.coverage_problems(REFS[:2], self.shards(zip(REFS, REFS)))
        self.assertIn("EXTRA v1.CCC", extra)
        wrong = gate.coverage_problems(REFS, self.shards([("v1.AAA", "v1.BBB")]))
        self.assertTrue(any(problem.startswith("SHARD") for problem in wrong))


class DiagnoseTest(unittest.TestCase):

    def run_diagnose(self, arguments, refs, outputs=None, workers=3):
        """Fake packaged JVM: --list-cases answers the listing; a shard answers per its --case."""
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        root = fake_root(directory.name)
        report = root / "report.json"
        commands = []
        lock = threading.Lock()

        def fake_run(_root, command, _timeout):
            with lock:
                commands.append(command)
            if "--list-cases" in command:
                return 0, listing(refs), 0.5
            ref = command[command.index("--case") + 1]
            result = (outputs or {}).get(ref, (0, CLEAN))
            if isinstance(result, BaseException):
                raise result
            return result[0], result[1], 1.0
        out = io.StringIO()
        with mock.patch.object(gate, "run_cli", side_effect=fake_run):
            status = gate.diagnose(root, "java", arguments, 10, root / "a", report, out=out,
                                   shard_workers=workers)
        return status, json.loads(report.read_text()), commands, out.getvalue(), root

    def test_each_shard_runs_exact_case_with_diagnostic_options_and_original_arguments(self):
        status, document, commands, out, root = self.run_diagnose(["--jobs", "8", "--file", "X"], REFS)
        self.assertEqual(status, 0)
        self.assertEqual(document["aggregate"]["status"], "PASS")
        self.assertEqual(document["case_refs"], REFS)
        self.assertEqual(document["shard_workers"], 3)
        shards = [command for command in commands if "--list-cases" not in command]
        self.assertEqual(len(shards), 3)
        java_options = len(truffle_jvm_launch.JVM_OPTIONS) + 1
        expected = sorted(["test", "--jobs", "8", "--file", "X", "--case", ref] for ref in REFS)
        self.assertEqual(sorted(command[command.index("test"):] for command in shards), expected)
        for command in shards:
            self.assertEqual(tuple(command[java_options:java_options + len(gate.DIAGNOSTIC_OPTIONS)]),
                             gate.DIAGNOSTIC_OPTIONS)
            self.assertFalse([option for option in command if "CompilerThreads" in option])
        for index, shard in enumerate(document["shards"]):
            self.assertEqual(shard["shard"], "%04d" % index)
            self.assertEqual(shard["case_ref"], REFS[index])
            self.assertEqual(shard["options"], list(gate.DIAGNOSTIC_OPTIONS))
            self.assertEqual(shard["test_arguments"], ["--jobs", "8", "--file", "X", "--case", REFS[index]])
            self.assertEqual(Path(shard["log"]).name, "shard-%04d.log" % index)
            self.assertTrue(Path(shard["log"]).is_file())
        self.assertEqual(len({shard["log"] for shard in document["shards"]}), 3)
        self.assertIn("TRUFFLE_COMPILATION_DIAGNOSE=PASS", out.splitlines())

    def test_per_shard_logs_hold_only_their_own_output(self):
        outputs = {ref: (0, CLEAN + "marker " + ref + "\n") for ref in REFS}
        _, document, _, _, _ = self.run_diagnose([], REFS, outputs)
        for shard in document["shards"]:
            log = Path(shard["log"]).read_text()
            self.assertEqual([ref for ref in REFS if "marker " + ref in log], [shard["case_ref"]])

    def test_findings_are_evidence_but_timeout_and_harness_errors_fail_closed(self):
        status, document, _, _, _ = self.run_diagnose([], REFS, {REFS[1]: (0, DONE + "\n" + PE_FAILURE + "\n" + SUMMARY)})
        self.assertEqual((status, document["aggregate"]["status"]), (0, "FAIL"))
        expired = subprocess.TimeoutExpired(["java"], 10, output=b"partial\n")
        status, document, _, _, _ = self.run_diagnose([], REFS, {REFS[0]: expired})
        self.assertEqual((status, document["aggregate"]["status"]), (1, "ERROR"))
        self.assertEqual([shard["status"] for shard in document["shards"]], ["FAIL", "PASS", "PASS"])
        status, document, _, _, _ = self.run_diagnose([], REFS, {REFS[2]: OSError("no java")})
        self.assertEqual((status, document["aggregate"]["status"]), (1, "ERROR"))
        self.assertEqual([shard["status"] for shard in document["shards"]], ["PASS", "PASS", "ERROR"])

    def test_discovery_failure_runs_no_shard_and_writes_report(self):
        status, document, commands, _, _ = self.run_diagnose([], [])
        self.assertEqual(status, 1)
        self.assertEqual(document["discovery"]["status"], "ERROR")
        self.assertEqual(document["shards"], [])
        self.assertEqual(len(commands), 1)

    def test_already_focused_case_is_not_duplicated(self):
        status, document, commands, _, _ = self.run_diagnose(["--case", "v1.BBB", "--jobs", "2"], ["v1.BBB"])
        self.assertEqual(status, 0)
        self.assertEqual(commands[0][-6:], ["test", "--case", "v1.BBB", "--jobs", "2", "--list-cases"])
        self.assertEqual([shard["test_arguments"] for shard in document["shards"]],
                         [["--jobs", "2", "--case", "v1.BBB"]])

    def test_list_cases_in_user_arguments_is_rejected(self):
        status, document, commands, _, _ = self.run_diagnose(["--list-cases"], REFS)
        self.assertEqual((status, commands), (1, []))
        self.assertEqual(document["aggregate"]["status"], "ERROR")


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
        self.assertEqual(fake_diagnose.call_args[1]["shard_workers"], gate.DEFAULT_SHARD_WORKERS)

    def test_shard_workers_is_independent_of_test_tool_jobs(self):
        with mock.patch.object(gate, "diagnose", return_value=0) as fake_diagnose:
            gate.main(["diagnose", "--shard-workers", "3", "--", "--jobs", "8"])
        self.assertEqual(fake_diagnose.call_args[0][2], ["--jobs", "8"])
        self.assertEqual(fake_diagnose.call_args[1]["shard_workers"], 3)

    def test_shard_workers_must_be_positive(self):
        with mock.patch("sys.stderr", io.StringIO()), self.assertRaises(SystemExit):
            gate.main(["diagnose", "--shard-workers", "0"])


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
