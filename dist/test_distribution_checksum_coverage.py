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

import hashlib
from pathlib import Path
import sys
import tempfile
import unittest

DIST = Path(__file__).resolve().parent
if str(DIST) not in sys.path:
    sys.path.insert(0, str(DIST))

import build_native
import build_portable


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class DistributionChecksumCoverageTest(unittest.TestCase):
    def assert_nested_sha256sums_is_covered(self, writer) -> None:
        with tempfile.TemporaryDirectory(
            prefix="protos-dist-checksum-coverage-"
        ) as temp:
            bundle = Path(temp) / "bundle"
            bundle.mkdir()

            payload = bundle / "payload.txt"
            payload.write_text("payload\n", encoding="utf-8")

            nested = (
                bundle
                / "protos/tests/conformance/library/toml/official/upstream/v2.2.0"
                / "SHA256SUMS"
            )
            nested.parent.mkdir(parents=True)
            nested.write_text(
                "0123456789abcdef  fixture.toml\n",
                encoding="utf-8",
            )

            writer(bundle)

            root_manifest = bundle / "SHA256SUMS"
            self.assertTrue(root_manifest.is_file())

            recorded = {}
            for line in root_manifest.read_text(
                encoding="utf-8"
            ).splitlines():
                checksum, relative = line.split("  ", 1)
                recorded[relative] = checksum

            self.assertNotIn("SHA256SUMS", recorded)
            self.assertEqual(
                recorded["payload.txt"],
                digest(payload),
            )
            self.assertEqual(
                recorded[
                    "protos/tests/conformance/library/toml/official/"
                    "upstream/v2.2.0/SHA256SUMS"
                ],
                digest(nested),
            )

    def test_native_manifest_covers_nested_sha256sums(self) -> None:
        self.assert_nested_sha256sums_is_covered(
            build_native.write_checksums
        )

    def test_portable_manifest_covers_nested_sha256sums(self) -> None:
        self.assert_nested_sha256sums_is_covered(
            build_portable.write_checksums
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
