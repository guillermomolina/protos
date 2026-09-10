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

"""Focused tests for scripts/validation_impact.py."""

from __future__ import print_function

import importlib.util
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
MODULE_PATH = os.path.join(HERE, "validation_impact.py")
SPEC = importlib.util.spec_from_file_location("validation_impact", MODULE_PATH)
IMPACT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(IMPACT)


class ValidationImpactTest(unittest.TestCase):
    def assert_package(self, paths):
        result = IMPACT.classify_paths(paths)
        self.assertEqual("TOOL_LOCAL:PACKAGE", result.impact)
        self.assertTrue(result.skip_allowed)
        self.assertEqual(IMPACT.PACKAGE_TEST_SET, result.test_set)

    def assert_test_tool(self, paths):
        result = IMPACT.classify_paths(paths)
        self.assertEqual("TOOL_LOCAL:TEST", result.impact)
        self.assertTrue(result.skip_allowed)
        self.assertEqual(IMPACT.TEST_TOOL_TEST_SET, result.test_set)

    def assert_full(self, paths, closure=False):
        result = IMPACT.classify_paths(paths, top_level_closure=closure)
        self.assertEqual("FULL", result.impact)
        self.assertFalse(result.skip_allowed)
        self.assertEqual("ALL", result.test_set)

    def assert_full_non_tool(self, paths, closure=False):
        result = IMPACT.classify_paths(paths, top_level_closure=closure)
        self.assertEqual("FULL:NON_TOOL", result.impact)
        self.assertTrue(result.skip_allowed)
        self.assertEqual("NON_TOOL", result.test_set)

    def test_package_source_is_local(self):
        self.assert_package(["protos/tools/package/LockDocument.protos"])

    def test_package_fixture_is_local(self):
        self.assert_package(["protos/tests/package-tool/lock/canonical.protos"])

    def test_package_java_owned_test_is_local(self):
        self.assert_package([
            "src/test/java/com/guillermomolina/protos/execution/"
            "ProtosPackageContentVerificationTest.java"
        ])

    def test_package_with_docs_is_still_local(self):
        self.assert_package([
            "protos/tools/package/ExecutionPlan.protos",
            "docs/project/work/TOOL001/TOOL001_PACKAGE_TOOL.md",
            "CHANGELOG.md",
        ])

    def test_package_affected_set_includes_test_tool_package_consumers(self):
        result = IMPACT.classify_paths([
            "protos/tools/package/ExecutionPlan.protos"
        ])
        self.assertEqual("TOOL_LOCAL:PACKAGE", result.impact)
        self.assertIn("ProtosTestToolPackage*Test", result.test_set)

    def test_test_tool_package_test_remains_test_tool_local_when_it_is_the_delta(self):
        self.assert_test_tool([
            "src/test/java/com/guillermomolina/protos/execution/"
            "ProtosTestToolPackageExecutionEnvironmentTest.java"
        ])

    def test_test_tool_source_is_local(self):
        self.assert_test_tool(["protos/tools/test/Runner.protos"])

    def test_tool002_fixture_is_local(self):
        self.assert_test_tool([
            "protos/tests/tooling/tool002-d3a3-sequential-runner.protos"
        ])

    def test_test_tool_java_owned_test_is_local(self):
        self.assert_test_tool([
            "src/test/java/com/guillermomolina/protos/execution/"
            "ProtosTestToolSequentialRunnerTest.java"
        ])

    def test_shared_main_is_full_non_tool(self):
        self.assert_full_non_tool([
            "src/main/java/com/guillermomolina/protos/execution/ProtosModuleRuntime.java"
        ])

    def test_shared_library_is_full_non_tool(self):
        self.assert_full_non_tool(["protos/lib/core/Object.protos"])

    def test_unknown_tooling_fixture_is_full_non_tool(self):
        self.assert_full_non_tool(["protos/tests/tooling/tool003-future.protos"])

    def test_validation_infrastructure_is_full_non_tool(self):
        self.assert_full_non_tool(["scripts/validation_impact.py"])

    def test_agents_change_is_full_even_with_package(self):
        self.assert_full([
            "protos/tools/package/LockDocument.protos",
            "AGENTS.md",
        ])

    def test_cross_tool_delta_is_full(self):
        self.assert_full([
            "protos/tools/package/LockDocument.protos",
            "protos/tools/test/Runner.protos",
        ])

    def test_unknown_path_is_full_non_tool(self):
        self.assert_full_non_tool(["future/new-executable-surface/file.protos"])

    def test_empty_delta_is_full_non_tool(self):
        self.assert_full_non_tool([])

    def test_top_level_closure_with_tool_change_forces_full(self):
        self.assert_full(["protos/tools/package/LockDocument.protos"], closure=True)

    def test_top_level_closure_without_tool_change_keeps_tools_quarantined(self):
        self.assert_full_non_tool(
            ["src/main/java/com/guillermomolina/protos/execution/ProtosModuleRuntime.java"],
            closure=True,
        )

    def test_shared_plus_package_tool_change_requires_full_with_tools(self):
        self.assert_full([
            "src/main/java/com/guillermomolina/protos/execution/ProtosModuleRuntime.java",
            "protos/tools/package/LockDocument.protos",
        ])

    def test_rename_parser_keeps_old_and_new_paths(self):
        payload = (
            b"R100\0"
            b"src/main/java/com/guillermomolina/protos/Shared.java\0"
            b"protos/tools/package/Shared.protos\0"
        )
        paths = IMPACT.parse_name_status_z(payload)
        self.assertEqual(2, len(paths))
        self.assert_full(paths)

    def test_normal_name_status_parser(self):
        payload = (
            b"M\0protos/tools/test/Runner.protos\0"
            b"A\0docs/project/work/TOOL002/TOOL002_TEST_TOOL.md\0"
        )
        self.assertEqual(
            [
                "protos/tools/test/Runner.protos",
                "docs/project/work/TOOL002/TOOL002_TEST_TOOL.md",
            ],
            IMPACT.parse_name_status_z(payload),
        )


if __name__ == "__main__":
    unittest.main()
