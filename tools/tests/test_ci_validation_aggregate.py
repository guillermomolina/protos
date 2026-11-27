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
import ci_protos_test_shards as protos
import ci_validation_aggregate as gate


def listing(refs):
    return json.dumps({"schema": protos.LISTING_SCHEMA,
                       "cases": [{"ref": ref, "display": "Case " + ref} for ref in refs]})


def protos_evidence(refs, count, jobs, rev):
    """A complete passing distributed run, partitioned as the Test Tool would."""
    plan = protos.make_plan(listing(refs), count, jobs, rev)
    results = [protos.shard_result(count, index, jobs, listing(refs), listing(refs[index - 1::count]),
                                   "[suite] done\n%s passed, 0 failed\n" % len(refs[index - 1::count]),
                                   0, rev)
               for index in range(1, count + 1)]
    return plan, results


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
        self.refs = ["case-%s" % n for n in range(1, 8)]
        self.protos_count = 4
        self.protos_plan, self.protos_results = protos_evidence(self.refs, 4, 2, self.rev)

    def accept(self):
        return gate.aggregate(self.plan, self.results, self.dependencies, self.rev,
                              self.protos_plan, self.protos_results, self.protos_count)

    def rebind_protos(self):
        self.protos_plan, self.protos_results = protos_evidence(self.refs, 4, 2, self.rev)

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
            self.rebind_protos()

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
            self.rebind_protos()

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
                "--protos-plan", str(directory / "missing-protos.json"),
                "--protos-results", str(directory), "--protos-shard-count", "4",
                "--output", str(output), "--summary", str(summary)]
        with patch.object(sys, "argv", argv):
            self.assertEqual(gate.main(), 1)
        self.assertEqual(json.loads(output.read_text())["status"], "fail")
        self.assertIn("Acceptance: **fail**", summary.read_text())


    # Protos Case evidence: coverage counts stay separate from Java.

    def reject(self, pattern=None):
        if pattern is None:
            with self.assertRaises((ValueError, KeyError, TypeError)):
                self.accept()
        else:
            with self.assertRaisesRegex(ValueError, pattern):
                self.accept()

    def test_protos_four_disjoint_partitions_accepted_separately(self):
        accepted = self.accept()
        self.assertEqual(accepted["assignedCandidateCount"], 3)
        self.assertEqual(accepted["protos"]["plannedCaseCount"], 7)
        self.assertEqual(accepted["protos"]["passedCaseCount"], 7)
        self.assertEqual([s["plannedCaseCount"] for s in accepted["protos"]["shards"]], [2, 2, 2, 1])
        self.assertEqual(accepted["dependencies"]["protos-plan"], "success")
        text = gate.summary(dict(accepted), self.dependencies)
        self.assertIn("Java candidates: 3 assigned", text)
        self.assertIn("Protos Cases: 7 planned across 4 shards (2 workers each); 7 passed; 0 failed.", text)
        self.assertIn("| protos-plan | success |", text)

    def test_protos_empty_partitions_accepted(self):
        self.refs = ["only-a", "only-b"]
        self.rebind_protos()
        accepted = self.accept()
        self.assertEqual([s["plannedCaseCount"] for s in accepted["protos"]["shards"]], [1, 1, 0, 0])
        self.assertEqual(self.protos_results[3]["cases"], [])
        self.assertEqual(self.protos_results[3]["status"], "pass")

    def test_protos_missing_repeated_or_out_of_range_indices(self):
        original = copy.deepcopy(self.protos_results)
        for index in range(4):
            self.protos_results = original[:index] + original[index + 1:]
            with self.subTest(missing=index + 1):
                self.reject("missing Protos shard result")
        self.protos_results = original + [copy.deepcopy(original[0])]
        self.reject("unexpected or duplicate Protos result")
        for bad in (0, 5, "1", None):
            self.protos_results = copy.deepcopy(original)
            self.protos_results[0]["shardIndex"] = bad
            with self.subTest(index=bad):
                self.reject("unexpected or duplicate Protos result")
        self.protos_results = []
        self.reject("missing Protos shard result")

    def test_protos_missing_unexpected_or_duplicated_case_refs(self):
        for label, cases, pattern in (
                ("missing", ["case-1"], "incomplete Protos Case coverage"),
                ("unexpected", ["case-1", "case-5", "case-99"], "unexpected CaseRef"),
                ("within", ["case-1", "case-1", "case-5"], "duplicate CaseRef within partition"),
                ("across", ["case-1", "case-5", "case-2"], "overlapping CaseRefs across partitions")):
            original = copy.deepcopy(self.protos_results)
            self.protos_results[0]["cases"] = cases
            self.protos_results[0]["passedCount"] = self.protos_results[0]["completedCount"] = len(cases)
            with self.subTest(label=label):
                self.reject(pattern)
            self.protos_results = original

    def test_protos_incorrect_ordering_or_assignment(self):
        for label, first, second in (
                ("reordered", ["case-5", "case-1"], None),
                ("swapped", ["case-2", "case-6"], ["case-1", "case-5"]),
                ("contiguous", ["case-1", "case-2"], ["case-5", "case-6"])):
            original = copy.deepcopy(self.protos_results)
            self.protos_results[0]["cases"] = first
            if second is not None:
                self.protos_results[1]["cases"] = second
            with self.subTest(label=label):
                self.reject("round-robin")
            self.protos_results = original

    def test_protos_revision_identity_and_count_mismatches(self):
        for key, value, pattern in (("revision", "b" * 40, "identity mismatch"),
                                    ("planDigest", "0" * 64, "identity mismatch"),
                                    ("shardCount", 3, "configuration mismatch"),
                                    ("jobs", 1, "configuration mismatch")):
            original = copy.deepcopy(self.protos_results)
            self.protos_results[2][key] = value
            with self.subTest(key=key):
                self.reject(pattern)
            self.protos_results = original
        self.protos_count = 3
        self.reject("Protos shard count mismatch")
        self.protos_count = 4
        stale = copy.deepcopy(self.protos_plan)
        stale["revision"] = "b" * 40
        stale["digest"] = shards.digest(stale)
        self.protos_plan = stale
        self.reject("Protos plan revision mismatch")

    def test_protos_plan_tampering_and_schema(self):
        original = copy.deepcopy(self.protos_plan)
        for change in (dict(schema="unknown"), dict(cases=self.refs[:-1]), dict(cases=[]),
                       dict(cases=self.refs + [self.refs[0]]), dict(jobs=0), dict(shardCount=0)):
            self.protos_plan = copy.deepcopy(original)
            self.protos_plan.update(change)
            self.protos_plan["digest"] = shards.digest(self.protos_plan)
            with self.subTest(change=change):
                self.reject()
        self.protos_plan = copy.deepcopy(original)
        self.protos_plan["cases"] = list(reversed(self.refs))
        with self.subTest(change="digest not recomputed"):
            self.reject("Protos plan digest mismatch")
        for evidence in (None, {}, {"schema": protos.RESULT_SCHEMA}, dict(original)):
            self.protos_plan = copy.deepcopy(original)
            self.protos_results = copy.deepcopy(self.protos_results)
            self.protos_results[0] = evidence
            with self.subTest(evidence=evidence):
                self.reject()

    def test_protos_failed_aborted_and_concealed_failures(self):
        for change in (dict(exitStatus=2), dict(outcome="infrastructure-aborted"),
                       dict(failedCount=1), dict(passedCount=1), dict(completedCount=3),
                       dict(status="fail"), dict(reasons=["nonzero process exit status 2"]),
                       dict(exitStatus=-1), dict(passedCount="2"), dict(shardCount=4.0), dict(jobs=2.0)):
            original = copy.deepcopy(self.protos_results)
            self.protos_results[0].update(change)
            with self.subTest(change=change):
                self.reject()
            self.protos_results = original

    def test_protos_plan_dependency_required(self):
        del self.dependencies["protos-plan"]
        self.reject("missing or unexpected workflow dependencies")
        for status in ("failure", "cancelled", "skipped"):
            self.dependencies["protos-plan"] = {"result": status}
            with self.subTest(status=status):
                self.reject("failed, cancelled, skipped or missing dependency result")

    def test_protos_cli_reads_downloaded_evidence(self):
        from unittest.mock import patch
        directory = Path(self.temporary.name)
        shards.write_json(directory / "plan.json", self.plan)
        for result in self.results:
            shards.write_json(directory / "java" / ("%s-%s" % (result["lane"], result["shardIndex"]))
                              / "result.json", result)
        shards.write_json(directory / "protos-plan.json", self.protos_plan)
        (directory / "listing.json").write_text(listing(self.refs), encoding="utf-8")
        for result in self.protos_results:
            artifact = directory / "protos" / ("protos-evidence-suite-%s" % result["shardIndex"])
            evidence = artifact / "protos-test-evidence"
            shards.write_json(evidence / "protos-result.json", result)
            (evidence / "complete-listing.json").write_text(listing(self.refs), encoding="utf-8")
            (evidence / "shard-listing.json").write_text(listing(result["cases"]), encoding="utf-8")
            logs = artifact / "ci-logs"
            logs.mkdir()
            (logs / "protos-suite.log").write_text("%s passed, 0 failed\n" % len(result["cases"]), encoding="utf-8")
            (logs / "protos-suite.exit").write_text("0\n", encoding="utf-8")
        output, summary = directory / "aggregate.json", directory / "summary.md"
        argv = ["aggregate", "--plan", str(directory / "plan.json"), "--results", str(directory / "java"),
                "--dependencies", json.dumps(self.dependencies), "--revision", self.rev,
                "--protos-plan", str(directory / "protos-plan.json"),
                "--protos-results", str(directory / "protos"), "--protos-shard-count", "4",
                "--output", str(output), "--summary", str(summary)]
        with patch.object(sys, "argv", argv):
            self.assertEqual(gate.main(), 0)
        self.assertEqual(json.loads(output.read_text())["protos"]["passedCaseCount"], 7)
        evidence = directory / "protos" / "protos-evidence-suite-3" / "protos-test-evidence"
        raw_paths = [directory / "listing.json", evidence / "complete-listing.json",
                     evidence / "shard-listing.json", evidence.parent / "ci-logs" / "protos-suite.log",
                     evidence.parent / "ci-logs" / "protos-suite.exit"]
        for path in raw_paths:
            original = path.read_text(encoding="utf-8")
            with self.subTest(missing=path.name):
                path.unlink()
                with patch.object(sys, "argv", argv):
                    self.assertEqual(gate.main(), 1)
            path.write_text(original, encoding="utf-8")
            malformed = "1" if path.suffix == ".exit" else "{}"
            with self.subTest(malformed=path.name):
                path.write_text(malformed, encoding="utf-8")
                with patch.object(sys, "argv", argv):
                    self.assertEqual(gate.main(), 1)
            path.write_text(original, encoding="utf-8")
        result_path = evidence / "protos-result.json"
        original = result_path.read_text(encoding="utf-8")
        result_path.write_text(original.replace('"exitStatus": 0', '"exitStatus": 1, "exitStatus": 0'),
                               encoding="utf-8")
        with patch.object(sys, "argv", argv):
            self.assertEqual(gate.main(), 1)
        self.assertIn("duplicate JSON", json.loads(output.read_text())["reason"])
        result_path.write_text(original, encoding="utf-8")
        (evidence / "protos-result.json").unlink()
        with patch.object(sys, "argv", argv):
            self.assertEqual(gate.main(), 1)
        self.assertEqual(json.loads(output.read_text())["reason"], "missing Protos shard result")

    def scoped_fixture(self, libraries):
        profile = "FULL" if libraries == "all" else "CORE" if libraries == "none" else "SELECTIVE"
        requirements = dict(protos.impact.Selection(profile, "ALL", "fixture", profile != "FULL", libraries).to_dict(),
                            revision=self.rev, shardCount=4)
        requirements["scopes"] = protos.impact.required_scopes(requirements)
        plans, results = [], []
        for scope in requirements["scopes"]:
            refs = [scope + ":one", scope + ":two"]
            plans.append(protos.make_plan(listing(refs), 4, 2, self.rev, scope))
            for index in range(1, 5):
                retained = refs[index - 1::4]
                results.append(protos.shard_result(4, index, 2, listing(refs), listing(retained),
                               "%s passed, 0 failed\n" % len(retained), 0, self.rev, scope))
        return requirements, plans, results

    def accept_scoped(self, requirements, plans, results):
        return gate.scoped_aggregate(self.plan, self.results, self.dependencies, self.rev,
                                     requirements, plans, results, 4)

    def test_required_repository_scope_profiles(self):
        for libraries in ("none", ["toml"], ["http", "toml"], "all"):
            requirements, plans, results = self.scoped_fixture(libraries)
            accepted = self.accept_scoped(requirements, plans, results)
            self.assertEqual(accepted["exhaustive"], libraries == "all")
            self.assertEqual(accepted["requiredScopes"], requirements["scopes"])
            if libraries != "all":
                self.assertEqual("NOT SELECTED", accepted["unselectedLibraries"])

    def test_missing_failed_and_incorrect_scoped_evidence(self):
        requirements, plans, results = self.scoped_fixture(["toml"])
        for bad_plans, bad_results in ((plans[:1], results), (plans, results[:-1]),
                                      (plans + plans[:1], results), (plans, results + results[:1])):
            with self.assertRaises((ValueError, KeyError)):
                self.accept_scoped(requirements, bad_plans, bad_results)
        for patch in (dict(scope="library:uri"), dict(revision="b" * 40), dict(exitStatus=1),
                      dict(planDigest="0" * 64), dict(status="fail")):
            altered = copy.deepcopy(results)
            altered[-1].update(patch)
            with self.subTest(patch=patch), self.assertRaises(ValueError):
                self.accept_scoped(requirements, plans, altered)
        for lane in ("compilerability", "protos-foreign", "native-image", "java-serial"):
            original = self.dependencies[lane]
            self.dependencies[lane] = dict(result="skipped")
            with self.assertRaises(ValueError):
                self.accept_scoped(requirements, plans, results)
            self.dependencies[lane] = original

    def test_requirements_cannot_change_revision_or_claim_exhaustive(self):
        requirements, plans, results = self.scoped_fixture("none")
        for patch in (dict(revision="b" * 40), dict(validation_impact="FULL"),
                      dict(scopes=["core", "libraries"]), dict(shardCount=3)):
            bad = dict(requirements, **patch)
            with self.assertRaises(ValueError):
                self.accept_scoped(bad, plans, results)


if __name__ == "__main__":
    unittest.main()
