from __future__ import annotations

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]

AUTHORITATIVE = (
    ROOT
    / "src/test/java/com/guillermomolina/protos/cli/"
    "ProtosTestToolFileSelectionPublicIntegrationTest.java"
)

CONSUMERS = (
    ROOT / "dist/validate_native.py",
    ROOT / "build/native/test-native.sh",
)

EXPECTED = (
    "[library/uri] 0/4",
    "[library/uri] 1/4",
    "[library/uri] 2/4",
    "[library/uri] 3/4",
    "[library/uri] 4/4 passed",
)

OBSOLETE = (
    "[uri] 0/4",
    "[uri] 1/4",
    "[uri] 2/4",
    "[uri] 3/4",
    "[uri] 4/4 passed",
)


class NativeTestToolProgressContractTest(unittest.TestCase):
    def test_public_test_tool_contract_uses_repository_group(self) -> None:
        text = AUTHORITATIVE.read_text(encoding="utf-8")

        for marker in EXPECTED:
            self.assertIn(marker, text)

        for marker in OBSOLETE:
            self.assertNotIn(marker, text)

    def test_native_consumers_match_public_test_tool_contract(self) -> None:
        for path in CONSUMERS:
            with self.subTest(path=path):
                text = path.read_text(encoding="utf-8")

                for marker in EXPECTED:
                    self.assertIn(marker, text)

                for marker in OBSOLETE:
                    self.assertNotIn(marker, text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
