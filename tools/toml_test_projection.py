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

"""LIB010-E2-C: retained official toml-test TOML 1.1 corpus and its projection.

The official toml-test v2.2.0 corpus selected by ``tests/files-toml-1.1.0`` is
retained byte-exact under ``UPSTREAM_DIR``. This tool derives, deterministically
and offline, everything else from that snapshot:

- ``PROVENANCE.toml``, ``CASES.tsv`` and ``SHA256SUMS`` beside the snapshot;
- the suite-native Protos shards under ``SUITE_DIR`` (the hand-written
  ``Harness.protos`` there is not generated).

Commands:

- ``vendor --from DIR``: copy the pinned files from a local checkout of the
  pinned upstream commit, verifying git blob ids, then ``generate``;
- ``generate``: rewrite every derived file from the retained snapshot;
- ``check`` (also ``--check``): fail unless the snapshot matches the pin and
  every derived file is byte-identical to its regeneration.

Normal ``generate``/``check`` never touch the network or a toml-test binary.
"""

from __future__ import print_function

import argparse
from collections import OrderedDict
import hashlib
import json
from pathlib import Path
import sys


REPO_ROOT = Path(__file__).resolve().parent.parent

OFFICIAL_DIR = "protos/tests/conformance/library/toml/official"
UPSTREAM_DIR = OFFICIAL_DIR + "/upstream/v2.2.0"
SUITE_DIR = OFFICIAL_DIR + "/suite"
HARNESS_NAME = "Harness.protos"
MANIFEST_PATH = "protos/tests/conformance/manifest.tsv"
MANIFEST_PREFIX = "library/toml/official/suite/"

PIN = OrderedDict((
    ("repository", "https://github.com/toml-lang/toml-test"),
    ("release", "v2.2.0"),
    ("commit", "ce08da1ddb075d1c7596d663c7fcba9a2ae02c5c"),
    ("tree", "05b36075fdf409db0448d3047b5dfdb7d8bd5f6a"),
    ("toml-version", "1.1.0"),
    ("license", "MIT"),
    ("license-copyright", "Copyright (c) 2018 TOML authors"),
))

# Retained name -> (upstream path, git blob id at PIN commit).
PINNED_FILES = OrderedDict((
    ("LICENSE", ("LICENSE", "93b22020a83d8a03c300bbaf965ceacc7c00f926")),
    (".gitattributes", (
        "tests/.gitattributes", "638165542dec9f82ef0769c871f5395c15b1ce6a")),
    ("files-toml-1.1.0", (
        "tests/files-toml-1.1.0", "e2bdb2a669ede0dec8813f7c12db990c9a469f42")),
))
SELECTOR_NAME = "files-toml-1.1.0"

EXPECTED_COUNTS = OrderedDict((
    ("selected-file-count", 895),
    ("selected-byte-count", 106344),
    ("valid-count", 214),
    ("invalid-count", 467),
    ("encoder-count", 214),
    ("invalid-applicable-count", 456),
    ("executable-count", 884),
    ("not-applicable-count", 11),
    ("logical-case-count", 895),
))

NOT_APPLICABLE = "NOT_APPLICABLE_INPUT_DOMAIN"
EXECUTABLE = "EXECUTABLE"

# Invalid fixtures whose bytes are not strict UTF-8 and therefore cannot be the
# String argument of TOML.parse(String). The rule is applied mechanically and
# must reproduce exactly this list.
NOT_APPLICABLE_CASES = (
    "invalid/encoding/bad-codepoint",
    "invalid/encoding/bad-utf8-at-end",
    "invalid/encoding/bad-utf8-in-array",
    "invalid/encoding/bad-utf8-in-comment",
    "invalid/encoding/bad-utf8-in-multiline",
    "invalid/encoding/bad-utf8-in-multiline-literal",
    "invalid/encoding/bad-utf8-in-string",
    "invalid/encoding/bad-utf8-in-string-literal",
    "invalid/encoding/bom-not-at-start-01",
    "invalid/encoding/bom-not-at-start-02",
    "invalid/encoding/utf16-bom",
)
NOT_APPLICABLE_RULE = (
    "invalid fixture bytes are not strict UTF-8, so they cannot be the "
    "String argument of TOML.parse(String)"
)

