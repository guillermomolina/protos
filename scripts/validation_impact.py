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

"""Fail-closed validation-impact selection for Protos publication deltas."""

from __future__ import print_function

import argparse
import fnmatch
import json
import os
import subprocess
import sys


PACKAGE_TEST_SET = (
    "ProtosPackage*Test,"
    "ProtosExternalPackage*Test,"
    "ProtosWorkspace*Test,"
    "ProtosTestToolPackage*Test,"
    "ProtosCliTest"
)

TEST_TOOL_TEST_SET = (
    "ProtosTestTool*Test,"
    "ProtosCliTest"
)

NEUTRAL_PREFIXES = ("docs/",)
NEUTRAL_EXACT = frozenset(("CHANGELOG.md",))

PACKAGE_PREFIXES = (
    "protos/tools/package/",
    "protos/tests/package-tool/",
)

TEST_TOOL_PREFIXES = ("protos/tools/test/",)

PACKAGE_TEST_GLOBS = (
    "src/test/java/com/guillermomolina/protos/execution/ProtosPackage*Test.java",
    "src/test/java/com/guillermomolina/protos/execution/ProtosExternalPackage*Test.java",
    "src/test/java/com/guillermomolina/protos/execution/ProtosWorkspace*Test.java",
    "src/test/java/com/guillermomolina/protos/cli/ProtosWorkspace*Test.java",
)

TEST_TOOL_TEST_GLOBS = (
    "src/test/java/com/guillermomolina/protos/execution/ProtosTestTool*Test.java",
)

# Production Java that is exclusively owned by the bundled Test Tool. Keep
# ProtosCli.java itself outside this list: it is shared CLI surface and receives
# a narrower method-body check in classify_delta().
TEST_TOOL_SOURCE_GLOBS = (
    "src/main/java/com/guillermomolina/protos/cli/ProtosTestCorpusRegistry.java",
    "src/main/java/com/guillermomolina/protos/cli/ProtosTestExecutionRequirementRegistry.java",
    "src/main/java/com/guillermomolina/protos/cli/ProtosTestTool*.java",
    "src/main/java/com/guillermomolina/protos/execution/ProtosTestTool*.java",
)

PROTOS_CLI_PATH = "src/main/java/com/guillermomolina/protos/cli/ProtosCli.java"
PROTOS_CLI_TEST_TOOL_METHOD = "    private int runBundledTestTool("


class Selection(object):
    def __init__(self, impact, test_set, reason, skip_allowed):
        self.impact = impact
        self.test_set = test_set
        self.reason = reason
        self.skip_allowed = bool(skip_allowed)

    def to_dict(self):
        return {
            "validation_impact": self.impact,
            "affected_test_set": self.test_set,
            "full_test_suite": "SKIP_ALLOWED" if self.skip_allowed else "REQUIRED",
            "reason": self.reason,
        }


def _has_prefix(path, prefixes):
    return any(path.startswith(prefix) for prefix in prefixes)


def _matches(path, patterns):
    return any(fnmatch.fnmatchcase(path, pattern) for pattern in patterns)


def _kind(path):
    if path in NEUTRAL_EXACT or _has_prefix(path, NEUTRAL_PREFIXES):
        return "NEUTRAL"

    if _has_prefix(path, PACKAGE_PREFIXES) or _matches(path, PACKAGE_TEST_GLOBS):
        return "PACKAGE"

    if _has_prefix(path, TEST_TOOL_PREFIXES):
        return "TEST"

    if path.startswith("protos/tests/tooling/"):
        base = path.rsplit("/", 1)[-1]
        if base.startswith("tool002-") and base.endswith(".protos"):
            return "TEST"
        return "FULL"

    if _matches(path, TEST_TOOL_TEST_GLOBS):
        return "TEST"

    if _matches(path, TEST_TOOL_SOURCE_GLOBS):
        return "TEST"

    return "FULL"


def classify_paths(paths, top_level_closure=False, kind_overrides=None):
    normalized = []
    for raw in paths:
        if raw is None:
            continue
        path = raw.replace("\\", "/").lstrip("./")
        if path and path not in normalized:
            normalized.append(path)

    if not normalized:
        return Selection(
            "FULL:NON_TOOL", "NON_TOOL",
            "empty definitive delta receives broad non-Tool validation under the temporary PERF007 quarantine",
            True,
        )

    tool_kinds = []
    first_full_path = None
    overrides = {} if kind_overrides is None else dict(kind_overrides)

    for path in normalized:
        kind = overrides.get(path, _kind(path))
        if kind in ("PACKAGE", "TEST") and kind not in tool_kinds:
            tool_kinds.append(kind)
        if kind == "FULL" and first_full_path is None:
            first_full_path = path

    tools_touched = bool(tool_kinds)

    if top_level_closure:
        if tools_touched:
            return Selection(
                "FULL", "ALL",
                "top-level executable closure touches a Tool-owned surface and requires complete Maven validation",
                False,
            )
        return Selection(
            "FULL:NON_TOOL", "NON_TOOL",
            "top-level executable closure has no Tool-owned delta; PERF007 temporarily excludes Tool-owned suites",
            True,
        )

    if first_full_path is not None:
        if tools_touched:
            return Selection(
                "FULL", "ALL",
                "shared, unknown, or unmapped path plus a Tool-owned delta requires complete validation: "
                + first_full_path,
                False,
            )
        return Selection(
            "FULL:NON_TOOL", "NON_TOOL",
            "shared, unknown, or unmapped delta receives broad non-Tool validation under PERF007: "
            + first_full_path,
            True,
        )

    if not tool_kinds:
        return Selection(
            "FULL:NON_TOOL", "NON_TOOL",
            "no Tool-owned executable/test-impact path was present; PERF007 temporarily excludes Tool-owned suites",
            True,
        )

    if len(tool_kinds) != 1:
        return Selection(
            "FULL", "ALL",
            "cross-tool delta touches multiple Tool-owned surfaces and requires complete Maven validation",
            False,
        )

    if tool_kinds[0] == "PACKAGE":
        return Selection(
            "TOOL_LOCAL:PACKAGE", PACKAGE_TEST_SET,
            "all executable/test-impact paths are explicitly mapped Package Tool-local",
            True,
        )

    if tool_kinds[0] == "TEST":
        return Selection(
            "TOOL_LOCAL:TEST", TEST_TOOL_TEST_SET,
            "all executable/test-impact paths are explicitly mapped Test Tool-local",
            True,
        )

    return Selection("FULL", "ALL", "unreachable fail-closed classification", False)

