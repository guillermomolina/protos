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
  <packaging>jar</packaging>
  <properties>
    <example.version>{version}</example.version>
  </properties>
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


class ReleaseCandidateVersionTransitionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="protos-e4b2-")
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

        self.prepare_candidate()

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

    def prepare_candidate(self) -> None:
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

    def invoke(self, *, check: bool = True) -> subprocess.CompletedProcess[str]:
        return run(
            [
                sys.executable,
                "dist/transition_release_candidate_version.py",
                "--selection",
                str(self.selection),
                "--candidate",
                str(self.candidate),
            ],
            cwd=self.repo,
            check=check,
        )

    def test_applies_only_exact_root_project_version_transition(self) -> None:
        before = (self.candidate / "pom.xml").read_text(encoding="utf-8")
        result = self.invoke()
        after = (self.candidate / "pom.xml").read_text(encoding="utf-8")

        self.assertIn("DIST001_E4B2_VERSION_TRANSITION: PASS", result.stdout)
        self.assertIn("<version>0.2.236</version>", after)
        self.assertIn("<example.version>0.2.236-SNAPSHOT</example.version>", after)
        self.assertEqual(
            after,
            before.replace(
                "<artifactId>protos</artifactId>\n  <version>0.2.236-SNAPSHOT</version>",
                "<artifactId>protos</artifactId>\n  <version>0.2.236</version>",
                1,
            ),
        )
        self.assertEqual(git(self.candidate, "diff", "--name-only"), "pom.xml")
        self.assertEqual(git(self.candidate, "diff", "--cached", "--name-only"), "")
        self.assertEqual(git(self.candidate, "rev-parse", "HEAD"), self.baseline)

    def test_rejects_dirty_candidate_before_transition(self) -> None:
        (self.candidate / "foreign.txt").write_text("foreign\n", encoding="utf-8")
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("must be clean before version transition", result.stderr)

    def test_rejects_attached_worktree(self) -> None:
        run(["git", "switch", "-c", "candidate-branch"], cwd=self.candidate)
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("attached to branch", result.stderr)

    def test_rejects_candidate_head_not_selected_baseline(self) -> None:
        (self.candidate / "x.txt").write_text("x\n", encoding="utf-8")
        git(self.candidate, "add", "x.txt")
        git(self.candidate, "commit", "-q", "-m", "unexpected")
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("candidate HEAD mismatch", result.stderr)

    def test_rejects_selection_version_mismatch(self) -> None:
        self.write_selection(
            release_baseline_version="0.2.237-SNAPSHOT",
            release_version="0.2.237",
            release_tag="v0.2.237",
        )
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("candidate project version mismatch", result.stderr)

    def test_rejects_repeat_transition_without_committing(self) -> None:
        self.invoke()
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("must be clean before version transition", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
