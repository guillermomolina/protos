#!/usr/bin/env python3
# THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
# ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
# DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
# DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
# OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS
# OF THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE.
# A COPY OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT"
# ACCOMPANYING THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT
# ACCOMPANY THE CONTENTS OF THIS FILE, A COPY OF THE LICENSE MAY ALSO BE
# OBTAINED AT THE FOLLOWING WEB SITE:
# https://github.com/guillermomolina/protos
#
# Software distributed under the License is distributed on an "AS IS" basis,
# WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
# for the specific language governing rights and limitations under the License.
"""I115 physical graph-budget gate for factorial(20) and fibonacci(20).

Runs ProtosI115FactorialFibonacciGraphBudgetTest once per (workload, root, tier)
in eight independent Maven/Surefire JVMs, reads the 'After TruffleTier' IR node
count (first IR value of the selected '[engine] opt done' line) and fails when
any count exceeds its frozen reference. Artifacts stay outside the repository.
"""

import argparse
import os
import re
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from i101_local_binding_graph import TRUFFLE_COMPILER_EXPORT
from truffle_root_diagnostic import (
    STABLE_ROOT_IDENTITY,
    TARGET_NAME,
    bgv_files,
    ordinary_compilation_evidence,
)

RUNNER = "ProtosI115FactorialFibonacciGraphBudgetTest"
CASES = Path("tools/i115_gate_cases")
WORKLOADS = ("factorial", "fibonacci")
ROOTS = ("run", "recursive")
TIERS = (1, 2)
EXPECTED_RESULT = {"factorial": "2432902008176640000", "fibonacci": "6765"}
# Frozen strict limits: protos_nodes <= reference_nodes.
REFERENCE = {
    ("factorial", "run", 1): 161,
    ("factorial", "run", 2): 407,
    ("factorial", "recursive", 1): 216,
    ("factorial", "recursive", 2): 568,
    ("fibonacci", "run", 1): 158,
    ("fibonacci", "run", 2): 1334,
    ("fibonacci", "recursive", 1): 231,
    ("fibonacci", "recursive", 2): 1531,
}
REQUIRED_MARKERS = frozenset(("WORKLOAD", "ROOT", "TARGET", "TIER", "RESULT", "COMPILED"))
MARKER = re.compile(r"GRAPH_BUDGET_([A-Z_]+)=(.*)")
CLOSURE_TARGET = re.compile(r"^(protos-root:[0-9a-f]{16})\[CLOSURE\|([^|\]\s]+)\|(\d+)\+(\d+)\]$")
SUREFIRE_SUMMARY = re.compile(
    r"Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+),\s*Skipped:\s*(\d+)")
# Same field interpretation as tools/i101_literal_one_compilation_tiers.py.
TIER_FIELD = re.compile(r"\|Tier\s+(\d+)\|")
IR_FIELD = re.compile(r"\|IR\s+(\d+)/\s*(\d+)\|")
COMPID_FIELD = re.compile(r"\|CompId\s+(\d+)")


class GateError(ValueError):
    pass


def lane_key(workload, root, tier):
    return "%s-%s-tier%d" % (workload, root, tier)


def parse_markers(output):
    """Exactly one value per required marker; duplicates and unknown markers fail."""
    found = {}
    for line in output.splitlines():
        match = MARKER.search(line)
        if not match:
            continue
        key, value = match.group(1), match.group(2).strip()
        if key in found:
            raise GateError("DUPLICATE_MARKER " + key)
        found[key] = value
    missing = sorted(REQUIRED_MARKERS - set(found))
    unknown = sorted(set(found) - REQUIRED_MARKERS)
    if missing:
        raise GateError("MISSING_MARKER %s" % ",".join(missing))
    if unknown:
        raise GateError("UNKNOWN_MARKER %s" % ",".join(unknown))
    return found