OFFICIAL_TAGS = frozenset((
    "string", "integer", "float", "bool",
    "datetime", "datetime-local", "date-local", "time-local",
))

# SHA-256 of the generated SHA256SUMS: pins the retained bytes so that a
# fixture change cannot be absorbed by regenerating the checksum file.
SNAPSHOT_DIGEST = "2522b7760d06247ff61d44e6ca4ec4a793f9b6d12fbd667a9aaf7d76e9f6d749"

LITERAL_PIECE_LIMIT = 64


class ProjectionError(Exception):
    pass


def git_blob_id(data):
    header = ("blob %d\0" % len(data)).encode("ascii")
    return hashlib.sha1(header + data).hexdigest()


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def read_selector(data):
    try:
        text = data.decode("ascii")
    except UnicodeDecodeError:
        raise ProjectionError("selector is not ASCII")
    if not text.endswith("\n"):
        raise ProjectionError("selector does not end with a newline")
    entries = text[:-1].split("\n")
    seen = set()
    for entry in entries:
        if entry in seen:
            raise ProjectionError("selector repeats " + entry)
        seen.add(entry)
        if not (entry.startswith("valid/") or entry.startswith("invalid/")):
            raise ProjectionError("selector entry outside valid/invalid: " + entry)
        if not (entry.endswith(".toml") or entry.endswith(".json")):
            raise ProjectionError("selector entry is not .toml/.json: " + entry)
        if ".." in entry.split("/") or "" in entry.split("/"):
            raise ProjectionError("selector entry is not a plain path: " + entry)
    return entries


def strict_utf8(data):
    try:
        return data.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        return None


class Case(object):
    def __init__(self, name, direction, classification, fixtures):
        self.name = name
        self.direction = direction
        self.classification = classification
        self.fixtures = fixtures
        self.test_name = None
        self.shard = None


class Snapshot(object):
    """The retained upstream files, validated against the pin."""

    def __init__(self, root):
        self.root = Path(root)
        self.upstream = self.root / UPSTREAM_DIR
        self.pinned = OrderedDict()
        for name, (_, blob) in PINNED_FILES.items():
            data = self._read(self.upstream / name)
            if git_blob_id(data) != blob:
                raise ProjectionError(
                    "retained %s does not match pinned blob %s" % (name, blob))
            self.pinned[name] = data

        self.selected = read_selector(self.pinned[SELECTOR_NAME])
        tests_dir = self.upstream / "tests"
        present = sorted(
            path.relative_to(tests_dir).as_posix()
            for path in tests_dir.rglob("*")
            if path.is_file() or path.is_symlink()
        )
        if present != sorted(self.selected):
            missing = sorted(set(self.selected) - set(present))
            extra = sorted(set(present) - set(self.selected))
            raise ProjectionError(
                "retained tests/ differs from selector: missing=%s extra=%s"
                % (missing[:5], extra[:5]))

        self.fixtures = OrderedDict()
        for entry in sorted(self.selected):
            path = tests_dir / entry
            if path.is_symlink():
                raise ProjectionError("retained fixture is a symlink: " + entry)
            self.fixtures[entry] = self._read(path)

    @staticmethod
    def _read(path):
        try:
            return path.read_bytes()
        except OSError as error:
            raise ProjectionError("cannot read %s: %s" % (path, error))


