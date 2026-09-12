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

"""Run the publication validation selected for one immutable candidate commit."""

from __future__ import print_function

import argparse
import json
from pathlib import Path
import subprocess
import sys


class PublicationValidationError(Exception):
    pass


# PERF007 temporary quarantine: these are the Tool-owned Java test families
# already mapped by the deterministic selector. ProtosCliTest intentionally
# remains in ordinary non-Tool validation because it is shared CLI smoke.
NON_TOOL_SUREFIRE_EXCLUDES = (
    "**/ProtosPackage*Test.java,"
    "**/ProtosExternalPackage*Test.java,"
    "**/ProtosWorkspace*Test.java,"
    "**/ProtosTestTool*Test.java"
)


def _git(repo, *args):
    return subprocess.check_output(
        ["git", "-C", str(repo)] + list(args),
        text=True,
    ).strip()


def _resolve_commit(repo, ref):
    try:
        return _git(repo, "rev-parse", str(ref) + "^{commit}")
    except subprocess.CalledProcessError as exc:
        raise PublicationValidationError(
            "cannot resolve commit ref: " + str(ref)
        ) from exc


def verify_candidate_state(repo, head):
    candidate = _resolve_commit(repo, head)
    current = _resolve_commit(repo, "HEAD")
    if candidate != current:
        raise PublicationValidationError(
            "candidate head mismatch: requested %s but worktree HEAD is %s"
            % (candidate, current)
        )

    tracked = subprocess.check_output(
        [
            "git",
            "-C",
            str(repo),
            "status",
            "--porcelain",
            "--untracked-files=no",
        ],
        text=True,
    ).strip()
    if tracked:
        raise PublicationValidationError(
            "candidate worktree has tracked changes after CANDIDATE_SHA"
        )
    return candidate


def parse_selector_result(text):
    try:
        data = json.loads(text)
    except (TypeError, ValueError) as exc:
        raise PublicationValidationError("selector output is not valid JSON") from exc

    if not isinstance(data, dict):
        raise PublicationValidationError("selector output must be a JSON object")

    required = (
        "validation_impact",
        "affected_test_set",
        "full_test_suite",
        "reason",
    )
    missing = [name for name in required if name not in data]
    if missing:
        raise PublicationValidationError(
            "selector output missing fields: " + ",".join(missing)
        )

    for name in required:
        if not isinstance(data[name], str) or not data[name]:
            raise PublicationValidationError(
                "selector field must be a non-empty string: " + name
            )

    impact = data["validation_impact"]
    tests = data["affected_test_set"]
    full = data["full_test_suite"]

    if impact == "FULL":
        if tests != "ALL" or full != "REQUIRED":
            raise PublicationValidationError(
                "FULL selector result has inconsistent affected/full fields"
            )
        return data

    if impact == "FULL:NON_TOOL":
        if tests != "NON_TOOL" or full != "SKIP_ALLOWED":
            raise PublicationValidationError(
                "FULL:NON_TOOL selector result has inconsistent affected/full fields"
            )
        return data

    if impact in ("TOOL_LOCAL:PACKAGE", "TOOL_LOCAL:TEST"):
        if tests == "ALL" or full != "SKIP_ALLOWED":
            raise PublicationValidationError(
                "tool-local selector result has inconsistent affected/full fields"
            )
        return data

    raise PublicationValidationError(
        "selector returned unsupported impact class: " + impact
    )


