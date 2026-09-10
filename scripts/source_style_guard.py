# THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
# ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
# DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
# DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
# OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
# THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
# OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
# THE CONTENTS OF THIS FILE.
#
# Software distributed under the License is distributed on an "AS IS" basis,
# WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
# the specific language governing rights and limitations under the License.

"""Differential source-style prevention gate for hand-written Protos source."""

from __future__ import print_function

import argparse
from collections import OrderedDict
import json
from pathlib import Path
import re
import subprocess
import sys


class SourceStyleGuardError(Exception):
    pass


EXCEPTIONS_PATH = "scripts/source_style_exceptions.json"
MARKDOWN_SOURCE_FENCE_LANGUAGES = frozenset(("protos", "js"))
FAMILY_PATTERNS = OrderedDict((
    ("indexing", (re.compile(r"\.at\s*\("), re.compile(r"\.atPut\s*\("))),
    ("lazy_boolean", (
        re.compile(r"\.(?:and|or)\s*\(\s*\(\s*\)\s*=>"),
        re.compile(r"\.(?:and|or)\s*\(\s*\)\s*\{"),
    )),
    ("not", (re.compile(r"\.not\s*\(\s*\)"),)),
    ("negated", (re.compile(r"\.negated\s*\(\s*\)"),)),
))


def _git(repo, *args):
    try:
        return subprocess.check_output(
            ["git", "-C", str(repo)] + list(args),
            text=True,
            stderr=subprocess.PIPE,
        ).strip()
    except subprocess.CalledProcessError as exc:
        detail = (exc.stderr or "").strip()
        raise SourceStyleGuardError(
            "git command failed: git %s%s"
            % (" ".join(args), (": " + detail) if detail else "")
        ) from exc


def _resolve_commit(repo, ref):
    try:
        return _git(repo, "rev-parse", str(ref) + "^{commit}")
    except SourceStyleGuardError as exc:
        raise SourceStyleGuardError("cannot resolve commit ref: " + str(ref)) from exc


