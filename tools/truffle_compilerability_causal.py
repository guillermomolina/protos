#!/usr/bin/env python3
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


"""TEST009-T causal compilerability records from one diagnostic shard log.

The diagnostic JVM runs with -Dprotos.compilerability.causalTrace=true, which makes the
Protos runtime print one prefixed single-line JSON event (protos.compilerability.causal/v1)
per compilation lifecycle callback: start, truffle_tier, graal_tier, success | failure.
This module joins those events into one record per compilation and attributes the
upstream textual traces (method/node expansion, the engine `opt done`/`opt failed` line)
printed between a compilation's start and terminal events to that compilation.

Everything fails closed: a malformed event, a missing or duplicate lifecycle step, an
unparsable expansion table, expansion text outside any compilation window, or text printed
while two compilations were active makes the acquisition INCOMPLETE; nothing is assigned to
the "most probable" compilation. Run-local labels (engine/target ids, `Name@hex` labels) are
used only for the in-run join and are removed from the records.
"""

from __future__ import print_function

import json
import re
from collections import OrderedDict
from typing import List

CAUSAL_TRACE_PROPERTY = "-Dprotos.compilerability.causalTrace=true"
CAUSAL_PREFIX = "[protos-compilerability] "
CAUSAL_SCHEMA = "protos.compilerability.causal/v1"

COMPLETE = "COMPLETE"
INCOMPLETE = "INCOMPLETE"
SUCCESS = "SUCCESS"
CODE_TOO_LARGE = "CODE_TOO_LARGE"
TOO_DEEP = "TOO_DEEP"
OTHER_FAILURE = "OTHER_FAILURE"
SEMANTIC_ROOT = "SEMANTIC_BYTECODE_ROOT"
GREATER_THAN_LIMIT = "GREATER_THAN_JVMCI_NMETHOD_SIZE_LIMIT"
TOP_CONTRIBUTORS = 20

EVENTS = ("installed", "install_error", "error", "start", "truffle_tier", "graal_tier", "success", "failure")
TERMINAL_EVENTS = ("success", "failure")
EXPANSION_HEADER = re.compile(r"\b(method|node)\s+expansion\b", re.IGNORECASE)
ENGINE_TERMINAL = re.compile(r"^\s*\[engine\] opt (done|failed)\s+engine=\s*(\d+)\s+id=\s*(\d+)\b")
NUMBER = re.compile(r"^-?[0-9][0-9,]*(?:\.[0-9]+)?%?$")
RUN_LOCAL_LABEL = re.compile(r"@[0-9a-fA-F]{4,}\b")
REASON_CLASS = re.compile(r"^((?:[A-Za-z_$][\w$]*\.)*[A-Z][\w$]*(?:Exception|Error|Bailout)[\w$]*):")


class CausalParseError(Exception):
    pass


def classify_outcome(event) -> str:
    if event["event"] == "success":
        return SUCCESS
    reason = (event.get("reason") or "").lower()
    if "code is too large" in reason:
        return CODE_TOO_LARGE
    if "too deep" in reason:
        return TOO_DEEP
    return OTHER_FAILURE


def reason_class(reason):
    match = REASON_CLASS.match((reason or "").strip())
    return match.group(1) if match else None


def parse_event(line: str):
    """The JSON event of one prefixed line; CausalParseError when it is not a valid v1 event."""
    try:
        event = json.loads(line[len(CAUSAL_PREFIX):], object_pairs_hook=OrderedDict)
    except ValueError as error:
        raise CausalParseError("malformed causal event JSON: %s" % error)
    if not isinstance(event, dict) or event.get("schema") != CAUSAL_SCHEMA:
        raise CausalParseError("causal event without schema %s" % CAUSAL_SCHEMA)
    if event.get("event") not in EVENTS:
        raise CausalParseError("unknown causal event %r" % event.get("event"))
    if event["event"] not in ("installed", "install_error", "error") and not isinstance(event.get("seq"), int):
        raise CausalParseError("%s event without an integer seq" % event["event"])
    return event