def build_cases(snapshot):
    """Answer the 895 logical cases in canonical (sorted upstream) order."""
    fixtures = snapshot.fixtures
    valid_names = sorted(
        entry[:-len(".toml")] for entry in fixtures
        if entry.startswith("valid/") and entry.endswith(".toml"))
    valid_json = sorted(
        entry[:-len(".json")] for entry in fixtures
        if entry.startswith("valid/") and entry.endswith(".json"))
    invalid_names = sorted(
        entry[:-len(".toml")] for entry in fixtures
        if entry.startswith("invalid/") and entry.endswith(".toml"))
    invalid_json = [
        entry for entry in fixtures
        if entry.startswith("invalid/") and entry.endswith(".json")]

    if valid_names != valid_json:
        raise ProjectionError("valid .toml and .json fixtures are not paired")
    if invalid_json:
        raise ProjectionError("invalid fixture has a .json: " + invalid_json[0])

    cases = []
    for name in valid_names:
        source = strict_utf8(fixtures[name + ".toml"])
        if source is None:
            raise ProjectionError("valid fixture is not UTF-8: " + name)
        validate_tagged_json(name, fixtures[name + ".json"])
        files = (name + ".toml", name + ".json")
        cases.append(Case(name, "decoder-valid", EXECUTABLE, files))
        cases.append(Case(
            "encoder/" + name[len("valid/"):], "encoder", EXECUTABLE, files))

    not_applicable = []
    for name in invalid_names:
        classification = EXECUTABLE
        if strict_utf8(fixtures[name + ".toml"]) is None:
            classification = NOT_APPLICABLE
            not_applicable.append(name)
        cases.append(Case(
            name, "decoder-invalid", classification, (name + ".toml",)))

    if tuple(not_applicable) != NOT_APPLICABLE_CASES:
        raise ProjectionError(
            "not-applicable set differs from the pinned list: %s"
            % not_applicable)

    cases.sort(key=lambda case: (
        case.fixtures[0].rsplit(".", 1)[0], case.direction))
    for case in cases:
        if case.classification == EXECUTABLE:
            case.shard = shard_name(case.fixtures[0])
            case.test_name = case.name
    return cases


def validate_tagged_json(name, data):
    text = strict_utf8(data)
    if text is None:
        raise ProjectionError("expected JSON is not UTF-8: " + name)
    try:
        root = json.loads(text)
    except ValueError as error:
        raise ProjectionError("expected JSON is malformed: %s: %s" % (name, error))
    if not isinstance(root, dict):
        raise ProjectionError("expected JSON root is not an object: " + name)

    def walk(value):
        if isinstance(value, list):
            for item in value:
                walk(item)
        elif isinstance(value, dict):
            if set(value) == {"type", "value"}:
                if value["type"] not in OFFICIAL_TAGS:
                    raise ProjectionError(
                        "unknown official tag %r in %s" % (value["type"], name))
                if not isinstance(value["value"], str):
                    raise ProjectionError("tagged value is not a string: " + name)
            else:
                for item in value.values():
                    walk(item)
        else:
            raise ProjectionError("untagged JSON scalar in " + name)

    walk(root)


def shard_name(fixture):
    parts = fixture.split("/")
    category = parts[1] if len(parts) > 2 else "root"
    return "%s-%s.protos" % (parts[0], category)


def counts_of(snapshot, cases):
    def count(direction, classification=None):
        return sum(
            1 for case in cases
            if case.direction == direction
            and (classification is None or case.classification == classification))

    return OrderedDict((
        ("selected-file-count", len(snapshot.fixtures)),
        ("selected-byte-count", sum(len(data) for data in snapshot.fixtures.values())),
        ("valid-count", count("decoder-valid")),
        ("invalid-count", count("decoder-invalid")),
        ("encoder-count", count("encoder")),
        ("invalid-applicable-count", count("decoder-invalid", EXECUTABLE)),
        ("executable-count", sum(
            1 for case in cases if case.classification == EXECUTABLE)),
        ("not-applicable-count", sum(
            1 for case in cases if case.classification == NOT_APPLICABLE)),
        ("logical-case-count", len(cases)),
    ))


