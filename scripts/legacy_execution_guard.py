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

"""Fail closed on growth of PLAT035 legacy execution dependencies."""

from __future__ import print_function

import argparse
from collections import OrderedDict
from pathlib import Path
import re
import subprocess
import sys


class LegacyExecutionGuardError(Exception):
    pass


FAMILY_PATTERNS = OrderedDict((
    (
        "source_compiler",
        re.compile(r"\bProtosSourceCompiler\b"),
    ),
    (
        "source_file_loader",
        re.compile(r"\bProtosSourceFileLoader\b"),
    ),
    (
        "truffle_lowerer",
        re.compile(r"\bCanonicalToTruffleLowerer\b"),
    ),
    (
        "direct_call_target",
        re.compile(
            r"\bProtosExecution\s*\.\s*createCallTarget\s*\("
        ),
    ),
    (
        "expression_node",
        re.compile(r"\bProtosExpressionNode\b"),
    ),
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
        raise LegacyExecutionGuardError(
            "git command failed: git %s%s"
            % (
                " ".join(args),
                (": " + detail) if detail else "",
            )
        ) from exc


def _resolve_commit(repo, ref):
    try:
        return _git(repo, "rev-parse", str(ref) + "^{commit}")
    except LegacyExecutionGuardError as exc:
        raise LegacyExecutionGuardError(
            "cannot resolve commit ref: " + str(ref)
        ) from exc


def _java_paths(repo, ref):
    output = _git(
        repo,
        "ls-tree",
        "-r",
        "--name-only",
        ref,
        "--",
        "src/main/java",
        "src/test/java",
    )
    if not output:
        return set()
    return {
        path
        for path in output.splitlines()
        if path.endswith(".java")
    }


def _show_text(repo, ref, path, missing_ok=False):
    completed = subprocess.run(
        [
            "git",
            "-C",
            str(repo),
            "show",
            "%s:%s" % (ref, path),
        ],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if completed.returncode == 0:
        return completed.stdout
    if missing_ok:
        return ""
    detail = completed.stderr.strip()
    raise LegacyExecutionGuardError(
        "cannot read %s at %s%s"
        % (
            path,
            ref,
            (": " + detail) if detail else "",
        )
    )


def _sanitize_java(text):
    out = []
    i = 0
    n = len(text)
    state = "normal"

    while i < n:
        if state == "normal":
            if text.startswith("//", i):
                out.extend((" ", " "))
                i += 2
                state = "line_comment"
                continue
            if text.startswith("/*", i):
                out.extend((" ", " "))
                i += 2
                state = "block_comment"
                continue
            if text.startswith('"""', i):
                out.extend((" ", " ", " "))
                i += 3
                state = "text_block"
                continue

            ch = text[i]
            if ch == '"':
                out.append(" ")
                i += 1
                state = "string"
                continue
            if ch == "'":
                out.append(" ")
                i += 1
                state = "char"
                continue

            out.append(ch)
            i += 1
            continue

        if state == "line_comment":
            ch = text[i]
            if ch == "\n":
                out.append("\n")
                state = "normal"
            else:
                out.append(" ")
            i += 1
            continue

        if state == "block_comment":
            if text.startswith("*/", i):
                out.extend((" ", " "))
                i += 2
                state = "normal"
                continue

            ch = text[i]
            out.append("\n" if ch == "\n" else " ")
            i += 1
            continue

        if state == "text_block":
            if text.startswith('"""', i):
                out.extend((" ", " ", " "))
                i += 3
                state = "normal"
                continue

            ch = text[i]
            out.append("\n" if ch == "\n" else " ")
            i += 1
            continue

        if state in ("string", "char"):
            ch = text[i]

            if ch == "\\" and i + 1 < n:
                out.append(" ")
                nxt = text[i + 1]
                out.append("\n" if nxt == "\n" else " ")
                i += 2
                continue

            terminator = '"' if state == "string" else "'"
            if ch == terminator:
                out.append(" ")
                i += 1
                state = "normal"
                continue

            out.append("\n" if ch == "\n" else " ")
            i += 1
            continue

    return "".join(out)


def _legacy_compile_count(source):
    names = set(
        re.findall(
            r"\bProtosSourceCompiler\s+"
            r"([A-Za-z_$][A-Za-z0-9_$]*)\b",
            source,
        )
    )

    names.update(
        re.findall(
            r"\b(?:var|final\s+var)\s+"
            r"([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*"
            r"new\s+ProtosSourceCompiler\s*\(",
            source,
        )
    )

    count = len(
        re.findall(
            r"\bnew\s+ProtosSourceCompiler\s*\(\s*\)"
            r"\s*\.\s*compile\s*\(",
            source,
        )
    )

    for name in names:
        count += len(
            re.findall(
                r"\b%s\s*\.\s*compile\s*\("
                % re.escape(name),
                source,
            )
        )

    return count


def candidate_counts(text):
    source = _sanitize_java(text)

    result = {
        family: len(pattern.findall(source))
        for family, pattern in FAMILY_PATTERNS.items()
    }
    result["legacy_compile_call"] = _legacy_compile_count(source)
    return result


def check(repo, base, head):
    repo = Path(repo).resolve()
    base = _resolve_commit(repo, base)
    head = _resolve_commit(repo, head)

    base_paths = _java_paths(repo, base)
    head_paths = _java_paths(repo, head)
    paths = sorted(base_paths | head_paths)

    families = list(FAMILY_PATTERNS)
    families.append("legacy_compile_call")

    base_totals = dict((family, 0) for family in families)
    head_totals = dict((family, 0) for family in families)
    violations = []

    for path in paths:
        base_text = (
            _show_text(repo, base, path)
            if path in base_paths
            else ""
        )
        head_text = (
            _show_text(repo, head, path)
            if path in head_paths
            else ""
        )

        before = candidate_counts(base_text)
        after = candidate_counts(head_text)

        for family in families:
            base_totals[family] += before[family]
            head_totals[family] += after[family]

            if after[family] > before[family]:
                violations.append(
                    (
                        path,
                        family,
                        before[family],
                        after[family],
                    )
                )

    return {
        "base": base,
        "head": head,
        "scanned_files": len(paths),
        "base_totals": base_totals,
        "head_totals": head_totals,
        "violations": violations,
    }


def run(repo, base, head):
    try:
        result = check(repo, base, head)
    except LegacyExecutionGuardError as exc:
        print(
            "LEGACY_EXECUTION_GUARD: FAIL_CLOSED",
            file=sys.stderr,
        )
        print(
            "LEGACY_EXECUTION_GUARD_ERROR: " + str(exc),
            file=sys.stderr,
        )
        return 2

    if result["violations"]:
        print(
            "LEGACY_EXECUTION_GUARD: FAIL_CLOSED",
            file=sys.stderr,
        )
        for path, family, before, after in result["violations"]:
            print(
                "LEGACY_EXECUTION_REGRESSION: "
                "path=%s family=%s base=%d head=%d"
                % (path, family, before, after),
                file=sys.stderr,
            )
        return 2

    print(
        "LEGACY_EXECUTION_GUARD_SCANNED_FILES=%d"
        % result["scanned_files"]
    )

    for family in list(FAMILY_PATTERNS) + ["legacy_compile_call"]:
        print(
            "LEGACY_EXECUTION_COUNT: "
            "family=%s base=%d head=%d"
            % (
                family,
                result["base_totals"][family],
                result["head_totals"][family],
            )
        )

    print("LEGACY_EXECUTION_GUARD: PASS")
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", required=True)
    args = parser.parse_args(argv)

    return run(
        args.repo,
        args.base,
        args.head,
    )


if __name__ == "__main__":
    sys.exit(main())
