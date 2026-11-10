#!/usr/bin/env python3
# THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
# ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina.
# See LICENSE.TXT at the repository root.
"""I115 autonomous semantic + structural gate; no protos-benchmarks dependency.

The default tracked baseline is the verified c58b28f0 structural checkpoint;
the original b1f4c053 baseline is archived and selectable with --baseline.
Candidate observations use only the local Protos JVM and tracked Protos fixtures.
An unverifiable metric is a failure, never an assumed zero or a PASS.
"""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / "tools/baselines/i115-graal25.5.json"
CASES_DIR = ROOT / "tools/i115_gate_cases"
JAVA_DIR = ROOT / "tools/i115_gate_java"
GRAAL_VERSION = "25.5.5.0.3"
BASELINE_REVISION = "c58b28f0c5141e20ad0397c7776389f6b6fd7508"
HISTORICAL_BASELINE_REVISION = "b1f4c05387e7e2143d3660c9834716e8383124f7"
HOST = "org.graalvm.polyglot.Value<ProtosHostExecutableClosure>.execute"
CASES = (
    "fibonacci", "factorial", "method-call", "integer-loop",
    "primitive-return-literal", "primitive-local-read", "primitive-local-write",
    "primitive-integer-add", "primitive-object-slot-read",
    "primitive-object-slot-write", "primitive-closure-call",
    "primitive-method-call", "primitive-if-true", "primitive-if-false",
    "primitive-while-zero", "primitive-while-once",
    "primitive-while-counted", "bubble-sort",
)
EXPECTED_RESULTS = {
    "fibonacci": "6765", "factorial": "2432902008176640000",
    "method-call": "499500", "integer-loop": "499500",
    "primitive-return-literal": "1", "primitive-local-read": "1",
    "primitive-local-write": "2", "primitive-integer-add": "3",
    "primitive-object-slot-read": "1", "primitive-object-slot-write": "2",
    "primitive-closure-call": "1", "primitive-method-call": "1",
    "primitive-if-true": "1", "primitive-if-false": "1",
    "primitive-while-zero": "1", "primitive-while-once": "1",
    "primitive-while-counted": "8", "bubble-sort": "89440",
}
# Old IDs belong solely to the immutable historical source URI. Local fixture
# URIs are different: attribute by verified kind + increasing semantic span,
# never by matching graph size, execution count or a physical Java identity.
GUEST_IDS_IN_SOURCE_ORDER = {
    "fibonacci": ("9e63b5cbd94a66e6", "4308b246ea11a3c5"),
    "factorial": ("0349b7af98147903", "78dd5017e44815f0"),
    "method-call": ("52ca6570f17f6fd3", "66bdbc199ef1f645"),
    "integer-loop": ("77d1c4a1d5f56b7a",),
    "primitive-return-literal": ("12000139cc4702dc",),
    "primitive-local-read": ("2e9ed1a71f73a5f0",),
    "primitive-local-write": ("85d74f07baa4c42b",),
    "primitive-integer-add": ("ce98415490e06f7d",),
    "primitive-object-slot-read": ("b1af43ea3a194806",),
    "primitive-object-slot-write": ("8e64d08e1bfab4e8",),
    "primitive-closure-call": ("e09ef5c7513d2c90", "767e03159230ea4a"),
    "primitive-method-call": ("8c41fefc9e980289", "16125c1e5cdb117c"),
    "primitive-if-true": ("5f70adb39a622520",),
    "primitive-if-false": ("40338fab65c4a66b",),
    "primitive-while-zero": ("68028c3f51c1177d",),
    "primitive-while-once": ("521567ba759907ff",),
    "primitive-while-counted": ("08f712e1e6afaa16",),
    "bubble-sort": ("70bac6408c0f1e35",),
}
# Every fixture is frozen by digest; the installer fills this table with the
# exact packaged bytes, so edits cannot silently change the sampled workload.
FIXTURE_SHA256 = {'bubble-sort': '63f7e203a420de79c2e8fd5f1113b898ed3103b0c9f4f352cad50deab76cd659', 'factorial': 'c4488d626b601f89b516ac4ff40dfdd0a941e198f14ef2a6d497e589487ebf4e', 'fibonacci': '0d6311e17c247b7ab49d2d531e6025c92fa729418f9e4be8432666184d9ceb98', 'integer-loop': '2c2d76e32e786155dace89d354a78eb2f6451b1bb03b965117a0848a444d806a', 'method-call': 'b60f37202223e4a3f414b4bcd3aa3b301e8afc72f4702c5371b80633761f1416', 'primitive-closure-call': 'c0bf7a7618b541e15e43fc14cad67d5ad16d026880ff39e6e1ba8c211fc38d87', 'primitive-if-false': '391d39080cc4058350fefadf04a8c581dae1ef732f06335cbb8e6d88a9fb2d7d', 'primitive-if-true': 'c3c2ca9068f7ed8655153d3ae1684f1a5351f25cef1e53cf8c22174b2ef8bb7d', 'primitive-integer-add': '04adfcd1832765aefe3af59332101b10a77f469ca156c84fde8cf1c678c8ca5d', 'primitive-local-read': '0cdb03f202819b1beaa3bb4e96973cd1fa2c10b6b00d9fc70c90e86e20a724cb', 'primitive-local-write': '8c4d55bb9d2becf58932b44c893328879e1eaa08e98143fe5aabdac9533b45e9', 'primitive-method-call': 'c9d0ebf71c33f8f3906455f5dee028a8d9427e4c86a63482e29d805ac0347487', 'primitive-object-slot-read': '2bd8cb53e6222dd87cfd2f397af9788ccc35b3daf980589edda0e3c6a1f6c29a', 'primitive-object-slot-write': 'b3b15b136e28a4ea94d276ee5aae8e11cb2a39faf5161fb23b20ab025c97c397', 'primitive-return-literal': '7ff0aca67600fa840fa18566b90e4e0f261cf72fda27718b70cfc6646ebd3bd6', 'primitive-while-counted': '150d0a38a0197ab3f78cd31314c72319c698fd00f58d7ab5a8cb4e6c04ed0c34', 'primitive-while-once': 'b34fa2268d7f5370a778d5aef813749c4ca269a1640823511cae57065a4c7cfe', 'primitive-while-zero': 'a5e108c1587c179c1f60f6b6a9b776d4c45cf0dbc2f6dd87a3dd91a431b7bf31'}