def invoke_test_ownership_guard(repo):
    guard = Path(repo) / "scripts" / "test_ownership_guard.py"
    if not guard.is_file():
        raise PublicationValidationError(
            "repository test-ownership guard is missing: " + str(guard)
        )
    completed = subprocess.run(
        [sys.executable, str(guard), "--repo", str(repo)],
        cwd=str(repo), text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise PublicationValidationError(
            "repository test-ownership guard failed"
            + ((": " + detail) if detail else "")
        )
    return completed.stdout.strip()


def invoke_source_style_guard(repo, base, head):
    guard = Path(repo) / "scripts" / "source_style_guard.py"
    if not guard.is_file():
        raise PublicationValidationError(
            "repository source-style guard is missing: " + str(guard)
        )
    completed = subprocess.run(
        [sys.executable, str(guard), "--repo", str(repo),
         "--base", str(base), "--head", str(head)],
        cwd=str(repo), text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise PublicationValidationError(
            "repository source-style guard failed" + ((": " + detail) if detail else "")
        )
    return completed.stdout.strip()


def invoke_selector(repo, base, head, top_level_closure):
    selector = Path(repo) / "scripts" / "validation_impact.py"
    if not selector.is_file():
        raise PublicationValidationError(
            "repository validation selector is missing: " + str(selector)
        )

    command = [
        sys.executable,
        str(selector),
        "--repo",
        str(repo),
        "--base",
        str(base),
        "--head",
        str(head),
        "--format",
        "json",
    ]
    if top_level_closure:
        command.append("--top-level-closure")

    completed = subprocess.run(
        command,
        cwd=str(repo),
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        if detail:
            detail = ": " + detail
        raise PublicationValidationError(
            "repository validation selector failed" + detail
        )
    return parse_selector_result(completed.stdout)


def maven_command(selection):
    impact = selection["validation_impact"]
    if impact == "FULL":
        return ["mvn", "test"]
    if impact == "FULL:NON_TOOL":
        return [
            "mvn",
            "-Dsurefire.excludes=" + NON_TOOL_SUREFIRE_EXCLUDES,
            "test",
        ]
    return ["mvn", "-Dtest=" + selection["affected_test_set"], "test"]


def run(repo, base, head, top_level_closure=False):
    repo = Path(repo).resolve()

    try:
        candidate = verify_candidate_state(repo, head)
        ownership_output = invoke_test_ownership_guard(repo)
        source_style_output = invoke_source_style_guard(repo, base, candidate)
        selection = invoke_selector(
            repo,
            base,
            candidate,
            top_level_closure,
        )
    except PublicationValidationError as exc:
        print("PUBLICATION_VALIDATION: FAIL_CLOSED", file=sys.stderr)
        print("PUBLICATION_VALIDATION_ERROR: " + str(exc), file=sys.stderr)
        return 2

    impact = selection["validation_impact"]
    tests = selection["affected_test_set"]

    if source_style_output:
        print(source_style_output)
    print("SOURCE_STYLE_PREVENTION_GATE: PASS")
    print("VALIDATION_IMPACT: " + impact)
    print("AFFECTED_TEST_SET: " + tests)
    print("VALIDATION_REASON: " + selection["reason"])

    if impact == "FULL:NON_TOOL":
        print("TOOL_TESTS: SKIPPED_TEMPORARY_PERF007")
    else:
        print("TOOL_TESTS: INCLUDED_BY_SELECTED_SCOPE")

    if top_level_closure:
        if impact == "FULL:NON_TOOL":
            print("TOP_LEVEL_RECONCILIATION: REQUIRED_NON_TOOL_FULL")
        else:
            print("TOP_LEVEL_RECONCILIATION: REQUIRED_FULL")
    else:
        print("TOP_LEVEL_RECONCILIATION: NOT_REQUIRED_FOR_THIS_CHILD_SLICE")

    completed = subprocess.run(maven_command(selection), cwd=str(repo))

    if completed.returncode != 0:
        if impact == "FULL":
            print("FULL_TEST_SUITE: FAIL")
        elif impact == "FULL:NON_TOOL":
            print("AFFECTED_TESTS: FAIL")
            print(
                "FULL_TEST_SUITE: SKIPPED "
                "(PERF007 temporary Tool-owned-suite quarantine)"
            )
        else:
            print("AFFECTED_TESTS: FAIL")
            print(
                "FULL_TEST_SUITE: SKIPPED "
                "(affected tool-local validation failed before publication)"
            )
        return completed.returncode

    if impact == "FULL":
        print("AFFECTED_TESTS: INCLUDED_IN_FULL_SUITE")
        print("FULL_TEST_SUITE: PASS")
    elif impact == "FULL:NON_TOOL":
        print("AFFECTED_TESTS: PASS_NON_TOOL_BROAD_SUITE")
        print(
            "FULL_TEST_SUITE: SKIPPED "
            "(PERF007 temporary Tool-owned-suite quarantine)"
        )
    else:
        print("AFFECTED_TESTS: PASS")
        print(
            "FULL_TEST_SUITE: SKIPPED "
            "(impact-aware tool-local intermediate publication)"
        )

    print("PUBLICATION_VALIDATION: PASS")
    return 0

def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", required=True)
    parser.add_argument("--top-level-closure", action="store_true")
    args = parser.parse_args(argv)
    return run(
        args.repo,
        args.base,
        args.head,
        top_level_closure=args.top_level_closure,
    )


if __name__ == "__main__":
    sys.exit(main())
