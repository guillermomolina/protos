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
import sys

from ci_java_test_shards import check_plan, require, revision, write_json, RESULT_SCHEMA

LANES = ("prepare", "compilerability", "java-parallel", "java-serial",
         "protos-suite", "protos-foreign", "native-image")


def aggregate(plan, results, dependencies, rev):
    check_plan(plan)
    require(plan["revision"] == rev, "revision mismatch")
    require(isinstance(dependencies, dict), "malformed dependency evidence")
    require(set(dependencies) == set(LANES), "missing or unexpected workflow dependencies")
    require(all(isinstance(dependencies[name], dict) and dependencies[name].get("result") == "success" for name in LANES),
            "failed, cancelled, skipped or missing dependency result")
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
                status="pass", dependencies={n: dependencies[n]["result"] for n in LANES},
                assignedCandidateCount=len(assigned_union), observedCandidateCount=len(observed_union),
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
                  "Executed invocations: %s; skipped: %s; failures: %s; errors: %s." %
                  tuple(result[k] for k in ("testCount", "skippedCount", "failureCount", "errorCount"))]
    else:
        lines += ["", result["reason"]]
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", required=True, type=Path)
    parser.add_argument("--results", required=True, type=Path)
    parser.add_argument("--dependencies", required=True)
    parser.add_argument("--revision")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--summary", required=True, type=Path)
    args = parser.parse_args()
    dependencies = {}
    try:
        dependencies = json.loads(args.dependencies)
        require(isinstance(dependencies, dict), "invalid dependency evidence")
        plan = json.loads(args.plan.read_text(encoding="utf-8"))
        files = sorted(args.results.rglob("result.json"))
        results = [json.loads(file.read_text(encoding="utf-8")) for file in files]
        result = aggregate(plan, results, dependencies, revision(args.revision))
    except (ValueError, TypeError, KeyError, OSError) as error:
        result = dict(schema="protos.ci.validation-result/v1", status="fail", reason=str(error))
    write_json(args.output, result)
    args.summary.parent.mkdir(parents=True, exist_ok=True)
    with args.summary.open("a", encoding="utf-8") as stream:
        stream.write(summary(result, dependencies if isinstance(dependencies, dict) else {}))
    return 0 if result["status"] == "pass" else 1


if __name__ == "__main__":
    sys.exit(main())
