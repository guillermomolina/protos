# THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
# ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
# DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
# DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
# OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
# THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
# OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
# THE CONTENTS OF THIS FILE.
#
# Software distributed under the License is distributed on an "AS IS" basis,
# WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
# the specific language governing rights and limitations under the License.

"""Focused tests for scripts/legacy_execution_guard.py."""

from __future__ import print_function

import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


HERE = Path(__file__).resolve().parent
GUARD_PATH = HERE / "legacy_execution_guard.py"

SPEC = importlib.util.spec_from_file_location(
    "legacy_execution_guard",
    str(GUARD_PATH),
)
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)


class LegacyExecutionGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp = Path(
            tempfile.mkdtemp(prefix="protos-legacy-execution-guard-")
        )
        self.repo = self.temp / "repo"

        subprocess.run(
            ["git", "init", "-b", "main", str(self.repo)],
            check=True,
            stdout=subprocess.DEVNULL,
        )
        subprocess.run(
            [
                "git",
                "-C",
                str(self.repo),
                "config",
                "user.name",
                "Guard Test",
            ],
            check=True,
        )
        subprocess.run(
            [
                "git",
                "-C",
                str(self.repo),
                "config",
                "user.email",
                "guard@example.invalid",
            ],
            check=True,
        )

        self.write(
            "src/main/java/example/Legacy.java",
            """
package example;

final class Legacy {
    private final ProtosSourceCompiler compiler =
            new ProtosSourceCompiler();

    Object execute(String source) {
        return compiler.compile(source);
    }

    Object lower(Object canonical) {
        return new CanonicalToTruffleLowerer().lower(canonical);
    }

    Object target(Object node) {
        return ProtosExecution.createCallTarget(node);
    }

    ProtosExpressionNode expression;
}
""",
        )
        self.commit("base")
        self.base = self.rev("HEAD")

    def tearDown(self):
        shutil.rmtree(self.temp)

    def write(self, relative, content):
        path = self.repo / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def commit(self, message):
        subprocess.run(
            ["git", "-C", str(self.repo), "add", "."],
            check=True,
        )
        subprocess.run(
            ["git", "-C", str(self.repo), "commit", "-m", message],
            check=True,
            stdout=subprocess.DEVNULL,
        )
        return self.rev("HEAD")

    def rev(self, ref):
        return subprocess.check_output(
            ["git", "-C", str(self.repo), "rev-parse", ref],
            text=True,
        ).strip()

    def check(self, head):
        return GUARD.check(
            self.repo,
            self.base,
            head,
        )

    def test_unchanged_legacy_baseline_passes(self):
        result = self.check(self.base)

        self.assertEqual([], result["violations"])

    def test_reducing_legacy_dependency_passes(self):
        self.write(
            "src/main/java/example/Legacy.java",
            """
package example;

final class Legacy {
    Object execute(String source) {
        return source;
    }
}
""",
        )
        head = self.commit("reduce")

        result = self.check(head)

        self.assertEqual([], result["violations"])
        self.assertLess(
            result["head_totals"]["source_compiler"],
            result["base_totals"]["source_compiler"],
        )
        self.assertLess(
            result["head_totals"]["legacy_compile_call"],
            result["base_totals"]["legacy_compile_call"],
        )

    def test_new_file_with_legacy_dependency_fails(self):
        self.write(
            "src/test/java/example/NewLegacyTest.java",
            """
package example;

final class NewLegacyTest {
    ProtosExpressionNode node;
}
""",
        )
        head = self.commit("new legacy file")

        result = self.check(head)

        self.assertIn(
            (
                "src/test/java/example/NewLegacyTest.java",
                "expression_node",
                0,
                1,
            ),
            result["violations"],
        )

    def test_retired_ast_backend_symbol_reintroduction_fails(self):
        self.write(
            "src/test/java/example/RetiredBackendTest.java",
            """
package example;

final class RetiredBackendTest {
    ProtosRootFactory factory;
}
""",
        )
        head = self.commit("reintroduce retired AST backend")

        result = self.check(head)

        self.assertIn(
            (
                "src/test/java/example/RetiredBackendTest.java",
                "retired_ast_backend",
                0,
                1,
            ),
            result["violations"],
        )

    def test_existing_file_growth_fails(self):
        path = "src/main/java/example/Legacy.java"
        original = (
            self.repo / path
        ).read_text(encoding="utf-8")

        self.write(
            path,
            original
            + """
final class ExtraLegacy {
    ProtosExpressionNode another;
}
""",
        )
        head = self.commit("grow legacy dependency")

        result = self.check(head)

        self.assertIn(
            (
                path,
                "expression_node",
                1,
                2,
            ),
            result["violations"],
        )

    def test_additional_compile_call_is_detected_without_new_type_reference(self):
        path = "src/main/java/example/Legacy.java"
        original = (
            self.repo / path
        ).read_text(encoding="utf-8")

        self.write(
            path,
            original.replace(
                """
    Object lower(Object canonical) {
""",
                """
    Object executeAgain(String source) {
        return compiler.compile(source);
    }

    Object lower(Object canonical) {
""",
            ),
        )
        head = self.commit("grow compile use")

        result = self.check(head)

        self.assertIn(
            (
                path,
                "legacy_compile_call",
                1,
                2,
            ),
            result["violations"],
        )
        self.assertEqual(
            result["base_totals"]["source_compiler"],
            result["head_totals"]["source_compiler"],
        )

    def test_comments_strings_and_text_blocks_are_ignored(self):
        self.write(
            "src/test/java/example/TextOnly.java",
            r'''
package example;

final class TextOnly {
    // ProtosSourceCompiler compiler;
    // compiler.compile(source);
    // CanonicalToTruffleLowerer lowerer;
    // ProtosExecution.createCallTarget(node);
    // ProtosExpressionNode node;

    String ordinary =
            "new ProtosSourceCompiler().compile(source) "
            + "ProtosSourceFileLoader "
            + "CanonicalToTruffleLowerer "
            + "ProtosExecution.createCallTarget(node) "
            + "ProtosExpressionNode";

    String block = """
            new ProtosSourceCompiler().compile(source)
            ProtosSourceFileLoader
            CanonicalToTruffleLowerer
            ProtosExecution.createCallTarget(node)
            ProtosExpressionNode
            """;
}
''',
        )
        head = self.commit("text only")

        result = self.check(head)

        self.assertEqual([], result["violations"])

    def test_source_file_loader_growth_fails(self):
        self.write(
            "src/test/java/example/LoaderTest.java",
            """
package example;

final class LoaderTest {
    private final ProtosSourceFileLoader loader =
            new ProtosSourceFileLoader();
}
""",
        )
        head = self.commit("loader")

        result = self.check(head)

        self.assertIn(
            (
                "src/test/java/example/LoaderTest.java",
                "source_file_loader",
                0,
                2,
            ),
            result["violations"],
        )


if __name__ == "__main__":
    unittest.main()