def junit_problems(output, code):
    problems = [] if code == 0 else ["MAVEN_RETURN_CODE=%s" % code]
    summaries = [tuple(int(v) for v in m.groups()) for m in SUREFIRE_SUMMARY.finditer(output)]
    if not summaries:
        problems.append("JUNIT_SUMMARY_MISSING")
    elif any(s[3] for s in summaries):
        problems.append("JUNIT_SKIPPED")
    elif any(s[0] != 1 or s[1] or s[2] for s in summaries):
        problems.append("JUNIT_NOT_EXACTLY_ONE_PASSING_TEST %r" % (summaries,))
    return problems


def root_problems(target, workload, root, source_text):
    """Independently re-check the selected root from its span in the gate program."""
    match = CLOSURE_TARGET.fullmatch(target)
    if not match:
        return ["TARGET_NOT_A_STABLE_CLOSURE_ROOT %s" % target]
    uri, start, length = match.group(2), int(match.group(3)), int(match.group(4))
    if not uri.endswith("/" + workload + ".protos"):
        return ["TARGET_FROM_OTHER_SOURCE %s" % uri]
    if source_text is None or start + length > len(source_text):
        return ["TARGET_SPAN_UNREADABLE"]
    text = source_text[start:start + length]
    run_witness, recursive_witness = workload + "(20)", workload + "(n - 1)"
    wanted, other = (run_witness, recursive_witness) if root == "run" else (recursive_witness, run_witness)
    if wanted not in text or other in text:
        return ["TARGET_IS_OTHER_ROOT"]
    return []


def compilation_records(output, target):
    """Every '[engine] opt done' line, attributed by exact target name."""
    records = []
    for line in output.splitlines():
        start = line.find("[engine] opt done")
        if start < 0:
            continue
        line = line[start:]
        names = TARGET_NAME.findall(line)
        tier = TIER_FIELD.search(line)
        ir = IR_FIELD.search(line)
        comp_id = COMPID_FIELD.search(line)
        records.append({
            "selected": target in names,
            "names": names,
            "tier": int(tier.group(1)) if tier else None,
            "ir_after_truffle_tier": int(ir.group(1)) if ir else None,
            "ir_final": int(ir.group(2)) if ir else None,
            "compilation_id": int(comp_id.group(1)) if comp_id else None,
            "trace": line,
        })
    return records


def select_compilation(records, target, tier):
    """Exactly one selected-root compilation at the requested tier, with an unambiguous CompId."""
    selected = [r for r in records if r["selected"]]
    if not selected:
        if records:
            raise GateError("COMPILATION_OF_OTHER_ROOT")
        raise GateError("NO_OPT_DONE")
    if any(r["tier"] is None for r in selected):
        raise GateError("TIER_UNREADABLE")
    other_tier = [r for r in selected if r["tier"] != tier]
    if other_tier:
        raise GateError("UNEXPECTED_TIER_COMPILATION %s" % sorted({r["tier"] for r in other_tier}))
    if any(r["compilation_id"] is None for r in selected):
        raise GateError("COMPID_UNREADABLE")
    by_id = {}
    for record in selected:
        known = by_id.setdefault(record["compilation_id"], record)
        if known is not record and known["ir_after_truffle_tier"] != record["ir_after_truffle_tier"]:
            raise GateError("CONTRADICTORY_COMPID %d" % record["compilation_id"])
    if len(by_id) != 1 or len(selected) != 1:
        raise GateError("AMBIGUOUS_COMPILATIONS %s" % sorted(by_id))
    record = selected[0]
    if record["ir_after_truffle_tier"] is None:
        raise GateError("IR_UNREADABLE")
    return record


def fresh_bgv_problems(output, selector, dump_directory, started):
    graphs = bgv_files(dump_directory)
    evidence = ordinary_compilation_evidence(output, selector, graphs)
    problems = list(evidence["problems"])
    selected = evidence["selected_bgv_files"]
    if selected and any(Path(p).stat().st_mtime < started for p in selected):
        problems.append("STALE_SELECTED_BGV")
    return problems, selected


