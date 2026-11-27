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

"""Fail-closed distributed CI evidence acceptance; never executes tests."""

import argparse
import json
from pathlib import Path
import re
import sys

from ci_java_test_shards import check_plan, require, revision, write_json, RESULT_SCHEMA
import ci_protos_test_shards as protos_shards

LANES = ("prepare", "compilerability", "java-parallel", "java-serial",
         "protos-plan", "protos-suite", "protos-foreign", "native-image")


def check_dependencies(dependencies):
    require(isinstance(dependencies, dict), "malformed dependency evidence")
    require(set(dependencies) == set(LANES), "missing or unexpected workflow dependencies")
    require(all(isinstance(dependencies[name], dict) and dependencies[name].get("result") == "success" for name in LANES),
            "failed, cancelled, skipped or missing dependency result")


def aggregate(plan, results, dependencies, rev, protos_plan, protos_results, protos_shard_count):
    """Java and Protos evidence are accepted independently and reported separately."""
    check_dependencies(dependencies)
    java = java_coverage(plan, results, rev)
    protos = protos_shards.check_results(protos_plan, protos_results, rev, protos_shard_count)
    return dict(java, dependencies={n: dependencies[n]["result"] for n in LANES}, protos=protos)


def scoped_aggregate(plan, results, dependencies, rev, requirements, protos_plans,
                     protos_results, count):
    """Accept exactly the scopes selected for this immutable candidate."""
    check_dependencies(dependencies)
    protos_shards.impact.validate_requirements(requirements)
    require(requirements.get("revision") == rev, "validation requirements revision mismatch")
    require(type(requirements.get("shardCount")) is int and requirements["shardCount"] == count,
            "requirements shard count mismatch")
    scopes = protos_shards.impact.required_scopes(requirements)
    require(requirements.get("scopes") == scopes, "contradictory required scopes")
    require(isinstance(protos_plans, list), "malformed scoped plans")
    by_scope = {}
    for item in protos_plans:
        scope = protos_shards.repository_scope(item["scope"])
        require(scope in scopes and scope not in by_scope, "unexpected or duplicate Protos scope")
        by_scope[scope] = item
    require(set(by_scope) == set(scopes), "missing required library or core plan")
    for item in protos_results:
        require(item.get("scope") in scopes, "unexpected Protos result scope")
    assigned_refs = set()
    for scope in scopes:
        refs = set(by_scope[scope]["cases"])
        require(not assigned_refs.intersection(refs), "overlapping CaseRefs across repository scopes")
        assigned_refs.update(refs)
    coverage = [protos_shards.check_results(by_scope[scope],
                [r for r in protos_results if r["scope"] == scope], rev, count) for scope in scopes]
    result = java_coverage(plan, results, rev)
    result.update(schema="protos.ci.validation-result/v2",
                  dependencies={n: dependencies[n]["result"] for n in LANES},
                  validationProfile=requirements["validation_impact"], requiredScopes=scopes,
                  exhaustive=requirements["validation_impact"] == "FULL",
                  unselectedLibraries="NOT SELECTED" if requirements["library_validation"] != "all" else "none",
                  protosScopes=coverage)
    return result


