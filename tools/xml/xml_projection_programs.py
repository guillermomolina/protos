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


"""Deterministic suite-native XML projection; never executes Protos.

Fixture decoding and classification belong to xml_test_projection. Local source
registration edits only exact owned manifest rows, leaving all other bytes intact.
"""

import hashlib
import json
from pathlib import Path
import re

EXECUTABLE = {"SUPPORTED_ACCEPT": "accept", "SUPPORTED_REJECT": "reject",
              "PROFILE_REJECT": "reject"}
PREFIXES = {"SUPPORTED_ACCEPT": "accept", "SUPPORTED_REJECT": "reject",
            "PROFILE_REJECT": "profile-reject"}
MAX_TESTS = 64
SUITE_AUXILIARIES = frozenset((
    "suite/Harness.protos",
    "suite/Fixtures.protos",
))
OWNED = {"INVENTORY.json", "CASES.tsv", "SHA256SUMS", "EXECUTION.json"}
MANIFEST_PREFIX = "library/xml/official/"
SUITE_NAME = re.compile(r"suite/(?:accept|reject|profile-reject)-[0-9]{2,}\.protos\Z")
GENERATED_HEADER = b"// GENERATED local fixture projection; redistribution pending.\n"
LEGACY_SUPPORT = {"programs/support/xml/Fixtures.protos": "../Fixtures.protos",
                  "programs/support/xml/official/Harness.protos": "Harness.protos"}


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=True, indent=2, sort_keys=True) + "\n").encode("ascii")


def protos_escape(character):
    # Same scalar escapes as the established TOML projection.
    if character == "\\":
        return "\\\\"
    if character == '"':
        return '\\"'
    if character == "\n":
        return "\\n"
    if character == "\r":
        return "\\r"
    if character == "\t":
        return "\\t"
    code = ord(character)
    if 0xD800 <= code <= 0xDFFF:
        raise ValueError("a Protos String cannot contain a surrogate")
    if code < 0x20 or code >= 0x7F:
        return "\\u{%X}" % code
    return character


def literal(text):
    """Bound escaped literal width and use a balanced concatenation tree."""
    pieces, current, width = [], [], 0
    for character in text:
        escaped = protos_escape(character)
        if current and width + len(escaped) > 1024:
            pieces.append('"' + "".join(current) + '"')
            current, width = [], 0
        current.append(escaped)
        width += len(escaped)
    if current or not pieces:
        pieces.append('"' + "".join(current) + '"')
    def balanced(begin, end):
        if end - begin == 1:
            return pieces[begin]
        middle = (begin + end) // 2
        return "(" + balanced(begin, middle) + ").concat(" + balanced(middle, end) + ")"
    return balanced(0, len(pieces))


def partition_chunks(text):
    """Python indexes Unicode scalars; bounded pieces reconstruct exact input.

    Small inputs use individual scalar feeds. Larger inputs use 256 scalars per
    piece, keeping fixture content once in the generated Test. The harness
    also runs one whole feed and admits empty feeds at both ends of each run.
    """
    step = 1 if len(text) <= 256 else 256
    return [text[i:i + step] for i in range(0, len(text), step)]


def suite_source(cases, text_for):
    lines = [GENERATED_HEADER.decode("ascii").rstrip(),
             'H: import("./Harness.protos")', 'Test: import("std:test/test")',
             '', 'tests: [']
    entries = []
    for case in cases:
        chunks = partition_chunks(text_for(case))
        body = ['    Test(%s, () => {' % literal(case["identity"]),
                '        chunks: Map()']
        for index, chunk in enumerate(chunks):
            body.append('        chunks[%d] = %s' % (index, literal(chunk)))
        body.extend(['        source: H.join(chunks, 0, %d)' % len(chunks),
                     '        H.run(%s, %s, source, chunks, %d)' % (
                         literal(case["identity"]), literal(case["expected"]), len(chunks)),
                     '    })'])
        entries.append("\n".join(body))
    lines.extend([",\n".join(entries), ']', 'tests.freeze()'])
    return ("\n".join(lines) + "\n").encode("ascii")


def manifest_lines(outputs):
    return [MANIFEST_PREFIX + name + "\tsuite-native\t-"
            for name in outputs if SUITE_NAME.fullmatch(name)]


