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

"""Deterministic Surefire selection plans and current-invocation XML evidence.

This is not JUnit discovery. Candidates lacking testcase evidence remain unresolved;
no filename-based helper exemptions may turn selection coverage into execution coverage.
"""

import argparse
import fnmatch
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

PLAN_SCHEMA = "protos.ci.java-test-plan/v1"
RESULT_SCHEMA = "protos.ci.java-test-result/v1"
IDENTITY = re.compile(r"[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def revision(value=None):
    value = value or os.environ.get("GITHUB_SHA", "")
    if os.environ.get("GITHUB_ACTIONS") == "true":
        require(bool(re.fullmatch(r"[0-9a-fA-F]{40}", value)), "CI requires an exact revision")
    return value


def inventory(root):
    root = Path(root)
    require(root.is_dir(), "compiled test-class root is missing")
    result = []
    for file in root.rglob("*.class"):
        relative = file.relative_to(root)
        name = file.stem
        if "$" in name:
            continue
        if not any(fnmatch.fnmatchcase(name, pattern) for pattern in
                   ("Test*", "*Test", "*Tests", "*TestCase")):
            continue
        parts = relative.with_suffix("").parts
        require(all(re.fullmatch(r"[A-Za-z_][\w]*", part) for part in parts),
                "malformed compiled class path: " + str(relative))
        identity = ".".join(parts)
        require(bool(IDENTITY.fullmatch(identity)), "malformed class identity: " + identity)
        result.append(identity)
    require(len(result) == len(set(result)), "duplicate compiled class identity")
    require(bool(result), "empty compiled candidate inventory")
    return sorted(result)


def excluded(identity, patterns):
    path = identity.replace(".", "/") + ".java"
    return any(fnmatch.fnmatchcase(path, pattern) or
               (pattern.startswith("**/") and fnmatch.fnmatchcase(path, pattern[3:]))
               for pattern in patterns)


def digest(plan):
    payload = {key: value for key, value in plan.items() if key != "digest"}
    return hashlib.sha256(json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def make_plan(root, serial, excludes, count, rev=""):
    require(type(count) is int and count > 0, "shard count must be positive")
    candidates = inventory(root)
    names = serial.split(",") if serial else []
    patterns = excludes.split(",") if excludes else []
    require(len(names) == len(set(names)), "duplicate serial class")
    resolved = []
    for name in names:
        matches = [item for item in candidates if item == name or item.rsplit(".", 1)[-1] == name]
        require(len(matches) == 1, "missing or ambiguous serial class: " + name)
        resolved.extend(matches)
    require(len(resolved) == len(set(resolved)), "duplicate serial identity")
    require(set(resolved) == {item for item in candidates if excluded(item, patterns)},
            "serial classification and parallel exclusions disagree")
    parallel = [item for item in candidates if item not in resolved]
    shards = [{"index": index, "classes": parallel[index - 1::count]}
              for index in range(1, count + 1)]
    require(all(shard["classes"] for shard in shards), "empty shard")
    plan = dict(schema=PLAN_SCHEMA, revision=revision(rev), shardCount=count,
                candidateInventory=candidates, parallelInventory=parallel,
                serialInventory=sorted(resolved), shards=shards)
    plan["digest"] = digest(plan)
    return plan


def check_plan(plan):
    require(isinstance(plan, dict) and plan.get("schema") == PLAN_SCHEMA, "invalid plan schema")
    require(plan.get("digest") == digest(plan), "plan digest mismatch")
    count = plan["shardCount"]
    require(type(count) is int and count > 0, "invalid shard count")
    for key in ("candidateInventory", "parallelInventory", "serialInventory"):
        values = plan[key]
        require(isinstance(values, list) and all(isinstance(v, str) and IDENTITY.fullmatch(v)
                                               and "$" not in v for v in values), "invalid identities")
        require(values == sorted(set(values)), "unordered or duplicate inventory")
    serial, parallel = plan["serialInventory"], plan["parallelInventory"]
    require(not set(serial).intersection(parallel), "serial/parallel overlap")
    require(sorted(serial + parallel) == plan["candidateInventory"], "incomplete selection")
    require(plan["shards"] == [{"index": i, "classes": parallel[i-1::count]}
                               for i in range(1, count+1)], "invalid partition assignment")
    require(all(s["classes"] for s in plan["shards"]), "empty shard")
    return plan


def selection(plan, index):
    require(type(index) is int and 1 <= index <= plan["shardCount"], "invalid shard index")
    return plan["shards"][index-1]["classes"]


def includes(classes):
    return "".join(item.replace(".", "/") + ".class\n" for item in classes)


def save_plan(plan, directory):
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    write_json(directory / "plan.json", plan)
    for shard in plan["shards"]:
        (directory / ("shard-%s.includes" % shard["index"])).write_text(includes(shard["classes"]), encoding="utf-8")


def validate_worker(plan, root, serial, excludes, count, index, rev, directory):
    check_plan(plan)
    selection(plan, index)
    require(plan == make_plan(root, serial, excludes, count, rev), "stale plan, inventory or revision mismatch")
    for shard in plan["shards"]:
        content = (Path(directory) / ("shard-%s.includes" % shard["index"])).read_text(encoding="utf-8")
        require(content == includes(shard["classes"]), "inclusion-file mismatch")


def begin_reports(plan, lane, index, reports):
    """Refuse stale output rather than deleting evidence from a prior invocation."""
    reports = Path(reports)
    require(not reports.exists() or not any(reports.iterdir()), "report directory must initially be empty")
    reports.mkdir(parents=True, exist_ok=True)
    write_json(reports / "invocation.json", dict(planDigest=plan["digest"], revision=plan["revision"],
                                                lane=lane, shardIndex=index))


def evidence(plan, lane, index, reports, maven_status):
    check_plan(plan)
    require(lane in ("parallel", "serial"), "invalid Java lane")
    require(type(maven_status) is int and maven_status >= 0, "invalid Maven exit code")
    assigned = selection(plan, index) if lane == "parallel" else plan["serialInventory"]
    reports = Path(reports)
    marker = json.loads((reports / "invocation.json").read_text(encoding="utf-8"))
    require(marker == dict(planDigest=plan["digest"], revision=plan["revision"], lane=lane, shardIndex=index),
            "invocation marker mismatch")
    xmls = sorted(reports.glob("TEST-*.xml"))
    require(bool(xmls), "missing Surefire XML")
    suites, methods, observed, reported, nested = set(), set(), set(), set(), set()
    candidate_counts = {c: dict(executed=0, skipped=0) for c in assigned}
    counts = dict(testCount=0, failureCount=0, errorCount=0, skippedCount=0)
    for file in xmls:
        require(file.stat().st_mtime_ns >= (reports / "invocation.json").stat().st_mtime_ns,
                "stale XML report")
        suite = ET.parse(file).getroot()
        require(suite.tag == "testsuite" and bool(suite.get("name")), "malformed report suite")
        require(suite.get("name") not in suites, "duplicate report identity")
        suites.add(suite.get("name"))
        cases = suite.findall("testcase")
        for key, actual in (("tests", len(cases)), ("failures", sum(c.find("failure") is not None for c in cases)),
                            ("errors", sum(c.find("error") is not None for c in cases)),
                            ("skipped", sum(c.find("skipped") is not None for c in cases))):
            require(int(suite.get(key, "-1")) == actual, "inconsistent XML counts")
        for case in cases:
            cls, name = case.get("classname", ""), case.get("name", "")
            require(bool(name) and bool(IDENTITY.fullmatch(cls)), "malformed testcase")
            owner = cls.split("$", 1)[0]
            require(owner in assigned, "unexpected executed class: " + cls)
            identity = (cls, name)
            require(identity not in methods, "duplicate testcase identity")
            methods.add(identity)
            reported.add(owner)
            if cls != owner:
                nested.add(cls)
            skip = case.find("skipped") is not None
            candidate_counts[owner]["skipped" if skip else "executed"] += 1
            counts["skippedCount"] += int(skip)
            counts["failureCount"] += int(case.find("failure") is not None)
            counts["errorCount"] += int(case.find("error") is not None)
            if not skip:
                counts["testCount"] += 1
                observed.add(cls)
    unresolved = sorted(set(assigned) - reported)
    status = "pass" if (maven_status == 0 and counts["testCount"] > 0 and not unresolved
                        and not counts["failureCount"] and not counts["errorCount"]) else "fail"
    return dict(schema=RESULT_SCHEMA, revision=plan["revision"], planDigest=plan["digest"],
                lane=lane, shardIndex=index, assignedClasses=assigned, observedClasses=sorted(observed),
                reportedCandidates=sorted(reported), nestedClasses=sorted(nested), candidateCounts=candidate_counts,
                unresolvedCandidates=unresolved, mavenExitCode=maven_status, status=status, **counts)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("prepare", "validate", "begin", "report", "outputs"))
    parser.add_argument("--root", default="target/test-classes")
    parser.add_argument("--directory", default="target/java-test-shards")
    parser.add_argument("--serial", default="")
    parser.add_argument("--excludes", default="")
    parser.add_argument("--count", type=int, default=1)
    parser.add_argument("--index", type=int, default=1)
    parser.add_argument("--revision")
    parser.add_argument("--lane", choices=("parallel", "serial"), default="parallel")
    parser.add_argument("--reports")
    parser.add_argument("--maven-status", type=int, default=0)
    args = parser.parse_args()
    try:
        directory = Path(args.directory)
        if args.command == "prepare":
            save_plan(make_plan(args.root, args.serial, args.excludes, args.count, args.revision), directory)
            return 0
        plan = check_plan(json.loads((directory / "plan.json").read_text(encoding="utf-8")))
        require(plan["revision"] == revision(args.revision), "revision mismatch")
        if args.command == "validate":
            validate_worker(plan, args.root, args.serial, args.excludes, args.count,
                            args.index, args.revision, directory)
        elif args.command == "begin":
            begin_reports(plan, args.lane, args.index, args.reports)
        elif args.command == "report":
            result = evidence(plan, args.lane, args.index, args.reports, args.maven_status)
            write_json(Path(args.reports) / "result.json", result)
            return 0 if result["status"] == "pass" else 1
        elif args.command == "outputs":
            print("count=" + str(plan["shardCount"]))
            print("indices=" + json.dumps([s["index"] for s in plan["shards"]]))
        return 0
    except (ValueError, KeyError, TypeError, OSError, ET.ParseError) as error:
        print("Java partition validation failed: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
