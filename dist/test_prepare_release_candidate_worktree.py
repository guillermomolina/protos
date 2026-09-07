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

import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True

POM = """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.guillermomolina</groupId>
  <artifactId>protos</artifactId>
  <version>{version}</version>
</project>
"""


def run(args: list[str], *, cwd: Path, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=cwd,
        check=check,
        text=True,
        capture_output=True,
        env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
    )


def git(root: Path, *args: str) -> str:
    return run(["git", *args], cwd=root).stdout.strip()


class PrepareReleaseCandidateWorktreeTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="protos-e4b1-")
        self.top = Path(self.temp.name)
        self.repo = self.top / "repo"
        self.remote = self.top / "origin.git"
        self.repo.mkdir()
        (self.repo / "dist").mkdir()
        (self.repo / "docs/project").mkdir(parents=True)

        source_root = Path(__file__).resolve().parents[1]
        shutil.copy2(
            source_root / "dist/prepare_release_candidate_worktree.py",
            self.repo / "dist/prepare_release_candidate_worktree.py",
        )

        git(self.repo, "init", "-q")
        git(self.repo, "config", "user.email", "fixture@example.invalid")
        git(self.repo, "config", "user.name", "Fixture")
        run(["git", "init", "--bare", "-q", str(self.remote)], cwd=self.top)
        git(self.repo, "remote", "add", "origin", str(self.remote))

        (self.repo / "pom.xml").write_text(POM.format(version="0.2.236-SNAPSHOT"), encoding="utf-8")
        git(self.repo, "add", ".")
        git(self.repo, "commit", "-q", "-m", "selected baseline")
        self.baseline = git(self.repo, "rev-parse", "HEAD")

        (self.repo / "README.txt").write_text("later main work\n", encoding="utf-8")
        git(self.repo, "add", "README.txt")
        git(self.repo, "commit", "-q", "-m", "later main")
        git(self.repo, "push", "-q", "-u", "origin", "HEAD:main")
        git(self.repo, "fetch", "-q", "origin", "main")

        self.selection = self.repo / "docs/project/DIST001_E4_SELECTION.txt"
        self.write_selection()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write_selection(self, **overrides: str) -> None:
        values = {
            "selection_format": "protos-dist001-e4-selection-v1",
            "selection_authorized": "true",
            "selection_authorization_basis": "explicit-user-decision",
            "release_publication_authorized": "false",
            "release_baseline_revision": self.baseline,
            "release_baseline_version": "0.2.236-SNAPSHOT",
            "release_version": "0.2.236",
            "release_tag": "v0.2.236",
            "specification_revision": "0.1.382",
            "i023_status": "CLOSED",
            "b007_status": "CLOSED",
            "candidate_source_revision": "UNMATERIALIZED",
            "git_tag_created": "false",
            "github_release_created": "false",
            "release_assets_published": "false",
        }
        values.update(overrides)
        self.selection.write_text(
            "\n".join(f"{key}={value}" for key, value in values.items()) + "\n",
            encoding="utf-8",
        )

    def invoke(self, destination: Path, *, check: bool = True) -> subprocess.CompletedProcess[str]:
        return run(
            [
                sys.executable,
                "dist/prepare_release_candidate_worktree.py",
                "--selection",
                str(self.selection),
                "--destination",
                str(destination),
            ],
            cwd=self.repo,
            check=check,
        )

    def test_creates_clean_detached_baseline_worktree_without_branch(self) -> None:
        destination = self.top / "candidate"
        branches_before = git(self.repo, "for-each-ref", "--format=%(refname)", "refs/heads")
        result = self.invoke(destination)
        self.assertIn("DIST001_E4B1_WORKTREE: PASS", result.stdout)
        self.assertEqual(git(destination, "rev-parse", "HEAD"), self.baseline)
        detached = run(
            ["git", "symbolic-ref", "--quiet", "--short", "HEAD"],
            cwd=destination,
            check=False,
        )
        self.assertEqual(detached.returncode, 1)
        self.assertEqual(git(destination, "status", "--porcelain=v1"), "")
        self.assertIn(
            "<version>0.2.236-SNAPSHOT</version>",
            (destination / "pom.xml").read_text(encoding="utf-8"),
        )
        branches_after = git(self.repo, "for-each-ref", "--format=%(refname)", "refs/heads")
        self.assertEqual(branches_after, branches_before)

    def test_rejects_destination_inside_main_checkout(self) -> None:
        result = self.invoke(self.repo / "candidate", check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("must be outside the main checkout", result.stderr)

    def test_rejects_existing_destination(self) -> None:
        destination = self.top / "candidate"
        destination.mkdir()
        result = self.invoke(destination, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("destination already exists", result.stderr)

    def test_rejects_unauthorized_selection(self) -> None:
        self.write_selection(selection_authorized="false")
        result = self.invoke(self.top / "candidate", check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("selection_authorized mismatch", result.stderr)

    def test_rejects_publication_authorization(self) -> None:
        self.write_selection(release_publication_authorized="true")
        result = self.invoke(self.top / "candidate", check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("release_publication_authorized mismatch", result.stderr)

    def test_rejects_already_materialized_candidate_selection(self) -> None:
        self.write_selection(candidate_source_revision="a" * 40)
        result = self.invoke(self.top / "candidate", check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("candidate_source_revision mismatch", result.stderr)

    def test_rejects_baseline_version_mismatch(self) -> None:
        self.write_selection(
            release_baseline_version="0.2.237-SNAPSHOT",
            release_version="0.2.237",
            release_tag="v0.2.237",
        )
        result = self.invoke(self.top / "candidate", check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("selected baseline project version mismatch", result.stderr)

    def test_rejects_baseline_outside_origin_main_lineage(self) -> None:
        orphan = self.top / "orphan"
        run(["git", "clone", "-q", str(self.remote), str(orphan)], cwd=self.top)
        git(orphan, "config", "user.email", "fixture@example.invalid")
        git(orphan, "config", "user.name", "Fixture")
        git(orphan, "checkout", "-q", "--orphan", "unrelated")
        for path in list(orphan.iterdir()):
            if path.name == ".git":
                continue
            if path.is_dir():
                shutil.rmtree(path)
            else:
                path.unlink()
        (orphan / "pom.xml").write_text(POM.format(version="0.2.236-SNAPSHOT"), encoding="utf-8")
        git(orphan, "add", "pom.xml")
        git(orphan, "commit", "-q", "-m", "unrelated")
        unrelated = git(orphan, "rev-parse", "HEAD")
        run(["git", "fetch", "-q", str(orphan), unrelated], cwd=self.repo)
        self.write_selection(release_baseline_revision=unrelated)
        result = self.invoke(self.top / "candidate", check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("not an ancestor of origin/main", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
