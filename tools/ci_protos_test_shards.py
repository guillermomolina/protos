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

"""Revision-bound CI evidence over the public Protos Test Tool output.

This never discovers, selects, partitions or executes Cases. Membership comes only
from the Test Tool's own `--list-cases` documents (D185 `protos.test.cases/v1`),
optionally restricted by its I178 `--shard-count/--shard-run` selection (D264-B').
CaseRefs are opaque: they are compared, never decoded or hashed into assignments.

Execution evidence is the real process exit status plus exactly one terminal
summary line. Per D264-B' this establishes planned identity coverage and terminal
completion quantity; it does not claim independently observed per-Case successes.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

from ci_java_test_shards import digest, require, revision, write_json

LISTING_SCHEMA = "protos.test.cases/v1"
PLAN_SCHEMA = "protos.ci.protos-test-plan/v1"
RESULT_SCHEMA = "protos.ci.protos-test-result/v1"
COUNT = r"(0|[1-9][0-9]*)"
# Progress.finishInvocation terminal summaries, one per outcome status.
COMPLETED = re.compile(r"^%s passed, %s failed$" % (COUNT, COUNT))
ABORTED = re.compile(r"^%s completed, %s failed - infrastructure-aborted$" % (COUNT, COUNT))


def unique_object(pairs):
    keys = [key for key, _ in pairs]
    require(len(keys) == len(set(keys)), "duplicate JSON object key")
    return dict(pairs)


def listing_entries(text):
    """Strictly parse one complete listing document; stray output is rejected."""
    document = json.loads(text, object_pairs_hook=unique_object)
    require(isinstance(document, dict) and set(document) == {"schema", "cases"}
            and document["schema"] == LISTING_SCHEMA, "invalid Case listing schema")
    cases = document["cases"]
    require(isinstance(cases, list), "invalid Case listing schema")
    for entry in cases:
        require(isinstance(entry, dict) and set(entry) == {"ref", "display"}
                and all(isinstance(entry[key], str) and entry[key] for key in ("ref", "display")),
                "malformed Case listing entry")
    refs = [entry["ref"] for entry in cases]
    require(len(refs) == len(set(refs)), "duplicate CaseRef in listing")
    return cases


def refs_digest(refs):
    return hashlib.sha256(json.dumps(refs, separators=(",", ":")).encode()).hexdigest()


def positive(value, label):
    require(type(value) is int and value > 0, "invalid " + label)
    return value


def case_refs(values, label):
    require(isinstance(values, list) and all(isinstance(v, str) and v for v in values),
            "invalid %s CaseRefs" % label)
    require(len(values) == len(set(values)), "duplicate CaseRef within %s" % label)
    return values


def make_plan(listing_text, count, jobs, rev=""):
    refs = [entry["ref"] for entry in listing_entries(listing_text)]
    # As for the Java inventory, a complete corpus without Cases is a discovery failure.
    require(bool(refs), "empty complete Case listing")
    plan = dict(schema=PLAN_SCHEMA, revision=revision(rev), shardCount=positive(count, "shard count"),
                jobs=positive(jobs, "job count"), cases=refs, planDigest=refs_digest(refs))
    plan["digest"] = digest(plan)
    return plan


def check_plan(plan):
    require(isinstance(plan, dict) and plan.get("schema") == PLAN_SCHEMA, "invalid Protos plan schema")
    require(plan.get("digest") == digest(plan), "Protos plan digest mismatch")
    positive(plan["shardCount"], "Protos shard count")
    positive(plan["jobs"], "Protos job count")
    require(bool(case_refs(plan["cases"], "complete plan")), "empty complete Protos plan")
    require(plan["planDigest"] == refs_digest(plan["cases"]), "Protos plan identity mismatch")
    return plan


def terminal_summary(log_text):
    matches = []
    for line in log_text.splitlines():
        completed, aborted = COMPLETED.match(line), ABORTED.match(line)
        if completed:
            passed, failed = int(completed.group(1)), int(completed.group(2))
            matches.append(dict(outcome="completed", completedCount=passed + failed,
                                passedCount=passed, failedCount=failed))
        elif aborted:
            done, failed = int(aborted.group(1)), int(aborted.group(2))
            require(failed <= done, "inconsistent terminal summary")
            matches.append(dict(outcome="infrastructure-aborted", completedCount=done,
                                passedCount=done - failed, failedCount=failed))
    require(len(matches) == 1, "missing or ambiguous terminal summary")
    return matches[0]


def shard_result(count, index, jobs, complete_text, shard_text, log_text, exit_status, rev=""):
    positive(count, "shard count")
    require(type(index) is int and 1 <= index <= count, "invalid shard index")
    positive(jobs, "job count")
    require(type(exit_status) is int and exit_status >= 0, "invalid process exit status")
    complete = [entry["ref"] for entry in listing_entries(complete_text)]
    require(bool(complete), "empty complete Case listing")
    # An empty shard listing is valid: the corpus may have fewer Cases than shards.
    cases = [entry["ref"] for entry in listing_entries(shard_text)]
    summary = terminal_summary(log_text)
    reasons = []
    if exit_status != 0:
        reasons.append("nonzero process exit status %s" % exit_status)
    if summary["outcome"] != "completed":
        reasons.append("infrastructure-aborted invocation")
    if summary["failedCount"]:
        reasons.append("%s failed Cases" % summary["failedCount"])
    if summary["passedCount"] != len(cases):
        reasons.append("%s passed Cases for %s listed CaseRefs" % (summary["passedCount"], len(cases)))
    result = dict(schema=RESULT_SCHEMA, revision=revision(rev), shardCount=count, shardIndex=index,
                  jobs=jobs, planDigest=refs_digest(complete), cases=cases, exitStatus=exit_status,
                  status="fail" if reasons else "pass", reasons=reasons, **summary)
    return result


def check_results(plan, results, rev, expected_count):
    """Accept only one passing, plan-conforming result per expected shard index."""
    check_plan(plan)
    require(plan["revision"] == rev, "Protos plan revision mismatch")
    require(type(expected_count) is int and plan["shardCount"] == expected_count,
            "Protos shard count mismatch")
    count, full = plan["shardCount"], plan["cases"]
    seen = {}
    for result in results:
        require(isinstance(result, dict) and result.get("schema") == RESULT_SCHEMA,
                "malformed Protos evidence")
        index = result["shardIndex"]
        require(type(index) is int and 1 <= index <= count and index not in seen,
                "unexpected or duplicate Protos result")
        seen[index] = result
        require(result["revision"] == rev and result["planDigest"] == plan["planDigest"],
                "Protos evidence identity mismatch")
        require(type(result["shardCount"]) is int and type(result["jobs"]) is int
                and result["shardCount"] == count and result["jobs"] == plan["jobs"],
                "Protos invocation configuration mismatch")
        case_refs(result["cases"], "partition")
    require(set(seen) == set(range(1, count + 1)), "missing Protos shard result")
    union = [ref for index in sorted(seen) for ref in seen[index]["cases"]]
    require(len(union) == len(set(union)), "overlapping CaseRefs across partitions")
    require(set(union) <= set(full), "unexpected CaseRef outside the complete plan")
    require(set(union) == set(full), "incomplete Protos Case coverage")
    shards = []
    for index in range(1, count + 1):
        result = seen[index]
        # Validation only: the Test Tool alone assigned these Cases.
        require(result["cases"] == full[index - 1::count],
                "partition violates the ordered global round-robin contract")
        planned = len(result["cases"])
        for key in ("exitStatus", "completedCount", "passedCount", "failedCount"):
            require(type(result[key]) is int and result[key] >= 0, "invalid Protos execution counts")
        require(result["exitStatus"] == 0, "failed Protos process exit status")
        require(result["outcome"] == "completed", "infrastructure-aborted Protos invocation")
        require(result["failedCount"] == 0, "failed Protos Cases")
        require(result["passedCount"] == planned and result["completedCount"] == planned,
                "Protos terminal count mismatch")
        require(result["status"] == "pass" and result["reasons"] == [], "failed Protos evidence")
        shards.append(dict(index=index, plannedCaseCount=planned, passedCaseCount=planned))
    return dict(planDigest=plan["planDigest"], shardCount=count, jobs=plan["jobs"],
                plannedCaseCount=len(full), passedCaseCount=len(full), failedCaseCount=0,
                shards=shards)


def read(path):
    return Path(path).read_text(encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("plan", "report"))
    parser.add_argument("--listing", help="complete unsharded --list-cases document")
    parser.add_argument("--shard-listing", help="I178 --list-cases document for this shard")
    parser.add_argument("--log", help="merged output of make test-protos-suite")
    parser.add_argument("--exit-status-file", help="recorded make test-protos-suite exit status")
    parser.add_argument("--count", type=int, required=True)
    parser.add_argument("--index", type=int, default=1)
    parser.add_argument("--jobs", type=int, required=True)
    parser.add_argument("--revision")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    try:
        if args.command == "plan":
            write_json(args.output, make_plan(read(args.listing), args.count, args.jobs, args.revision))
            return 0
        status_text = read(args.exit_status_file).strip()
        require(bool(re.fullmatch(r"[0-9]+", status_text)), "invalid recorded exit status")
        result = shard_result(args.count, args.index, args.jobs, read(args.listing),
                              read(args.shard_listing), read(args.log), int(status_text), args.revision)
        write_json(args.output, result)
        for reason in result["reasons"]:
            print("Protos shard evidence: " + reason, file=sys.stderr)
        return 0 if result["status"] == "pass" else 1
    except (ValueError, KeyError, TypeError, OSError) as error:
        print("Protos partition validation failed: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