def check_counts(counts):
    if counts != EXPECTED_COUNTS:
        raise ProjectionError("inventory counts differ: %s" % dict(counts))
    if counts["executable-count"] + counts["not-applicable-count"] != \
            counts["logical-case-count"]:
        raise ProjectionError("executable + not-applicable != logical cases")


def protos_escape(character):
    code = ord(character)
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
    if code < 0x20 or code >= 0x7F:
        return "\\u{%X}" % code
    return character


def protos_string_pieces(text):
    """Answer double-quoted Protos literals whose concatenation is ``text``."""
    pieces = []
    current = []
    width = 0
    for character in text:
        escaped = protos_escape(character)
        current.append(escaped)
        width += len(escaped)
        if character == "\n" or width >= LITERAL_PIECE_LIMIT:
            pieces.append('"' + "".join(current) + '"')
            current = []
            width = 0
    if current or not pieces:
        pieces.append('"' + "".join(current) + '"')
    return pieces


def protos_argument(text, indent, last):
    pieces = protos_string_pieces(text)
    lines = []
    for index, piece in enumerate(pieces):
        if index < len(pieces) - 1:
            lines.append(indent + piece + " +")
        else:
            lines.append(indent + piece + ("" if last else ","))
    return lines


def shard_text(shard, cases, snapshot, omitted):
    lines = [
        "// GENERATED by tools/toml_test_projection.py; do not edit.",
        "//",
        "// Suite-native projection of the official toml-test %s corpus"
        % PIN["release"],
        "// (commit %s, selector %s)." % (PIN["commit"], PINNED_FILES[SELECTOR_NAME][0]),
        "// Fixture data is derived from ../upstream/%s/tests and remains under"
        % PIN["release"],
        "// the upstream %s license retained at ../upstream/%s/LICENSE."
        % (PIN["license"], PIN["release"]),
    ]
    if omitted:
        lines.append("//")
        lines.append("// %s, recorded in CASES.tsv and not executed:" % NOT_APPLICABLE)
        for name in omitted:
            lines.append("//   " + name)
    lines.extend([
        "",
        'Harness: import("./%s")' % HARNESS_NAME,
        'Test: import("std:test/Test")',
        "",
        "tests: [",
    ])

    entries = []
    for case in cases:
        toml_text = strict_utf8(snapshot.fixtures[case.fixtures[0]])
        body = []
        if case.direction == "decoder-valid":
            body.append("        Harness.decodes(")
            body.extend(protos_argument(toml_text, "            ", False))
            body.extend(protos_argument(
                strict_utf8(snapshot.fixtures[case.fixtures[1]]),
                "            ", True))
            body.append("        )")
        elif case.direction == "encoder":
            body.append("        Harness.encodes(")
            body.extend(protos_argument(
                strict_utf8(snapshot.fixtures[case.fixtures[1]]),
                "            ", True))
            body.append("        )")
        else:
            body.append("        Harness.rejects(")
            body.extend(protos_argument(toml_text, "            ", True))
            body.append("        )")
        name = '"' + "".join(protos_escape(c) for c in case.test_name) + '"'
        entry = ["    Test(%s, () => {" % name]
        entry.extend(body)
        entry.append("    })")
        entries.append(entry)

    for index, entry in enumerate(entries):
        if index < len(entries) - 1:
            entry[-1] += ","
        lines.extend(entry)
    lines.extend(["]", "tests.freeze()", ""])
    return "\n".join(lines).encode("utf-8")


