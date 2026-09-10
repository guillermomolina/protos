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

from prepare_release_candidate_worktree import (
    parse_selection,
    prepare_worktree,
    require_selection,
)
from transition_release_candidate_version import (
    exact_transition,
    project_version,
    registered_worktrees,
    transition_candidate_version,
)
from commit_release_candidate import commit_candidate


def fail(message: str) -> "NoReturn":
    raise SystemExit("release candidate materialization failed: " + message)


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


def exact_expected_pom(root: Path, selection: dict[str, str]) -> str:
    baseline = selection["release_baseline_revision"]
    baseline_pom = git(root, "show", baseline + ":pom.xml").stdout
    return exact_transition(
        baseline_pom,
        snapshot_version=selection["release_baseline_version"],
        public_version=selection["release_version"],
    )


def candidate_refs(root: Path, candidate_sha: str) -> tuple[str, ...]:
    output = git(
        root,
        "for-each-ref",
        "--points-at",
        candidate_sha,
        "--format=%(refname)",
        "refs/heads",
        "refs/tags",
    ).stdout
    return tuple(sorted(line for line in output.splitlines() if line))


def verify_materialized_candidate(
    root: Path,
    candidate: Path,
    selection: dict[str, str],
) -> str:
    candidate = candidate.resolve()
    if candidate not in registered_worktrees(root):
        fail("candidate path is not a registered worktree")

    baseline = selection["release_baseline_revision"]
    expected_pom = exact_expected_pom(root, selection)
    head = git(candidate, "rev-parse", "HEAD").stdout.strip()

    if head == baseline:
        fail("candidate HEAD is still the selected baseline")
    if len(head) != 40:
        fail("candidate source revision is not a full 40-hex SHA")

    parent = git(candidate, "rev-parse", "HEAD^").stdout.strip()
    if parent != baseline:
        fail(f"candidate parent mismatch: {parent!r} != {baseline!r}")

    parents = git(candidate, "rev-list", "--parents", "-n", "1", head).stdout.split()
    if len(parents) != 2:
        fail("candidate commit must have exactly one parent")

    changed = [
        line
        for line in git(candidate, "diff", "--name-only", baseline, head).stdout.splitlines()
        if line
    ]
    if changed != ["pom.xml"]:
        fail("candidate commit changes paths other than pom.xml: " + repr(changed))

    committed_pom = git(candidate, "show", head + ":pom.xml").stdout
    if committed_pom != expected_pom:
        fail("candidate committed pom.xml is not the exact selected transition")
    if project_version(committed_pom) != selection["release_version"]:
        fail("candidate committed project version is not the selected public version")

    message = git(candidate, "log", "-1", "--format=%B").stdout.rstrip("\n")
    expected_message = "release: materialize Protos " + selection["release_version"] + " candidate"
    if message != expected_message:
        fail("candidate commit message mismatch")

    status = git(
        candidate,
        "status",
        "--porcelain=v1",
        "--untracked-files=all",
    ).stdout
    if status:
        fail("candidate worktree is not clean")

    detached = git(
        candidate,
        "symbolic-ref",
        "--quiet",
        "--short",
        "HEAD",
        check=False,
    )
    if detached.returncode != 1:
        fail("candidate worktree is not detached after materialization")

    refs = candidate_refs(root, head)
    if refs:
        fail("candidate commit is referenced by local branch/tag refs: " + repr(refs))

    return head


def resume_or_materialize(
    *,
    selection_path: Path,
    candidate: Path,
) -> str:
    root = repository_root().resolve()
    selection = parse_selection(selection_path.resolve())
    require_selection(selection)
    candidate = candidate.resolve()
    baseline = selection["release_baseline_revision"]

    created_here = False
    candidate_commit_exists = False
    try:
        if not candidate.exists():
            prepare_worktree(
                selection_path=selection_path,
                destination=candidate,
            )
            created_here = True
        else:
            if candidate not in registered_worktrees(root):
                fail("existing candidate path is not a registered worktree")

        head = git(candidate, "rev-parse", "HEAD").stdout.strip()
        if head != baseline:
            candidate_commit_exists = True
            result = verify_materialized_candidate(root, candidate, selection)
            print("E4B3B1_RECOVERY_REUSE_CHECK: PASS")
            print("DIST001_E4B3B1_MATERIALIZATION: PASS sha=" + result)
            return result

        detached = git(
            candidate,
            "symbolic-ref",
            "--quiet",
            "--short",
            "HEAD",
            check=False,
        )
        if detached.returncode != 1:
            fail("baseline candidate worktree is not detached")

        expected_pom = exact_expected_pom(root, selection)
        status = git(
            candidate,
            "status",
            "--porcelain=v1",
            "--untracked-files=all",
        ).stdout

        if not status:
            transition_candidate_version(
                selection_path=selection_path,
                candidate=candidate,
            )
        else:
            changed = [
                line
                for line in git(candidate, "diff", "--name-only").stdout.splitlines()
                if line
            ]
            staged = [
                line
                for line in git(candidate, "diff", "--cached", "--name-only").stdout.splitlines()
                if line
            ]
            untracked = [
                line
                for line in git(candidate, "ls-files", "--others", "--exclude-standard").stdout.splitlines()
                if line
            ]
            if changed != ["pom.xml"] or staged or untracked:
                fail("existing baseline worktree is not a resumable E4B2 state")
            if (candidate / "pom.xml").read_text(encoding="utf-8") != expected_pom:
                fail("existing baseline worktree pom.xml is not exact E4B2 output")
            print("E4B3B1_RECOVERY_E4B2_CHECK: PASS")

        head = commit_candidate(
            selection_path=selection_path,
            candidate=candidate,
        )
        candidate_commit_exists = True
        verified = verify_materialized_candidate(root, candidate, selection)
        if verified != head:
            fail("candidate SHA changed during post-commit verification")

        print("E4B3B1_COMPOSITION_CHECK: PASS")
        print("E4B3B1_RELEASE_ONLY_COMMIT_CHECK: PASS")
        print("E4B3B1_NO_BRANCH_TAG_REF_CHECK: PASS")
        print("DIST001_E4B3B1_MATERIALIZATION: PASS sha=" + verified)
        return verified
    except BaseException:
        # If this invocation created only a baseline/dirty preparation worktree,
        # remove it so a retry starts clean. Once a candidate commit exists, keep
        # the detached worktree registered so its commit remains reachable and
        # diagnosable/reusable.
        if created_here and not candidate_commit_exists:
            git(
                root,
                "worktree",
                "remove",
                "--force",
                str(candidate),
                check=False,
            )
        raise


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Compose DIST001-E4B1/E4B2/E4B3A to materialize or safely resume one "
            "detached candidate commit. This helper does not mutate the E4 "
            "selection record and performs no push/tag/GitHub Release action."
        )
    )
    parser.add_argument(
        "--selection",
        default="docs/project/evidence/DIST001/DIST001_E4_SELECTION.txt",
    )
    parser.add_argument("--candidate", required=True)
    args = parser.parse_args()

    resume_or_materialize(
        selection_path=Path(args.selection),
        candidate=Path(args.candidate),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
