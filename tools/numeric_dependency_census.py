#!/usr/bin/env python3
# THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
# ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
# DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
# DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
# OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
# THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
# OF THE LICENSE IS LOCATED AT:
# https://github.com/guillermomolina/protos
#
# Software distributed under the License is distributed on an "AS IS" basis,
# WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
# the specific language governing rights and limitations under the License.

"""I091-A exhaustive production-Java numeric dependency census.

This is an occurrence inventory, not an automated architectural judgment.
References are unclassified until source-level ownership review.
Comments and Java string/character/text-block contents are excluded.
"""

import csv
import hashlib
import json
import re
import subprocess
from bisect import bisect_right
from collections import Counter
from pathlib import Path


SOURCE_ROOT = Path("src/main/java")
OUTPUT_ROOT = Path("target")
PATTERN = re.compile(
    r"(?<![A-Za-z0-9_$])"
    r"(java\.math\.BigInteger|BigInteger|"
    r"ProtosIntegerValue|ProtosFloatValue)"
    r"(?![A-Za-z0-9_$])"
)


def mask_noncode(source):
    result = list(source)
    length = len(source)
    position = 0
    state = "code"

    def blank(start, stop):
        for index in range(start, min(stop, length)):
            if source[index] != "\n":
                result[index] = " "

    while position < length:
        if state == "code":
            if source.startswith("//", position):
                blank(position, position + 2)
                position += 2
                state = "line"
                continue
            if source.startswith("/*", position):
                blank(position, position + 2)
                position += 2
                state = "block"
                continue
            if source.startswith('"""', position):
                blank(position, position + 3)
                position += 3
                state = "text"
                continue
            if source[position] == '"':
                blank(position, position + 1)
                position += 1
                state = "string"
                continue
            if source[position] == "'":
                blank(position, position + 1)
                position += 1
                state = "character"
                continue

        elif state == "line":
            if source[position] == "\n":
                state = "code"
            else:
                blank(position, position + 1)
            position += 1
            continue

        elif state == "block":
            if source.startswith("*/", position):
                blank(position, position + 2)
                position += 2
                state = "code"
                continue
            blank(position, position + 1)
            position += 1
            continue

        elif state == "text":
            if source.startswith('"""', position):
                blank(position, position + 3)
                position += 3
                state = "code"
                continue
            if source[position] == "\\":
                blank(position, position + 2)
                position += 2
                continue
            blank(position, position + 1)
            position += 1
            continue

        else:
            if source[position] == "\\":
                blank(position, position + 2)
                position += 2
                continue
            if source[position] == (
                '"' if state == "string" else "'"
            ):
                blank(position, position + 1)
                position += 1
                state = "code"
                continue
            blank(position, position + 1)
            position += 1
            continue

        position += 1

    return "".join(result)


def git_head():
    return subprocess.check_output(
        ["git", "rev-parse", "HEAD"],
        text=True
    ).strip()


def inventory():
    if not SOURCE_ROOT.is_dir():
        raise RuntimeError(f"Missing source root: {SOURCE_ROOT}")

    files = sorted(SOURCE_ROOT.rglob("*.java"))
    if not files:
        raise RuntimeError("No production Java files found")

    fingerprint = hashlib.sha256()
    sites = []
    per_file = []
    symbol_counts = Counter()
    module_counts = Counter()

    for path in files:
        raw = path.read_bytes()
        source = raw.decode("utf-8")
        relative = path.as_posix()

        fingerprint.update(relative.encode("utf-8"))
        fingerprint.update(b"\0")
        fingerprint.update(raw)
        fingerprint.update(b"\0")

        visible = mask_noncode(source)
        visible_lines = visible.splitlines()
        original_lines = source.splitlines()

        line_starts = [0]
        line_starts.extend(
            match.end()
            for match in re.finditer("\n", visible)
        )

        file_counts = Counter()
        module = (
            path.parts[6]
            if len(path.parts) > 6
            else "unclassified-module"
        )

        for match in PATTERN.finditer(visible):
            line_number = bisect_right(line_starts, match.start())
            line_index = line_number - 1
            column = match.start() - line_starts[line_index] + 1

            spelling = match.group()
            symbol = (
                "BigInteger"
                if spelling.endswith("BigInteger")
                else spelling
            )
            line = visible_lines[line_index].strip()
            kind = (
                "import"
                if re.match(r"^import\s", line)
                else "code"
            )

            record = {
                "path": relative,
                "module": module,
                "line": line_number,
                "column": column,
                "symbol": symbol,
                "spelling": spelling,
                "kind": kind,
                "classification": "UNCLASSIFIED_NEEDS_SOURCE_AUDIT",
                "source": original_lines[line_index].strip(),
            }
            sites.append(record)
            file_counts[symbol] += 1
            symbol_counts[symbol] += 1
            module_counts[module] += 1

        if file_counts:
            per_file.append({
                "path": relative,
                "module": module,
                "counts": dict(sorted(file_counts.items())),
                "total": sum(file_counts.values()),
            })

    per_file.sort(key=lambda item: item["path"])

    report = {
        "schema": "I091-NUMERIC-CENSUS-1",
        "head": git_head(),
        "production_source_fingerprint": fingerprint.hexdigest(),
        "production_java_files": len(files),
        "matching_java_files": len(per_file),
        "total_sites": len(sites),
        "symbol_counts": dict(sorted(symbol_counts.items())),
        "module_counts": dict(sorted(module_counts.items())),
        "files": per_file,
        "sites": sites,
    }

    return report


def main():
    report = inventory()
    OUTPUT_ROOT.mkdir(parents=True, exist_ok=True)

    json_path = OUTPUT_ROOT / "i091-numeric-census.json"
    tsv_path = OUTPUT_ROOT / "i091-numeric-census.tsv"

    json_path.write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    fields = [
        "path", "module", "line", "column", "symbol",
        "spelling", "kind", "classification", "source"
    ]

    with tsv_path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(
            stream, fieldnames=fields, delimiter="\t"
        )
        writer.writeheader()
        writer.writerows(report["sites"])

    print("I091-A NUMERIC DEPENDENCY CENSUS")
    print(f"HEAD: {report['head']}")
    print(f"Fingerprint: {report['production_source_fingerprint']}")
    print(f"Production Java files: {report['production_java_files']}")
    print(f"Matching files: {report['matching_java_files']}")
    print(f"Total references: {report['total_sites']}")

    for symbol, count in report["symbol_counts"].items():
        print(f"  {symbol}: {count}")

    print("Modules:")
    for module, count in report["module_counts"].items():
        print(f"  {module}: {count}")

    print(f"JSON: {json_path}")
    print(f"TSV: {tsv_path}")
    print("CLASSIFICATION: PENDING SOURCE AUDIT")


if __name__ == "__main__":
    main()
