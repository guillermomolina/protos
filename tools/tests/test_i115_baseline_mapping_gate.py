#!/usr/bin/env python3
"""Regression tests for historical-baseline identity mapping; pure Python."""
from __future__ import annotations

import importlib.util
import base64
import json
from pathlib import Path
import tempfile
import unittest

FILE = Path(__file__).resolve().parents[1] / "i115_semantic_performance_gate.py"
spec = importlib.util.spec_from_file_location("i115_repaired", FILE)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def synthetic_baseline() -> dict:
    all_roots = {}
    for case in mod.CASES:
        host_pair, guest_pairs = mod.BASELINE_TIER_COUNTS[mod.BASELINE_REVISION][case]
        refs = mod.GUEST_IDS_IN_SOURCE_ORDER[case]
        assert len(refs) == len(guest_pairs)
        roots = {
            mod.HOST: {"tier1_nodes": host_pair[0], "tier2_nodes": host_pair[1],
                       "dsl_static": {}}
        }
        for index, (root_id, counts) in enumerate(zip(refs, guest_pairs)):
            original_label = (f"protos-root:{root_id}[CLOSURE|"
                              f"file:///work/benchmarks/{case}/{case}.protos|"
                              f"{index * 100 + 10}+50]")
            roots[original_label] = {
                "tier1_nodes": counts[0], "tier2_nodes": counts[1],
                "dsl_static": {"UNCACHED": 15, "CACHED": 14}}
        all_roots[case] = roots
    return {
        "schema": 1, "baseline_protos_revision": mod.BASELINE_REVISION,
        "expected_graalvm_ce": mod.GRAAL_VERSION,
        "workload_count": 18, "root_count": 41, "roots": all_roots,
    }


class HistoricalMappingTest(unittest.TestCase):
    def read(self, value):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "baseline.json"
            file.write_text(json.dumps(value), encoding="utf-8")
            return mod.read_baseline(file)

    def test_full_historical_label_from_verified_baseline(self):
        b = self.read(synthetic_baseline())
        labels = mod.historical_guest_labels("bubble-sort", b["roots"]["bubble-sort"])
        self.assertEqual(len(labels), 1)
        self.assertIn("[CLOSURE|file:///work/benchmarks/", labels[0])
        local_label = "protos-root:1234567890abcdef[CLOSURE|i115:/cases/bubble-sort.protos|5+12]"
        mapping = mod.match_roots("bubble-sort", [mod.HOST, local_label], labels)
        self.assertEqual(mapping[local_label], labels[0])

    def test_module_top_level_not_misattributed_to_compiled_closure(self):
        case = "bubble-sort"
        names = [
            "protos-root:8888888888888888[TOP_LEVEL|i115:/cases/bubble-sort.protos|0+999]",
            "protos-root:1234567890abcdef[CLOSURE|i115:/cases/bubble-sort.protos|40+50]",
        ]
        raw = "\n".join(
            "I115_BYTECODE_SIZE_V1\t" + base64.b64encode(label.encode()).decode()
            + "\tCACHED\t" + str(count)
            for label, count in zip(names, (9, 48))
        )
        labels = mod.historical_guest_labels(case, synthetic_baseline()["roots"][case])
        measured, provenance = mod.parse_bytecode_sizes(case, raw, labels)
        self.assertEqual(measured[labels[0]]["CACHED"], 48)
        self.assertIn("__uncompiled_top_level__", provenance)
        self.assertNotIn(names[0], measured)

    def test_extra_root_is_rejected(self):
        b = synthetic_baseline()
        b["roots"]["bubble-sort"]["protos-root:1111111111111111[CLOSURE|file:///work/benchmarks/bubble-sort/bubble-sort.protos|800+50]"] = {
            "tier1_nodes": 0, "tier2_nodes": 0, "dsl_static": {"CACHED": 1}}
        with self.assertRaisesRegex(mod.GateError, "BASELINE_GUEST_ROOT_COUNT_MISMATCH"):
            self.read(b)

    def test_graph_signature_stays_frozen(self):
        b = synthetic_baseline()
        target = next(
            value
            for name, value in b["roots"]["bubble-sort"].items()
            if name != mod.HOST
        )
        target["tier2_nodes"] += 1
        with self.assertRaisesRegex(mod.GateError, "BASELINE_GRAPH_SIGNATURE_MISMATCH"):
            self.read(b)

    def test_multi_root_mapping_never_guessed_by_node_count(self):
        b = synthetic_baseline()
        src = b["roots"]["fibonacci"]
        original = next(x for x in src if x.startswith("protos-root:"))
        src["protos-root:cccccccccccccccc[" + original.split("[", 1)[1]] = src.pop(original)
        with self.assertRaisesRegex(mod.GateError, "BASELINE_GUEST_SOURCE_ORDER_MISMATCH"):
            self.read(b)

    def test_unexpected_host_identity_rejected(self):
        b = synthetic_baseline()
        src = b["roots"]["bubble-sort"]
        src["some.other.host.execute"] = src.pop(mod.HOST)
        with self.assertRaisesRegex(mod.GateError, "BASELINE_HOST_ROOT_MISSING"):
            self.read(b)


if __name__ == "__main__":
    unittest.main()