def _java_method_span(source, signature):
    # Return [start,end) for one Java method, failing closed on ambiguity.
    start = source.find(signature)
    if start < 0 or source.find(signature, start + 1) >= 0:
        return None

    brace = source.find("{", start + len(signature))
    if brace < 0:
        return None

    depth = 0
    index = brace
    state = "code"
    while index < len(source):
        char = source[index]
        nxt = source[index + 1] if index + 1 < len(source) else ""

        if state == "code":
            if char == '"':
                state = "string"
            elif char == "'":
                state = "char"
            elif char == "/" and nxt == "/":
                state = "line-comment"
                index += 1
            elif char == "/" and nxt == "*":
                state = "block-comment"
                index += 1
            elif char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    return (start, index + 1)
                if depth < 0:
                    return None
        elif state == "string":
            if char == "\\":
                index += 1
            elif char == '"':
                state = "code"
        elif state == "char":
            if char == "\\":
                index += 1
            elif char == "'":
                state = "code"
        elif state == "line-comment":
            if char == "\n":
                state = "code"
        elif state == "block-comment":
            if char == "*" and nxt == "/":
                state = "code"
                index += 1

        index += 1

    return None


def _without_java_method(source, signature):
    span = _java_method_span(source, signature)
    if span is None:
        return None
    start, end = span
    return source[:start] + "<TEST_TOOL_METHOD_BODY>\n" + source[end:]


def _git_text(repo, ref, path):
    try:
        data = subprocess.check_output([
            "git", "-C", repo, "show", ref + ":" + path
        ])
    except subprocess.CalledProcessError:
        return None
    return data.decode("utf-8", "strict")


def protos_cli_change_is_test_tool_only(repo, base, head):
    # True only when ProtosCli.java changed inside runBundledTestTool().
    before = _git_text(repo, base, PROTOS_CLI_PATH)
    after = _git_text(repo, head, PROTOS_CLI_PATH)
    if before is None or after is None or before == after:
        return False

    before_outer = _without_java_method(before, PROTOS_CLI_TEST_TOOL_METHOD)
    after_outer = _without_java_method(after, PROTOS_CLI_TEST_TOOL_METHOD)
    return (
        before_outer is not None
        and after_outer is not None
        and before_outer == after_outer
    )


def classify_delta(repo, base, head, top_level_closure=False):
    paths = changed_paths(repo, base, head)
    overrides = {}
    normalized = [p.replace("\\", "/").lstrip("./") for p in paths]
    if PROTOS_CLI_PATH in normalized:
        if protos_cli_change_is_test_tool_only(repo, base, head):
            overrides[PROTOS_CLI_PATH] = "TEST"

    return classify_paths(
        paths,
        top_level_closure=top_level_closure,
        kind_overrides=overrides,
    )


def parse_name_status_z(data):
    """Parse `git diff --name-status -z`, retaining both paths for R/C."""
    fields = data.split(b"\0")
    if fields and fields[-1] == b"":
        fields.pop()

    paths = []
    index = 0
    while index < len(fields):
        status = fields[index].decode("utf-8", "strict")
        index += 1
        if not status:
            raise ValueError("empty git diff status")

        code = status[0]
        if code in ("R", "C"):
            if index + 1 >= len(fields):
                raise ValueError("truncated rename/copy record")
            paths.append(fields[index].decode("utf-8", "strict"))
            paths.append(fields[index + 1].decode("utf-8", "strict"))
            index += 2
        else:
            if index >= len(fields):
                raise ValueError("truncated git diff record")
            paths.append(fields[index].decode("utf-8", "strict"))
            index += 1
    return paths


def changed_paths(repo, base, head):
    data = subprocess.check_output([
        "git", "-C", repo, "diff", "--name-status", "-z",
        "--find-renames", "--find-copies", base, head, "--",
    ])
    return parse_name_status_z(data)


def render_env(selection):
    data = selection.to_dict()
    return "\n".join((
        "VALIDATION_IMPACT=" + data["validation_impact"],
        "AFFECTED_TEST_SET=" + data["affected_test_set"],
        "FULL_TEST_SUITE=" + data["full_test_suite"],
        "VALIDATION_REASON=" + data["reason"],
    ))


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", required=True)
    parser.add_argument("--top-level-closure", action="store_true")
    parser.add_argument("--format", choices=("env", "json"), default="env")
    args = parser.parse_args(argv)

    selection = classify_delta(
        os.path.abspath(args.repo),
        args.base,
        args.head,
        top_level_closure=args.top_level_closure,
    )

    if args.format == "json":
        print(json.dumps(selection.to_dict(), sort_keys=True))
    else:
        print(render_env(selection))
    return 0


if __name__ == "__main__":
    sys.exit(main())
