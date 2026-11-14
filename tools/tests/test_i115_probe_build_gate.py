#!/usr/bin/env python3
"""TEST: I115 local gate probe must isolate structural builds and retain failure evidence."""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock

GATE_FILE = Path(__file__).resolve().parents[1] / "i115_semantic_performance_gate.py"
SPEC = importlib.util.spec_from_file_location("i115_probe_build_repaired", GATE_FILE)
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class I115ProbeBuildGateTest(unittest.TestCase):
    def stub_preflight(self):
        return (
            mock.patch.object(
                GATE,
                "read_baseline",
                return_value={
                    "baseline_protos_revision": GATE.BASELINE_REVISION,
                    "roots": {},
                },
            ),
            mock.patch.object(GATE, "verify_sources"),
            mock.patch.object(GATE, "check_runtime_version"),
            mock.patch.object(GATE, "git_data", return_value={"head": "synthetic", "clean": False}),
        )

    def test_product_build_skips_test_compilation(self):
        with mock.patch.object(GATE, "execute") as execute:
            GATE.build_product_for_gate()
        execute.assert_called_once_with(
            ["make", "build", "MVN_FLAGS=-Dmaven.test.skip=true"], GATE.ROOT
        )

    def test_probe_records_original_failure_even_if_maven_clean_deletes_target(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "target" / "i115" / "candidate"
            args = SimpleNamespace(action="probe", baseline=Path(temp) / "baseline.json", output=output)

            def simulated_maven_clean():
                self.assertTrue(output.is_dir())
                shutil.rmtree(output)

            preflight = self.stub_preflight()
            with preflight[0], preflight[1], preflight[2], preflight[3], \
                 mock.patch.object(GATE, "build_product_for_gate", side_effect=simulated_maven_clean), \
                 mock.patch.object(GATE, "compile_gate_java", side_effect=GATE.GateError("SIMULATED_DIAGNOSTIC_FAILURE")):
                with self.assertRaisesRegex(GATE.GateError, "SIMULATED_DIAGNOSTIC_FAILURE"):
                    GATE.run_command(args)

            report = json.loads((output / "gate-report.json").read_text(encoding="utf-8"))
            self.assertEqual(report["status"], "FAIL")
            self.assertEqual(report["semantic_gate"], "NOT_RUN")
            self.assertEqual(report["error"], "SIMULATED_DIAGNOSTIC_FAILURE")

    def test_full_gate_still_requires_real_test_phase(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "candidate"
            args = SimpleNamespace(action="run", baseline=Path(temp) / "baseline.json", output=output)
            preflight = self.stub_preflight()
            with preflight[0], preflight[1], preflight[2], preflight[3], \
                 mock.patch.object(GATE, "build_product_for_gate") as build, \
                 mock.patch.object(GATE, "execute", side_effect=GATE.GateError("SIMULATED_TEST_FAILURE")) as execute:
                with self.assertRaisesRegex(GATE.GateError, "SIMULATED_TEST_FAILURE"):
                    GATE.run_command(args)
            build.assert_called_once_with()
            execute.assert_called_once_with(["make", "test"], GATE.ROOT)
            report = json.loads((output / "gate-report.json").read_text(encoding="utf-8"))
            self.assertEqual(report["status"], "FAIL")
            self.assertEqual(report["semantic_gate"], "FAIL")


if __name__ == "__main__":
    unittest.main()