def java_coverage(plan, results, rev):
    check_plan(plan)
    require(plan["revision"] == rev, "revision mismatch")
    expected = {("parallel", s["index"]): s["classes"] for s in plan["shards"]}
    expected[("serial", 1)] = plan["serialInventory"]
    seen, assigned_union, observed_union = set(), set(), set()
    totals = dict(testCount=0, failureCount=0, errorCount=0, skippedCount=0)
    for result in results:
        require(isinstance(result, dict) and result.get("schema") == RESULT_SCHEMA, "malformed evidence")
        require(isinstance(result["lane"], str) and type(result["shardIndex"]) is int,
                "malformed lane identity")
        key = (result["lane"], result["shardIndex"])
        require(key in expected and key not in seen, "unexpected or duplicate Java result")
        seen.add(key)
        require(result["revision"] == rev and result["planDigest"] == plan["digest"], "evidence identity mismatch")
        require(result["assignedClasses"] == expected[key], "assignment mismatch")
        assigned = set(expected[key])
        require(not assigned_union.intersection(assigned), "overlapping assignments")
        assigned_union.update(assigned)
        require(result["status"] == "pass" and type(result["mavenExitCode"]) is int
                and result["mavenExitCode"] == 0, "failed Java invocation")
        require(result["unresolvedCandidates"] == [], "unresolved execution coverage")
        reported = result["reportedCandidates"]
        require(reported == sorted(assigned), "incomplete execution evidence")
        observed = result["observedClasses"]
        require(isinstance(observed, list) and observed == sorted(set(observed)), "invalid observed classes")
        owners = set()
        for cls in observed:
            require(isinstance(cls, str) and cls.split("$", 1)[0] in assigned, "unexpected observed class")
            owners.add(cls.split("$", 1)[0])
        candidate_counts = result["candidateCounts"]
        require(isinstance(candidate_counts, dict) and set(candidate_counts) == assigned,
                "missing candidate execution evidence")
        for candidate, counts in candidate_counts.items():
            require(isinstance(counts, dict) and set(counts) == {"executed", "skipped"},
                    "malformed candidate evidence")
            require(all(type(v) is int and v >= 0 for v in counts.values()), "invalid candidate counts")
            require(counts["executed"] + counts["skipped"] > 0, "unreconciled candidate")
            require((counts["executed"] > 0) == (candidate in owners), "false observed execution claim")
        require(sum(c["executed"] for c in candidate_counts.values()) == result["testCount"]
                and sum(c["skipped"] for c in candidate_counts.values()) == result["skippedCount"],
                "candidate totals mismatch")
        observed_union.update(owners)
        for name in totals:
            value = result[name]
            require(type(value) is int and value >= 0, "invalid execution counts")
            totals[name] += value
        require(result["testCount"] > 0 and result["failureCount"] == 0 and result["errorCount"] == 0,
                "failed or empty Java execution")
        require(len(observed) <= result["testCount"], "inconsistent execution count")
        # Missing execution is explained only by actual Surefire skipped testcases.
        require(len(assigned - owners) <= result["skippedCount"], "false execution coverage claim")
    require(seen == set(expected), "missing Java execution result")
    require(assigned_union == set(plan["candidateInventory"]), "incomplete candidate coverage")
    return dict(schema="protos.ci.validation-result/v1", revision=rev, planDigest=plan["digest"],
                status="pass", assignedCandidateCount=len(assigned_union), observedCandidateCount=len(observed_union),
                unresolvedCandidateCount=0, **totals)


def summary(result, dependencies):
    lines = ["## Distributed CI", "", "| Lane | Status |", "| --- | --- |"]
    for name in LANES:
        dependency = dependencies.get(name)
        status = dependency.get("result", "missing") if isinstance(dependency, dict) else "missing"
        lines.append("| %s | %s |" % (name, status))
    lines += ["", "Acceptance: **%s**" % result["status"]]
    if result["status"] == "pass":
        lines += ["", "Java candidates: %s assigned; %s observed executing; 0 unresolved." %
                  (result["assignedCandidateCount"], result["observedCandidateCount"]),
                  "Java executed invocations: %s; skipped: %s; failures: %s; errors: %s." %
                  tuple(result[k] for k in ("testCount", "skippedCount", "failureCount", "errorCount"))]
        if "protosScopes" in result:
            lines += ["", "Validation profile: " + result["validationProfile"],
                      "Exhaustive validation: " + ("PASS" if result["exhaustive"] else "NOT SELECTED"),
                      "Libraries outside selected scope: " + result["unselectedLibraries"]]
            for scope in result["protosScopes"]:
                lines.append("Scope %s: %s planned; %s passed across %s shards." %
                             (scope["scope"], scope["plannedCaseCount"], scope["passedCaseCount"], scope["shardCount"]))
            lines.append("Evidence binds assigned CaseRefs and terminal completion quantities.")
            return "\n".join(lines) + "\n"
        protos = result["protos"]
        lines += ["", "Protos Cases: %s planned across %s shards (%s workers each); %s passed; %s failed." %
                  (protos["plannedCaseCount"], protos["shardCount"], protos["jobs"],
                   protos["passedCaseCount"], protos["failedCaseCount"]),
                  "Protos evidence covers planned CaseRef identity and terminal completion counts.",
                  "", "| Protos shard | Planned | Passed |", "| --- | --- | --- |"]
        lines += ["| %s | %s | %s |" % (s["index"], s["plannedCaseCount"], s["passedCaseCount"])
                  for s in protos["shards"]]
    else:
        lines += ["", result["reason"]]
    return "\n".join(lines) + "\n"


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=protos_shards.unique_object)