def validate_lane(output, code, workload, root, tier, dump_directory, started, source_text):
    lane = {"workload": workload, "root": root, "tier": tier,
            "reference": REFERENCE[(workload, root, tier)], "protos": None, "problems": []}
    problems = lane["problems"]
    problems.extend(junit_problems(output, code))
    try:
        markers = parse_markers(output)
    except GateError as error:
        problems.append(str(error))
        return lane
    if markers["WORKLOAD"] != workload:
        problems.append("WRONG_WORKLOAD %s" % markers["WORKLOAD"])
    if markers["ROOT"] != root:
        problems.append("WRONG_ROOT %s" % markers["ROOT"])
    if markers["TIER"] != str(tier):
        problems.append("WRONG_TIER %s" % markers["TIER"])
    if markers["RESULT"] != EXPECTED_RESULT[workload]:
        problems.append("WRONG_RESULT %s" % markers["RESULT"])
    if markers["COMPILED"] != "YES":
        problems.append("NOT_COMPILED")
    target = markers["TARGET"]
    lane["target"] = target
    problems.extend(root_problems(target, workload, root, source_text))
    try:
        record = select_compilation(compilation_records(output, target), target, tier)
        lane["protos"] = record["ir_after_truffle_tier"]
        lane["compilation_id"] = record["compilation_id"]
        lane["trace"] = record["trace"]
    except GateError as error:
        problems.append(str(error))
    bgv_issues, selected_bgv = fresh_bgv_problems(
        output, target.split("[", 1)[0], dump_directory, started)
    problems.extend(bgv_issues)
    lane["selected_bgv"] = selected_bgv
    if lane["protos"] is not None and lane["protos"] > lane["reference"]:
        problems.append("BUDGET_EXCEEDED %d > %d" % (lane["protos"], lane["reference"]))
    return lane


def lane_options(workload, root, tier, dump_path):
    """No CompileImmediately: each tier is requested through the explicit compile API."""
    return [
        TRUFFLE_COMPILER_EXPORT,
        "-Dpolyglot.engine.AllowExperimentalOptions=true",
        "-Dpolyglot.engine.BackgroundCompilation=false",
        "-Dpolyglot.engine.TraceCompilation=true",
        "-Dpolyglot.engine.CompilationFailureAction=Throw",
        # Same isolation as the I115-R capture: no natural compilation before the request.
        "-Dpolyglot.engine.DynamicCompilationThresholds=false",
        "-Dpolyglot.engine.FirstTierCompilationThreshold=1000000",
        "-Dpolyglot.engine.LastTierCompilationThreshold=1000000",
        STABLE_ROOT_IDENTITY,
        "-Dprotos.graphBudget.capture=true",
        "-Dprotos.graphBudget.workload=" + workload,
        "-Dprotos.graphBudget.root=" + root,
        "-Dprotos.graphBudget.tier=" + str(tier),
        "-Djdk.graal.Dump=Truffle:1",
        "-Djdk.graal.DumpPath=" + dump_path.as_posix(),
    ]


def lane_environment(options, base):
    # As in i101_local_binding_graph.py: the POM's literal argLine cannot be replaced
    # with -DargLine, so JAVA_TOOL_OPTIONS reaches the Surefire forked JVM.
    environment = dict(base)
    existing = environment.get("JAVA_TOOL_OPTIONS", "").strip()
    environment["JAVA_TOOL_OPTIONS"] = (existing + " " + " ".join(options)).strip()
    return environment


def evaluate(lanes):
    """PASS only with all eight lanes present, measured, problem-free and within budget."""
    expected = {lane_key(w, r, t) for w in WORKLOADS for r in ROOTS for t in TIERS}
    problems = []
    missing = sorted(expected - set(lanes))
    if missing:
        problems.append("MISSING_LANES %s" % ",".join(missing))
    extra = sorted(set(lanes) - expected)
    if extra:
        problems.append("UNKNOWN_LANES %s" % ",".join(extra))
    for key in sorted(expected & set(lanes)):
        lane = lanes[key]
        if lane["protos"] is None:
            problems.append("%s NO_MEASUREMENT" % key)
        for problem in lane["problems"]:
            problems.append("%s %s" % (key, problem))
    return ("PASS" if not problems else "FAIL"), problems


