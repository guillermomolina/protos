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

"""Deterministic Git-index directory-file inventory for I103."""

from __future__ import annotations

import argparse
from collections import Counter
import json
import os
from pathlib import Path
import re
import subprocess
import sys


LIMIT = 30
UPSTREAM_PREFIX = (
    "protos/tests/conformance/library/toml/"
    "official/upstream/v2.2.0/"
)
REGULAR_MODES = frozenset(("100644", "100755"))
OTHER_MODES = frozenset(("120000", "160000"))
HEX_SHA = re.compile(r"(?:[0-9a-f]{40}|[0-9a-f]{64})\Z")


class InventoryError(RuntimeError):
    """Invalid or unavailable canonical Git index."""


def parse_index(data: bytes) -> dict[str, int]:
    if not isinstance(data, bytes):
        raise InventoryError("Git index output must be bytes")

    counts: Counter[str] = Counter()
    seen: set[bytes] = set()
    records = data.split(b"\0")
    if records[-1] == b"":
        records.pop()

    for record in records:
        metadata, separator, name = record.partition(b"\t")
        if not separator or not name:
            raise InventoryError("malformed Git index record")

        fields = metadata.split(b" ")
        if len(fields) != 3:
            raise InventoryError("malformed Git index metadata")

        try:
            mode, sha, stage = (
                item.decode("ascii", "strict") for item in fields
            )
        except UnicodeDecodeError as exc:
            raise InventoryError("non-ASCII Git index metadata") from exc

        if mode not in REGULAR_MODES | OTHER_MODES:
            raise InventoryError("unsupported index mode: " + mode)
        if not HEX_SHA.fullmatch(sha):
            raise InventoryError("invalid index object id")
        if stage != "0":
            raise InventoryError("unmerged Git index entry")
        if name in seen:
            raise InventoryError("duplicate Git index entry")
        seen.add(name)

        if name.startswith(b"/") or b"\0" in name:
            raise InventoryError("invalid tracked path")
        parts = name.split(b"/")
        if any(part in (b"", b".", b"..") for part in parts):
            raise InventoryError("invalid tracked path components")

        if mode not in REGULAR_MODES:
            continue

        path = os.fsdecode(name)
        if path.startswith(UPSTREAM_PREFIX):
            continue

        directory = path.rpartition("/")[0] or "."
        counts[directory] += 1

    return dict(counts)


def inventory(repo: Path | str = ".") -> dict[str, int]:
    command = [
        "git", "-C", str(repo),
        "ls-files", "--cached", "--stage", "-z",
    ]
    try:
        result = subprocess.run(
            command, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, check=False,
        )
    except OSError as exc:
        raise InventoryError(f"Git unavailable: {exc}") from exc

    if result.returncode:
        message = result.stderr.decode(
            "utf-8", "replace"
        ).strip()
        raise InventoryError(
            f"Git index inventory failed: {message}"
        )

    return parse_index(result.stdout)


def ordered(counts: dict[str, int]):
    return sorted(counts.items(), key=lambda item: os.fsencode(item[0]))


def violations(counts: dict[str, int]):
    return [
        (path, count)
        for path, count in ordered(counts)
        if count > LIMIT
    ]


def render(counts: dict[str, int], mode: str) -> bool:
    offenders = violations(counts)
    print(f"DIRECT_FILE_LIMIT={LIMIT}")
    print(f"APPLICABLE_DIRECTORIES={len(counts)}")
    print(f"TRACKED_REGULAR_FILES={sum(counts.values())}")
    print(f"VIOLATING_DIRECTORIES={len(offenders)}")

    for path, count in offenders:
        escaped = json.dumps(path, ensure_ascii=True)
        print(f"VIOLATION count={count} directory={escaped}")

    status = "VIOLATIONS" if offenders else "PASS"
    print(f"STATUS={status}")
    return not offenders


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        description="Check direct tracked regular files per directory"
    )
    parser.add_argument(
        "mode", choices=("report", "check")
    )
    parser.add_argument("--repo", default=".")
    args = parser.parse_args(argv)

    try:
        counts = inventory(Path(args.repo))
    except InventoryError as exc:
        print(f"DIRECTORY_FILE_LIMIT_ERROR: {exc}", file=sys.stderr)
        return 2

    passed = render(counts, args.mode)
    return 0 if (passed or args.mode == "report") else 1


if __name__ == "__main__":
    raise SystemExit(main())
