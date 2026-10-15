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

from __future__ import annotations

import sys as _i103_sys
from pathlib import Path as _I103Path
_I103_DIST = _I103Path(__file__).resolve().parents[2]
if str(_I103_DIST) not in _i103_sys.path:
    _i103_sys.path.insert(0, str(_I103_DIST))

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[3]

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