def table(lanes):
    rows = ["%-9s %-10s %-5s %-7s %-10s %-6s %s" % (
        "workload", "root", "tier", "protos", "reference", "delta", "result")]
    for workload in WORKLOADS:
        for root in ROOTS:
            for tier in TIERS:
                lane = lanes.get(lane_key(workload, root, tier))
                reference = REFERENCE[(workload, root, tier)]
                if lane is None or lane["protos"] is None:
                    protos, delta, result = "-", "-", "FAIL"
                else:
                    protos = str(lane["protos"])
                    delta = "%+d" % (lane["protos"] - reference)
                    result = "PASS" if not lane["problems"] else "FAIL"
                rows.append("%-9s %-10s %-5d %-7s %-10d %-6s %s" % (
                    workload, root, tier, protos, reference, delta, result))
    return "\n".join(rows)


def run(root, mvn, timeout, artifacts):
    try:
        artifacts.resolve().relative_to(root.resolve())
        print("FACTORIAL_FIBONACCI_GRAPH_BUDGET_ISSUE=ARTIFACTS_INSIDE_REPOSITORY")
        print("FACTORIAL_FIBONACCI_GRAPH_BUDGET=FAIL")
        return 1
    except ValueError:
        pass
    print("FACTORIAL_FIBONACCI_GRAPH_BUDGET_ARTIFACTS=" + str(artifacts), flush=True)
    lanes = {}
    for workload in WORKLOADS:
        source_path = root / CASES / (workload + ".protos")
        source_text = source_path.read_text(encoding="utf-8") if source_path.is_file() else None
        for root_role in ROOTS:
            for tier in TIERS:
                key = lane_key(workload, root_role, tier)
                lane_directory = artifacts / key
                dump_directory = lane_directory / "graal_dumps"
                dump_directory.mkdir(parents=True, exist_ok=False)
                options = lane_options(workload, root_role, tier, dump_directory)
                command = [mvn, "-Dtest=" + RUNNER, "test"]
                print("GRAPH_BUDGET_LANE=" + key, flush=True)
                started = time.time() - 1
                try:
                    completed = subprocess.run(
                        command, cwd=root, env=lane_environment(options, os.environ),
                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                        timeout=timeout, check=False)
                    output, code = completed.stdout, completed.returncode
                except (OSError, subprocess.TimeoutExpired) as error:
                    output, code = str(error), -1
                (lane_directory / "maven.log").write_text(output, encoding="utf-8")
                lane = validate_lane(output, code, workload, root_role, tier,
                                     dump_directory, started, source_text)
                lanes[key] = lane
                for problem in lane["problems"]:
                    print("  " + problem, flush=True)
    result, problems = evaluate(lanes)
    print(table(lanes))
    for problem in problems:
        print("FACTORIAL_FIBONACCI_GRAPH_BUDGET_ISSUE=" + problem)
    print("FACTORIAL_FIBONACCI_GRAPH_BUDGET=" + result)
    return 0 if result == "PASS" else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--mvn", default="mvn")
    parser.add_argument("--timeout", type=int, default=600, help="Seconds per JVM lane")
    parser.add_argument("--artifacts", type=Path, default=None,
                        help="New directory outside the repository (default: fresh temporary directory)")
    args = parser.parse_args(argv)
    artifacts = args.artifacts or Path(tempfile.mkdtemp(prefix="protos-graph-budget-"))
    return run(args.root.resolve(), args.mvn, args.timeout, artifacts.resolve())


if __name__ == "__main__":
    sys.exit(main())
