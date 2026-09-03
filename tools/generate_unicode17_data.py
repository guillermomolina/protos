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

from __future__ import annotations

import io
import struct
import urllib.request
import zipfile
from pathlib import Path

VERSION = "17.0.0"
URL = f"https://www.unicode.org/Public/{VERSION}/ucd/UCD.zip"
OUTPUT = Path("src/main/resources/com/guillermomolina/protos/lexer/unicode17.bin")
MAGIC = 0x50543137


def parse_range(text):
    if ".." in text:
        first, last = text.split("..", 1)
        return int(first, 16), int(last, 16)
    value = int(text, 16)
    return value, value


def merge_ranges(ranges):
    merged = []
    for start, end in sorted(ranges):
        if merged and start <= merged[-1][1] + 1:
            merged[-1][1] = max(merged[-1][1], end)
        else:
            merged.append([start, end])
    return [tuple(entry) for entry in merged]


def property_ranges(text, name):
    result = []
    for raw in text.splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line or ";" not in line:
            continue
        code_field, prop = (part.strip() for part in line.split(";", 1))
        if prop == name:
            result.append(parse_range(code_field))
    return merge_ranges(result)


def unicode_data(text):
    combining = {}
    decompositions = {}
    pending = None
    for line in text.splitlines():
        fields = line.split(";")
        cp = int(fields[0], 16)
        name = fields[1]
        ccc = int(fields[3])
        decomposition = fields[5]

        if name.endswith(", First>"):
            pending = (cp, ccc)
            continue
        if name.endswith(", Last>"):
            start, range_ccc = pending
            if ccc != range_ccc:
                raise RuntimeError("UnicodeData range CCC mismatch")
            if ccc:
                for value in range(start, cp + 1):
                    combining[value] = ccc
            pending = None
            continue

        if ccc:
            combining[cp] = ccc
        if decomposition and not decomposition.startswith("<"):
            decompositions[cp] = tuple(int(value, 16) for value in decomposition.split())
    return combining, decompositions


def combining_ranges(values):
    result = []
    for cp, ccc in sorted(values.items()):
        if result and cp == result[-1][1] + 1 and ccc == result[-1][2]:
            result[-1][1] = cp
        else:
            result.append([cp, cp, ccc])
    return [tuple(entry) for entry in result]


def full_composition_exclusions(text):
    result = set()
    for raw in text.splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line or ";" not in line:
            continue
        code_field, prop = (part.strip() for part in line.split(";", 1))
        if prop != "Full_Composition_Exclusion":
            continue
        start, end = parse_range(code_field)
        result.update(range(start, end + 1))
    return result


def write_int(stream, value):
    stream.write(struct.pack(">i", value))


def write_entries(stream, entries):
    write_int(stream, len(entries))
    for entry in entries:
        for value in entry:
            write_int(stream, value)


def main():
    request = urllib.request.Request(URL, headers={"User-Agent": "Protos Unicode generator"})
    with urllib.request.urlopen(request, timeout=60) as response:
        archive = zipfile.ZipFile(io.BytesIO(response.read()))

    derived = archive.read("DerivedCoreProperties.txt").decode()
    if not derived.startswith("# DerivedCoreProperties-17.0.0.txt"):
        raise RuntimeError("Unexpected Unicode data version")

    xid_start = property_ranges(derived, "XID_Start")
    xid_continue = property_ranges(derived, "XID_Continue")
    combining, decompositions = unicode_data(archive.read("UnicodeData.txt").decode())
    ccc_ranges = combining_ranges(combining)
    exclusions = full_composition_exclusions(
        archive.read("DerivedNormalizationProps.txt").decode()
    )

    compositions = []
    for composite, decomposition in decompositions.items():
        if len(decomposition) == 2 and composite not in exclusions:
            first, second = decomposition
            compositions.append(((first << 21) | second, first, second, composite))
    compositions.sort()

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("wb") as stream:
        for value in (MAGIC, 17, 0, 0):
            write_int(stream, value)
        write_entries(stream, xid_start)
        write_entries(stream, xid_continue)
        write_entries(stream, ccc_ranges)

        ordered = sorted(decompositions.items())
        write_int(stream, len(ordered))
        write_int(stream, sum(len(parts) for _, parts in ordered))
        for cp, parts in ordered:
            write_int(stream, cp)
            write_int(stream, len(parts))
            for part in parts:
                write_int(stream, part)

        write_int(stream, len(compositions))
        for _, first, second, composite in compositions:
            write_int(stream, first)
            write_int(stream, second)
            write_int(stream, composite)

    print(f"generated {OUTPUT}")


if __name__ == "__main__":
    main()
