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

"""Focused integration tests for scripts/publication_validation.py.

AUD007-B2 cases print ``AUD007_RESULT <KEY>=<VALUE>`` lines to stderr after
their invariants have been asserted.
"""

from __future__ import print_function

import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


HERE = Path(__file__).resolve().parent
HELPER_PATH = HERE / "publication_validation.py"
SELECTOR_PATH = HERE / "validation_impact.py"
STYLE_GUARD_PATH = HERE / "source_style_guard.py"
STYLE_EXCEPTIONS_PATH = HERE / "source_style_exceptions.json"
LEGACY_GUARD_PATH = HERE / "legacy_execution_guard.py"

SPEC = importlib.util.spec_from_file_location(
    "publication_validation",
    str(HELPER_PATH),
)
HELPER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HELPER)

SHADOW_SOURCE = "src/main/java/com/guillermomolina/protos/Shadow.java"


def report(key, value):
    print("AUD007_RESULT %s=%s" % (key, value), file=sys.stderr)


class PublicationValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp = Path(tempfile.mkdtemp(prefix="protos-publication-validation-"))
        self.repo = self.temp / "repo"
        self.bin = self.temp / "bin"
        self.log = self.temp / "mvn.log"
        self.make_log = self.temp / "make.log"
        self.bin.mkdir()
        self.tmp = self.temp / "tmp"
        self.tmp.mkdir()
        self.shared_m2 = self.temp / "shared-m2"

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
        shutil.copyfile(str(STYLE_GUARD_PATH), str(scripts / "source_style_guard.py"))
        shutil.copyfile(str(STYLE_EXCEPTIONS_PATH), str(scripts / "source_style_exceptions.json"))
        shutil.copyfile(
            str(LEGACY_GUARD_PATH),
            str(scripts / "legacy_execution_guard.py"),
        )
        (self.repo / "tracked.txt").write_text("base\n", encoding="utf-8")
        (self.repo / ".gitignore").write_text(
            "__pycache__/\ntmp/\n.mvn/\n", encoding="utf-8")

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

        make = self.bin / "make"
        make.write_text(
            "#!/usr/bin/env bash\n"
            "printf '%s\\n' \"$*\" >> \"$MAKE_LOG\"\n"
            "if [ -e \"" + SHADOW_SOURCE + "\" ]; then\n"
            "  printf 'observed\\n' > \"$MAKE_LOG.shadow\"\n"
            "fi\n"
            "exit \"${MAKE_EXIT_CODE:-0}\"\n",
            encoding="utf-8",
        )
        make.chmod(0o755)

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

    def run_helper(
        self,
        candidate,
        top_level=False,
        exit_code="0",
        make_exit_code="0",
    ):
        env = os.environ.copy()
        env["PATH"] = str(self.bin) + os.pathsep + env.get("PATH", "")
        env["MVN_LOG"] = str(self.log)
        env["MVN_EXIT_CODE"] = exit_code
        env["MAKE_LOG"] = str(self.make_log)
        env["MAKE_EXIT_CODE"] = make_exit_code
        env["HOME"] = str(self.temp / "home")
        env[HELPER.SHARED_MAVEN_REPOSITORY_ENV] = str(self.shared_m2)
        env.pop("MVN_FLAGS", None)
        with mock.patch.dict(os.environ, env, clear=True), \
                mock.patch.object(HELPER.tempfile, "tempdir", str(self.tmp)):
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

    def make_calls(self):
        if not self.make_log.exists():
            return []
        return [
            line.strip()
            for line in self.make_log.read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]

    def assert_full_make(self):
        calls = self.make_calls()
        self.assertEqual(1, len(calls))
        words = calls[0].split()
        self.assertEqual("test", words[0])
        self.assert_isolated_maven(words[1:], prefix="MVN_FLAGS=")

    def assert_isolated_maven(self, words, prefix=""):
        private = [w for w in words
                   if w.startswith(prefix + "-Dmaven.repo.local=")
                   or w.startswith("-Dmaven.repo.local=")]
        tail = [w for w in words if w.startswith("-Dmaven.repo.local.tail=")]
        self.assertEqual(1, len(private), words)
        self.assertEqual(["-Dmaven.repo.local.tail=" + str(self.shared_m2)], tail)
        private_path = Path(private[0].split("=", 2)[-1])
        self.assertEqual(self.tmp, private_path.parent.parent)
        self.assertTrue(private_path.parent.name.startswith(
            HELPER.PRIVATE_MAVEN_PREFIX))
        self.assertFalse(private_path.parent.exists(),
                         "private Maven repository must be cleaned up")
        return private_path

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
        self.assert_isolated_maven(calls[0].split())

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
        self.assertEqual([], self.maven_calls())
        self.assert_full_make()

    def test_unknown_path_runs_full(self):
        candidate = self.commit_files({
            "future/executable/Probe.protos": "self\n",
        })
        self.assertEqual(0, self.run_helper(candidate))
        self.assertEqual([], self.maven_calls())
        self.assert_full_make()

    def test_cross_tool_delta_runs_full(self):
        candidate = self.commit_files({
            "protos/tools/package/Probe.protos": "self\n",
            "protos/tools/test/Probe.protos": "self\n",
        })
        self.assertEqual(0, self.run_helper(candidate))
        self.assertEqual([], self.maven_calls())
        self.assert_full_make()

    def test_top_level_closure_with_tool_change_forces_full(self):
        candidate = self.commit_files({
            "protos/tools/package/Probe.protos": "self\n",
        })
        self.assertEqual(0, self.run_helper(candidate, top_level=True))
        self.assertEqual([], self.maven_calls())
        self.assert_full_make()

    def test_top_level_closure_without_tool_change_runs_full(self):
        candidate = self.commit_files({
            "src/main/java/com/guillermomolina/protos/Probe.java": "final class Probe {}\n",
        })
        self.assertEqual(0, self.run_helper(candidate, top_level=True))
        self.assertEqual([], self.maven_calls())
        self.assert_full_make()

    def test_shared_plus_package_tool_change_runs_complete_suite(self):
        candidate = self.commit_files({
            "src/main/java/com/guillermomolina/protos/Probe.java": "final class Probe {}\n",
            "protos/tools/package/Probe.protos": "self\n",
        })
        self.assertEqual(0, self.run_helper(candidate))
        self.assertEqual([], self.maven_calls())
        self.assert_full_make()

    def test_source_style_regression_fails_before_maven(self):
        candidate = self.commit_files({
            "protos/tools/package/Probe.protos": 'm: Map()\nm.at("x")\n',
        })
        self.assertEqual(2, self.run_helper(candidate))
        self.assertEqual([], self.maven_calls())

    def test_assertion_duplication_fails_before_maven(self):
        candidate = self.commit_files({
            "protos/tests/library/probe.protos": """
require: (condition) => {
    condition.ifFalse(() => {
        Error().signal()
    })
}

require(true)
""",
        })

        self.assertEqual(2, self.run_helper(candidate))
        self.assertEqual([], self.maven_calls())

    def test_missing_source_style_guard_fails_before_maven(self):
        candidate = self.commit_files({"scripts/source_style_guard.py": None})
        self.assertEqual(2, self.run_helper(candidate))
        self.assertEqual([], self.maven_calls())

    def test_legacy_execution_regression_fails_before_maven(self):
        candidate = self.commit_files({
            "src/main/java/com/guillermomolina/protos/LegacyProbe.java":
                "final class LegacyProbe { ProtosExpressionNode node; }\n",
        })
        self.assertEqual(2, self.run_helper(candidate))
        self.assertEqual([], self.maven_calls())

    def test_missing_legacy_execution_guard_fails_before_maven(self):
        candidate = self.commit_files({
            "scripts/legacy_execution_guard.py": None,
        })
        self.assertEqual(2, self.run_helper(candidate))
        self.assertEqual([], self.maven_calls())

    def test_selected_test_failure_prevents_success(self):
        candidate = self.commit_files({
            "protos/tools/package/Probe.protos": "self\n",
        })
        self.assertEqual(7, self.run_helper(candidate, exit_code="7"))

    def test_full_make_failure_prevents_success(self):
        candidate = self.commit_files({
            "src/main/java/com/guillermomolina/protos/Probe.java":
                "final class Probe {}\n",
        })
        self.assertEqual(
            9,
            self.run_helper(candidate, make_exit_code="9"),
        )
        self.assertEqual([], self.maven_calls())
        self.assert_full_make()

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

    def test_parser_rejects_retired_full_non_tool_result(self):
        with self.assertRaises(HELPER.PublicationValidationError):
            HELPER.parse_selector_result(
                '{"validation_impact":"FULL:NON_TOOL",'
                '"affected_test_set":"NON_TOOL","full_test_suite":"SKIP_ALLOWED",'
                '"reason":"retired quarantine"}'
            )

    # AUD007-B2 F3: candidate evidence binds every observable input -------
    def untracked(self, relative, content="untracked\n"):
        path = self.repo / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def run_helper_capture(self, candidate):
        with mock.patch.object(sys, "stderr", new=_Capture()) as err:
            code = self.run_helper(candidate)
        return code, err.text()

    def test_f3_proof_untracked_input_escapes_tracked_only_binding(self):
        candidate = self.commit_files({
            "src/main/java/com/guillermomolina/protos/Probe.java":
                "final class Probe {}\n",
        })
        self.untracked(SHADOW_SOURCE, "final class Shadow {}\n")

        absent = subprocess.run(
            ["git", "-C", str(self.repo), "cat-file", "-e",
             candidate + ":" + SHADOW_SOURCE],
            stderr=subprocess.DEVNULL,
        ).returncode != 0
        self.assertTrue(absent)
        report("CANDIDATE_SHA_DOES_NOT_CONTAIN_INPUT", "YES")

        # The pre-repair binding was exactly this tracked-only status query.
        pre_repair = subprocess.check_output(
            ["git", "-C", str(self.repo), "status", "--porcelain",
             "--untracked-files=no"], text=True).strip()
        self.assertEqual("", pre_repair)
        with mock.patch.object(HELPER, "observable_untracked_inputs",
                               return_value=[]):
            self.assertEqual(0, self.run_helper(candidate))
        report("VALIDATOR_ACCEPTED_BEFORE_REPAIR", "YES")
        shadow_log = Path(str(self.make_log) + ".shadow")
        self.assertTrue(shadow_log.is_file(),
                        "selected validation must observe the untracked input")
        report("VALIDATION_CAN_OBSERVE_INPUT", "YES")

        self.make_log.unlink()
        shadow_log.unlink()
        code, err = self.run_helper_capture(candidate)
        self.assertEqual(2, code)
        self.assertIn("untracked validation-observable input", err)
        self.assertIn(SHADOW_SOURCE, err)
        self.assertEqual([], self.make_calls())
        self.assertTrue((self.repo / SHADOW_SOURCE).is_file(),
                        "fail-closed must preserve the untracked input")
        report("F3_UNTRACKED_CANDIDATE_INPUT", "REPAIRED")

    def assert_untracked_fails_closed(self, relative):
        candidate = self.commit_files({
            "protos/tools/package/Probe.protos": "self\n",
        })
        self.untracked(relative)
        code, err = self.run_helper_capture(candidate)
        self.assertEqual(2, code, relative)
        self.assertIn(relative, err)
        self.assertEqual([], self.maven_calls())
        self.assertEqual([], self.make_calls())
        self.assertTrue((self.repo / relative).is_file())

    def test_f3_untracked_main_source_fails_closed(self):
        self.assert_untracked_fails_closed(SHADOW_SOURCE)

    def test_f3_untracked_test_source_fails_closed(self):
        self.assert_untracked_fails_closed(
            "src/test/java/com/guillermomolina/protos/ShadowTest.java")

    def test_f3_untracked_protos_library_and_tool_fail_closed(self):
        self.assert_untracked_fails_closed("protos/lib/Shadow.protos")

    def test_f3_untracked_tool_local_input_fails_closed(self):
        self.assert_untracked_fails_closed("protos/tools/package/Shadow.protos")

    def test_f3_untracked_validation_script_fails_closed(self):
        self.assert_untracked_fails_closed("scripts/shadow_guard.py")

    def test_f3_untracked_tools_helper_fails_closed(self):
        self.assert_untracked_fails_closed("tools/shadow_guard.py")

    def test_f3_untracked_unknown_root_fails_closed(self):
        self.assert_untracked_fails_closed("future/Shadow.protos")

    def test_f3_ignored_maven_config_fails_closed(self):
        candidate = self.commit_files({
            "protos/tools/package/Probe.protos": "self\n",
        })
        self.untracked(".mvn/maven.config", "-Daether.offline=true\n")
        code, err = self.run_helper_capture(candidate)
        self.assertEqual(2, code)
        self.assertIn(".mvn/maven.config", err)
        self.assertEqual([], self.maven_calls())

    def test_f3_unrelated_untracked_scratch_is_preserved(self):
        candidate = self.commit_files({
            "protos/tools/package/Probe.protos": "self\n",
        })
        self.untracked("docs/scratch/notes.md")
        self.untracked("tmp/scratch.txt")
        self.assertEqual(0, self.run_helper(candidate))
        self.assertEqual(1, len(self.maven_calls()))
        self.assertTrue((self.repo / "docs/scratch/notes.md").is_file())
        self.assertTrue((self.repo / "tmp/scratch.txt").is_file())
        report("F3_UNRELATED_UNTRACKED_PRESERVED", "YES")

    def test_f3_clean_exact_candidate_passes(self):
        candidate = self.commit_files({
            "protos/tools/package/Probe.protos": "self\n",
        })
        self.assertEqual(0, self.run_helper(candidate))
        report("F3_RELEVANT_UNTRACKED_FAIL_CLOSED", "YES")

    def test_f3_dirty_tracked_error_names_path(self):
        candidate = self.commit_files({
            "protos/tools/package/Probe.protos": "self\n",
        })
        (self.repo / "tracked.txt").write_text("dirty\n", encoding="utf-8")
        code, err = self.run_helper_capture(candidate)
        self.assertEqual(2, code)
        self.assertIn("tracked.txt", err)

    # AUD007-B2 F1: writable Maven state is private per validation --------
    def test_f1_concurrent_validations_get_distinct_private_heads(self):
        with mock.patch.object(HELPER.tempfile, "tempdir", str(self.tmp)):
            with HELPER.PrivateMavenRepository() as first:
                with HELPER.PrivateMavenRepository() as second:
                    self.assertNotEqual(first, second)
                    self.assertTrue(first.is_dir() and second.is_dir())
        self.assertEqual([], list(self.tmp.iterdir()))

    def test_f1_recovery_removes_only_dead_owner_residue(self):
        dead = subprocess.Popen([sys.executable, "-c", "pass"])
        dead.wait()
        stale = self.tmp / (HELPER.PRIVATE_MAVEN_PREFIX + "stale")
        live = self.tmp / (HELPER.PRIVATE_MAVEN_PREFIX + "live")
        foreign = self.tmp / "foreign-dir"
        for path, pid in ((stale, dead.pid), (live, os.getpid())):
            (path / "repository").mkdir(parents=True)
            (path / HELPER.PRIVATE_MAVEN_OWNER).write_text(
                '{"pid": %d}' % pid, encoding="utf-8")
        foreign.mkdir()
        recovered = HELPER.recover_private_maven_residue(self.tmp)
        self.assertEqual([stale.name], recovered)
        self.assertTrue(live.is_dir())
        self.assertTrue(foreign.is_dir())

    def test_f1_shared_repository_follows_user_settings(self):
        home = self.temp / "home"
        (home / ".m2").mkdir(parents=True)
        (home / ".m2" / "settings.xml").write_text(
            '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">'
            "<localRepository>${user.home}/custom-m2</localRepository>"
            "</settings>", encoding="utf-8")
        self.assertEqual(
            (home / "custom-m2").resolve(),
            HELPER.shared_maven_repository({"HOME": str(home)}))
        self.assertEqual(
            (self.temp / "x").resolve(),
            HELPER.shared_maven_repository({
                "HOME": str(home),
                HELPER.SHARED_MAVEN_REPOSITORY_ENV: str(self.temp / "x")}))

    def test_f1_full_validation_preserves_existing_mvn_flags(self):
        command = HELPER.validation_command(
            {"validation_impact": "FULL", "affected_test_set": "ALL"},
            ["-Dmaven.repo.local=/p", "-Dmaven.repo.local.tail=/s"],
            env={"MVN_FLAGS": "-B -q"})
        self.assertEqual(
            ["make", "test",
             "MVN_FLAGS=-B -q -Dmaven.repo.local=/p -Dmaven.repo.local.tail=/s"],
            command)

    def test_f1_whitespace_repository_path_fails_closed(self):
        with self.assertRaises(HELPER.PublicationValidationError):
            HELPER.maven_isolation_flags(Path("/a b"), Path("/s"))


class _Capture(object):
    def __init__(self):
        self.parts = []

    def write(self, text):
        self.parts.append(text)

    def flush(self):
        pass

    def text(self):
        return "".join(self.parts)


if __name__ == "__main__":
    unittest.main()
