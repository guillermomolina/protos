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

"""Focused tests for scripts/source_style_guard.py."""

from __future__ import print_function

import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("source_style_guard", str(HERE / "source_style_guard.py"))
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)


class SourceStyleGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp = Path(tempfile.mkdtemp(prefix="protos-source-style-guard-"))
        self.repo = self.temp / "repo"
        subprocess.run(["git", "init", "-b", "main", str(self.repo)], check=True, stdout=subprocess.DEVNULL)
        subprocess.run(["git", "-C", str(self.repo), "config", "user.name", "Guard Test"], check=True)
        subprocess.run(["git", "-C", str(self.repo), "config", "user.email", "guard@example.invalid"], check=True)
        scripts = self.repo / "scripts"
        scripts.mkdir()
        (scripts / "source_style_exceptions.json").write_text('{"version":1,"exceptions":[]}\n', encoding="utf-8")
        (self.repo / "tracked.txt").write_text("base\n", encoding="utf-8")
        self._commit("base")
        self.base = self._rev("HEAD")

    def tearDown(self):
        shutil.rmtree(self.temp)

    def _rev(self, ref):
        return subprocess.check_output(["git", "-C", str(self.repo), "rev-parse", ref], text=True).strip()

    def _commit(self, message):
        subprocess.run(["git", "-C", str(self.repo), "add", "-A"], check=True)
        subprocess.run(["git", "-C", str(self.repo), "commit", "-m", message], check=True, stdout=subprocess.DEVNULL)

    def commit_files(self, mapping):
        for relative, content in mapping.items():
            path = self.repo / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            if content is None:
                if path.exists():
                    path.unlink()
            else:
                path.write_text(content, encoding="utf-8")
        self._commit("candidate")
        return self._rev("HEAD")

    def test_new_ordinary_indexing_fails(self):
        head = self.commit_files({"protos/lib/Probe.protos": 'm: Map()\nm.at("x")\n'})
        self.assertEqual([("protos/lib/Probe.protos", "indexing", 0, 1)], GUARD.check(self.repo, self.base, head)["violations"])

    def test_existing_debt_can_stay_flat(self):
        p = self.repo / "protos/lib/Legacy.protos"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text('m.at("x")\n', encoding="utf-8")
        self._commit("legacy")
        base = self._rev("HEAD")
        head = self.commit_files({"protos/lib/Legacy.protos": 'm.at("x")\nanswer: 42\n'})
        self.assertEqual([], GUARD.check(self.repo, base, head)["violations"])

    def test_reduction_is_allowed(self):
        p = self.repo / "protos/lib/Legacy.protos"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text('m.at("x")\nm.at("y")\n', encoding="utf-8")
        self._commit("legacy")
        base = self._rev("HEAD")
        head = self.commit_files({"protos/lib/Legacy.protos": 'm["x"]\nm.at("y")\n'})
        self.assertEqual([], GUARD.check(self.repo, base, head)["violations"])

    def test_exact_exception_allows_reviewed_increase(self):
        manifest = {"version": 1, "exceptions": [{
            "path": "protos/tests/conformance/example/direct.protos",
            "family": "indexing", "max_count": 1,
            "reason": "direct indexing protocol conformance",
        }]}
        head = self.commit_files({
            "protos/tests/conformance/example/direct.protos": "receiver.at(0)\n",
            "scripts/source_style_exceptions.json": json.dumps(manifest) + "\n",
        })
        result = GUARD.check(self.repo, self.base, head)
        self.assertEqual([], result["violations"])
        self.assertEqual(1, len(result["allowed_increases"]))

    def test_exception_cannot_preauthorize_future_count(self):
        manifest = {"version": 1, "exceptions": [{
            "path": "protos/tests/conformance/example/direct.protos",
            "family": "indexing", "max_count": 2,
            "reason": "direct indexing protocol conformance",
        }]}
        head = self.commit_files({
            "protos/tests/conformance/example/direct.protos": "receiver.at(0)\n",
            "scripts/source_style_exceptions.json": json.dumps(manifest) + "\n",
        })
        with self.assertRaises(GUARD.SourceStyleGuardError):
            GUARD.check(self.repo, self.base, head)

    def test_comments_and_strings_do_not_count(self):
        head = self.commit_files({"protos/lib/Probe.protos": '// x.at(0)\ntext: "y.atPut(0, 1)"\n/* z.negated() */\nself\n'})
        self.assertEqual([], GUARD.check(self.repo, self.base, head)["violations"])

    def test_boolean_and_unary_families_are_detected(self):
        head = self.commit_files({"protos/lib/Probe.protos": "a.and(() => b)\nc.or() { d }\nflag.not()\nvalue.negated()\n"})
        self.assertEqual({
            ("protos/lib/Probe.protos", "lazy_boolean", 0, 2),
            ("protos/lib/Probe.protos", "not", 0, 1),
            ("protos/lib/Probe.protos", "negated", 0, 1),
        }, set(GUARD.check(self.repo, self.base, head)["violations"]))

    def test_markdown_scans_only_protos_js_fences(self):
        head = self.commit_files({"docs/guide/99-test.md": "Inline `m.at(0)` prose.\n\n```bash\necho 'm.at(0)'\n```\n\n```protos\nm.at(0)\n```\n"})
        self.assertEqual([("docs/guide/99-test.md", "indexing", 0, 1)], GUARD.check(self.repo, self.base, head)["violations"])

    def test_rename_preserves_baseline_count(self):
        old = self.repo / "protos/lib/Old.protos"
        old.parent.mkdir(parents=True, exist_ok=True)
        old.write_text("m.at(0)\n", encoding="utf-8")
        self._commit("legacy")
        base = self._rev("HEAD")
        old.rename(self.repo / "protos/lib/New.protos")
        self._commit("rename")
        head = self._rev("HEAD")
        self.assertEqual([], GUARD.check(self.repo, base, head)["violations"])


if __name__ == "__main__":
    unittest.main()
