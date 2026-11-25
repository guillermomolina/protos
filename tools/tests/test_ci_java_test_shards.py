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

"""Compiled selection and XML evidence regression fixtures; no JVM required."""

import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import ci_java_test_shards as shards


class ShardTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.root = self.directory / "classes"
        self.plan_dir = self.directory / "plan"
        self.reports = self.directory / "reports"
        self.rev = "a" * 40
        self.serial = "SerialTest"
        self.excludes = "**/SerialTest.java"
        for identity in ("p.ZTest", "p.ATest", "p.SerialTest", "q.ATest", "p.ATest$Nested"):
            self.compiled(identity)

    def compiled(self, identity):
        path = self.root / (identity.replace(".", "/") + ".class")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"")
        return path

    def plan(self, count=2):
        return shards.make_plan(self.root, self.serial, self.excludes, count, self.rev)

    def validate(self, plan, count=2, index=1, rev=None):
        shards.validate_worker(plan, self.root, self.serial, self.excludes,
                               count, index, rev or self.rev, self.plan_dir)

    def xml(self, cases, *, name="p.ATest", file="TEST-a.xml"):
        failures = sum("<failure" in case for case in cases)
        errors = sum("<error" in case for case in cases)
        skips = sum("<skipped" in case for case in cases)
        content = ('<testsuite name="%s" tests="%s" failures="%s" errors="%s" skipped="%s">'
                   % (name, len(cases), failures, errors, skips)) + "".join(cases) + "</testsuite>"
        (self.reports / file).write_text(content, encoding="utf-8")

    def begin(self):
        plan = self.plan()
        shards.begin_reports(plan, "parallel", 1, self.reports)
        return plan

    def case(self, cls="p.ATest", name="works", child=""):
        return '<testcase classname="%s" name="%s">%s</testcase>' % (cls, name, child)

    def test_defaults_fully_qualified_and_nested(self):
        for name in ("TestPrefix", "SuffixTests", "SuffixTestCase", "Helper", "Stress"):
            self.compiled("p." + name)
        found = shards.inventory(self.root)
        for name in ("TestPrefix", "SuffixTests", "SuffixTestCase", "ATest"):
            self.assertIn("p." + name, found)
        self.assertIn("q.ATest", found)
        self.assertNotIn("p.ATest$Nested", found)
        self.assertNotIn("p.Helper", found)
        self.assertNotIn("p.Stress", found)

    def test_partition_determinism_union_and_exact_includes(self):
        plan = self.plan()
        self.assertEqual(plan, self.plan())
        self.assertEqual(plan["digest"], shards.digest(copy.deepcopy(plan)))
        parallel = [set(shard["classes"]) for shard in plan["shards"]]
        self.assertFalse(parallel[0] & parallel[1])
        self.assertFalse(set(plan["serialInventory"]) & set.union(*parallel))
        self.assertEqual(set(plan["candidateInventory"]), set.union(*parallel) | set(plan["serialInventory"]))
        self.assertEqual(plan["shards"][0]["classes"], ["p.ATest", "q.ATest"])
        shards.save_plan(plan, self.plan_dir)
        self.assertEqual((self.plan_dir / "shard-1.includes").read_text(), "p/ATest.class\nq/ATest.class\n")
        self.validate(plan)
        # Reverse catalogue iteration while preserving the same compiled paths.
        from unittest.mock import patch
        original = Path.rglob
        def reversed_paths(path, pattern):
            return iter(reversed(list(original(path, pattern))))
        with patch.object(Path, "rglob", reversed_paths):
            self.assertEqual(plan, self.plan())

    def test_serial_classification_failures(self):
        for serial, excludes in (("MissingTest", self.excludes),
                                 ("ATest", self.excludes),
                                 ("SerialTest,SerialTest", self.excludes),
                                 ("SerialTest,p.SerialTest", self.excludes),
                                 (self.serial, ""), (self.serial, "**/*Test.java")):
            with self.subTest(serial=serial, excludes=excludes), self.assertRaises(ValueError):
                shards.make_plan(self.root, serial, excludes, 2, self.rev)

    def test_invalid_count_index_and_empty_shard(self):
        for count in (0, -1, 4, True, "2"):
            with self.subTest(count=count), self.assertRaises(ValueError):
                self.plan(count)
        for index in (0, -1, 3, True):
            with self.subTest(index=index), self.assertRaises(ValueError):
                shards.selection(self.plan(), index)

    def test_missing_compiled_root_and_malformed_identity(self):
        with self.assertRaises(ValueError):
            shards.inventory(self.directory / "missing")
        self.compiled("bad-name.BadTest")
        with self.assertRaises(ValueError):
            self.plan()

    def test_plan_and_inclusion_tampering(self):
        plan = self.plan()
        shards.save_plan(plan, self.plan_dir)
        altered = copy.deepcopy(plan)
        altered["shards"][0]["classes"].append("evil.Test")
        with self.assertRaises(ValueError):
            self.validate(altered)
        altered["digest"] = shards.digest(altered)
        with self.assertRaises(ValueError):
            self.validate(altered)
        (self.plan_dir / "shard-1.includes").write_text("**/ATest.class\n")
        with self.assertRaises(ValueError):
            self.validate(plan)

    def test_stale_revision_inventory_count_and_index(self):
        plan = self.plan()
        shards.save_plan(plan, self.plan_dir)
        for kwargs in (dict(rev="b" * 40), dict(count=1), dict(index=3)):
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                self.validate(plan, **kwargs)
        path = self.root / "p/ZTest.class"
        path.unlink()
        with self.assertRaises(ValueError):
            self.validate(plan)
        self.compiled("p.ZTest")
        self.compiled("p.NewTest")
        with self.assertRaises(ValueError):
            self.validate(plan)

    def test_missing_malformed_and_duplicate_xml(self):
        plan = self.begin()
        with self.assertRaises(ValueError):
            shards.evidence(plan, "parallel", 1, self.reports, 0)
        file = self.reports / "TEST-a.xml"
        file.write_text("<broken")
        import xml.etree.ElementTree as ET
        with self.assertRaises(ET.ParseError):
            shards.evidence(plan, "parallel", 1, self.reports, 0)
        self.xml([self.case()])
        self.xml([self.case()], file="TEST-duplicate.xml")
        with self.assertRaises(ValueError):
            shards.evidence(plan, "parallel", 1, self.reports, 0)

    def test_unexpected_class_and_duplicate_case(self):
        plan = self.begin()
        for cases in ([self.case("p.SerialTest")], [self.case(), self.case()]):
            self.xml(cases)
            with self.assertRaises(ValueError):
                shards.evidence(plan, "parallel", 1, self.reports, 0)

    def test_nested_ownership_and_skipped_evidence(self):
        plan = self.begin()
        self.xml([self.case("p.ATest$Nested"), self.case("q.ATest", child="<skipped/>")])
        result = shards.evidence(plan, "parallel", 1, self.reports, 0)
        self.assertEqual(result["status"], "pass")
        self.assertEqual(result["nestedClasses"], ["p.ATest$Nested"])
        self.assertEqual(result["observedClasses"], ["p.ATest$Nested"])
        self.assertEqual(result["testCount"], 1)
        self.assertEqual(result["skippedCount"], 1)
        self.assertEqual(result["unresolvedCandidates"], [])

    def test_unresolved_candidates_are_not_covered(self):
        plan = self.begin()
        self.xml([self.case()])
        result = shards.evidence(plan, "parallel", 1, self.reports, 0)
        self.assertEqual(result["unresolvedCandidates"], ["q.ATest"])
        self.assertEqual(result["status"], "fail")

    def test_failures_errors_and_maven_failure_survive(self):
        plan = self.begin()
        for child, key in (("<failure/>", "failureCount"), ("<error/>", "errorCount")):
            self.xml([self.case(child=child), self.case("q.ATest")])
            result = shards.evidence(plan, "parallel", 1, self.reports, 0)
            self.assertEqual(result[key], 1)
            self.assertEqual(result["status"], "fail")
        self.xml([self.case(), self.case("q.ATest")])
        self.assertEqual(shards.evidence(plan, "parallel", 1, self.reports, 7)["status"], "fail")

    def test_revision_required_in_ci_and_optional_locally(self):
        from unittest.mock import patch
        with patch.dict("os.environ", {"GITHUB_ACTIONS": "true", "GITHUB_SHA": ""}):
            with self.assertRaises(ValueError):
                shards.revision()
            with self.assertRaises(ValueError):
                shards.revision("main")
            self.assertEqual(shards.revision(self.rev), self.rev)
        with patch.dict("os.environ", {"GITHUB_ACTIONS": "false", "GITHUB_SHA": ""}):
            self.assertEqual(shards.revision(), "")

    def test_stale_reports_and_count_tampering(self):
        plan = self.begin()
        with self.assertRaises(ValueError):
            shards.begin_reports(plan, "parallel", 1, self.reports)
        self.xml([self.case()])
        file = self.reports / "TEST-a.xml"
        file.write_text(file.read_text().replace('tests="1"', 'tests="2"'))
        with self.assertRaises(ValueError):
            shards.evidence(plan, "parallel", 1, self.reports, 0)


if __name__ == "__main__":
    unittest.main()