ROOT_RE = re.compile(
    r"^protos-root:[0-9a-f]{16}\[(?P<kind>TOP_LEVEL|CLOSURE)\|"
    r"(?P<uri>[^\]|]+)\|(?P<start>[0-9]+)\+(?P<length>[0-9]+)\]$"
)
COMP_RE = re.compile(
    r"^\[engine\] opt done\s+"
    r"(?:engine=\d+\s+id=\d+\s+)?(?P<label>.+?)\s+\|Tier\s+[12]\|"
)
IR_RE = re.compile(r"\|IR\s+(?P<after>\d+)\s*/\s*(?P<final>\d+)")
TIER_RE = re.compile(r"\|Tier\s+(?P<tier>[12])(?:\s*\||$)")
COMP_ID_RE = re.compile(r"\|CompId\s+(?P<id>\d+)")
SIZE_PREFIX = "I115_BYTECODE_SIZE_V1\t"


class GateError(Exception):
    pass


def read_json(path: Path) -> dict:
    if not path.is_file():
        raise GateError(f"MISSING_FILE: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError) as error:
        raise GateError(f"INVALID_JSON: {path}: {error}") from error
    if not isinstance(value, dict):
        raise GateError(f"NOT_OBJECT: {path}")
    return value


def required_int(value, path: str) -> int:
    if type(value) is not int or value < 0:
        raise GateError(f"INVALID_COUNTER: {path} = {value!r}")
    return value


def compare(reference: dict, candidate: dict) -> list[str]:
    failures: list[str] = []
    for case in CASES:
        old, new = reference.get(case), candidate.get(case)
        if not isinstance(old, dict) or not isinstance(new, dict):
            failures.append(f"CASE_MISSING: {case}")
            continue
        old_keys, new_keys = set(old), set(new)
        if old_keys != new_keys:
            failures.append(f"ROOT_SET_CHANGED: {case}: added={sorted(new_keys-old_keys)} removed={sorted(old_keys-new_keys)}")
        for name in sorted(old_keys & new_keys):
            a, b = old[name], new[name]
            for field in ("tier1_nodes", "tier2_nodes"):
                before = required_int(a.get(field), f"baseline/{case}/{field}")
                after = required_int(b.get(field), f"candidate/{case}/{field}")
                if after > before:
                    failures.append(f"NODE_REGRESSION: {case} {name} {field} {before}->{after}")
            before_states, after_states = a.get("dsl_static"), b.get("dsl_static")
            if (not isinstance(before_states, dict) or not isinstance(after_states, dict)
                    or set(before_states) != set(after_states)):
                failures.append(f"DSL_STATE_COVERAGE_CHANGED: {case} {name}")
                continue
            for state in sorted(before_states):
                before = required_int(before_states[state], f"baseline/{case}/DSL/{state}")
                after = required_int(after_states[state], f"candidate/{case}/DSL/{state}")
                if after > before:
                    failures.append(f"DSL_REGRESSION: {case} {name} {state} {before}->{after}")
    return failures


# The imported reference is authoritative for historical root IDs. For a case
# with exactly one guest root, unique identity is established by case+host+
# frozen graph fingerprint; the old source hash need not match a separately
# copied hardcoded identifier. For multi-root cases, the explicit historical
# source-order map is still required to avoid ambiguous root swaps.
HISTORICAL_BASELINE_TIER_COUNTS = {
    "bubble-sort": ((239, 496), ((531, 376),)),
    "factorial": ((239, 421), ((174, 336), (143, 281))),
    "fibonacci": ((239, 1129), ((207, 1199), (143, 989))),
    "integer-loop": ((239, 212), ((183, 75),)),
    "method-call": ((239, 235), ((106, 49), (235, 94))),
    "primitive-closure-call": ((239, 144), ((105, 48), (149, 44))),
    "primitive-if-false": ((239, 144), ((104, 47),)),
    "primitive-if-true": ((239, 144), ((104, 47),)),
    "primitive-integer-add": ((239, 144), ((104, 47),)),
    "primitive-local-read": ((239, 144), ((104, 47),)),
    "primitive-local-write": ((239, 144), ((104, 47),)),
    "primitive-method-call": ((239, 162), ((149, 60), (106, 49))),
    "primitive-object-slot-read": ((239, 165), ((124, 67),)),
    "primitive-object-slot-write": ((239, 223), ((151, 92),)),
    "primitive-return-literal": ((239, 144), ((104, 47),)),
    "primitive-while-counted": ((239, 207), ((177, 70),)),
    "primitive-while-once": ((239, 206), ((177, 69),)),
    "primitive-while-zero": ((239, 144), ((104, 47),)),
}
CHECKPOINT_BASELINE_TIER_COUNTS = {
    "bubble-sort": ((244, 500), ((532, 377),)),
    "factorial": ((244, 531), ((172, 394), (208, 440))),
    "fibonacci": ((244, 1575), ((172, 1438), (270, 1654))),
    "integer-loop": ((244, 217), ((184, 76),)),
    "method-call": ((244, 311), ((107, 50), (302, 174))),
    "primitive-closure-call": ((244, 240), ((106, 49), (181, 103))),
    "primitive-if-false": ((244, 170), ((105, 48),)),
    "primitive-if-true": ((244, 170), ((105, 48),)),
    "primitive-integer-add": ((244, 170), ((105, 48),)),
    "primitive-local-read": ((244, 170), ((105, 48),)),
    "primitive-local-write": ((244, 170), ((105, 48),)),
    "primitive-method-call": ((244, 262), ((107, 50), (201, 125))),
    "primitive-object-slot-read": ((244, 190), ((125, 68),)),
    "primitive-object-slot-write": ((244, 228), ((152, 93),)),
    "primitive-return-literal": ((244, 170), ((105, 48),)),
    "primitive-while-counted": ((244, 212), ((178, 71),)),
    "primitive-while-once": ((244, 211), ((178, 70),)),
    "primitive-while-zero": ((244, 170), ((105, 48),)),
}
# Frozen per-revision graph signatures. A baseline file is accepted only when
# its declared revision is listed here and every count matches exactly.
BASELINE_TIER_COUNTS = {
    BASELINE_REVISION: CHECKPOINT_BASELINE_TIER_COUNTS,
    HISTORICAL_BASELINE_REVISION: HISTORICAL_BASELINE_TIER_COUNTS,
}


def historical_guest_labels(case: str, entries: dict) -> tuple[str, ...]:
    """Resolve full original source-backed root names, not truncated selectors.

    The old graph report stores `protos-root:hash[CLOSURE|uri|start+length]`;
    a printed short-name table intentionally hid the bracketed suffix.
    The original baseline is immutable and provenance-checked at import.
    We pair roots by exact, unique source order, not graph size or execution.
    """
    if HOST not in entries:
        raise GateError(
            f"BASELINE_HOST_ROOT_MISSING: {case}: keys={sorted(entries)}"
        )
    guests = []
    for name in entries:
        if name == HOST:
            continue
        match = ROOT_RE.fullmatch(name)
        if match is None or match["kind"] != "CLOSURE":
            raise GateError(
                f"BASELINE_GUEST_ROOT_LABEL_INVALID: {case}: {name!r}"
            )
        if not match["uri"].endswith("/" + case + ".protos"):
            raise GateError(
                f"BASELINE_GUEST_ROOT_SOURCE_MISMATCH: {case}: {name!r}"
            )
        guests.append((int(match["start"]), int(match["length"]), name))
    prescribed = GUEST_IDS_IN_SOURCE_ORDER[case]
    if len(guests) != len(prescribed):
        raise GateError(
            f"BASELINE_GUEST_ROOT_COUNT_MISMATCH: {case}: "
            f"expected={len(prescribed)} actual={len(guests)} labels={[x[2] for x in guests]}"
        )
    guests.sort()
    if len({(start, length) for start, length, _ in guests}) != len(guests):
        raise GateError(f"BASELINE_GUEST_ROOT_SPAN_COLLISION: {case}")
    actual_ids = tuple(name.split("[", 1)[0][len("protos-root:"):] for _, _, name in guests)
    if actual_ids != prescribed:
        raise GateError(
            f"BASELINE_GUEST_SOURCE_ORDER_MISMATCH: {case}: "
            f"expected_ids={prescribed} observed_ids={actual_ids} "
            f"observed_labels={[x[2] for x in guests]}"
        )
    return tuple(name for _, _, name in guests)


def read_baseline(path: Path) -> dict:
    b = read_json(path)
    signatures = BASELINE_TIER_COUNTS.get(b.get("baseline_protos_revision"))
    if (b.get("schema") != 1 or signatures is None
            or b.get("expected_graalvm_ce") != GRAAL_VERSION
            or b.get("workload_count") != len(CASES) or b.get("root_count") != 41):
        raise GateError("BASELINE_METADATA_INVALID")
    roots = b.get("roots")
    if not isinstance(roots, dict) or set(roots) != set(CASES):
        raise GateError("BASELINE_CASES_INVALID")
    total = 0
    for case, entries in roots.items():
        if not isinstance(entries, dict):
            raise GateError(f"BASELINE_CASE_ROOTS_NOT_OBJECT: {case}")
        historical_guest_labels(case, entries)
        total += len(entries)
        for name, item in entries.items():
            if not isinstance(item, dict):
                raise GateError(f"BASELINE_ROOT_NOT_OBJECT: {case}/{name}")
            required_int(item.get("tier1_nodes"), f"baseline/{case}/{name}/T1")
            required_int(item.get("tier2_nodes"), f"baseline/{case}/{name}/T2")
            states = item.get("dsl_static")
            if not isinstance(states, dict):
                raise GateError(f"BASELINE_DSL_INVALID: {case}/{name}")
            if name == HOST and states:
                raise GateError(f"BASELINE_HOST_DSL_UNEXPECTED: {case}")
            if name != HOST and not states:
                raise GateError(f"BASELINE_DSL_MISSING: {case}/{name}")
            for state, count in states.items():
                if state not in ("UNCACHED", "CACHED"):
                    raise GateError(f"BASELINE_STATE_INVALID: {case}/{name}/{state}")
                required_int(count, f"baseline/{case}/{name}/DSL/{state}")
        host_pair, expected_guests = signatures[case]
        observed_host = (entries[HOST]["tier1_nodes"], entries[HOST]["tier2_nodes"])
        observed_guests = sorted(
            (v["tier1_nodes"], v["tier2_nodes"])
            for name, v in entries.items() if name != HOST
        )
        if observed_host != host_pair or observed_guests != sorted(expected_guests):
            raise GateError(
                f"BASELINE_GRAPH_SIGNATURE_MISMATCH: {case}: "
                f"expected_host={host_pair} actual_host={observed_host} "
                f"expected_guest={sorted(expected_guests)} actual_guest={observed_guests}"
            )
    if total != 41:
        raise GateError("BASELINE_ROOT_COUNT_INVALID")
    return b

def verify_sources() -> None:
    if set(FIXTURE_SHA256) != set(CASES):
        raise GateError("FIXTURE_CATALOG_INVALID")
    for case in CASES:
        path = CASES_DIR / (case + ".protos")
        if not path.is_file():
            raise GateError(f"FIXTURE_MISSING: {case}")
        actual = hashlib.sha256(path.read_bytes()).hexdigest()
        if actual != FIXTURE_SHA256[case]:
            raise GateError(f"FIXTURE_MUTATED: {case}")
    if set(EXPECTED_RESULTS) != set(CASES) or set(GUEST_IDS_IN_SOURCE_ORDER) != set(CASES):
        raise GateError("CASE_METADATA_INVALID")


def match_roots(case: str, observed: list[str],
                expected_ids: tuple[str, ...] | None = None) -> dict[str, str]:
    """Same-JVM diagnostic identities -> historical identities via source order.

    The source is a frozen fixture with an explicit synthetic URI. Each guest
    root must be CLOSURE, unique by span, and in that source. This is not a
    nearest-node-count matching heuristic.
    """
    unique = set(observed)
    if len(unique) != len(observed):
        raise GateError(f"DUPLICATE_ROOT_LABEL: {case}")
    guest = []
    framework = []
    for label in observed:
        if label == HOST:
            framework.append(label)
            continue
        m = ROOT_RE.fullmatch(label)
        if m is None:
            raise GateError(f"UNKNOWN_COMPILED_ROOT: {case}: {label}")
        if m["kind"] != "CLOSURE" or m["uri"] != f"i115:/cases/{case}.protos":
            raise GateError(f"GUEST_ROOT_SOURCE_CHANGED: {case}: {label}")
        start, length = int(m["start"]), int(m["length"])
        if length < 1 or start < 0:
            raise GateError(f"ROOT_SPAN_INVALID: {case}: {label}")
        if start + length > len((CASES_DIR / (case + ".protos")).read_text(encoding="utf-8")):
            raise GateError(f"ROOT_SPAN_OUT_OF_BOUNDS: {case}: {label}")
        guest.append((start, length, label))
    expected = (tuple("protos-root:" + key for key in GUEST_IDS_IN_SOURCE_ORDER[case])
                if expected_ids is None else expected_ids)
    if len(framework) != 1 or len(guest) != len(expected):
        raise GateError(f"COMPILED_ROOT_COVERAGE_CHANGED: {case}: host={len(framework)} guest={len(guest)} expected={len(expected)}")
    guest.sort()
    if len({(start, length) for start, length, _ in guest}) != len(guest):
        raise GateError(f"GUEST_ROOT_SPAN_COLLISION: {case}")
    result = {HOST: HOST}
    for (_, _, label), original in zip(guest, expected):
        result[label] = original
    return result


def parse_trace(case: str, raw: str, requested_tier: int,
                expected_ids: tuple[str, ...] | None = None) -> tuple[dict[str, int], dict]:
    """Read After-TruffleTier node counts from genuine opt done IR fields.

    Compilation failure, duplicate compilation, wrong tier or missing metrics
    are errors. The scope is exactly the compiled root inventory.
    """
    if any(line.startswith("[engine] opt failed") or line.startswith("[engine] opt inval.")
           for line in raw.splitlines()):
        raise GateError(f"COMPILATION_FAILED_OR_INVALIDATED: {case}/T{requested_tier}")
    compiled = []
    for line in raw.splitlines():
        if not line.startswith("[engine] opt done"):
            continue
        m = COMP_RE.match(line)
        tier = TIER_RE.search(line)
        ir = IR_RE.search(line)
        cid = COMP_ID_RE.search(line)
        if m is None or tier is None or ir is None or cid is None:
            raise GateError(f"COMPILATION_TRACE_UNPARSEABLE: {case}: {line[:240]}")
        if int(tier["tier"]) != requested_tier:
            # Last-tier captures can naturally observe first-tier compilations;
            # only the requested tier is comparable to the baseline.
            continue
        compiled.append((m["label"].strip(), int(ir["after"]), int(cid["id"])))
    mapping = match_roots(case, [label for label, _, _ in compiled], expected_ids)
    values = {mapping[label]: nodes for label, nodes, _ in compiled}
    if len(values) != len(compiled):
        raise GateError(f"COMPILATION_DUPLICATED: {case}/T{requested_tier}")
    provenance = {mapping[label]: {"observed_root": label, "comp_id": compid,
                                   "ir_after_truffle_tier": nodes}
                  for label, nodes, compid in compiled}
    return values, provenance


def parse_bytecode_sizes(case: str, raw: str,
                         expected_ids: tuple[str, ...] | None = None) -> tuple[dict[str, dict], dict]:
    snapshots: dict[str, dict[str, set[int]]] = {}
    for line in raw.splitlines():
        if not line.startswith(SIZE_PREFIX):
            continue
        fields = line[len(SIZE_PREFIX):].split("\t")
        if len(fields) != 3 or fields[1] not in ("UNCACHED", "CACHED"):
            raise GateError(f"DSL_DIAGNOSTIC_MALFORMED: {case}: {line[:180]}")
        try:
            label = base64.b64decode(fields[0], validate=True).decode("utf-8")
            count = int(fields[2])
        except (ValueError, UnicodeError) as error:
            raise GateError(f"DSL_DIAGNOSTIC_INVALID: {case}") from error
        if count < 1:
            raise GateError(f"DSL_DIAGNOSTIC_EMPTY: {case}")
        snapshots.setdefault(label, {}).setdefault(fields[1], set()).add(count)
    if not snapshots:
        raise GateError(f"DSL_INSTRUMENT_NO_SNAPSHOTS: {case}")
    # Diagnostic execution also observes the module TOP_LEVEL, which is not
    # among the 41 compiled roots in the imported reference. Keep that evidence
    # separate: never misattribute its instructions to a Closure root.
    # Core roots have other source URIs and are outside this frozen workload.
    selected = []
    top_level = []
    for label in snapshots:
        if f"|i115:/cases/{case}.protos|" not in label:
            continue
        match = ROOT_RE.fullmatch(label)
        if match is None:
            raise GateError(f"DSL_WORKLOAD_ROOT_LABEL_INVALID: {case}: {label}")
        if match["kind"] == "TOP_LEVEL":
            top_level.append(label)
        else:
            selected.append(label)
    if len(top_level) != 1:
        raise GateError(f"DSL_TOP_LEVEL_COVERAGE_INVALID: {case}: {top_level}")
    mapping = match_guest_only(case, selected, expected_ids)
    counts = {}
    prov = {}
    for label, original in mapping.items():
        counts[original] = {}
        prov[original] = {"observed_root": label, "states": {}}
        for state, values in snapshots[label].items():
            if len(values) != 1:
                raise GateError(f"DSL_SNAPSHOTS_VARIABLE: {case}: {label}/{state}: {sorted(values)}")
            count = next(iter(values))
            counts[original][state] = count
            prov[original]["states"][state] = count
    prov["__uncompiled_top_level__"] = {"observed_root": top_level[0],
                                        "states": {state: sorted(values)
                                                   for state, values in snapshots[top_level[0]].items()}}
    return counts, prov


def match_guest_only(case: str, labels: list[str],
                     expected_ids: tuple[str, ...] | None = None) -> dict[str, str]:
    if len(set(labels)) != len(labels):
        raise GateError(f"DSL_ROOT_COLLISION: {case}")
    parsed = []
    for label in labels:
        m = ROOT_RE.fullmatch(label)
        if m is None or m["kind"] != "CLOSURE" or m["uri"] != f"i115:/cases/{case}.protos":
            raise GateError(f"DSL_UNEXPECTED_GUEST_ROOT: {case}: {label}")
        parsed.append((int(m["start"]), int(m["length"]), label))
    expected = (tuple("protos-root:" + key for key in GUEST_IDS_IN_SOURCE_ORDER[case])
                if expected_ids is None else expected_ids)
    if len(parsed) != len(expected):
        raise GateError(f"DSL_ROOT_COVERAGE_CHANGED: {case}: {len(parsed)} != {len(expected)}")
    parsed.sort()
    return {label: original for (_, _, label), original in zip(parsed, expected)}


def git_data() -> dict:
    def run(*args):
        p = subprocess.run(["git", *args], cwd=ROOT, text=True,
                           capture_output=True, check=False)
        if p.returncode:
            raise GateError("GIT_PROVENANCE_UNAVAILABLE: " + p.stderr.strip())
        return p.stdout
    head = run("rev-parse", "HEAD").strip()
    state = run("status", "--porcelain", "--untracked-files=all")
    diff = run("diff", "HEAD", "--binary")
    untracked = []
    for line in state.splitlines():
        if line.startswith("?? "):
            path = ROOT / line[3:]
            if not path.is_file():
                raise GateError(f"UNTRACKED_NONFILE: {path}")
            untracked.append({"path": line[3:], "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    return {"head": head, "clean": not state.strip(),
            "status": state, "tracked_diff_sha256": hashlib.sha256(diff.encode()).hexdigest(),
            "untracked": untracked}


def check_runtime_version() -> None:
    tree = ET.parse(ROOT / "pom.xml")
    ns = {"p": "http://maven.apache.org/POM/4.0.0"}
    version = tree.findtext("p:properties/p:graalvm.version", namespaces=ns)
    if version != GRAAL_VERSION:
        raise GateError(f"GRAAL_VERSION_MISMATCH: expected={GRAAL_VERSION} got={version}")


def verify_runtime_plane(runtime: Path) -> dict[str, Path]:
    """Verify Maven's canonical groupId-prefixed runtime-plane artifacts.

    The root POM has maven-dependency-plugin <prependGroupId>true</...> and
    <stripVersion>false</...>, so a Truffle jar is named
    org.graalvm.truffle.<artifact>-<version>.jar, NOT <artifact>-<version>.jar.
    Fail closed on absent, duplicate or different-version copies, including
    stale unprefixed jars left by a different packaging convention.
    """
    if not runtime.is_dir():
        raise GateError(f"RUNTIME_PLANE_MISSING: {runtime}")
    found: dict[str, Path] = {}
    for artifact in ("truffle-runtime", "truffle-compiler", "truffle-api"):
        expected = runtime / f"org.graalvm.truffle.{artifact}-{GRAAL_VERSION}.jar"
        matches = sorted({*runtime.glob(f"org.graalvm.truffle.{artifact}-*.jar"),
                          *runtime.glob(f"{artifact}-*.jar")})
        if not expected.is_file():
            raise GateError(
                f"RUNTIME_JAR_VERSION_MISMATCH: {artifact}/{GRAAL_VERSION}: "
                f"expected={expected.name} found={[p.name for p in matches]}"
            )
        if matches != [expected]:
            raise GateError(
                f"RUNTIME_JAR_AMBIGUOUS: {artifact}: found={[p.name for p in matches]}"
            )
        found[artifact] = expected
    return found


def compile_gate_java(out: Path) -> tuple[str, str, str]:
    sys.path.insert(0, str(ROOT / "tools"))
    from truffle_jvm_launch import CheckError, packaged_classpath, select_java
    try:
        cp = packaged_classpath(ROOT)
    except CheckError as error:
        raise GateError(f"PRODUCT_NOT_PACKAGED: {error}") from error
    verify_runtime_plane(ROOT / "target/runtime")
    java = select_java(None)
    driver_classes = out / "driver-classes"
    instrument_classes = out / "instrument-classes"
    driver_classes.mkdir(parents=True)
    instrument_classes.mkdir(parents=True)
    driver = JAVA_DIR / "I115GateSurface.java"
    instrument = JAVA_DIR / "I115BytecodeSizeInstrument.java"
    if not driver.is_file() or not instrument.is_file():
        raise GateError("I115_JAVA_PROBE_MISSING")
    execute(["javac", "--release", "25", "-proc:none", "-cp", cp,
             "-d", str(driver_classes), str(driver)], ROOT, out / "javac-driver.log")
    version = GRAAL_VERSION
    processor = Path.home() / ".m2/repository/org/graalvm/truffle/truffle-dsl-processor" / version / f"truffle-dsl-processor-{version}.jar"
    if not processor.is_file():
        raise GateError(f"TRUFFLE_DSL_PROCESSOR_MISSING: {processor}")
    execute(["javac", "--release", "25", "-proc:full", "-cp", cp,
             "-processorpath", str(processor) + os.pathsep + cp,
             "-d", str(instrument_classes), str(instrument)], ROOT, out / "javac-instrument.log")
    service = (instrument_classes / "META-INF/services/com.oracle.truffle.api.instrumentation.provider.TruffleInstrumentProvider")
    if not service.is_file():
        raise GateError("I115_INSTRUMENT_SERVICE_MISSING")
    version_command = subprocess.run([java, "-version"], cwd=ROOT,
                                     stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                     text=True, check=False)
    if version_command.returncode != 0 or not re.search(
            r'\b(?:openjdk|java) version "25(?:[.+\-]|")', version_command.stdout,
            re.IGNORECASE):
        raise GateError("JDK_25_REQUIRED: " + version_command.stdout.strip()[:250])
    runner_cp = str(driver_classes) + os.pathsep + cp
    diagnostic_cp = str(instrument_classes) + os.pathsep + runner_cp
    return java, runner_cp, diagnostic_cp


def execute(command: list[str], cwd: Path, log: Path | None = None,
            timeout: int = 3600) -> str:
    print("I115_EXEC=", " ".join(command[:5]) + (" ..." if len(command) > 5 else ""), flush=True)
    if log is None:
        completed = subprocess.run(command, cwd=cwd, check=False, timeout=timeout)
        if completed.returncode:
            raise GateError(f"COMMAND_FAILED({completed.returncode}): {' '.join(command[:5])}")
        return ""
    log.parent.mkdir(parents=True, exist_ok=True)
    with log.open("wb") as stream:
        completed = subprocess.run(command, cwd=cwd, stdout=stream,
                                   stderr=subprocess.STDOUT, timeout=timeout,
                                   check=False, env=dict(os.environ, PROTOS_HOME=str(ROOT)))
    if completed.returncode:
        raise GateError(f"COMMAND_FAILED({completed.returncode}): {' '.join(command[:5])}: log={log}")
    return log.read_text(encoding="utf-8", errors="replace")


def run_jvm(java: str, cp: str, case: str, mode: str, out: Path,
            tier: int | None = None) -> str:
    file = CASES_DIR / f"{case}.protos"
    core = ROOT / "protos/lib/core"
    cmd = [java, "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow",
           "-Dpolyglot.engine.AllowExperimentalOptions=true",
           "-Dprotos.diagnostic.stableRootIdentity=true"]
    if mode == "dsl":
        cmd += ["-Dpolyglot.engine.BytecodeHistogram=language,root",
                "-Dpolyglot.i115-bytecode-size=true"]
        # The original diagnostic did one cold call plus 100 sample calls.
        calls = 101
    elif mode in ("tier1", "tier2"):
        if tier not in (1, 2):
            raise GateError("INVALID_REQUESTED_TIER")
        dumps = out / "graphs"
        dumps.mkdir(parents=True, exist_ok=False)
        cmd += [
            "-Dpolyglot.engine.MultiTier=true",
            "-Dpolyglot.engine.DynamicCompilationThresholds=false",
            "-Dpolyglot.engine.FirstTierMinInvokeThreshold=1",
            "-Dpolyglot.engine.MinInvokeThreshold=1",
            "-Dpolyglot.engine.OSR=false",
            "-Dpolyglot.engine.FirstTierCompilationThreshold=100",
            f"-Dpolyglot.engine.LastTierCompilationThreshold={1000000 if tier == 1 else 1000}",
            "-Dpolyglot.engine.TraceCompilation=true",
            "-Dpolyglot.engine.BackgroundCompilation=false",
            "-Dpolyglot.compiler.TraceNodeExpansion=truffleTier",
            "-Dpolyglot.compiler.TraceInlining=true",
            "-Djdk.graal.Dump=Truffle:1",
            f"-Djdk.graal.DumpPath={dumps}",
        ]
        calls = 2001
    else:
        raise GateError("INVALID_CAPTURE_MODE")
    cmd.extend(["-cp", cp, "com.guillermomolina.protos.gate.I115GateSurface",
                str(core), str(file), case, EXPECTED_RESULTS[case], str(calls)])
    log = out / "jvm.log"
    raw = execute(cmd, ROOT, log)
    if f"result={EXPECTED_RESULTS[case]}" not in raw.splitlines() or f"calls={calls}" not in raw.splitlines():
        raise GateError(f"GUEST_CORRECTNESS_UNVERIFIED: {case}/{mode}: {log}")
    if tier is not None and not any(path.stat().st_size > 0 for path in (out / "graphs").rglob("*.bgv")):
        raise GateError(f"GRAPH_BGV_MISSING: {case}/T{tier}: {out}")
    return raw


def build_product_for_gate() -> None:
    """Package production code without compiling tests as part of the build.

    ``make build`` normally uses -DskipTests, which still compiles every test.
    The probe is structural-only, while the full gate invokes ``make test``
    separately and must continue to fail when tests do not compile or pass.
    """
    execute(["make", "build", "MVN_FLAGS=-Dmaven.test.skip=true"], ROOT)


def run_command(args: argparse.Namespace) -> None:
    baseline = read_baseline(args.baseline)
    verify_sources()
    check_runtime_version()
    run_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    out = args.output.resolve() if args.output else ROOT / "target/i115-semantic-performance-gate" / run_id
    if out.exists():
        raise GateError(f"OUTPUT_ALREADY_EXISTS: {out}")
    out.mkdir(parents=True)
    report: dict = {
        "schema": 2, "status": "STARTED", "baseline_revision": baseline["baseline_protos_revision"],
        "runtime_graalvm": GRAAL_VERSION, "provenance": git_data(),
        "measurement_method": "local Protos Polyglot Value.execute; IR after TruffleTier; BytecodeRootNode instructions",
        "semantic_gate": "NOT_RUN", "cases": {},
    }
    try:
        build_product_for_gate()
        if args.action == "run":
            # Only the full gate has semantic authority. Never use the probe's
            # build flag to bypass test compilation in this separate phase.
            try:
                execute(["make", "test"], ROOT)
                execute(["make", "check"], ROOT)
            except GateError:
                report["semantic_gate"] = "FAIL"
                print("I115_SEMANTIC_GATE=FAIL", flush=True)
                raise
            report["semantic_gate"] = "PASS"
        else:
            # Explicit structural-only calibration never earns a full gate PASS.
            print("I115_SEMANTIC_GATE=NOT_RUN", flush=True)
        java, runtime_cp, diagnostic_cp = compile_gate_java(out)
        measured: dict = {}
        for n, case in enumerate(CASES, start=1):
            print(f"I115_CASE={n}/{len(CASES)} {case}", flush=True)
            dsl_dir = out / case / "dsl"
            t1_dir = out / case / "tier1"
            t2_dir = out / case / "tier2"
            for dest in (dsl_dir, t1_dir, t2_dir):
                dest.mkdir(parents=True)
            expected_ids = historical_guest_labels(case, baseline["roots"][case])
            dsl, dsl_prov = parse_bytecode_sizes(case, run_jvm(java, diagnostic_cp, case, "dsl", dsl_dir), expected_ids)
            t1, t1_prov = parse_trace(case, run_jvm(java, runtime_cp, case, "tier1", t1_dir, 1), 1, expected_ids)
            t2, t2_prov = parse_trace(case, run_jvm(java, runtime_cp, case, "tier2", t2_dir, 2), 2, expected_ids)
            if set(t1) != set(t2) or set(t1) != set(baseline["roots"][case]):
                raise GateError(f"ROOT_INVENTORY_MISMATCH: {case}")
            # Source identities must refer to the same semantic spans in both
            # JIT tiers and diagnostic JVM. No ordinal-only mismatched root may pass.
            for root in t1:
                if t1_prov[root]["observed_root"] != t2_prov[root]["observed_root"]:
                    raise GateError(f"CROSS_TIER_ROOT_IDENTITY_CHANGED: {case}/{root}")
                if root != HOST and (root not in dsl_prov or
                                     t1_prov[root]["observed_root"] != dsl_prov[root]["observed_root"]):
                    raise GateError(f"CROSS_DSL_ROOT_IDENTITY_CHANGED: {case}/{root}")
            measured[case] = {root: {"tier1_nodes": t1[root], "tier2_nodes": t2[root],
                                      "dsl_static": dsl.get(root, {})} for root in t1}
            report["cases"][case] = {"status": "CAPTURED", "roots": measured[case],
                                      "observations": {"tier1": t1_prov, "tier2": t2_prov,
                                                       "dsl": dsl_prov}}
            (out / case / "case-report.json").write_text(
                json.dumps(report["cases"][case], indent=2, sort_keys=True) + "\n",
                encoding="utf-8")
        failures = compare(baseline["roots"], measured)
        report["failures"] = failures
        report["captured_roots"] = sum(len(entries) for entries in measured.values())
        if failures:
            for failure in failures:
                print("I115_GATE_FAILURE=", failure, flush=True)
            raise GateError(f"STRUCTURAL_REGRESSION: {len(failures)}")
        print(f"I115_CAPTURES={len(CASES) * 3}", flush=True)
        print(f"I115_COMPILED_ROOT_PAIRS={report['captured_roots']}", flush=True)
        if args.action == "run":
            report["status"] = "PASS"
            print("I115_SEMANTIC_GATE=PASS", flush=True)
            print("I115_STRUCTURAL_GATE=PASS", flush=True)
            print("I115_GATE=PASS", flush=True)
        else:
            report["status"] = "STRUCTURAL_ONLY_PASS"
            print("I115_STRUCTURAL_GATE=PASS", flush=True)
            print("I115_GATE=NOT_RUN_SEMANTIC", flush=True)
    except (GateError, OSError, ValueError, KeyError, TypeError, subprocess.TimeoutExpired) as error:
        report["status"] = "FAIL"
        report["error"] = str(error)
        raise
    finally:
        # `make build` includes Maven `clean`: it can delete an output
        # directory below target/, even when the build fails. Recreate the
        # report parent so the original failure remains visible and durable.
        out.mkdir(parents=True, exist_ok=True)
        (out / "gate-report.json").write_text(
            json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(f"I115_GATE_REPORT={out / 'gate-report.json'}", flush=True)


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("action", choices=("run", "probe", "baseline", "compare"))
    p.add_argument("--baseline", type=Path, default=BASELINE)
    p.add_argument("--output", type=Path)
    p.add_argument("--candidate", type=Path)
    args = p.parse_args()
    try:
        if args.action == "baseline":
            baseline = read_baseline(args.baseline)
            print("I115_BASELINE=AVAILABLE")
            print(f"I115_BASELINE_ROOTS={baseline['root_count']}")
        elif args.action in ("run", "probe"):
            run_command(args)
        else:
            if args.candidate is None:
                raise GateError("COMPARE_REQUIRES_CANDIDATE")
            candidate = read_json(args.candidate)
            if candidate.get("schema") != 2 or not isinstance(candidate.get("cases"), dict):
                raise GateError("CANDIDATE_FORMAT_INVALID: expect local gate-report.json")
            roots = {case: item["roots"] for case, item in candidate["cases"].items()}
            failures = compare(read_baseline(args.baseline)["roots"], roots)
            for failure in failures:
                print("I115_GATE_FAILURE=", failure)
            print("I115_STRUCTURAL_GATE=" + ("FAIL" if failures else "PASS"))
            if failures:
                return 1
    except (GateError, OSError, ValueError, KeyError, TypeError, subprocess.TimeoutExpired) as error:
        print("I115_GATE=FAIL: " + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
