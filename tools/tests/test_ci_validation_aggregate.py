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

"""Acceptance fixtures for missing, inconsistent and failed distributed evidence."""

import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import ci_java_test_shards as shards
import ci_validation_aggregate as gate


class AggregateTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        root = Path(self.temporary.name)
        for name in ("ATest", "BTest", "SerialTest"):
            (root / (name + ".class")).write_bytes(b"")
        self.rev = "a" * 40
        self.plan = shards.make_plan(root, "SerialTest", "**/SerialTest.java", 2, self.rev)
        self.dependencies = {lane: {"result": "success"} for lane in gate.LANES}
        self.results = []
        for lane, index, classes in (("parallel", 1, ["ATest"]), ("parallel", 2, ["BTest"]),
                                     ("serial", 1, ["SerialTest"])):
            self.results.append(dict(schema=shards.RESULT_SCHEMA, revision=self.rev,
                                     planDigest=self.plan["digest"], lane=lane, shardIndex=index,
                                     assignedClasses=classes, observedClasses=classes,
                                     reportedCandidates=classes, unresolvedCandidates=[],
                                     candidateCounts={c: dict(executed=1, skipped=0) for c in classes},
                                     testCount=1, failureCount=0, errorCount=0, skippedCount=0,
                                     mavenExitCode=0, status="pass"))

    def accept(self):
        return gate.aggregate(self.plan, self.results, self.dependencies, self.rev)

    def test_local_aggregation_without_revision(self):
        from unittest.mock import patch

        with patch.dict("os.environ", {}, clear=True):
            self.rev = shards.revision()
            self.assertEqual(self.rev, "")
            self.plan["revision"] = self.rev
            self.plan["digest"] = shards.digest(self.plan)

            for result in self.results:
                result["revision"] = self.rev
                result["planDigest"] = self.plan["digest"]

            accepted = self.accept()
            self.assertEqual(accepted["status"], "pass")
            self.assertEqual(accepted["revision"], "")
            self.assertEqual(accepted["assignedCandidateCount"], 3)
            self.assertEqual(accepted["unresolvedCandidateCount"], 0)

    def test_local_aggregation_rejects_mixed_revisions(self):
        from unittest.mock import patch

        with patch.dict("os.environ", {}, clear=True):
            self.rev = shards.revision()
            self.plan["revision"] = self.rev
            self.plan["digest"] = shards.digest(self.plan)

            for result in self.results:
                result["revision"] = self.rev
                result["planDigest"] = self.plan["digest"]

            self.results[0]["revision"] = "b" * 40

            with self.assertRaisesRegex(ValueError, "evidence identity mismatch"):
                self.accept()

    def test_ci_requires_exact_revision(self):
        from unittest.mock import patch

        for value in ("", "invalid", "a" * 39, "g" * 40):
            with self.subTest(value=value):
                with patch.dict("os.environ", {
                    "GITHUB_ACTIONS": "true",
                    "GITHUB_SHA": value,
                }, clear=True):
                    with self.assertRaisesRegex(ValueError, "CI requires an exact revision"):
                        shards.revision()

    def test_ci_accepts_exact_revision(self):
        from unittest.mock import patch

        with patch.dict("os.environ", {
            "GITHUB_ACTIONS": "true",
            "GITHUB_SHA": self.rev,
        }, clear=True):
            self.assertEqual(shards.revision(), self.rev)
            self.assertEqual(self.accept()["status"], "pass")

    def test_complete_success_and_skips_preserved(self):
        self.assertEqual(self.accept()["status"], "pass")
        self.results[0]["skippedCount"] = 2
        self.results[0]["candidateCounts"]["ATest"]["skipped"] = 2
        self.assertEqual(self.accept()["skippedCount"], 2)

    def test_missing_prepare_and_every_failed_cancelled_or_skipped_lane(self):
        del self.dependencies["prepare"]
        with self.assertRaises(ValueError):
            self.accept()
        self.dependencies["prepare"] = {"result": "success"}
        for lane in gate.LANES:
            for status in ("failure", "cancelled", "skipped", ""):
                with self.subTest(lane=lane, status=status):
                    self.dependencies[lane] = {"result": status}
                    with self.assertRaises(ValueError):
                        self.accept()
            self.dependencies[lane] = {"result": "success"}

    def test_missing_shard_serial_or_all_artifacts(self):
        original = copy.deepcopy(self.results)
        for index in range(3):
            self.results = original[:index] + original[index+1:]
            with self.subTest(index=index), self.assertRaises(ValueError):
                self.accept()
        self.results = []
        with self.assertRaises(ValueError):
            self.accept()

    def test_duplicate_and_unexpected_shards(self):
        self.results.append(copy.deepcopy(self.results[0]))
        with self.assertRaises(ValueError):
            self.accept()
        self.results[-1]["shardIndex"] = 3
        with self.assertRaises(ValueError):
            self.accept()

    def test_failed_evidence_and_concealed_maven_failure(self):
        for change in (dict(status="fail"), dict(mavenExitCode=1), dict(failureCount=1),
                       dict(errorCount=1), dict(testCount=0), dict(skippedCount=-1)):
            original = copy.deepcopy(self.results)
            self.results[0].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.accept()
            self.results = original

    def test_revision_digest_assignment_and_observed_mismatch(self):
        for change in (dict(revision="b"*40), dict(planDigest="bad"),
                       dict(assignedClasses=[]), dict(assignedClasses=["ATest", "ATest"]),
                       dict(assignedClasses=["SerialTest"]), dict(observedClasses=["UnexpectedTest"]),
                       dict(unresolvedCandidates=["ATest"]), dict(reportedCandidates=[]),
                       dict(schema="unknown")):
            original = copy.deepcopy(self.results)
            self.results[0].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.accept()
            self.results = original
        self.rev = "c" * 40
        with self.assertRaises(ValueError):
            self.accept()

    def test_malformed_and_false_success(self):
        for evidence in (None, {}, {"schema": shards.RESULT_SCHEMA}):
            self.results[0] = evidence
            with self.assertRaises((ValueError, KeyError)):
                self.accept()

    def test_false_execution_claim_with_no_observed_classes(self):
        self.results[0]["observedClasses"] = []
        with self.assertRaises(ValueError):
            self.accept()
        # A fabricated skipped total cannot explain an executing candidate.
        self.results[0]["skippedCount"] = 1
        with self.assertRaises(ValueError):
            self.accept()

    def test_plan_union_duplicates_and_serial_overlap(self):
        for key, values in (("candidateInventory", ["ATest", "SerialTest"]),
                            ("parallelInventory", ["ATest", "ATest", "BTest"]),
                            ("serialInventory", ["ATest", "SerialTest"])):
            original = copy.deepcopy(self.plan)
            self.plan[key] = values
            self.plan["digest"] = shards.digest(self.plan)
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.accept()
            self.plan = original

    def test_missing_artifact_cli_fails_and_writes_summary(self):
        from unittest.mock import patch
        directory = Path(self.temporary.name)
        output, summary = directory / "result.json", directory / "summary.md"
        argv = ["aggregate", "--plan", str(directory / "missing.json"), "--results", str(directory),
                "--dependencies", json.dumps(self.dependencies), "--revision", self.rev,
                "--output", str(output), "--summary", str(summary)]
        with patch.object(sys, "argv", argv):
            self.assertEqual(gate.main(), 1)
        self.assertEqual(json.loads(output.read_text())["status"], "fail")
        self.assertIn("Acceptance: **fail**", summary.read_text())


if __name__ == "__main__":
    unittest.main()
