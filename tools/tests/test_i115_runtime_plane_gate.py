#!/usr/bin/env python3
"""I115: enforce canonical Maven groupId-prefixed Graal runtime-plane jars."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import tempfile
import unittest

GATE_FILE = Path(__file__).resolve().parents[1] / 'i115_semantic_performance_gate.py'
SPEC = importlib.util.spec_from_file_location('i115_runtime_plane_gate', GATE_FILE)
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class I115RuntimePlaneGateTest(unittest.TestCase):
    def write_canonical(self, runtime: Path) -> None:
        for artifact in ('truffle-runtime', 'truffle-compiler', 'truffle-api'):
            (runtime / f'org.graalvm.truffle.{artifact}-{GATE.GRAAL_VERSION}.jar').write_bytes(b'PK')

    def test_accepts_exact_canonical_truffle_jars(self):
        with tempfile.TemporaryDirectory() as tmp:
            runtime = Path(tmp)
            self.write_canonical(runtime)
            files = GATE.verify_runtime_plane(runtime)
            self.assertEqual(set(files), {'truffle-runtime', 'truffle-compiler', 'truffle-api'})
            self.assertTrue(all(p.is_file() for p in files.values()))

    def test_rejects_missing_runtime_plane(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(GATE.GateError, 'RUNTIME_PLANE_MISSING'):
                GATE.verify_runtime_plane(Path(tmp) / 'missing')

    def test_rejects_absent_canonical_artifact(self):
        with tempfile.TemporaryDirectory() as tmp:
            runtime = Path(tmp)
            self.write_canonical(runtime)
            (runtime / f'org.graalvm.truffle.truffle-runtime-{GATE.GRAAL_VERSION}.jar').unlink()
            with self.assertRaisesRegex(GATE.GateError, 'RUNTIME_JAR_VERSION_MISMATCH: truffle-runtime'):
                GATE.verify_runtime_plane(runtime)

    def test_rejects_wrong_version_without_canonical(self):
        with tempfile.TemporaryDirectory() as tmp:
            runtime = Path(tmp)
            self.write_canonical(runtime)
            (runtime / f'org.graalvm.truffle.truffle-runtime-{GATE.GRAAL_VERSION}.jar').unlink()
            (runtime / 'org.graalvm.truffle.truffle-runtime-25.4.4.1.1.jar').write_bytes(b'PK')
            with self.assertRaisesRegex(GATE.GateError, 'RUNTIME_JAR_VERSION_MISMATCH: truffle-runtime'):
                GATE.verify_runtime_plane(runtime)

    def test_rejects_duplicate_different_version(self):
        with tempfile.TemporaryDirectory() as tmp:
            runtime = Path(tmp)
            self.write_canonical(runtime)
            (runtime / 'org.graalvm.truffle.truffle-runtime-25.4.4.1.1.jar').write_bytes(b'PK')
            with self.assertRaisesRegex(GATE.GateError, 'RUNTIME_JAR_AMBIGUOUS: truffle-runtime'):
                GATE.verify_runtime_plane(runtime)

    def test_rejects_legacy_unprefixed_duplicate(self):
        with tempfile.TemporaryDirectory() as tmp:
            runtime = Path(tmp)
            self.write_canonical(runtime)
            (runtime / f'truffle-api-{GATE.GRAAL_VERSION}.jar').write_bytes(b'PK')
            with self.assertRaisesRegex(GATE.GateError, 'RUNTIME_JAR_AMBIGUOUS: truffle-api'):
                GATE.verify_runtime_plane(runtime)


if __name__ == '__main__':
    unittest.main()
