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

"""Focused integration tests for scripts/publication_validation.py."""

from __future__ import print_function

import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest import mock


HERE = Path(__file__).resolve().parent
HELPER_PATH = HERE / "publication_validation.py"
SELECTOR_PATH = HERE / "validation_impact.py"

SPEC = importlib.util.spec_from_file_location(
    "publication_validation",
    str(HELPER_PATH),
)
HELPER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HELPER)


class PublicationValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp = Path(tempfile.mkdtemp(prefix="protos-publication-validation-"))
        self.repo = self.temp / "repo"
        self.bin = self.temp / "bin"
        self.log = self.temp / "mvn.log"
        self.bin.mkdir()

        subprocess.run(
            ["git", "init", "-b", "main", str(self.repo)],
            check=True,
            stdout=subprocess.DEVNULL,
        )
        subprocess.run(
            ["git", "-C", str(self.repo), "config", "user.name", "Validation Test"],
            check=True,
        )
        subprocess.run(
            [
                "git",
                "-C",
                str(self.repo),
                "config",
                "user.email",
                "validation@example.invalid",
            ],
            check=True,
        )

        scripts = self.repo / "scripts"
        scripts.mkdir()
        shutil.copyfile(str(SELECTOR_PATH), str(scripts / "validation_impact.py"))
        (self.repo / "tracked.txt").write_text("base\n", encoding="utf-8")

        subprocess.run(
            ["git", "-C", str(self.repo), "add", "."],
            check=True,
        )
        subprocess.run(
            ["git", "-C", str(self.repo), "commit", "-m", "base"],
            check=True,
            stdout=subprocess.DEVNULL,
        )
        self.base = self.rev("HEAD")

        mvn = self.bin / "mvn"
        mvn.write_text(
            "#!/usr/bin/env bash\n"
            "printf '%s\\n' \"$*\" >> \"$MVN_LOG\"\n"
            "exit \"${MVN_EXIT_CODE:-0}\"\n",
            encoding="utf-8",
        )
        mvn.chmod(0o755)

    def tearDown(self):
        shutil.rmtree(self.temp)

    def rev(self, ref):
        return subprocess.check_output(
            ["git", "-C", str(self.repo), "rev-parse", ref],
            text=True,
        ).strip()

    def commit_files(self, mapping, message="candidate"):
        for relative, content in mapping.items():
            path = self.repo / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            if content is None:
                if path.exists():
                    path.unlink()
            else:
                path.write_text(content, encoding="utf-8")
        subprocess.run(
            ["git", "-C", str(self.repo), "add", "-A"],
            check=True,
        )
        subprocess.run(
            ["git", "-C", str(self.repo), "commit", "-m", message],
            check=True,
            stdout=subprocess.DEVNULL,
        )
        return self.rev("HEAD")

    def run_helper(self, candidate, top_level=False, exit_code="0"):
        env = os.environ.copy()
        env["PATH"] = str(self.bin) + os.pathsep + env.get("PATH", "")
        env["MVN_LOG"] = str(self.log)
        env["MVN_EXIT_CODE"] = exit_code
        with mock.patch.dict(os.environ, env, clear=True):
            return HELPER.run(
                self.repo,
                self.base,
                candidate,
                top_level_closure=top_level,
            )

    def maven_calls(self):
        if not self.log.exists():
            return []
        return [
            line.strip()
            for line in self.log.read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]

    def test_package_local_runs_complete_package_affected_set(self):
        candidate = self.commit_files({
            "protos/tools/package/Probe.protos": "self\n",
        })
        self.assertEqual(0, self.run_helper(candidate))
        calls = self.maven_calls()
        self.assertEqual(1, len(calls))
        self.assertIn("-Dtest=ProtosPackage*Test", calls[0])
        self.assertIn("ProtosTestToolPackage*Test", calls[0])
        self.assertTrue(calls[0].endswith(" test"))

    def test_test_tool_local_runs_complete_test_tool_affected_set(self):
        candidate = self.commit_files({
            "protos/tools/test/Probe.protos": "self\n",
        })
        self.assertEqual(0, self.run_helper(candidate))
        calls = self.maven_calls()
        self.assertEqual(1, len(calls))
        self.assertIn("-Dtest=ProtosTestTool*Test,ProtosCliTest", calls[0])

    def test_shared_path_runs_full(self):
        candidate = self.commit_files({
            "src/main/java/com/guillermomolina/protos/Probe.java": "final class Probe {}\n",
        })
        self.assertEqual(0, self.run_helper(candidate))
        self.assertEqual(["test"], self.maven_calls())

    def test_unknown_path_runs_full(self):
        candidate = self.commit_files({
            "future/executable/Probe.protos": "self\n",
        })
        self.assertEqual(0, self.run_helper(candidate))
        self.assertEqual(["test"], self.maven_calls())

    def test_cross_tool_delta_runs_full(self):
        candidate = self.commit_files({
            "protos/tools/package/Probe.protos": "self\n",
            "protos/tools/test/Probe.protos": "self\n",
        })
        self.assertEqual(0, self.run_helper(candidate))
        self.assertEqual(["test"], self.maven_calls())

    def test_top_level_closure_forces_full(self):
        candidate = self.commit_files({
            "protos/tools/package/Probe.protos": "self\n",
        })
        self.assertEqual(0, self.run_helper(candidate, top_level=True))
        self.assertEqual(["test"], self.maven_calls())

    def test_selected_test_failure_prevents_success(self):
        candidate = self.commit_files({
            "protos/tools/package/Probe.protos": "self\n",
        })
        self.assertEqual(7, self.run_helper(candidate, exit_code="7"))

    def test_dirty_tracked_state_fails_before_maven(self):
        candidate = self.commit_files({
            "protos/tools/package/Probe.protos": "self\n",
        })
        (self.repo / "tracked.txt").write_text("dirty\n", encoding="utf-8")
        self.assertEqual(2, self.run_helper(candidate))
        self.assertEqual([], self.maven_calls())

    def test_head_mismatch_fails_before_maven(self):
        candidate = self.commit_files({
            "protos/tools/package/Probe.protos": "self\n",
        })
        self.assertNotEqual(self.base, candidate)
        self.assertEqual(2, self.run_helper(self.base))
        self.assertEqual([], self.maven_calls())

    def test_missing_selector_fails_closed(self):
        candidate = self.commit_files({
            "scripts/validation_impact.py": None,
        })
        self.assertEqual(2, self.run_helper(candidate))
        self.assertEqual([], self.maven_calls())

    def test_malformed_selector_output_fails_closed(self):
        candidate = self.commit_files({
            "scripts/validation_impact.py": "print('not-json')\n",
        })
        self.assertEqual(2, self.run_helper(candidate))
        self.assertEqual([], self.maven_calls())

    def test_parser_rejects_inconsistent_local_result(self):
        with self.assertRaises(HELPER.PublicationValidationError):
            HELPER.parse_selector_result(
                '{"validation_impact":"TOOL_LOCAL:PACKAGE",'
                '"affected_test_set":"ALL","full_test_suite":"SKIP_ALLOWED",'
                '"reason":"bad"}'
            )


if __name__ == "__main__":
    unittest.main()