def strip_run_local(text: str) -> str:
    return RUN_LOCAL_LABEL.sub("@<run-local>", text)


def expansion_sections(lines: List[str]):
    """(sections, problems) for the method/node expansion tables in one compilation window.

    A section starts at a line naming "method expansion" or "node expansion". Its table header is
    the first following line containing the columns Count and Size; rows follow until a blank
    line, an `[engine]` line or the next section. Only the first `|`-separated column group
    (the cumulative columns) is used: label, then exactly one number per header column.
    """
    sections, problems, index = [], [], 0
    while index < len(lines):
        match = EXPANSION_HEADER.search(lines[index])
        if not match:
            index += 1
            continue
        family = match.group(1).upper()
        title = lines[index].lower()
        kind = "TREE" if "tree" in title else ("HISTOGRAM" if "histogram" in title or "statistic" in title
                                                else "OTHER")
        index += 1
        header = None
        while index < len(lines) and header is None and not boundary(lines[index]):
            columns = lines[index].split("|")[0].split()
            if "Count" in columns and "Size" in columns:
                header = columns[1:]
            index += 1
        rows = []
        while index < len(lines) and not boundary(lines[index]) and lines[index].strip():
            if set(lines[index].strip()) <= set("-=|+ "):
                index += 1
                continue
            rows.append(lines[index].split("|")[0])
            index += 1
        if header is None or not rows:
            problems.append("UNRECOGNIZED_%s_EXPANSION_TABLE" % family)
            continue
        parsed = parse_rows(rows, header)
        if parsed is None:
            problems.append("UNPARSABLE_%s_EXPANSION_ROW" % family)
            continue
        sections.append(OrderedDict([("family", family), ("kind", kind)] + list(parsed.items())))
    return sections, problems


def boundary(line: str) -> bool:
    return line.lstrip().startswith("[engine]") or line.startswith(CAUSAL_PREFIX) or bool(
        EXPANSION_HEADER.search(line))


def parse_rows(rows: List[str], header: List[str]):
    count_column, size_column = header.index("Count"), header.index("Size")
    entries = []
    for row in rows:
        tokens = row.split()
        numbers = []
        while tokens and NUMBER.match(tokens[-1]) and len(numbers) < len(header):
            numbers.insert(0, tokens.pop())
        if len(numbers) != len(header) or not tokens:
            return None
        indent = len(row) - len(row.lstrip())
        label = strip_run_local(" ".join(tokens))
        entries.append((indent, label, number(numbers[count_column]), number(numbers[size_column])))
    top_indent = min(entry[0] for entry in entries)
    top_level_size = sum(entry[3] for entry in entries if entry[0] == top_indent)
    ranked = sorted(entries, key=lambda entry: (-entry[3], -entry[2], entry[1]))[:TOP_CONTRIBUTORS]
    contributors = [OrderedDict([("label", label), ("depth", indent - top_indent), ("count", count),
                                 ("size", size),
                                 ("share_of_top_level_size",
                                  round(size / float(top_level_size), 6) if top_level_size else None)])
                    for indent, label, count, size in ranked]
    return OrderedDict([("rows", len(entries)), ("top_level_size", top_level_size),
                        ("top_contributors", contributors)])


def number(token: str):
    value = float(token.rstrip("%").replace(",", ""))
    return int(value) if value.is_integer() else value


class _Compilation(object):

    def __init__(self, start):
        self.start = start
        self.events = OrderedDict([("start", start)])
        self.lines = []
        self.overlapped = False
        self.problems = []


