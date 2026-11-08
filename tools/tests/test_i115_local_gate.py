#!/usr/bin/env python3
# THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
# ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina.
# See LICENSE.TXT at the repository root.
"""Local measurement parsing and root identity; no external harness or JVM."""
from __future__ import annotations

import base64
import importlib.util
from pathlib import Path
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[1] / 'i115_semantic_performance_gate.py'
spec = importlib.util.spec_from_file_location('i115_local', SOURCE)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def guest_label(case, index=0, start=0, size=2):
    key = mod.GUEST_IDS_IN_SOURCE_ORDER[case][index]
    return f'protos-root:{key}[CLOSURE|i115:/cases/{case}.protos|{start}+{size}]'


def done(label, tier, nodes, comp):
    return (f'[engine] opt done   engine=2  id={comp}   {label}   '
            f'|Tier {tier}|Time 4(1+3)|CompId {comp}  |IR {nodes}/2000|UTC x')


class LocalGateTest(unittest.TestCase):
    def test_cases_and_source_digests(self):
        self.assertEqual(len(mod.CASES), 18)
        self.assertEqual(sum(len(v) for v in mod.GUEST_IDS_IN_SOURCE_ORDER.values()), 23)
        mod.verify_sources()

    def test_compilation_trace_with_internal_pipes(self):
        c = 'bubble-sort'
        lines = '\n'.join((done(mod.HOST, 1, 239, 2),
                           done(guest_label(c), 1, 531, 3)))
        roots, evidence = mod.parse_trace(c, lines, 1)
        self.assertEqual(roots[mod.HOST], 239)
        self.assertEqual(roots['protos-root:70bac6408c0f1e35'], 531)
        self.assertEqual(evidence[mod.HOST]['comp_id'], 2)

    def test_two_roots_map_by_source_position_not_nodes(self):
        case = 'fibonacci'
        later = guest_label(case, 1, 150, 12)
        first = guest_label(case, 0, 5, 50)
        mapping = mod.match_roots(case, [mod.HOST, later, first])
        self.assertEqual(mapping[first], 'protos-root:9e63b5cbd94a66e6')
        self.assertEqual(mapping[later], 'protos-root:4308b246ea11a3c5')

    def test_wrong_source_uri_or_extra_root_fail_closed(self):
        case = 'bubble-sort'
        wrong = guest_label(case).replace('i115:/cases/', 'i115:/other/')
        with self.assertRaises(mod.GateError):
            mod.match_roots(case, [mod.HOST, wrong])
        with self.assertRaises(mod.GateError):
            mod.match_roots(case, [mod.HOST, guest_label(case), guest_label(case, start=4)])

    def test_duplicate_trace_compilation_fails(self):
        c = 'bubble-sort'
        record = done(guest_label(c), 1, 531, 3)
        with self.assertRaises(mod.GateError):
            mod.parse_trace(c, '\n'.join([done(mod.HOST, 1, 239, 1), record, record]), 1)

    def test_static_instructions_from_instrument(self):
        c = 'bubble-sort'
        label = guest_label(c)
        encoded = base64.b64encode(label.encode()).decode()
        top = base64.b64encode(f'protos-root:1111111111111111[TOP_LEVEL|i115:/cases/{c}.protos|0+90]'.encode()).decode()
        raw = '\n'.join([f'I115_BYTECODE_SIZE_V1\t{top}\tCACHED\t10',
                         f'I115_BYTECODE_SIZE_V1\t{encoded}\tUNCACHED\t20',
                         f'I115_BYTECODE_SIZE_V1\t{encoded}\tCACHED\t16'])
        roots, _ = mod.parse_bytecode_sizes(c, raw)
        self.assertEqual(roots['protos-root:70bac6408c0f1e35'],
                         {'UNCACHED': 20, 'CACHED': 16})

    def test_variable_dsl_static_fails(self):
        c = 'bubble-sort'
        encoded = base64.b64encode(guest_label(c).encode()).decode()
        top = base64.b64encode(f'protos-root:1111111111111111[TOP_LEVEL|i115:/cases/{c}.protos|0+90]'.encode()).decode()
        raw = '\n'.join([f'I115_BYTECODE_SIZE_V1\t{top}\tCACHED\t10',
                         f'I115_BYTECODE_SIZE_V1\t{encoded}\tCACHED\t20',
                         f'I115_BYTECODE_SIZE_V1\t{encoded}\tCACHED\t21'])
        with self.assertRaises(mod.GateError):
            mod.parse_bytecode_sizes(c, raw)

    def test_missing_metrics_fails(self):
        c = 'bubble-sort'
        with self.assertRaises(mod.GateError):
            mod.parse_trace(c, done(mod.HOST, 1, 239, 3), 1)
        with self.assertRaises(mod.GateError):
            mod.parse_bytecode_sizes(c, 'nothing')


if __name__ == '__main__':
    unittest.main()
