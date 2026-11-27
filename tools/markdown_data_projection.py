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

"""Offline Markdown tables and official conformance source projection.

Run ``python tools/markdown_data_projection.py generate`` only after the exact
upstream inputs and their original license files have been supplied locally.
``check`` compares candidate bytes without modifying them. Neither mode fetches
anything, invokes a parser, or uses Python's Unicode classification databases.
"""
from __future__ import annotations

import argparse
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
BASE = Path("protos/tests/conformance/library/markdown/official")
UPSTREAM = BASE / "upstream"
SUITE = BASE / "suite"
DATA = Path("protos/lib/markdown/internal/generated")
MANIFEST = Path("protos/tests/conformance/manifest.tsv")
CMARK_COMMIT = "eec0eeba6d31189fd828314576494566d539b1e3"
CMARK_BLOB = "e80d9aa9a4f83e2a062c06ed7a3075c031794b4e"
INPUTS = {
    "commonmark-0.31.2/spec.json": ("https://spec.commonmark.org/0.31.2/spec.json", "0.31.2", "CC-BY-SA-4.0"),
    "gfm-0.29/spec.html": ("https://github.github.com/gfm/", "0.29", "CC-BY-SA-4.0"),
    "unicode-17.0.0/UnicodeData.txt": ("https://www.unicode.org/Public/17.0.0/ucd/UnicodeData.txt", "17.0.0", "Unicode-3.0"),
    "unicode-17.0.0/CaseFolding.txt": ("https://www.unicode.org/Public/17.0.0/ucd/CaseFolding.txt", "17.0.0", "Unicode-3.0"),
    "whatwg/entities.json": ("https://html.spec.whatwg.org/entities.json", "local retained snapshot", "retained WHATWG notice"),
    "cmark-0.31.2/entities.inc": ("https://github.com/commonmark/cmark/blob/" + CMARK_COMMIT + "/src/entities.inc", CMARK_COMMIT, "retained cmark notice"),
}
LICENSES = (
    "commonmark-0.31.2/LICENSE", "gfm-0.29/LICENSE",
    "unicode-17.0.0/LICENSE", "whatwg/LICENSE", "cmark-0.31.2/COPYING",
)
GFM_SECTIONS = {
    "Tables": tuple(range(198, 206)),
    "Task list items": (279, 280),
    "Strikethrough": (491, 492, 493),
    "Extended autolinks": tuple(range(622, 636)),
}
GFM_IDS = {number for values in GFM_SECTIONS.values() for number in values}


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON key: " + key)
        result[key] = value
    return result


def scalar(value):
    if not 0 <= value <= 0x10FFFF or 0xD800 <= value <= 0xDFFF:
        raise ValueError("non-scalar mapping")
    return value


def literal(text):
    output = ['"']
    # Literal spellings are built explicitly: no host Unicode normalization.
    escapes = {34: r'\"', 92: r'\\', 10: r'\n', 13: r'\r', 9: r'\t'}
    for char in text:
        code = scalar(ord(char))
        if code in escapes:
            output.append(escapes[code])
        elif 32 <= code < 127:
            output.append(char)
        else:
            output.append("\\u{" + format(code, "x") + "}")
    output.append('"')
    return "".join(output)


def merged(ranges):
    result = []
    for start, end in sorted(ranges):
        if result and start <= result[-1][1] + 1:
            result[-1] = (result[-1][0], max(end, result[-1][1]))
        else:
            result.append((start, end))
    return result