def parse_causal_trace(output: str) -> "OrderedDict[str, object]":
    """{status, problems, compilations} for one shard log; see the module docstring."""
    problems, compilations, active, installs = [], [], OrderedDict(), 0
    stray_expansion = False
    for line in output.splitlines():
        if CAUSAL_PREFIX in line and not line.startswith(CAUSAL_PREFIX):
            problems.append("EMBEDDED_CAUSAL_EVENT a causal event does not start its line")
            continue
        if not line.startswith(CAUSAL_PREFIX):
            if len(active) == 1:
                next(iter(active.values())).lines.append(line)
            elif len(active) > 1:
                for compilation in active.values():
                    compilation.overlapped = True
            elif EXPANSION_HEADER.search(line):
                stray_expansion = True
            continue
        try:
            event = parse_event(line)
        except CausalParseError as error:
            problems.append("MALFORMED %s" % error)
            continue
        name = event["event"]
        if name == "installed":
            installs += 1
            continue
        if name in ("install_error", "error"):
            problems.append("%s %s: %s" % (name.upper(), event.get("code"), event.get("detail")))
            continue
        seq = event["seq"]
        if name == "start":
            if any(seq <= known.start["seq"] for known in compilations):
                problems.append("LIFECYCLE start seq %d is not monotonic or repeats" % seq)
                continue
            compilation = _Compilation(event)
            if active:
                compilation.overlapped = True
                for other in active.values():
                    other.overlapped = True
            active[seq] = compilation
            compilations.append(compilation)
            continue
        compilation = active.get(seq)
        expected = {"truffle_tier": ("start",), "graal_tier": ("truffle_tier",), "success": ("graal_tier",),
                    "failure": ("start", "truffle_tier", "graal_tier")}[name]
        if compilation is None or next(reversed(compilation.events)) not in expected:
            problems.append("LIFECYCLE %s for seq %d out of order or without an active start" % (name, seq))
            if compilation is not None:
                compilation.problems.append("LIFECYCLE_VIOLATION")
                del active[seq]
            continue
        compilation.events[name] = event
        if name in TERMINAL_EVENTS:
            del active[seq]
    if installs != 1:
        problems.append("INSTALLATION %d 'installed' events; expected exactly one" % installs)
    if stray_expansion:
        problems.append("UNATTRIBUTED_EXPANSION_TRACE expansion text outside any compilation window")
    records = [build_record(compilation) for compilation in compilations]
    for record in records:
        if record["causal_complete"] is not True:
            problems.append("RECORD seq %d incomplete: %s" % (record["run_sequence"],
                                                             ", ".join(record["causal_problems"])))
    keys = OrderedDict()
    for record in records:
        if record["durable_root_key"] is not None and record["structural_fingerprint"] is not None:
            keys.setdefault(record["durable_root_key"], set()).add(record["structural_fingerprint"])
    for key, fingerprints in keys.items():
        if len(fingerprints) > 1:
            problems.append("DURABLE_KEY_COLLISION %s names %d different structures" % (key, len(fingerprints)))
    return OrderedDict([("status", INCOMPLETE if problems else COMPLETE), ("problems", problems),
                        ("compilations", records)])


IDENTITY_FIELDS = ("durable_root_key", "durable_root_key_problem", "root_family", "root_class", "root_name",
                   "source_uri", "source_start", "source_length", "semantic_root_kind", "continuation",
                   "continuation_source_root", "continuation_resume_bci", "structural_fingerprint")


