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

"""Isolated synthetic-Git conformance tests for I103."""

from __future__ import annotations

from contextlib import redirect_stdout
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import directory_file_limit as guard  # noqa: E402


class DirectoryFileLimitTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.git("init", "-q")

    def git(self, *args, data=None):
        return subprocess.run(
            ["git", "-C", str(self.root), *args],
            input=data,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=True,
        ).stdout

    def create(self, directory, number, *, tracked=True):
        paths = []
        for index in range(number):
            relative = Path(directory) / f"file-{index:03}.txt"
            absolute = self.root / relative
            absolute.parent.mkdir(parents=True, exist_ok=True)
            absolute.write_text(str(index), encoding="utf-8")
            paths.append(relative.as_posix())
        if tracked and paths:
            self.git("add", "--", *paths)
        return paths

    def counts(self):
        return guard.inventory(self.root)

    def test_29_passes(self):
        self.create("domain", 29)
        self.assertEqual([], guard.violations(self.counts()))

    def test_30_passes(self):
        self.create("domain", 30)
        self.assertEqual([], guard.violations(self.counts()))

    def test_31_fails(self):
        self.create("domain", 31)
        self.assertEqual([("domain", 31)],
                         guard.violations(self.counts()))

    def test_parent_does_not_accumulate_descendants(self):
        self.create("parent", 30)
        self.create("parent/child", 31)
        counts = self.counts()
        self.assertEqual(30, counts["parent"])
        self.assertEqual(31, counts["parent/child"])
        self.assertEqual(
            [("parent/child", 31)], guard.violations(counts)
        )

    def test_parent_and_child_each_30_pass(self):
        self.create("parent", 30)
        self.create("parent/child", 30)
        self.assertEqual([], guard.violations(self.counts()))

    def test_parent_31_and_child_30_only_parent_fails(self):
        self.create("parent", 31)
        self.create("parent/child", 30)
        counts = self.counts()
        self.assertEqual(31, counts["parent"])
        self.assertEqual(30, counts["parent/child"])
        self.assertEqual([("parent", 31)], guard.violations(counts))

    def test_parent_29_and_child_30_both_pass(self):
        self.create("parent", 29)
        self.create("parent/child", 30)
        self.assertEqual([], guard.violations(self.counts()))

    def test_multiple_offenders_sorted_and_reported(self):
        self.create("zeta", 31)
        self.create("alpha", 32)
        counts = self.counts()
        self.assertEqual(
            [("alpha", 32), ("zeta", 31)],
            guard.violations(counts),
        )
        output = io.StringIO()
        with redirect_stdout(output):
            self.assertEqual(
                0, guard.main(["report", "--repo", str(self.root)])
            )
        lines = [
            line for line in output.getvalue().splitlines()
            if line.startswith("VIOLATION ")
        ]
        self.assertEqual(2, len(lines))
        self.assertIn("alpha", lines[0])
        self.assertIn("zeta", lines[1])
        self.assertIn("STATUS=VIOLATIONS", output.getvalue())
        with redirect_stdout(io.StringIO()):
            self.assertEqual(
                1, guard.main(["check", "--repo", str(self.root)])
            )

    def test_executable_is_regular(self):
        relative = Path("bin/run")
        target = self.root / relative
        target.parent.mkdir()
        target.write_text("hello\n", encoding="utf-8")
        target.chmod(0o755)
        self.git("add", "--", relative.as_posix())
        self.assertEqual(1, self.counts()["bin"])

    def test_symlink_does_not_count(self):
        self.create("domain", 30)
        link = self.root / "domain/link"
        try:
            os.symlink("file-000.txt", link)
        except (OSError, NotImplementedError) as exc:
            self.skipTest(f"symlink unsupported: {exc}")
        self.git("add", "--", "domain/link")
        self.assertEqual(30, self.counts()["domain"])

    def test_untracked_does_not_count(self):
        self.create("domain", 30)
        untracked = self.root / "domain/untracked.txt"
        untracked.write_text("new", encoding="utf-8")
        self.assertEqual(30, self.counts()["domain"])

    def test_upstream_exemption_is_exact(self):
        parent = (
            "protos/tests/conformance/library/toml/official"
        )
        self.create(parent + "/upstream/v2.2.0/fixtures", 31)
        self.create(parent + "/suite", 31)
        counts = self.counts()
        self.assertNotIn(
            parent + "/upstream/v2.2.0/fixtures", counts
        )
        self.assertEqual(
            [(parent + "/suite", 31)],
            guard.violations(counts),
        )

    def test_unusual_tracked_filename(self):
        relative = "spaces/a file with space\nand newline.txt"
        path = self.root / relative
        path.parent.mkdir(parents=True)
        path.write_text("ok", encoding="utf-8")
        self.git("add", "--", relative)
        self.assertEqual(1, self.counts()["spaces"])

    def test_unmerged_index_rejected(self):
        sha = self.git(
            "hash-object", "-w", "--stdin", data=b"conflict\n"
        ).decode("ascii").strip()
        records = (
            f"100644 {sha} 1\tconflict.txt\n"
            f"100644 {sha} 2\tconflict.txt\n"
        ).encode("ascii")
        self.git("update-index", "--index-info", data=records)
        with self.assertRaises(guard.InventoryError):
            self.counts()

    def test_git_failure_rejected(self):
        with self.assertRaises(guard.InventoryError):
            guard.inventory(self.root / "not-a-repository")

    def test_malformed_metadata_rejected(self):
        with self.assertRaises(guard.InventoryError):
            guard.parse_index(b"100644 invalid 0\tbad.txt\0")

    def test_unknown_mode_rejected(self):
        record = b"100600 " + b"a" * 40 + b" 0\tbad.txt\0"
        with self.assertRaises(guard.InventoryError):
            guard.parse_index(record)


if __name__ == "__main__":
    unittest.main(verbosity=2)