def provenance_text(counts, snapshot_digest):
    lines = [
        "# GENERATED by tools/toml_test_projection.py; do not edit.",
        "# Provenance of the retained official toml-test corpus (LIB010-E2-C,",
        "# AUD005 F3). Normal test execution is offline and uses only these bytes.",
        "",
        "[upstream]",
    ]
    for key, value in PIN.items():
        lines.append('%s = "%s"' % (key, value))
    for name, (path, blob) in PINNED_FILES.items():
        key = {"LICENSE": "license", ".gitattributes": "gitattributes",
               SELECTOR_NAME: "selector"}[name]
        lines.append('%s-path = "%s"' % (key, path))
        lines.append('%s-blob = "%s"' % (key, blob))
    lines.extend(["", "[inventory]"])
    for key, value in counts.items():
        lines.append("%s = %d" % (key, value))
    lines.append('sha256sums-sha256 = "%s"' % snapshot_digest)
    lines.extend(["", "[not-applicable]"])
    lines.append('classification = "%s"' % NOT_APPLICABLE)
    lines.append('rule = "%s"' % NOT_APPLICABLE_RULE)
    lines.append("cases = [")
    for name in NOT_APPLICABLE_CASES:
        lines.append('    "%s",' % name)
    lines.extend(["]", ""])
    return "\n".join(lines).encode("utf-8")


def cases_text(cases):
    lines = [
        "# GENERATED by tools/toml_test_projection.py; do not edit.",
        "# upstream-case\tdirection\tclassification\ttest\tsuite-source\tfixtures",
    ]
    for case in cases:
        lines.append("\t".join((
            case.name,
            case.direction,
            case.classification,
            case.test_name or "-",
            ("suite/" + case.shard) if case.shard else "-",
            ",".join("tests/" + fixture for fixture in case.fixtures),
        )))
    lines.append("")
    return "\n".join(lines).encode("utf-8")


def sha256sums_text(snapshot):
    entries = [(name, data) for name, data in snapshot.pinned.items()]
    entries.extend(
        ("tests/" + name, data) for name, data in snapshot.fixtures.items())
    entries.sort(key=lambda entry: entry[0])
    return "".join(
        "%s  %s\n" % (sha256(data), name) for name, data in entries
    ).encode("ascii")


def derive(snapshot):
    """Answer (counts, cases, OrderedDict of repo path -> derived bytes)."""
    cases = build_cases(snapshot)
    counts = counts_of(snapshot, cases)
    check_counts(counts)

    names = [case.test_name for case in cases if case.test_name]
    if len(names) != len(set(names)):
        raise ProjectionError("a logical test name is generated twice")

    sums = sha256sums_text(snapshot)
    outputs = OrderedDict()
    outputs[UPSTREAM_DIR + "/SHA256SUMS"] = sums
    outputs[UPSTREAM_DIR + "/PROVENANCE.toml"] = provenance_text(
        counts, sha256(sums))
    outputs[UPSTREAM_DIR + "/CASES.tsv"] = cases_text(cases)

    shards = OrderedDict()
    for case in cases:
        if case.shard:
            shards.setdefault(case.shard, []).append(case)
    omitted = OrderedDict()
    for case in cases:
        if case.classification == NOT_APPLICABLE:
            omitted.setdefault(shard_name(case.fixtures[0]), []).append(case.name)
    for shard in sorted(shards):
        outputs[SUITE_DIR + "/" + shard] = shard_text(
            shard, shards[shard], snapshot, omitted.get(shard, ()))
    return counts, cases, outputs


def manifest_lines(outputs):
    return [
        MANIFEST_PREFIX + path.rsplit("/", 1)[1] + "\tsuite-native\t-"
        for path in outputs if path.startswith(SUITE_DIR + "/")
    ]


