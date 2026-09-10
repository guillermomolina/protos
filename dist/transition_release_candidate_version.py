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
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True

from prepare_release_candidate_worktree import (
    parse_selection,
    require_selection,
)

PROJECT_VERSION_RE = re.compile(
    r"(<artifactId>protos</artifactId>\s*<version>)"
    r"([^<]+)"
    r"(</version>)"
)


def fail(message: str) -> "NoReturn":
    raise SystemExit("release candidate version transition failed: " + message)


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


def project_version(text: str) -> str:
    try:
        document = ET.fromstring(text)
    except ET.ParseError as exc:
        fail("pom.xml is not valid XML: " + str(exc))
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    version = document.findtext("m:version", namespaces=ns)
    if not version:
        fail("pom.xml project version is missing")
    return version


def registered_worktrees(root: Path) -> set[Path]:
    result = git(root, "worktree", "list", "--porcelain").stdout
    worktrees: set[Path] = set()
    for line in result.splitlines():
        if line.startswith("worktree "):
            worktrees.add(Path(line[len("worktree "):]).resolve())
    return worktrees


def require_candidate_pretransition(
    root: Path,
    candidate: Path,
    selection: dict[str, str],
) -> str:
    candidate = candidate.resolve()
    if candidate == root.resolve():
        fail("candidate worktree must not be the main checkout")
    if candidate not in registered_worktrees(root):
        fail("candidate path is not a registered worktree")

    head = git(candidate, "rev-parse", "HEAD").stdout.strip()
    baseline = selection["release_baseline_revision"]
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

    status = git(
        candidate,
        "status",
        "--porcelain=v1",
        "--untracked-files=all",
    ).stdout
    if status:
        fail("candidate worktree must be clean before version transition")

    pom_path = candidate / "pom.xml"
    if not pom_path.is_file():
        fail("candidate pom.xml is missing")
    pom = pom_path.read_text(encoding="utf-8")

    actual_version = project_version(pom)
    expected_version = selection["release_baseline_version"]
    if actual_version != expected_version:
        fail(
            "candidate project version mismatch: "
            + repr(actual_version)
            + " != "
            + repr(expected_version)
        )

    baseline_pom = git(candidate, "show", baseline + ":pom.xml").stdout
    if pom != baseline_pom:
        fail("candidate pom.xml bytes do not equal selected baseline pom.xml")

    return pom


def exact_transition(
    baseline_pom: str,
    *,
    snapshot_version: str,
    public_version: str,
) -> str:
    matches = list(PROJECT_VERSION_RE.finditer(baseline_pom))
    if len(matches) != 1:
        fail(
            "pom.xml must contain exactly one root Protos project-version surface; "
            + f"found {len(matches)}"
        )
    match = matches[0]
    if match.group(2) != snapshot_version:
        fail(
            "root Protos project-version surface mismatch: "
            + repr(match.group(2))
            + " != "
            + repr(snapshot_version)
        )
    return (
        baseline_pom[: match.start(2)]
        + public_version
        + baseline_pom[match.end(2) :]
    )


def verify_posttransition(
    candidate: Path,
    selection: dict[str, str],
    expected_pom: str,
) -> None:
    pom_path = candidate / "pom.xml"
    actual_pom = pom_path.read_text(encoding="utf-8")
    if actual_pom != expected_pom:
        fail("candidate pom.xml differs from exact expected transition bytes")

    actual_version = project_version(actual_pom)
    if actual_version != selection["release_version"]:
        fail(
            "candidate public project version mismatch: "
            + repr(actual_version)
            + " != "
            + repr(selection["release_version"])
        )

    changed = [
        line
        for line in git(candidate, "diff", "--name-only").stdout.splitlines()
        if line
    ]
    if changed != ["pom.xml"]:
        fail("candidate transition changed paths other than pom.xml: " + repr(changed))

    staged = git(candidate, "diff", "--cached", "--name-only").stdout
    if staged:
        fail("candidate transition must not stage files")

    git(candidate, "diff", "--check")

    head = git(candidate, "rev-parse", "HEAD").stdout.strip()
    if head != selection["release_baseline_revision"]:
        fail("candidate transition changed HEAD before E4B3")

    detached = git(
        candidate,
        "symbolic-ref",
        "--quiet",
        "--short",
        "HEAD",
        check=False,
    )
    if detached.returncode != 1:
        fail("candidate transition no longer has detached HEAD")


def transition_candidate_version(
    *,
    selection_path: Path,
    candidate: Path,
) -> None:
    root = repository_root().resolve()
    selection = parse_selection(selection_path.resolve())
    require_selection(selection)

    baseline_pom = require_candidate_pretransition(root, candidate, selection)
    expected_pom = exact_transition(
        baseline_pom,
        snapshot_version=selection["release_baseline_version"],
        public_version=selection["release_version"],
    )

    (candidate.resolve() / "pom.xml").write_text(expected_pom, encoding="utf-8")
    try:
        verify_posttransition(candidate.resolve(), selection, expected_pom)
    except BaseException:
        # Restore only the file this invocation owns, and only to the exact bytes
        # verified before mutation.
        (candidate.resolve() / "pom.xml").write_text(
            baseline_pom,
            encoding="utf-8",
        )
        raise

    print("E4B2_SELECTION_CHECK: PASS")
    print("E4B2_DETACHED_BASELINE_CHECK: PASS")
    print("E4B2_PRETRANSITION_CLEAN_CHECK: PASS")
    print("E4B2_EXACT_POM_TRANSITION_CHECK: PASS")
    print("E4B2_ONLY_POM_CHANGED_CHECK: PASS")
    print("E4B2_UNSTAGED_GUARD: PASS")
    print("E4B2_NO_COMMIT_GUARD: PASS")
    print("DIST001_E4B2_VERSION_TRANSITION: PASS")


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Apply only the exact DIST001-E4 selected V-SNAPSHOT -> V project "
            "version transition to an existing E4B1 detached candidate worktree. "
            "This does not stage or commit the change."
        )
    )
    parser.add_argument(
        "--selection",
        default="docs/project/evidence/DIST001/DIST001_E4_SELECTION.txt",
    )
    parser.add_argument("--candidate", required=True)
    args = parser.parse_args()

    transition_candidate_version(
        selection_path=Path(args.selection),
        candidate=Path(args.candidate),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
