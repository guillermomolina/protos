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


class ReleaseCandidateLineageVerificationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="protos-e4b4-")
        self.top = Path(self.temp.name)
        self.repo = self.top / "repo"
        self.remote = self.top / "origin.git"
        self.candidate_worktree = self.top / "candidate"
        self.repo.mkdir()
        (self.repo / "dist").mkdir()
        (self.repo / "docs/project/evidence/DIST001").mkdir(parents=True, exist_ok=True)

        source = Path(__file__).resolve().parents[1] / "dist/verify_release_candidate_lineage.py"
        shutil.copy2(source, self.repo / "dist/verify_release_candidate_lineage.py")

        git(self.repo, "init", "-q")
        git(self.repo, "config", "user.email", "fixture@example.invalid")
        git(self.repo, "config", "user.name", "Fixture")
        run(["git", "init", "--bare", "-q", str(self.remote)], cwd=self.top)
        git(self.repo, "remote", "add", "origin", str(self.remote))

        (self.repo / "pom.xml").write_text(
            POM.format(version="0.2.236-SNAPSHOT"),
            encoding="utf-8",
        )
        git(self.repo, "add", "pom.xml")
        git(self.repo, "commit", "-q", "-m", "baseline")
        self.baseline = git(self.repo, "rev-parse", "HEAD")

        git(self.repo, "worktree", "add", "--detach", str(self.candidate_worktree), self.baseline)
        pom = self.candidate_worktree / "pom.xml"
        pom.write_text(POM.format(version="0.2.236"), encoding="utf-8")
        git(self.candidate_worktree, "add", "pom.xml")
        git(
            self.candidate_worktree,
            "-c",
            "commit.gpgSign=false",
            "-c",
            "core.hooksPath=/dev/null",
            "commit",
            "-q",
            "-m",
            "release: materialize Protos 0.2.236 candidate",
        )
        self.candidate = git(self.candidate_worktree, "rev-parse", "HEAD")

        (self.repo / "later.txt").write_text("later main\n", encoding="utf-8")
        git(self.repo, "add", "later.txt")
        git(self.repo, "commit", "-q", "-m", "later main")
        git(self.repo, "push", "-q", "-u", "origin", "HEAD:main")

        self.selection = self.repo / "docs/project/evidence/DIST001/DIST001_E4_SELECTION.txt"
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
            "candidate_source_revision": self.candidate,
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
                "dist/verify_release_candidate_lineage.py",
                "--selection",
                str(self.selection),
            ],
            cwd=self.repo,
            check=check,
        )

    def test_accepts_exact_release_only_detached_candidate(self) -> None:
        result = self.invoke()
        self.assertIn("DIST001_E4B4_LINEAGE: PASS candidate=" + self.candidate, result.stdout)

    def test_accepts_external_bundle_invocation_with_explicit_repository_root(self) -> None:
        source_root = Path(__file__).resolve().parents[1]
        verifier = source_root / "dist/verify_release_candidate_lineage.py"
        result = run(
            [
                sys.executable,
                str(verifier),
                "--repository-root",
                str(self.repo),
                "--selection",
                str(self.selection),
            ],
            cwd=self.top,
        )
        self.assertIn(
            "DIST001_E4B4_LINEAGE: PASS candidate=" + self.candidate,
            result.stdout,
        )

    def test_rejects_extra_candidate_path(self) -> None:
        (self.candidate_worktree / "extra.txt").write_text("extra\n", encoding="utf-8")
        git(self.candidate_worktree, "add", "extra.txt")
        git(self.candidate_worktree, "commit", "-q", "-m", "extra")
        self.write_selection(candidate_source_revision=git(self.candidate_worktree, "rev-parse", "HEAD"))
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("candidate parent mismatch", result.stderr)

    def test_rejects_dirty_candidate_worktree(self) -> None:
        (self.candidate_worktree / "dirty.txt").write_text("dirty\n", encoding="utf-8")
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("candidate worktree is not clean", result.stderr)

    def test_rejects_local_branch_ref_to_candidate(self) -> None:
        git(self.repo, "branch", "forbidden-candidate", self.candidate)
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("forbidden local/remote refs", result.stderr)

    def test_rejects_future_local_tag_collision(self) -> None:
        git(self.repo, "tag", "v0.2.236", self.baseline)
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("future release tag already exists locally", result.stderr)

    def test_rejects_publication_authorization(self) -> None:
        self.write_selection(release_publication_authorized="true")
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("release_publication_authorized mismatch", result.stderr)

    def test_rejects_candidate_without_registered_worktree(self) -> None:
        git(self.repo, "worktree", "remove", "--force", str(self.candidate_worktree))
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("exactly one registered worktree", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
