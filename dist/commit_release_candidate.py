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

import argparse
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True

from prepare_release_candidate_worktree import parse_selection, require_selection
from transition_release_candidate_version import (
    exact_transition,
    project_version,
    registered_worktrees,
)


def fail(message: str) -> "NoReturn":
    raise SystemExit("release candidate commit failed: " + message)


def git(
    root: Path,
    *args: str,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=root,
        check=check,
        text=True,
        capture_output=True,
    )


def repository_root() -> Path:
    return Path(__file__).resolve().parents[1]


def ref_snapshot(root: Path) -> tuple[str, ...]:
    result = git(
        root,
        "for-each-ref",
        "--format=%(refname) %(objectname)",
        "refs/heads",
        "refs/tags",
    ).stdout
    return tuple(sorted(line for line in result.splitlines() if line))


def exact_expected_pom(candidate: Path, selection: dict[str, str]) -> str:
    baseline = selection["release_baseline_revision"]
    baseline_pom = git(candidate, "show", baseline + ":pom.xml").stdout
    return exact_transition(
        baseline_pom,
        snapshot_version=selection["release_baseline_version"],
        public_version=selection["release_version"],
    )


def require_precommit_candidate(
    root: Path,
    candidate: Path,
    selection: dict[str, str],
) -> str:
    candidate = candidate.resolve()
    if candidate == root.resolve():
        fail("candidate worktree must not be the main checkout")
    if candidate not in registered_worktrees(root):
        fail("candidate path is not a registered worktree")

    baseline = selection["release_baseline_revision"]
    head = git(candidate, "rev-parse", "HEAD").stdout.strip()
    if head != baseline:
        fail(f"candidate HEAD mismatch: {head!r} != {baseline!r}")

    detached = git(
        candidate,
        "symbolic-ref",
        "--quiet",
        "--short",
        "HEAD",
        check=False,
    )
    if detached.returncode == 0:
        fail("candidate worktree is attached to branch: " + detached.stdout.strip())
    if detached.returncode != 1:
        fail("cannot determine candidate detached state")

    staged = [
        line
        for line in git(candidate, "diff", "--cached", "--name-only").stdout.splitlines()
        if line
    ]
    if staged:
        fail("candidate must be completely unstaged before E4B3 commit")

    changed = [
        line
        for line in git(candidate, "diff", "--name-only").stdout.splitlines()
        if line
    ]
    if changed != ["pom.xml"]:
        fail("candidate must have exactly one unstaged pom.xml change: " + repr(changed))

    untracked = [
        line
        for line in git(candidate, "ls-files", "--others", "--exclude-standard").stdout.splitlines()
        if line
    ]
    if untracked:
        fail("candidate has untracked paths before commit: " + repr(untracked))

    expected = exact_expected_pom(candidate, selection)
    actual = (candidate / "pom.xml").read_text(encoding="utf-8")
    if actual != expected:
        fail("candidate pom.xml is not the exact E4B2 transition")

    if project_version(actual) != selection["release_version"]:
        fail("candidate pom.xml does not carry the selected public version")

    git(candidate, "diff", "--check")

    for variable in ("GIT_AUTHOR_IDENT", "GIT_COMMITTER_IDENT"):
        ident = git(candidate, "var", variable, check=False)
        if ident.returncode != 0 or not ident.stdout.strip():
            fail("Git identity unavailable: " + variable)

    return expected