def check(root):
    root = Path(root)
    snapshot = Snapshot(root)
    counts, cases, outputs = derive(snapshot)

    digest = sha256(outputs[UPSTREAM_DIR + "/SHA256SUMS"])
    if digest != SNAPSHOT_DIGEST:
        raise ProjectionError(
            "retained snapshot digest %s differs from pinned %s"
            % (digest, SNAPSHOT_DIGEST))

    for path, data in outputs.items():
        try:
            current = (root / path).read_bytes()
        except OSError:
            raise ProjectionError("derived file is missing: " + path)
        if current != data:
            raise ProjectionError("derived file is stale: " + path)

    suite = root / SUITE_DIR
    present = sorted(path.name for path in suite.iterdir())
    expected = sorted(
        [path.rsplit("/", 1)[1] for path in outputs if path.startswith(SUITE_DIR)]
        + [HARNESS_NAME])
    if present != expected:
        raise ProjectionError("suite directory content differs: %s" % present)

    upstream = root / UPSTREAM_DIR
    upstream_present = sorted(path.name for path in upstream.iterdir())
    upstream_expected = sorted(
        list(PINNED_FILES) + ["tests", "PROVENANCE.toml", "CASES.tsv", "SHA256SUMS"])
    if upstream_present != upstream_expected:
        raise ProjectionError(
            "upstream directory content differs: %s" % upstream_present)

    generated_tests = 0
    for path, data in outputs.items():
        if path.startswith(SUITE_DIR + "/"):
            generated_tests += data.decode("utf-8").count("\n    Test(")
    if generated_tests != counts["executable-count"]:
        raise ProjectionError("generated %d Tests" % generated_tests)

    manifest = (root / MANIFEST_PATH).read_text(encoding="utf-8").split("\n")
    official = [line for line in manifest if line.startswith(MANIFEST_PREFIX)]
    if official != manifest_lines(outputs):
        raise ProjectionError("manifest does not list exactly the generated shards")
    return counts


def generate(root):
    root = Path(root)
    snapshot = Snapshot(root)
    counts, cases, outputs = derive(snapshot)
    suite = root / SUITE_DIR
    suite.mkdir(parents=True, exist_ok=True)
    for path in suite.iterdir():
        if path.name != HARNESS_NAME and path.suffix == ".protos" \
                and (SUITE_DIR + "/" + path.name) not in outputs:
            path.unlink()
    for path, data in outputs.items():
        (root / path).write_bytes(data)
    return counts, sha256(outputs[UPSTREAM_DIR + "/SHA256SUMS"])


def vendor(root, source):
    """Copy the pinned files from a local checkout of the pinned commit."""
    root = Path(root)
    source = Path(source)
    upstream = root / UPSTREAM_DIR
    if upstream.exists():
        raise ProjectionError("refusing to overwrite existing " + UPSTREAM_DIR)

    staged = OrderedDict()
    for name, (path, blob) in PINNED_FILES.items():
        data = (source / path).read_bytes()
        if git_blob_id(data) != blob:
            raise ProjectionError("upstream %s does not match pinned blob" % path)
        staged[name] = data
    for entry in read_selector(staged[SELECTOR_NAME]):
        path = source / "tests" / entry
        if path.is_symlink() or not path.is_file():
            raise ProjectionError("upstream fixture is not a regular file: " + entry)
        staged["tests/" + entry] = path.read_bytes()

    for name, data in staged.items():
        target = upstream / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    return generate(root)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--root", default=str(REPO_ROOT))
    parser.add_argument("--check", action="store_true",
                        help="same as the check command")
    sub = parser.add_subparsers(dest="command")
    sub.add_parser("generate")
    sub.add_parser("check")
    vendor_parser = sub.add_parser("vendor")
    vendor_parser.add_argument("--from", dest="source", required=True)
    args = parser.parse_args(argv)

    command = "check" if args.check else args.command
    try:
        if command == "check":
            counts = check(args.root)
            print("toml-test projection: OK (%d executable, %d not applicable, "
                  "%d logical cases, %d retained files)" % (
                      counts["executable-count"], counts["not-applicable-count"],
                      counts["logical-case-count"], counts["selected-file-count"]))
        elif command == "generate":
            counts, digest = generate(args.root)
            print("toml-test projection: generated; sha256sums-sha256 %s" % digest)
        elif command == "vendor":
            counts, digest = vendor(args.root, args.source)
            print("toml-test projection: vendored; sha256sums-sha256 %s" % digest)
        else:
            parser.error("a command is required")
    except ProjectionError as error:
        print("toml-test projection: FAIL: %s" % error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