def categories(text):
    ranges = {"spaces": [], "punctuation": []}
    pending = None
    previous = -1
    for line in text.splitlines():
        fields = line.split(";")
        if len(fields) != 15:
            raise ValueError("invalid UnicodeData record")
        code = int(fields[0], 16)
        if code <= previous:
            raise ValueError("duplicate or unordered UnicodeData scalar")
        previous = code
        name, category = fields[1:3]
        if category not in {"Lu", "Ll", "Lt", "Lm", "Lo", "Mn", "Mc", "Me", "Nd", "Nl", "No", "Pc", "Pd", "Ps", "Pe", "Pi", "Pf", "Po", "Sm", "Sc", "Sk", "So", "Zs", "Zl", "Zp", "Cc", "Cf", "Cs", "Co", "Cn"}:
            raise ValueError("unknown Unicode general category")
        if name.endswith(", First>"):
            if pending is not None:
                raise ValueError("nested UnicodeData range")
            pending = (code, name[:-8], category)
            continue
        start = code
        if name.endswith(", Last>"):
            if pending is None or pending[1] != name[:-7] or pending[2] != category:
                raise ValueError("inconsistent UnicodeData First/Last pair")
            start = pending[0]
            pending = None
        elif pending is not None:
            raise ValueError("unterminated UnicodeData range")
        if category == "Zs":
            ranges["spaces"].append((start, code))
        if category.startswith(("P", "S")):
            ranges["punctuation"].append((start, code))
    if pending is not None:
        raise ValueError("unterminated UnicodeData range at EOF")
    return {name: merged(values) for name, values in ranges.items()}


def folding(text):
    if "CaseFolding-17.0.0.txt" not in text:
        raise ValueError("case-folding version is not Unicode 17.0.0")
    mappings = {}
    seen = set()
    for raw in text.splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        fields = [field.strip() for field in line.split(";")]
        if len(fields) != 4 or not fields[2]:
            raise ValueError("invalid case-folding record")
        code, status = scalar(int(fields[0], 16)), fields[1]
        identity = (code, status)
        if identity in seen:
            raise ValueError("duplicate case-folding record")
        seen.add(identity)
        if status not in ("C", "F", "S", "T"):
            raise ValueError("unknown case-folding status")
        if status in ("C", "F"):
            if code in mappings:
                raise ValueError("duplicate full folding mapping")
            mappings[code] = "".join(chr(scalar(int(cp, 16))) for cp in fields[2].split())
    return sorted(mappings.items())


def entities(raw):
    source = json.loads(raw, object_pairs_hook=unique_object)
    result = {}
    for name, record in source.items():
        if not name.startswith("&"):
            raise ValueError("invalid entity name")
        if not name.endswith(";"):
            continue
        key = name[1:-1]
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9]*", key):
            raise ValueError("non-ASCII entity name")
        value = "".join(chr(scalar(cp)) for cp in record["codepoints"])
        if value != record["characters"] or len(value) not in (1, 2) or key in result:
            raise ValueError("inconsistent entity record: " + name)
        result[key] = value
    if len(result) != 2125:
        raise ValueError("expected exactly 2125 named entities")
    return result


def packed_initializer(text, name, c_type, count, maximum):
    declaration = re.compile(
        r"static\s+const\s+" + re.escape(c_type).replace(r"\ ", r"\s+")
        + r"\s+" + re.escape(name) + r"\s*\[\s*" + str(count)
        + r"\s*\]\s*=\s*\{([^{}]*)\}\s*;", re.DOTALL)
    matches = list(declaration.finditer(text))
    if len(matches) != 1:
        raise ValueError("missing or malformed packed array: " + name)
    body = re.sub(r"/\*.*?\*/|//[^\n]*", "", matches[0].group(1), flags=re.DOTALL)
    fields = body.strip().split(",")
    if fields and not fields[-1].strip():
        fields.pop()
    if len(fields) != count:
        raise ValueError("unexpected packed array count: " + name)
    values = []
    for field in fields:
        spelling = field.strip()
        if not re.fullmatch(r"0[xX][0-9A-Fa-f]+", spelling):
            raise ValueError("malformed hexadecimal initializer: " + name)
        value = int(spelling, 16)
        if value > maximum:
            raise ValueError("out-of-range packed array entry: " + name)
        values.append(value)
    return values


