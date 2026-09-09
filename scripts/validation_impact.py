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

    return "FULL"


def classify_paths(paths, top_level_closure=False):
    normalized = []
    for raw in paths:
        if raw is None:
            continue
        path = raw.replace("\\", "/").lstrip("./")
        if path and path not in normalized:
            normalized.append(path)

    if top_level_closure:
        return Selection(
            "FULL", "ALL",
            "top-level executable closure/reconciliation requires complete Maven validation",
            False,
        )

    if not normalized:
        return Selection(
            "FULL", "ALL",
            "empty definitive delta is not eligible for reduced validation",
            False,
        )

    kinds = []
    for path in normalized:
        kind = _kind(path)
        if kind == "FULL":
            return Selection(
                "FULL", "ALL",
                "shared, unknown, or unmapped path requires complete validation: " + path,
                False,
            )
        if kind != "NEUTRAL" and kind not in kinds:
            kinds.append(kind)

    if not kinds:
        return Selection(
            "FULL", "ALL",
            "no executable/test-impact tool-local path was present; adaptive validation must classify this delta",
            False,
        )

    if len(kinds) != 1:
        return Selection(
            "FULL", "ALL",
            "cross-tool delta requires complete Maven validation",
            False,
        )

    if kinds[0] == "PACKAGE":
        return Selection(
            "TOOL_LOCAL:PACKAGE", PACKAGE_TEST_SET,
            "all executable/test-impact paths are explicitly mapped Package Tool-local",
            True,
        )

    if kinds[0] == "TEST":
        return Selection(
            "TOOL_LOCAL:TEST", TEST_TOOL_TEST_SET,
            "all executable/test-impact paths are explicitly mapped Test Tool-local",
            True,
        )

    return Selection("FULL", "ALL", "unreachable fail-closed classification", False)


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

    paths = changed_paths(os.path.abspath(args.repo), args.base, args.head)
    selection = classify_paths(paths, top_level_closure=args.top_level_closure)

    if args.format == "json":
        print(json.dumps(selection.to_dict(), sort_keys=True))
    else:
        print(render_env(selection))
    return 0


if __name__ == "__main__":
    sys.exit(main())
