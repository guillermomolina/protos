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

"""Protos Case listing, terminal summary and shard evidence fixtures; no CLI required."""

import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import ci_protos_test_shards as protos

REV = "a" * 40


def listing(refs):
    return json.dumps({"schema": protos.LISTING_SCHEMA,
                       "cases": [{"ref": ref, "display": "Case " + ref} for ref in refs]}) + "\n"


class ListingTests(unittest.TestCase):
    def test_valid_and_empty_listings(self):
        self.assertEqual([e["ref"] for e in protos.listing_entries(listing(["b", "a"]))], ["b", "a"])
        self.assertEqual(protos.listing_entries(listing([])), [])

    def test_malformed_listings_rejected(self):
        for label, text in (
                ("not json", "4 passed, 0 failed"),
                ("stray output", listing(["a"]) + "Protos test tool bootstrap\n"),
                ("wrong schema", json.dumps({"schema": "protos.test.cases/v2", "cases": []})),
                ("missing cases", json.dumps({"schema": protos.LISTING_SCHEMA})),
                ("extra key", json.dumps({"schema": protos.LISTING_SCHEMA, "cases": [], "x": 1})),
                ("duplicate key", '{"schema": "%s", "cases": [], "cases": []}' % protos.LISTING_SCHEMA),
                ("cases not list", json.dumps({"schema": protos.LISTING_SCHEMA, "cases": {}})),
                ("missing display", json.dumps({"schema": protos.LISTING_SCHEMA, "cases": [{"ref": "a"}]})),
                ("empty ref", json.dumps({"schema": protos.LISTING_SCHEMA,
                                          "cases": [{"ref": "", "display": "x"}]})),
                ("non-string ref", json.dumps({"schema": protos.LISTING_SCHEMA,
                                               "cases": [{"ref": 1, "display": "x"}]})),
                ("duplicate ref", listing(["a", "a"]))):
            with self.subTest(label=label), self.assertRaises(ValueError):
                protos.listing_entries(text)


class SummaryTests(unittest.TestCase):
    def test_completed_and_aborted_summaries(self):
        self.assertEqual(protos.terminal_summary("[a] 3/3\n3 passed, 0 failed\nProtos tests total time: 9 s\n"),
                         dict(outcome="completed", completedCount=3, passedCount=3, failedCount=0))
        self.assertEqual(protos.terminal_summary("2 passed, 1 failed\r\n"),
                         dict(outcome="completed", completedCount=3, passedCount=2, failedCount=1))
        self.assertEqual(protos.terminal_summary("5 completed, 2 failed - infrastructure-aborted\n"),
                         dict(outcome="infrastructure-aborted", completedCount=5, passedCount=3, failedCount=2))

    def test_missing_ambiguous_or_malformed_summaries(self):
        for label, text in (("missing", "[a] 3/3\n"), ("empty", ""),
                            ("duplicate", "3 passed, 0 failed\n3 passed, 0 failed\n"),
                            ("mixed", "3 passed, 0 failed\n3 completed, 0 failed - infrastructure-aborted\n"),
                            ("leading zero", "03 passed, 0 failed\n"),
                            ("prefixed", "note: 3 passed, 0 failed\n"),
                            ("inconsistent abort", "1 completed, 2 failed - infrastructure-aborted\n")):
            with self.subTest(label=label), self.assertRaises(ValueError):
                protos.terminal_summary(text)


class ShardResultTests(unittest.TestCase):
    refs = ["c1", "c2", "c3", "c4", "c5"]

    def result(self, index=1, log=None, status=0, shard=None):
        shard = self.refs[index - 1::4] if shard is None else shard
        log = "%s passed, 0 failed\n" % len(shard) if log is None else log
        return protos.shard_result(4, index, 2, listing(self.refs), listing(shard), log, status, REV)

    def test_passing_and_empty_shards(self):
        first = self.result()
        self.assertEqual((first["status"], first["cases"], first["passedCount"]), ("pass", ["c1", "c5"], 2))
        self.assertEqual(first["planDigest"], protos.refs_digest(self.refs))
        empty = protos.shard_result(4, 4, 2, listing(["a", "b"]), listing([]), "0 passed, 0 failed\n", 0, REV)
        self.assertEqual((empty["status"], empty["cases"]), ("pass", []))

    def test_failed_outcomes_recorded_as_failures(self):
        for label, kwargs in (
                ("failed Cases", dict(log="1 passed, 1 failed\n", status=2)),
                ("aborted", dict(log="2 completed, 0 failed - infrastructure-aborted\n", status=2)),
                ("nonzero despite success", dict(status=2)),
                ("success despite failures", dict(log="1 passed, 1 failed\n")),
                ("count mismatch", dict(log="1 passed, 0 failed\n")),
                ("over count", dict(log="3 passed, 0 failed\n"))):
            with self.subTest(label=label):
                result = self.result(**kwargs)
                self.assertEqual(result["status"], "fail")
                self.assertTrue(result["reasons"])

    def test_invalid_inputs_rejected(self):
        for kwargs in (dict(index=0), dict(index=5), dict(status=-1), dict(log="no summary\n")):
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                self.result(**kwargs)
        with self.assertRaises(ValueError):
            protos.shard_result(4, 1, 2, listing([]), listing([]), "0 passed, 0 failed\n", 0, REV)


class PlanAndCliTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)

    def test_plan_identity(self):
        plan = protos.check_plan(protos.make_plan(listing(["x", "y"]), 4, 2, REV))
        self.assertEqual((plan["cases"], plan["shardCount"], plan["jobs"]), (["x", "y"], 4, 2))
        for count, jobs in ((0, 2), (4, 0), ("4", 2)):
            with self.subTest(count=count, jobs=jobs), self.assertRaises(ValueError):
                protos.make_plan(listing(["x"]), count, jobs, REV)
        with self.assertRaises(ValueError):
            protos.make_plan(listing([]), 4, 2, REV)

    def run_report(self, exit_text, log="1 passed, 0 failed\n"):
        files = dict(listing=listing(["a", "b"]), shard=listing(["a"]), log=log, exit=exit_text)
        for name, text in files.items():
            (self.directory / name).write_text(text, encoding="utf-8")
        output = self.directory / "out" / "protos-result.json"
        argv = ["ci", "report", "--listing", str(self.directory / "listing"),
                "--shard-listing", str(self.directory / "shard"), "--log", str(self.directory / "log"),
                "--exit-status-file", str(self.directory / "exit"), "--count", "2", "--index", "1",
                "--jobs", "2", "--revision", REV, "--output", str(output)]
        with patch.object(sys, "argv", argv):
            return protos.main(), output

    def test_cli_report_preserves_exit_status(self):
        status, output = self.run_report("0\n")
        self.assertEqual(status, 0)
        self.assertEqual(json.loads(output.read_text())["status"], "pass")
        status, output = self.run_report("2\n")
        self.assertEqual(status, 1)
        self.assertEqual(json.loads(output.read_text())["exitStatus"], 2)
        output.unlink()
        for bad in ("", "x", "-1"):
            with self.subTest(exit=bad):
                self.assertEqual(self.run_report(bad)[0], 1)
                self.assertFalse(output.exists())

    def test_cli_plan(self):
        (self.directory / "listing").write_text(listing(["a"]), encoding="utf-8")
        output = self.directory / "plan.json"
        argv = ["ci", "plan", "--listing", str(self.directory / "listing"), "--count", "4",
                "--jobs", "2", "--revision", REV, "--output", str(output)]
        with patch.object(sys, "argv", argv):
            self.assertEqual(protos.main(), 0)
        protos.check_plan(json.loads(output.read_text()))


class ScopeBindingTests(unittest.TestCase):
    def test_scoped_plans_and_empty_shards(self):
        for scope in ("core", "libraries", "library:toml"):
            plan = protos.make_plan(listing(["a"]), 2, 1, REV, scope)
            results = [protos.shard_result(2, index, 1, listing(["a"]),
                       listing(["a"] if index == 1 else []),
                       "%s passed, 0 failed\n" % (1 if index == 1 else 0), 0, REV, scope)
                       for index in (1, 2)]
            self.assertEqual(scope, protos.check_results(plan, results, REV, 2)["scope"])
            other = dict(results[0], scope="all")
            with self.assertRaises(ValueError):
                protos.check_results(plan, [other, results[1]], REV, 2)
            with self.assertRaises(ValueError):
                protos.check_results(plan, results, "b" * 40, 2)
            with self.assertRaises(ValueError):
                protos.check_results(plan, results[:1], REV, 2)
            with self.assertRaises(ValueError):
                protos.check_results(plan, [results[0], results[0]], REV, 2)
            wrong = dict(results[0], planDigest="0" * 64)
            with self.assertRaises(ValueError):
                protos.check_results(plan, [wrong, results[1]], REV, 2)

    def test_invalid_scopes_fail_closed(self):
        for scope in (None, "", "library:", "library:unknown", "library:../toml", [], "CORE"):
            with self.subTest(scope=scope), self.assertRaises(ValueError):
                protos.make_plan(listing(["a"]), 1, 1, REV, scope)

    def test_scope_participates_in_digest(self):
        self.assertNotEqual(protos.make_plan(listing(["a"]), 1, 1, REV, "core")["planDigest"],
                            protos.make_plan(listing(["a"]), 1, 1, REV, "libraries")["planDigest"])


if __name__ == "__main__":
    unittest.main()