def independent_entities(raw):
    blob = hashlib.sha1(b"blob " + str(len(raw)).encode("ascii") + b"\0" + raw).hexdigest()
    if blob != CMARK_BLOB:
        raise ValueError("cmark entities.inc does not match pinned Git blob")
    source = raw.decode("ascii", "strict")
    packed = packed_initializer(source, "cmark_entities", "uint32_t", 2125, 0xFFFFFFFF)
    text = bytes(packed_initializer(source, "cmark_entity_text", "unsigned char", 20220, 255))
    result = {}
    previous = None
    for entry in packed:
        offset = entry & 0x7FFF
        name_length = (entry >> 15) & 0x1F
        replacement_length = entry >> 20
        name_end = offset + name_length
        end = name_end + replacement_length
        if not name_length or not replacement_length or offset >= len(text) or end > len(text):
            raise ValueError("invalid cmark packed entity extent")
        name = text[offset:name_end].decode("ascii", "strict")
        replacement = text[name_end:end].decode("utf-8", "strict")
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9]*", name):
            raise ValueError("invalid cmark entity name")
        if not 1 <= len(replacement) <= 2:
            raise ValueError("invalid cmark replacement scalar count")
        for character in replacement:
            scalar(ord(character))
        if name in result or (previous is not None and name <= previous):
            raise ValueError("duplicate or unordered cmark entity name")
        result[name] = replacement
        previous = name
    return result