def _show_text(repo, ref, path, missing_ok=False):
    completed = subprocess.run(
        ["git", "-C", str(repo), "show", "%s:%s" % (ref, path)],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if completed.returncode == 0:
        return completed.stdout
    if missing_ok:
        return ""
    detail = completed.stderr.strip()
    raise SourceStyleGuardError(
        "cannot read %s at %s%s"
        % (path, ref, (": " + detail) if detail else "")
    )


def _is_scoped_path(path):
    return (
        (path.startswith("protos/") and path.endswith(".protos"))
        or (path.startswith("docs/guide/") and path.endswith(".md"))
        or path == "README.md"
    )


def _sanitize_protos(text):
    out = []
    i = 0
    n = len(text)
    state = "normal"
    quote = None
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if state == "normal":
            if ch == "/" and nxt == "/":
                out.extend((" ", " "))
                i += 2
                state = "line_comment"
                continue
            if ch == "/" and nxt == "*":
                out.extend((" ", " "))
                i += 2
                state = "block_comment"
                continue
            if ch in ('"', "'"):
                out.append(" ")
                quote = ch
                i += 1
                state = "string"
                continue
            out.append(ch)
            i += 1
            continue
        if state == "line_comment":
            if ch == "\n":
                out.append("\n")
                state = "normal"
            else:
                out.append(" ")
            i += 1
            continue
        if state == "block_comment":
            if ch == "*" and nxt == "/":
                out.extend((" ", " "))
                i += 2
                state = "normal"
                continue
            out.append("\n" if ch == "\n" else " ")
            i += 1
            continue
        if state == "string":
            if ch == "\\" and i + 1 < n:
                out.append(" ")
                out.append("\n" if text[i + 1] == "\n" else " ")
                i += 2
                continue
            if ch == quote:
                out.append(" ")
                i += 1
                state = "normal"
                quote = None
                continue
            out.append("\n" if ch == "\n" else " ")
            i += 1
    return "".join(out)


def _markdown_source(text):
    chunks = []
    fence = re.compile(
        r"^```([A-Za-z0-9_-]*)[^\n]*\n(.*?)^```[ \t]*$",
        re.MULTILINE | re.DOTALL,
    )
    for match in fence.finditer(text):
        if match.group(1).lower() in MARKDOWN_SOURCE_FENCE_LANGUAGES:
            chunks.append(match.group(2))
    return "\n".join(chunks)


def candidate_counts(path, text):
    source = _markdown_source(text) if path.endswith(".md") else text
    sanitized = _sanitize_protos(source)
    return {
        family: sum(len(pattern.findall(sanitized)) for pattern in patterns)
        for family, patterns in FAMILY_PATTERNS.items()
    }


def _changed_scoped_pairs(repo, base, head):
    output = _git(repo, "diff", "--name-status", "-M", "--diff-filter=ACMR", base, head)
    result = []
    if not output:
        return result
    for raw in output.splitlines():
        parts = raw.split("\t")
        status = parts[0]
        if status.startswith("R"):
            if len(parts) != 3:
                raise SourceStyleGuardError("unexpected rename record: " + raw)
            base_path, head_path = parts[1], parts[2]
        else:
            if len(parts) != 2:
                raise SourceStyleGuardError("unexpected path record: " + raw)
            head_path = parts[1]
            base_path = None if status == "A" else head_path
        if _is_scoped_path(head_path):
            result.append((base_path, head_path))
    return result


def _load_exceptions(repo, head):
    raw = _show_text(repo, head, EXCEPTIONS_PATH)
    try:
        data = json.loads(raw)
    except (TypeError, ValueError) as exc:
        raise SourceStyleGuardError(EXCEPTIONS_PATH + " is not valid JSON") from exc
    if not isinstance(data, dict) or data.get("version") != 1:
        raise SourceStyleGuardError(EXCEPTIONS_PATH + " must contain object version=1")
    entries = data.get("exceptions")
    if not isinstance(entries, list):
        raise SourceStyleGuardError(EXCEPTIONS_PATH + " exceptions must be a list")
    result = {}
    for index, entry in enumerate(entries):
        prefix = "%s exceptions[%d]" % (EXCEPTIONS_PATH, index)
        if not isinstance(entry, dict):
            raise SourceStyleGuardError(prefix + " must be an object")
        path = entry.get("path")
        family = entry.get("family")
        maximum = entry.get("max_count")
        reason = entry.get("reason")
        if not isinstance(path, str) or not path or not _is_scoped_path(path):
            raise SourceStyleGuardError(prefix + " path must be one exact guarded path")
        if family not in FAMILY_PATTERNS:
            raise SourceStyleGuardError(prefix + " has unknown family: " + str(family))
        if not isinstance(maximum, int) or isinstance(maximum, bool) or maximum <= 0:
            raise SourceStyleGuardError(prefix + " max_count must be a positive integer")
        if not isinstance(reason, str) or len(reason.strip()) < 10:
            raise SourceStyleGuardError(prefix + " reason must be meaningful")
        key = (path, family)
        if key in result:
            raise SourceStyleGuardError(prefix + " duplicates path/family entry")
        actual = candidate_counts(path, _show_text(repo, head, path))[family]
        if actual != maximum:
            raise SourceStyleGuardError(
                "%s max_count must equal current head count for %s/%s: declared=%d actual=%d"
                % (prefix, path, family, maximum, actual)
            )
        result[key] = {"max_count": maximum, "reason": reason.strip()}
    return result


def check(repo, base, head):
    repo = Path(repo).resolve()
    base = _resolve_commit(repo, base)
    head = _resolve_commit(repo, head)
    exceptions = _load_exceptions(repo, head)
    changed = _changed_scoped_pairs(repo, base, head)
    violations = []
    allowed_increases = []
    for base_path, head_path in changed:
        base_text = "" if base_path is None else _show_text(repo, base, base_path, missing_ok=True)
        head_text = _show_text(repo, head, head_path)
        base_counts = candidate_counts(base_path or head_path, base_text)
        head_counts = candidate_counts(head_path, head_text)
        for family in FAMILY_PATTERNS:
            before = base_counts[family]
            after = head_counts[family]
            if after <= before:
                continue
            exception = exceptions.get((head_path, family))
            if exception is None:
                violations.append((head_path, family, before, after))
            else:
                allowed_increases.append((head_path, family, before, after, exception["reason"]))
    return {
        "scanned_changed_files": len(changed),
        "exceptions": len(exceptions),
        "allowed_increases": allowed_increases,
        "violations": violations,
    }


def run(repo, base, head):
    try:
        result = check(repo, base, head)
    except SourceStyleGuardError as exc:
        print("SOURCE_STYLE_GUARD: FAIL_CLOSED", file=sys.stderr)
        print("SOURCE_STYLE_GUARD_ERROR: " + str(exc), file=sys.stderr)
        return 2
    for path, family, before, after, reason in result["allowed_increases"]:
        print("SOURCE_STYLE_EXCEPTION: path=%s family=%s base=%d head=%d reason=%s" % (
            path, family, before, after, reason))
    if result["violations"]:
        print("SOURCE_STYLE_GUARD: FAIL_CLOSED", file=sys.stderr)
        for path, family, before, after in result["violations"]:
            print("SOURCE_STYLE_REGRESSION: path=%s family=%s base=%d head=%d" % (
                path, family, before, after), file=sys.stderr)
        print("SOURCE_STYLE_GUARD_HINT: use idiomatic surface syntax or add one reviewed exact exception", file=sys.stderr)
        return 2
    print("SOURCE_STYLE_GUARD_SCANNED_CHANGED_FILES=%d" % result["scanned_changed_files"])
    print("SOURCE_STYLE_GUARD_EXCEPTION_ENTRIES=%d" % result["exceptions"])
    print("SOURCE_STYLE_GUARD_ALLOWED_INCREASES=%d" % len(result["allowed_increases"]))
    print("SOURCE_STYLE_GUARD: PASS")
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", required=True)
    args = parser.parse_args(argv)
    return run(args.repo, args.base, args.head)


if __name__ == "__main__":
    sys.exit(main())
