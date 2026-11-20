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
import os
from pathlib import Path
import stat
import unittest
import zipfile


class SqlitePortablePackagingTest(unittest.TestCase):
    """Inspect real portable artifacts; absence is UNSUPPORTED, never PASS evidence."""

    def setUp(self) -> None:
        selected = os.environ.get("PROTOS_SQLITE_DISTRIBUTION")
        if not selected:
            self.skipTest("UNSUPPORTED: set PROTOS_SQLITE_DISTRIBUTION to an extracted installation")
        self.root = Path(selected).resolve(strict=True)

    def test_launcher_profile_and_digest(self) -> None:
        launcher = self.root / "lib/sqlite/sandbox-exec"
        self.assertTrue(launcher.is_file())
        self.assertTrue(launcher.stat().st_mode & stat.S_IXUSR)
        expected = ("profile=linux-landlock-abi10\nlauncher_sha256="
                    + hashlib.sha256(launcher.read_bytes()).hexdigest() + "\n")
        self.assertEqual(expected, (launcher.parent / "PROFILE.txt").read_text())

    def test_provider_is_private_and_licensed(self) -> None:
        with zipfile.ZipFile(self.root / "lib/sqlite/protos-ffm-provider.jar") as provider:
            names = provider.namelist()
            self.assertIn("META-INF/services/com.guillermomolina.protos.spi.foreign.ProtosForeignProviderPlugin", names)
            self.assertIn("META-INF/LICENSE.TXT", names)
            self.assertTrue(any(name.endswith("FfmForeignProvider.class") for name in names))
        with zipfile.ZipFile(self.root / "lib/protos.jar") as parent:
            self.assertFalse(any("/ffm/" in name and name.endswith(".class") for name in parent.namelist()))
            self.assertIn("META-INF/LICENSE.TXT", parent.namelist())
        self.assertFalse(any("ffm-provider" in path.name for path in (self.root / "lib/runtime").glob("*.jar")))

    def test_installed_helper_and_public_examples(self) -> None:
        for relative in ("protos/lib/sqlite/isolated-helper.protos",
                         "protos/lib/sqlite/internal/IsolatedConnection.protos",
                         "protos/lib/sqlite/internal/IsolatedHelper.protos",
                         "protos/lib/sqlite/internal/IsolatedProtocol.protos",
                         "protos/examples/ffi/sqlite-durable-confined.protos",
                         "protos/examples/ffi/sqlite-durable-reopen.protos"):
            self.assertTrue((self.root / relative).is_file(), relative)

    def test_checksums_cover_every_sqlite_artifact(self) -> None:
        recorded = dict(line.split("  ", 1)[::-1]
                        for line in (self.root / "SHA256SUMS").read_text().splitlines())
        for directory in (self.root / "lib/sqlite", self.root / "protos/lib/sqlite"):
            for path in directory.rglob("*"):
                if path.is_file():
                    relative = path.relative_to(self.root).as_posix()
                    self.assertIn(relative, recorded)
                    self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), recorded[relative])