def execution_artifacts(report, text_for, harness=None, fixtures=None):
    if report.get("inventory_failures"):
        raise ValueError("cannot project unresolved inventory references or identities")
    result, records, seen = {}, [], set()
    groups = {category: [] for category in EXECUTABLE}
    for case in report["cases"]:
        identity, category = case["identity"], case["classification"]
        if identity in seen or case.get("inventory_problem"):
            raise ValueError("unresolved or duplicate executable identity: " + identity)
        seen.add(identity)
        if case["expected"] != EXECUTABLE.get(category):
            raise ValueError("contradictory executable expectation: " + identity)
        record = {key: case.get(key) for key in (
            "identity", "input_member", "input_archive_member", "input_sha256",
            "original_input_target", "reconciliation", "decoding", "classification", "expected")}
        record.update(suite_source_path=None, suite_source_sha256=None, test_name=None,
                      status="NOT_EXECUTED", nonexecution_reason=case["reason"])
        records.append(record)
        if category in groups:
            groups[category].append((case, record))
    for category, members in groups.items():
        for begin in range(0, len(members), MAX_TESTS):
            batch = members[begin:begin + MAX_TESTS]
            name = "suite/%s-%02d.protos" % (PREFIXES[category], begin // MAX_TESTS)
            data = suite_source([case for case, record in batch], text_for)
            result[name] = data
            for case, record in batch:
                record.update(suite_source_path=MANIFEST_PREFIX + name,
                              suite_source_sha256=sha256(data), test_name=case["identity"],
                              nonexecution_reason=None)
    result["EXECUTION.json"] = json_bytes({
        "schema_version": 2, "cases": records,
        "harness_sha256": sha256(harness) if harness is not None else None,
        "fixtures_sha256": sha256(fixtures) if fixtures is not None else None,
        "executable_acceptance": "NOT_EXECUTED",
        "complete_acceptance_claim_permitted": False,
        "partition_policy": "whole feed; empty boundary feeds; scalar feeds up to 256 scalars, otherwise 256-scalar chunks"})
    return result


def target_path(work, name):
    if SUITE_NAME.fullmatch(name):
        return Path(work).parent / name
    if name in OWNED:
        return Path(work) / name
    raise ValueError("artifact is outside projection ownership: " + name)


def safe_path(path):
    if path.is_symlink() or any(parent.is_symlink() for parent in path.parents):
        raise ValueError("refusing symlink: " + str(path))


def local_manifest(data, names, remove=False):
    """Exact row surgery, not a second manifest parser.

    Actual TAB-separated rows are canonical; literal backslash-t rows are
    malformed conflicts. Unrelated rows (including line endings) remain byte-exact.
    Appending requires a terminated last row so cleanup can restore original bytes.
    """
    names = list(names)
    if len(names) != len(set(names)) or any(not SUITE_NAME.fullmatch(name) for name in names):
        raise ValueError("registration requires distinct generated suite source paths")
    wanted = {(MANIFEST_PREFIX + name).encode("ascii"): row.encode("ascii")
              for name, row in zip(names, manifest_lines(names))}
    found, retained = set(), []
    for line in data.splitlines(keepends=True):
        content = line.rstrip(b"\r\n")
        # Recognize malformed separators only to reject conflicting owned rows.
        path = content.split(b"\\t", 1)[0].split(b"\t", 1)[0]
        if path in wanted:
            if content != wanted[path] or path in found:
                raise ValueError("conflicting XML registration: " + path.decode("ascii"))
            found.add(path)
            if remove:
                continue
        elif path.startswith((MANIFEST_PREFIX + "suite/").encode("ascii")):
            raise ValueError("unowned XML suite registration: " + path.decode("ascii"))
        retained.append(line)
    if not remove:
        missing = [row for path, row in wanted.items() if path not in found]
        if missing and data and not data.endswith(b"\n"):
            raise ValueError("manifest must end with a newline before local registration")
        retained.extend(row + b"\n" for row in missing)
    return b"".join(retained)


def recorded_sources(work):
    """Prove ownership before any deletion, including the previous projection.

    Legacy names must match the full identity hash and recorded support layout;
    every present file must retain its recorded hash. Unknown files are rejected.
    No acquisitions, inventory artifacts or historical reports are deletion targets.
    """
    work = Path(work)
    safe_path(work)
    metadata = work / "EXECUTION.json"
    safe_path(metadata)
    execution = json.loads(metadata.read_bytes()) if metadata.exists() else {}
    if execution and execution.get("schema_version") not in (1, 2):
        raise ValueError("unknown execution metadata schema")
    owned = {}
    for case in execution.get("cases", []):
        if execution.get("schema_version") == 1:
            name = case.get("program_path")
            if name is None:
                continue
            if name != "programs/" + sha256(case["identity"].encode("utf-8")) + ".protos":
                raise ValueError("unproven legacy source: " + str(name))
            owned[name] = case["program_sha256"]
        else:
            name = case.get("suite_source_path")
            if name is None:
                continue
            if not name.startswith(MANIFEST_PREFIX) or not SUITE_NAME.fullmatch(name[len(MANIFEST_PREFIX):]):
                raise ValueError("unproven suite source: " + str(name))
            name = name[len(MANIFEST_PREFIX):]
            digest = case["suite_source_sha256"]
            if name in owned and owned[name] != digest:
                raise ValueError("conflicting recorded source hash: " + name)
            owned[name] = digest
    for entry in execution.get("support_sources", []):
        name = entry["path"]
        if execution.get("schema_version") != 1 or LEGACY_SUPPORT.get(name) != entry["canonical_source"]:
            raise ValueError("unproven legacy support: " + name)
        owned[name] = entry["sha256"]
    legacy = work / "programs"
    safe_path(legacy)
    present = {p.relative_to(work).as_posix(): p for p in legacy.rglob("*")
               if p.is_file() or p.is_symlink()}
    for name, digest in owned.items():
        path = work / name if name.startswith("programs/") else target_path(work, name)
        safe_path(path)
        if path.exists():
            if not path.is_file() or sha256(path.read_bytes()) != digest:
                raise ValueError("modified owned source: " + name)
            present[name] = path
    for name, path in present.items():
        safe_path(path)
        if name not in owned:
            raise ValueError("unknown legacy source: " + name)
    return owned, present


def compare_artifacts(work, expected, manifest=None):
    """Read-only comparison, including explicit local registration."""
    problems = []
    for name, data in expected.items():
        path = target_path(work, name)
        safe_path(path)
        if not path.is_file():
            problems.append("missing artifact: " + name)
        elif path.read_bytes() != data:
            problems.append("modified artifact: " + name)
    legacy = Path(work) / "programs"
    safe_path(legacy)
    if any(p.is_file() or p.is_symlink() for p in legacy.rglob("*")):
        problems.append("obsolete standalone sources remain")
    suite = Path(work).parent / "suite"
    safe_path(suite)
    for path in suite.glob("*.protos"):
        safe_path(path)
        name = "suite/" + path.name
        if name not in expected and name not in SUITE_AUXILIARIES:
            problems.append("unexpected suite source: " + name)
    if manifest is not None:
        safe_path(Path(manifest))
        data = Path(manifest).read_bytes()
        if local_manifest(data, [name for name in expected if SUITE_NAME.fullmatch(name)]) != data:
            problems.append("missing local XML registrations")
    return problems


def write_artifacts(work, expected, manifest=None):
    """Preflight ownership and conflicts before materialization or migration."""
    work = Path(work)
    safe_path(work)
    owned, present = recorded_sources(work) if "EXECUTION.json" in expected else ({}, {})
    for name in expected:
        path = target_path(work, name)
        safe_path(path)
        if SUITE_NAME.fullmatch(name) and path.exists() and name not in owned:
            raise ValueError("unowned suite source: " + name)
    names = [name for name in expected if SUITE_NAME.fullmatch(name)]
    if "EXECUTION.json" in expected:
        suite = work.parent / "suite"
        safe_path(suite)
        for path in suite.glob("*.protos"):
            safe_path(path)
            name = "suite/" + path.name
            if name not in owned and name not in SUITE_AUXILIARIES:
                raise ValueError("unowned suite source: " + path.name)
    updated = None
    if manifest is not None:
        safe_path(Path(manifest))
        updated = local_manifest(Path(manifest).read_bytes(), names)
    for name, path in present.items():
        if name not in expected:
            path.unlink()
    for name, data in expected.items():
        path = target_path(work, name)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    # Register only sources already materialized successfully.
    if updated is not None:
        Path(manifest).write_bytes(updated)


def cleanup(work, manifest):
    """Remove only verified owned sources and their exact local manifest rows."""
    owned, present = recorded_sources(work)
    names = [name for name in owned if SUITE_NAME.fullmatch(name)]
    safe_path(Path(manifest))
    original = Path(manifest).read_bytes()
    updated = local_manifest(original, names, remove=True)
    # Unregister first: an interrupted cleanup never leaves rows for deleted files.
    if updated != original:
        Path(manifest).write_bytes(updated)
    for path in present.values():
        path.unlink()