def build_record(compilation: _Compilation) -> "OrderedDict[str, object]":
    start, events, problems = compilation.start, compilation.events, list(compilation.problems)
    truffle, graal = events.get("truffle_tier"), events.get("graal_tier")
    terminal = events.get("success") or events.get("failure")
    record = OrderedDict([("run_sequence", start["seq"])])
    for field in IDENTITY_FIELDS:
        record[field] = start.get(field)
    record["tier"] = start.get("tier")
    record["ast_non_trivial_node_count"] = start.get("ast_non_trivial_node_count")
    record["truffle_tier_graph_nodes"] = truffle.get("graph_nodes") if truffle else None
    record["truffle_tier_top_node_types"] = truffle.get("top_node_types") if truffle else None
    record["graal_tier_graph_nodes"] = graal.get("graph_nodes") if graal else None
    record["graal_tier_top_node_types"] = graal.get("top_node_types") if graal else None
    record["inlining"] = truffle.get("inlining") if truffle else None
    outcome = classify_outcome(terminal) if terminal else None
    record["outcome"] = outcome
    failure = events.get("failure")
    record["failure_phase_reached"] = failure.get("phase_reached") if failure else None
    record["bailout"] = failure.get("bailout") if failure else None
    record["permanent_bailout"] = failure.get("permanent_bailout") if failure else None
    record["failure_reason_class"] = reason_class(failure.get("reason")) if failure else None
    record["failure_reason"] = failure.get("reason") if failure else None
    success = events.get("success")
    for field in ("compilation_id", "target_code_size", "total_frame_size", "exception_handlers_count",
                  "infopoints_count"):
        record["success_" + field] = success.get(field) if success else None
    # A failed installation proves only the relation, never the install-buffer size.
    record["install_size_relation"] = GREATER_THAN_LIMIT if outcome == CODE_TOO_LARGE else None

    if terminal is None:
        problems.append("MISSING_TERMINAL_EVENT")
    if record["durable_root_key"] is None:
        problems.append("NO_DURABLE_ROOT_KEY %s" % record["durable_root_key_problem"])
    if outcome in (SUCCESS, CODE_TOO_LARGE) and (truffle is None or graal is None):
        problems.append("MISSING_TIER_EVENT for %s" % outcome)
    if truffle is not None:
        inlined = (truffle.get("inlining") or {}).get("inlined_targets") or []
        if any(target.get("durable_root_key") is None for target in inlined):
            problems.append("INLINED_TARGET_WITHOUT_DURABLE_KEY")
    if compilation.overlapped:
        problems.append("AMBIGUOUS_TEXT_WINDOW another compilation was active")
    sections, text_problems = expansion_sections(compilation.lines)
    problems.extend(text_problems)
    record["method_expansion"] = [section for section in sections if section["family"] == "METHOD"]
    record["node_expansion"] = [section for section in sections if section["family"] == "NODE"]
    if truffle is not None and not compilation.overlapped:
        for family in ("method", "node"):
            if not record[family + "_expansion"] and not any(family.upper() in p for p in text_problems):
                problems.append("MISSING_%s_EXPANSION" % family.upper())
    problems.extend(engine_join_problems(compilation, terminal))
    record["causal_complete"] = not problems
    record["causal_problems"] = problems
    return record


def engine_join_problems(compilation: _Compilation, terminal) -> List[str]:
    """The window's `[engine] opt done|failed` lines must be this compilation's and agree with it."""
    run_local = compilation.start.get("run_local") or {}
    own = (run_local.get("engine"), run_local.get("id"))
    problems = []
    for line in compilation.lines:
        match = ENGINE_TERMINAL.match(line)
        if not match:
            continue
        if (int(match.group(2)), int(match.group(3))) != own:
            problems.append("FOREIGN_ENGINE_TRACE another target's opt line inside the window")
        elif terminal is not None and (match.group(1) == "done") != (terminal["event"] == "success"):
            problems.append("ENGINE_TRACE_DISAGREES engine reported opt %s" % match.group(1))
    return problems


def summarize(causal) -> "OrderedDict[str, object]":
    records = causal["compilations"]
    too_large = [record for record in records if record["outcome"] == CODE_TOO_LARGE]
    return OrderedDict([
        ("CAUSAL_ACQUISITION", causal["status"]),
        ("CAUSAL_COMPILATIONS", len(records)),
        ("CODE_TOO_LARGE_CAUSAL_RECORDS", len(too_large)),
        ("SEMANTIC_CODE_TOO_LARGE_CAUSAL_RECORDS",
         sum(1 for record in too_large if record["root_family"] == SEMANTIC_ROOT)),
    ])