def verify_candidate_commit(
    root: Path,
    candidate: Path,
    selection: dict[str, str],
    expected_pom: str,
    refs_before: tuple[str, ...],
    commit_message: str,
) -> str:
    baseline = selection["release_baseline_revision"]
    head = git(candidate, "rev-parse", "HEAD").stdout.strip()
    if head == baseline:
        fail("candidate commit did not advance detached HEAD")
    if len(head) != 40:
        fail("candidate commit SHA is not full length")

    parent = git(candidate, "rev-parse", "HEAD^").stdout.strip()
    if parent != baseline:
        fail(f"candidate parent mismatch: {parent!r} != {baseline!r}")

    changed = [
        line
        for line in git(candidate, "diff", "--name-only", baseline, head).stdout.splitlines()
        if line
    ]
    if changed != ["pom.xml"]:
        fail("candidate commit changes paths other than pom.xml: " + repr(changed))

    committed_pom = git(candidate, "show", head + ":pom.xml").stdout
    if committed_pom != expected_pom:
        fail("candidate committed pom.xml is not the exact E4B2 transition")

    message = git(candidate, "log", "-1", "--format=%B").stdout.rstrip("\n")
    if message != commit_message:
        fail("candidate commit message changed unexpectedly")

    status = git(
        candidate,
        "status",
        "--porcelain=v1",
        "--untracked-files=all",
    ).stdout
    if status:
        fail("candidate worktree is not clean after commit")

    detached = git(
        candidate,
        "symbolic-ref",
        "--quiet",
        "--short",
        "HEAD",
        check=False,
    )
    if detached.returncode != 1:
        fail("candidate commit no longer has detached HEAD")

    refs_after = ref_snapshot(root)
    if refs_after != refs_before:
        fail("candidate commit changed local branch/tag refs")

    return head


def commit_candidate(
    *,
    selection_path: Path,
    candidate: Path,
) -> str:
    root = repository_root().resolve()
    selection = parse_selection(selection_path.resolve())
    require_selection(selection)

    candidate = candidate.resolve()
    expected_pom = require_precommit_candidate(root, candidate, selection)
    refs_before = ref_snapshot(root)
    message = "release: materialize Protos " + selection["release_version"] + " candidate"

    git(candidate, "add", "--", "pom.xml")
    try:
        staged = [
            line
            for line in git(candidate, "diff", "--cached", "--name-only").stdout.splitlines()
            if line
        ]
        if staged != ["pom.xml"]:
            fail("E4B3 staging scope is not exactly pom.xml: " + repr(staged))
        indexed_pom = git(candidate, "show", ":pom.xml").stdout
        if indexed_pom != expected_pom:
            fail("staged pom.xml is not the exact E4B2 transition")

        commit = git(
            candidate,
            "-c",
            "core.hooksPath=/dev/null",
            "-c",
            "commit.gpgSign=false",
            "commit",
            "--no-verify",
            "-m",
            message,
            check=False,
        )
        if commit.returncode != 0:
            git(candidate, "reset", "--quiet", "HEAD", "--", "pom.xml", check=False)
            detail = commit.stderr.strip() or commit.stdout.strip()
            fail("git commit failed: " + detail)
    except BaseException:
        staged_now = git(
            candidate,
            "diff",
            "--cached",
            "--name-only",
            check=False,
        ).stdout
        if "pom.xml" in staged_now.splitlines():
            git(candidate, "reset", "--quiet", "HEAD", "--", "pom.xml", check=False)
        raise

    head = verify_candidate_commit(
        root,
        candidate,
        selection,
        expected_pom,
        refs_before,
        message,
    )

    print("E4B3A_SELECTION_CHECK: PASS")
    print("E4B3A_EXACT_E4B2_INPUT_CHECK: PASS")
    print("E4B3A_STAGING_SCOPE_CHECK: PASS")
    print("E4B3A_DETACHED_COMMIT_CHECK: PASS")
    print("E4B3A_SINGLE_PARENT_CHECK: PASS")
    print("E4B3A_RELEASE_ONLY_DIFF_CHECK: PASS")
    print("E4B3A_BRANCH_TAG_REF_GUARD: PASS")
    print("E4B3A_POSTCOMMIT_CLEAN_CHECK: PASS")
    print("DIST001_E4B3A_CANDIDATE_COMMIT: PASS sha=" + head)
    return head


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Commit exactly the validated E4B2 pom.xml transition in an existing "
            "detached candidate worktree. This creates no branch/tag and performs "
            "no push or GitHub Release publication."
        )
    )
    parser.add_argument(
        "--selection",
        default="docs/project/evidence/DIST001/DIST001_E4_SELECTION.txt",
    )
    parser.add_argument("--candidate", required=True)
    args = parser.parse_args()
    commit_candidate(
        selection_path=Path(args.selection),
        candidate=Path(args.candidate),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
