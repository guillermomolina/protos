#!/usr/bin/env python3
"""Pure gate contract tests. No JVM, container, or benchmark execution."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest

FILE = Path(__file__).resolve().parents[1] / "i115_semantic_performance_gate.py"
spec = importlib.util.spec_from_file_location("i115_gate", FILE)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class GateContractTest(unittest.TestCase):
    def test_equal_counts_accepted(self):
        base = {case: {"guest": {"tier1_nodes": 40, "tier2_nodes": 20,
                                 "dsl_static": {"UNCACHED": 8, "CACHED": 5}}}
                for case in module.CASES}
        self.assertEqual(module.compare(base, base), [])

    def test_cost_growth_rejected(self):
        base = {case: {"guest": {"tier1_nodes": 40, "tier2_nodes": 20,
                                 "dsl_static": {"UNCACHED": 8, "CACHED": 5}}}
                for case in module.CASES}
        now = {case: {key: {**value, "dsl_static": dict(value["dsl_static"])}
                      for key, value in roots.items()} for case, roots in base.items()}
        now["bubble-sort"]["guest"]["tier1_nodes"] = 41
        now["factorial"]["guest"]["tier2_nodes"] = 21
        now["fibonacci"]["guest"]["dsl_static"]["CACHED"] = 6
        failures = module.compare(base, now)
        self.assertEqual(sum(x.startswith("NODE_REGRESSION") for x in failures), 2)
        self.assertEqual(sum(x.startswith("DSL_REGRESSION") for x in failures), 1)

    def test_missing_root_or_dsl_state_rejected(self):
        base = {case: {"guest": {"tier1_nodes": 1, "tier2_nodes": 1,
                                 "dsl_static": {"UNCACHED": 1}}}
                for case in module.CASES}
        now = {case: dict(roots) for case, roots in base.items()}
        now["bubble-sort"] = {}
        now["fibonacci"] = {"guest": {"tier1_nodes": 1, "tier2_nodes": 1, "dsl_static": {}}}
        errors = module.compare(base, now)
        self.assertTrue(any(x.startswith("ROOT_SET_CHANGED") for x in errors))
        self.assertTrue(any(x.startswith("DSL_STATE_COVERAGE_CHANGED") for x in errors))

    def test_null_counter_fails_closed(self):
        with self.assertRaises(module.GateError):
            module.required_int(None, "root/tier1")


if __name__ == "__main__":
    unittest.main()
