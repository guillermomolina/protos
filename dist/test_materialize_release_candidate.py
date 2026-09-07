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


def run(
    args: list[str],
    *,
    cwd: Path,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
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


class ReleaseCandidateMaterializationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="protos-e4b3b1-")
        self.top = Path(self.temp.name)
        self.repo = self.top / "repo"
        self.remote = self.top / "origin.git"
        self.candidate = self.top / "candidate"
        self.repo.mkdir()
        (self.repo / "dist").mkdir()
        (self.repo / "docs/project").mkdir(parents=True)

        source_root = Path(__file__).resolve().parents[1]
        for name in [
            "prepare_release_candidate_worktree.py",
            "transition_release_candidate_version.py",
            "commit_release_candidate.py",
            "materialize_release_candidate.py",
        ]:
            shutil.copy2(source_root / "dist" / name, self.repo / "dist" / name)

        git(self.repo, "init", "-q")
        git(self.repo, "config", "user.email", "fixture@example.invalid")
        git(self.repo, "config", "user.name", "Fixture")
        run(["git", "init", "--bare", "-q", str(self.remote)], cwd=self.top)
        git(self.repo, "remote", "add", "origin", str(self.remote))

        (self.repo / "pom.xml").write_text(
            POM.format(version="0.2.236-SNAPSHOT"),
            encoding="utf-8",
        )
        git(self.repo, "add", ".")
        git(self.repo, "commit", "-q", "-m", "selected baseline")
        self.baseline = git(self.repo, "rev-parse", "HEAD")

        (self.repo / "README.txt").write_text("later main\n", encoding="utf-8")
        git(self.repo, "add", "README.txt")
        git(self.repo, "commit", "-q", "-m", "later main")
        git(self.repo, "push", "-q", "-u", "origin", "HEAD:main")

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

    def invoke(self, *, check: bool = True) -> subprocess.CompletedProcess[str]:
        return run(
            [
                sys.executable,
                "dist/materialize_release_candidate.py",
                "--selection",
                str(self.selection),
                "--candidate",
                str(self.candidate),
            ],
            cwd=self.repo,
            check=check,
        )

    def prepare_only(self) -> None:
        run(
            [
                sys.executable,
                "dist/prepare_release_candidate_worktree.py",
                "--selection",
                str(self.selection),
                "--destination",
                str(self.candidate),
            ],
            cwd=self.repo,
        )

    def transition_only(self) -> None:
        self.prepare_only()
        run(
            [
                sys.executable,
                "dist/transition_release_candidate_version.py",
                "--selection",
                str(self.selection),
                "--candidate",
                str(self.candidate),
            ],
            cwd=self.repo,
        )

    def test_fresh_composition_materializes_exact_candidate(self) -> None:
        result = self.invoke()
        head = git(self.candidate, "rev-parse", "HEAD")
        self.assertIn("DIST001_E4B3B1_MATERIALIZATION: PASS sha=" + head, result.stdout)
        self.assertEqual(git(self.candidate, "rev-parse", "HEAD^"), self.baseline)
        self.assertEqual(
            git(self.candidate, "diff", "--name-only", self.baseline, head),
            "pom.xml",
        )
        self.assertEqual(git(self.candidate, "status", "--porcelain=v1"), "")

    def test_resumes_clean_b1_worktree(self) -> None:
        self.prepare_only()
        result = self.invoke()
        self.assertIn("E4B3B1_COMPOSITION_CHECK: PASS", result.stdout)

    def test_resumes_exact_b2_dirty_worktree(self) -> None:
        self.transition_only()
        result = self.invoke()
        self.assertIn("E4B3B1_RECOVERY_E4B2_CHECK: PASS", result.stdout)
        self.assertEqual(git(self.candidate, "status", "--porcelain=v1"), "")

    def test_reuses_exact_existing_candidate_commit(self) -> None:
        first = self.invoke()
        head = git(self.candidate, "rev-parse", "HEAD")
        second = self.invoke()
        self.assertIn("E4B3B1_RECOVERY_REUSE_CHECK: PASS", second.stdout)
        self.assertIn("sha=" + head, first.stdout)
        self.assertIn("sha=" + head, second.stdout)

    def test_rejects_existing_non_worktree_path(self) -> None:
        self.candidate.mkdir()
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("not a registered worktree", result.stderr)

    def test_rejects_nonresumable_dirty_baseline_worktree(self) -> None:
        self.prepare_only()
        (self.candidate / "foreign.txt").write_text("foreign\n", encoding="utf-8")
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("not a resumable E4B2 state", result.stderr)

    def test_rejects_candidate_with_local_tag_ref(self) -> None:
        self.invoke()
        head = git(self.candidate, "rev-parse", "HEAD")
        git(self.repo, "tag", "forbidden", head)
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("local branch/tag refs", result.stderr)

    def test_rejects_already_persisted_selection(self) -> None:
        self.write_selection(candidate_source_revision="a" * 40)
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("candidate_source_revision mismatch", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