class GfmExamples(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.examples = {}
        self.current = None
        self.language = None
        self.buffer = []
        self.tab_depth = 0
        self.element_stack = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        identity = attrs.get("id", "")
        if tag == "div" and re.fullmatch(r"example-\d+", identity):
            number = int(identity[8:])
            if number in GFM_IDS:
                if number in self.examples:
                    raise ValueError("duplicate GFM example")
                self.current = number
                self.examples[number] = {}
            else:
                self.current = None
        if self.current is not None and tag == "code":
            language = attrs.get("class", "")
            if language in ("language-markdown", "language-html"):
                self.language = language
                self.buffer = []
        if self.language is not None and tag == "span":
            tab = "tab" in attrs.get("class", "").split()
            self.element_stack.append(tab)
            if tab:
                if not self.tab_depth:
                    self.buffer.append("\t")
                self.tab_depth += 1

    def handle_endtag(self, tag):
        if tag == "span" and self.language is not None:
            if not self.element_stack:
                raise ValueError("unbalanced GFM code presentation spans")
            if self.element_stack.pop():
                self.tab_depth -= 1
        if tag == "code" and self.language is not None:
            if self.element_stack or self.tab_depth:
                raise ValueError("unclosed GFM code presentation span")
            record = self.examples[self.current]
            if self.language in record:
                raise ValueError("duplicate GFM example language")
            record[self.language] = "".join(self.buffer)
            self.language = None

    def handle_data(self, data):
        if self.language is not None and not self.tab_depth:
            self.buffer.append(data)


def gfm_examples(text):
    parser = GfmExamples()
    parser.feed(text)
    parser.close()
    if set(parser.examples) != GFM_IDS:
        raise ValueError("published GFM selected example identities differ")
    output = []
    for section, numbers in GFM_SECTIONS.items():
        for number in numbers:
            record = parser.examples[number]
            if set(record) != {"language-markdown", "language-html"}:
                raise ValueError("incomplete GFM example " + str(number))
            output.append({"example": number, "section": section, "markdown": record["language-markdown"], "html": record["language-html"]})
    return output


def table_source(groups, attribution):
    output = ["// Generated by tools/markdown_data_projection.py; do not edit.\n", "// " + attribution + "\n", "// Source provenance and licenses: protos/tests/conformance/library/markdown/official/upstream/\n\n"]
    for name, rows in groups.items():
        chunks = []
        for index in range(0, len(rows), 128):
            chunk = name + "Part" + str(index // 128)
            chunks.append(chunk)
            output.append(chunk + ": [\n    " + ",\n    ".join(rows[index:index + 128]) + "\n]\n")
            output.append("for(entry in " + chunk + ") { entry.freeze() }\n" + chunk + ".freeze()\n")
        output.append(name + ": Array(" + ", ".join("..." + chunk for chunk in chunks) + ")\n" + name + ".freeze()\n\n")
    return "".join(output).rstrip("\n") + "\n"


def suite_source(cases, profile, attribution, directory):
    parent = "../" if directory != SUITE else ""
    harness = "./Harness.protos" if directory != SUITE else "./commonmark/Harness.protos"
    output = ["// Generated by tools/markdown_data_projection.py; do not edit.\n", "// " + attribution + "\n", "// Original fixtures and licenses are retained under " + parent + "../upstream/.\n", 'Assertions: import("std:test/assertions")\nTest: import("std:test/test")\nMarkdown: import("std:markdown")\nHarness: import("' + harness + '")\n\ntests: [\n']
    for index, case in enumerate(cases):
        section = re.sub(
            r"[^A-Za-z0-9]+", "-", case["section"]
        ).strip("-")
        group = profile + "/" + section

        if len(cases) > 100:
            group += "/part-" + str(index // 100 + 1).zfill(2)

        name = group + "/example-" + str(case["example"])
        output.append("    Test(" + literal(name) + ", () => {\n        tree: Markdown.parse(" + literal(case["markdown"]) + ", profile: " + literal(profile) + ")\n        Assertions.require(Harness.render(tree) == " + literal(case["html"]) + ")\n    })" + ("," if index + 1 < len(cases) else "") + "\n")
    output.append("]\ntests.freeze()\n")
    return "".join(output)


def project():
    missing = [str(UPSTREAM / name) for name in (*INPUTS, *LICENSES) if not (ROOT / UPSTREAM / name).is_file()]
    if missing:
        raise ValueError("missing retained inputs/licenses:\n" + "\n".join(missing))
    raw = {name: (ROOT / UPSTREAM / name).read_bytes() for name in INPUTS}
    unicode_categories = categories(raw["unicode-17.0.0/UnicodeData.txt"].decode("utf-8"))
    folds = folding(raw["unicode-17.0.0/CaseFolding.txt"].decode("utf-8"))
    names = entities(raw["whatwg/entities.json"])
    if independent_entities(raw["cmark-0.31.2/entities.inc"]) != names:
        raise ValueError("WHATWG and pinned cmark entity mappings differ")
    commonmark = json.loads(raw["commonmark-0.31.2/spec.json"], object_pairs_hook=unique_object)
    if len(commonmark) != 652 or [case["example"] for case in commonmark] != list(range(1, 653)):
        raise ValueError("expected all 652 ordered CommonMark examples")
    for case in commonmark:
        if any(not isinstance(case.get(field), str) for field in ("section", "markdown", "html")):
            raise ValueError("invalid CommonMark example fields")
    gfm = gfm_examples(raw["gfm-0.29/spec.html"].decode("utf-8"))
    output = {}
    output[DATA / "UnicodeCategories.protos"] = table_source({name: ["[" + str(a) + ", " + str(b) + "]" for a, b in values] for name, values in unicode_categories.items()}, "Unicode Character Database 17.0.0; Unicode License V3.")
    output[DATA / "UnicodeCaseFolding.protos"] = table_source({"entries": ["[" + str(code) + ", " + literal(value) + "]" for code, value in folds]}, "Unicode full default case folding 17.0.0; Unicode License V3.")
    output[DATA / "NamedEntities.protos"] = table_source({"entries": ["[" + literal(name) + ", " + literal(value) + "]" for name, value in sorted(names.items())]}, "WHATWG named references; retained WHATWG attribution applies. Independently compared with pinned cmark.")
    for prefix, corpus, profile, attribution in (
        ("CommonMark", commonmark, "commonmark-0.31.2", "CommonMark 0.31.2 examples, CC BY-SA 4.0."),
        ("GFM", gfm, "commonmark-0.31.2-gfm-extensions", "Published GFM 0.29 extension examples, CC BY-SA 4.0."),
    ):
        sections = {}
        for case in corpus:
            sections.setdefault(case["section"], []).append(case)
        for section, cases in sections.items():
            slug = re.sub(r"[^A-Za-z0-9]+", "-", section).strip("-")
            directory = SUITE / "commonmark" if prefix == "CommonMark" else SUITE
            output[directory / (prefix + "-" + slug + ".protos")] = suite_source(cases, profile, attribution, directory)
    items = sorted(names.items())
    for offset in range(0, len(items), 100):
        chunk = items[offset:offset + 100]
        lines = ['// Generated WHATWG entity conformance data; retained WHATWG license applies.\n', 'Assertions: import("std:test/assertions")\nTest: import("std:test/test")\nMarkdown: import("std:markdown")\ntests: [\n']
        for index, (name, value) in enumerate(chunk):
            lines.append("    Test(" + literal("entities/" + str(offset // 100).zfill(2) + "/" + name) + ", () => {\n        node: Markdown.parse(" + literal("&" + name + ";") + ").children[0].children[0]\n        Assertions.require(node.kind == \"text\")\n        Assertions.require(node.value == " + literal(value) + ")\n    })" + ("," if index + 1 < len(chunk) else "") + "\n")
        lines.append("]\ntests.freeze()\n")
        output[SUITE / ("Entities-" + str(offset // 100).zfill(2) + ".protos")] = "".join(lines)
    provenance = {"inputs": [], "commonmark_examples": list(range(1, 653)), "gfm_examples": sorted(GFM_IDS), "entity_count": 2125, "compatibility_differences": [], "generated_sources": [str(path) for path in sorted(output)]}
    checksums = []
    for name in sorted((*INPUTS, *LICENSES)):
        data = (ROOT / UPSTREAM / name).read_bytes()
        if name in LICENSES and not data.strip():
            raise ValueError("empty upstream license: " + name)
        digest = hashlib.sha256(data).hexdigest()
        checksums.append(digest + "  " + name + "\n")
        if name in INPUTS:
            url, version, license_name = INPUTS[name]
            record = {"path": name, "url": url, "version": version, "license": license_name, "sha256": digest}
            if name.startswith("cmark-"):
                record.update(commit=CMARK_COMMIT, blob=CMARK_BLOB)
            provenance["inputs"].append(record)
        else:
            record = {"path": name, "sha256": digest}
            if name == "whatwg/LICENSE":
                record.update(repository="https://github.com/whatwg/html", revision="0162cee0c42a82267354d38fbc6a2503ee295b77", terms="Retained publication and source-code incorporation terms")
            elif name == "cmark-0.31.2/COPYING":
                record.update(repository="https://github.com/commonmark/cmark", revision=CMARK_COMMIT, terms="Exact retained COPYING notices")
            elif name.startswith("unicode-"):
                record.update(version="17.0.0", terms="Unicode License V3")
            else:
                record.update(terms="CC BY-SA 4.0", version="0.31.2" if name.startswith("commonmark-") else "0.29")
            provenance.setdefault("licenses", []).append(record)
    output[UPSTREAM / "PROVENANCE.json"] = json.dumps(provenance, ensure_ascii=True, indent=2, sort_keys=True) + "\n"
    output[UPSTREAM / "SHA256SUMS"] = "".join(checksums)
    manifest = (ROOT / MANIFEST).read_text()
    prefix = "library/markdown/official/suite/"
    owned_names = ("CommonMark-", "GFM-", "Entities-")
    lines = [line for line in manifest.splitlines()
             if not (line.startswith(prefix) and Path(line.split("\t", 1)[0]).name.startswith(owned_names))]
    for path in sorted(output):
        if SUITE in path.parents and path.suffix == ".protos":
            lines.append(str(path.relative_to("protos/tests/conformance")) + "\tsuite-native\t-")
    output[MANIFEST] = "\n".join(lines) + "\n"
    return {path: value.encode("utf-8") for path, value in output.items()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("generate", "check"))
    args = parser.parse_args()
    try:
        projected = project()
        stale = []
        for relative, expected in projected.items():
            path = ROOT / relative
            if args.mode == "generate":
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(expected)
            elif not path.is_file() or path.read_bytes() != expected:
                stale.append(str(relative))
        if stale:
            raise ValueError("stale generated files:\n" + "\n".join(stale))
    except (ValueError, KeyError, UnicodeError, OSError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
