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

"""Focused regression tests for scripts/maven_worktree_guard.py."""

from __future__ import print_function

import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import unittest


HERE = Path(__file__).resolve().parent
GUARD = HERE / "maven_worktree_guard.py"


class MavenWorktreeGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp = Path(tempfile.mkdtemp(prefix="protos-maven-worktree-guard-"))
        self.repo = self.temp / "repo"
        subprocess.run(
            ["git", "init", "-b", "main", str(self.repo)],
            check=True,
            stdout=subprocess.DEVNULL,
        )
        subprocess.run(
            ["git", "-C", str(self.repo), "config", "user.name", "Guard Test"],
            check=True,
        )
        subprocess.run(
            ["git", "-C", str(self.repo), "config", "user.email", "guard@example.invalid"],
            check=True,
        )
        (self.repo / "tracked.txt").write_text("base\n", encoding="utf-8")
        subprocess.run(["git", "-C", str(self.repo), "add", "tracked.txt"], check=True)
        subprocess.run(
            ["git", "-C", str(self.repo), "commit", "-m", "base"],
            check=True,
            stdout=subprocess.DEVNULL,
        )

    def tearDown(self):
        shutil.rmtree(self.temp)

    def guard_command(self, child):
        return [sys.executable, str(GUARD), "--repo", str(self.repo), "--"] + child

    def run_guard(self, child):
        return subprocess.run(
            self.guard_command(child),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

    def wait_for(self, path, process=None, timeout=10.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if path.exists():
                return
            if process is not None and process.poll() is not None:
                out, err = process.communicate()
                self.fail(
                    "guard child exited before marker; rc=%s stdout=%r stderr=%r"
                    % (process.returncode, out, err)
                )
            time.sleep(0.02)
        self.fail("timed out waiting for " + str(path))

    def waiting_child(self, marker, release, exit_code=0):
        source = (
            "from pathlib import Path; import sys,time; "
            "marker=Path(sys.argv[1]); release=Path(sys.argv[2]); "
            "marker.write_text('ready'); "
            "deadline=time.time()+10; "
            "exec(\"while not release.exists():\\n"
            "    if time.time() > deadline: raise SystemExit(91)\\n"
            "    time.sleep(0.02)\"); "
            "raise SystemExit(int(sys.argv[3]))"
        )
        return [
            sys.executable,
            "-c",
            source,
            str(marker),
            str(release),
            str(exit_code),
        ]

    def test_stable_inputs_preserve_child_success(self):
        result = self.run_guard([sys.executable, "-c", "raise SystemExit(0)"])
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("MAVEN_WORKTREE_INTEGRITY: PASS", result.stdout)

    def test_stable_inputs_preserve_child_failure(self):
        result = self.run_guard([sys.executable, "-c", "raise SystemExit(7)"])
        self.assertEqual(7, result.returncode, result.stderr)
        self.assertIn("MAVEN_WORKTREE_INTEGRITY: PASS", result.stdout)

    def test_tracked_source_change_invalidates_result(self):
        marker = self.temp / "marker"
        release = self.temp / "release"
        process = subprocess.Popen(
            self.guard_command(self.waiting_child(marker, release)),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        self.wait_for(marker, process)
        (self.repo / "tracked.txt").write_text("changed\n", encoding="utf-8")
        release.write_text("go\n", encoding="utf-8")
        out, err = process.communicate(timeout=10)
        self.assertEqual(2, process.returncode, err)
        self.assertIn("FAIL_CHANGED_DURING_COMMAND", err)
        self.assertIn("changed: tracked.txt", err)
        self.assertIn("discard Maven test/build result", err)
        self.assertIn("MAVEN_WORKTREE_LOCK: RELEASED", out)

    def test_head_change_invalidates_result(self):
        marker = self.temp / "head-marker"
        release = self.temp / "head-release"
        process = subprocess.Popen(
            self.guard_command(self.waiting_child(marker, release)),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        self.wait_for(marker, process)
        (self.repo / "tracked.txt").write_text("committed\n", encoding="utf-8")
        subprocess.run(["git", "-C", str(self.repo), "add", "tracked.txt"], check=True)
        subprocess.run(
            ["git", "-C", str(self.repo), "commit", "-m", "advance"],
            check=True,
            stdout=subprocess.DEVNULL,
        )
        release.write_text("go\n", encoding="utf-8")
        out, err = process.communicate(timeout=10)
        self.assertEqual(2, process.returncode, err)
        self.assertIn("MAVEN_WORKTREE_CHANGE: HEAD ", err)
        self.assertIn("MAVEN_WORKTREE_LOCK: RELEASED", out)

    def test_second_guarded_command_in_same_worktree_fails_closed(self):
        marker = self.temp / "lock-marker"
        release = self.temp / "lock-release"
        first = subprocess.Popen(
            self.guard_command(self.waiting_child(marker, release)),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        self.wait_for(marker, first)

        second = self.run_guard([sys.executable, "-c", "raise SystemExit(0)"])
        self.assertEqual(2, second.returncode)
        self.assertIn("another guarded Maven command is already active", second.stderr)

        release.write_text("go\n", encoding="utf-8")
        out, err = first.communicate(timeout=10)
        self.assertEqual(0, first.returncode, err)
        self.assertIn("MAVEN_WORKTREE_INTEGRITY: PASS", out)

    def test_untracked_nonignored_input_change_is_detected(self):
        (self.repo / "local-input.txt").write_text("one\n", encoding="utf-8")
        marker = self.temp / "untracked-marker"
        release = self.temp / "untracked-release"
        process = subprocess.Popen(
            self.guard_command(self.waiting_child(marker, release)),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        self.wait_for(marker, process)
        (self.repo / "local-input.txt").write_text("two\n", encoding="utf-8")
        release.write_text("go\n", encoding="utf-8")
        out, err = process.communicate(timeout=10)
        self.assertEqual(2, process.returncode, err)
        self.assertIn("changed: local-input.txt", err)
        self.assertIn("MAVEN_WORKTREE_LOCK: RELEASED", out)


if __name__ == "__main__":
    unittest.main()