def read_protos_evidence(plan_path, results_path, rev):
    """Reconcile derived evidence with the retained public output, fail closed.

    upload-artifact preserves the common target/ root for shard evidence, so
    each protos-test-evidence directory has its matching ci-logs sibling.
    """
    plan = read_json(plan_path)
    complete_text = (plan_path.parent / "listing.json").read_text(encoding="utf-8")
    expected_plan = protos_shards.make_plan(complete_text, plan["shardCount"], plan["jobs"], rev, plan["scope"])
    require(plan == expected_plan, "Protos plan disagrees with authoritative listing")
    results = []
    for path in sorted(results_path.rglob("protos-result.json")):
        result = read_json(path)
        require(isinstance(result, dict), "malformed Protos result artifact")
        if result.get("scope") != plan["scope"]:
            continue
        logs = path.parent.parent / "ci-logs"
        status = (logs / "protos-suite.exit").read_text(encoding="utf-8").strip()
        require(bool(re.fullmatch(r"[0-9]+", status)), "invalid recorded exit status")
        expected = protos_shards.shard_result(
            result["shardCount"], result["shardIndex"], result["jobs"],
            (path.parent / "complete-listing.json").read_text(encoding="utf-8"),
            (path.parent / "shard-listing.json").read_text(encoding="utf-8"),
            (logs / "protos-suite.log").read_text(encoding="utf-8"), int(status), rev, result["scope"])
        require(result == expected, "Protos result disagrees with authoritative output")
        results.append(result)
    return plan, results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", required=True, type=Path)
    parser.add_argument("--results", required=True, type=Path)
    parser.add_argument("--dependencies", required=True)
    parser.add_argument("--protos-plan", required=True, type=Path)
    parser.add_argument("--protos-results", required=True, type=Path)
    parser.add_argument("--protos-shard-count", required=True, type=int)
    parser.add_argument("--revision")
    parser.add_argument("--requirements", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--summary", required=True, type=Path)
    args = parser.parse_args()
    dependencies = {}
    try:
        dependencies = json.loads(args.dependencies, object_pairs_hook=protos_shards.unique_object)
        require(isinstance(dependencies, dict), "invalid dependency evidence")
        plan = read_json(args.plan)
        files = sorted(args.results.rglob("result.json"))
        results = [read_json(file) for file in files]
        rev = revision(args.revision)
        if args.requirements:
            requirements = read_json(args.requirements)
            protos_plans, protos_results = [], []
            for path in sorted(args.protos_plan.rglob("plan.json")):
                scoped_plan, scoped_results = read_protos_evidence(path, args.protos_results, rev)
                protos_plans.append(scoped_plan)
                protos_results.extend(scoped_results)
            # Reject extraneous results rather than silently ignoring them.
            expected_scopes = protos_shards.impact.required_scopes(requirements)
            for path in args.protos_results.rglob("protos-result.json"):
                artifact = read_json(path)
                require(isinstance(artifact, dict) and artifact.get("scope") in expected_scopes,
                        "unexpected scope artifact")
            result = scoped_aggregate(plan, results, dependencies, rev, requirements,
                                      protos_plans, protos_results, args.protos_shard_count)
        else:
            protos_plan, protos_results = read_protos_evidence(args.protos_plan, args.protos_results, rev)
            result = aggregate(plan, results, dependencies, rev,
                               protos_plan, protos_results, args.protos_shard_count)
    except (ValueError, TypeError, KeyError, OSError) as error:
        result = dict(schema="protos.ci.validation-result/v1", status="fail", reason=str(error))
    write_json(args.output, result)
    args.summary.parent.mkdir(parents=True, exist_ok=True)
    with args.summary.open("a", encoding="utf-8") as stream:
        stream.write(summary(result, dependencies if isinstance(dependencies, dict) else {}))
    return 0 if result["status"] == "pass" else 1


if __name__ == "__main__":
    sys.exit(main())
