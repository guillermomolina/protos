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
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True

FULL_SHA_RE = re.compile(r"[0-9a-f]{40}")
SELECTION_FORMAT = "protos-dist001-e4-selection-v1"
SELECTION_KEYS = {
    "selection_format",
    "selection_authorized",
    "selection_authorization_basis",
    "release_publication_authorized",
    "release_baseline_revision",
    "release_baseline_version",
    "release_version",
    "release_tag",
    "specification_revision",
    "i023_status",
    "b007_status",
    "candidate_source_revision",
    "git_tag_created",
    "github_release_created",
    "release_assets_published",
}
SNAPSHOT_RE = re.compile(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)-SNAPSHOT")
PUBLIC_RE = re.compile(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)")


def fail(message: str) -> "NoReturn":
    raise SystemExit("release candidate worktree preparation failed: " + message)


def run(root: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=root,
        check=check,
        text=True,
        capture_output=True,
    )


def repository_root() -> Path:
    return Path(__file__).resolve().parents[1]


def parse_selection(path: Path) -> dict[str, str]:
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        fail("cannot read selection file: " + str(exc))

    result: dict[str, str] = {}
    for line_number, raw in enumerate(text.splitlines(), start=1):
        if not raw:
            continue
        if "=" not in raw:
            fail(f"selection line {line_number} is not key=value")
        key, value = raw.split("=", 1)
        if not key or not value:
            fail(f"selection line {line_number} has empty key/value")
        if key in result:
            fail("duplicate selection key: " + key)
        result[key] = value

    actual = set(result)
    if actual != SELECTION_KEYS:
        fail(
            "selection key set mismatch; missing="
            + repr(sorted(SELECTION_KEYS - actual))
            + " extra="
            + repr(sorted(actual - SELECTION_KEYS))
        )
    return result


def require_selection(selection: dict[str, str]) -> None:
    expected_fixed = {
        "selection_format": SELECTION_FORMAT,
        "selection_authorized": "true",
        "selection_authorization_basis": "explicit-user-decision",
        "release_publication_authorized": "false",
        "i023_status": "CLOSED",
        "b007_status": "CLOSED",
        "candidate_source_revision": "UNMATERIALIZED",
        "git_tag_created": "false",
        "github_release_created": "false",
        "release_assets_published": "false",
    }
    for key, expected in expected_fixed.items():
        actual = selection[key]
        if actual != expected:
            fail(f"selection {key} mismatch: {actual!r} != {expected!r}")

    baseline = selection["release_baseline_revision"]
    snapshot = selection["release_baseline_version"]
    public = selection["release_version"]
    tag = selection["release_tag"]

    if FULL_SHA_RE.fullmatch(baseline) is None:
        fail("release_baseline_revision must be exact 40-hex SHA")
    if SNAPSHOT_RE.fullmatch(snapshot) is None:
        fail("release_baseline_version is not canonical V-SNAPSHOT")
    if PUBLIC_RE.fullmatch(public) is None:
        fail("release_version is not canonical V")
    if snapshot != public + "-SNAPSHOT":
        fail("release version does not exactly derive from selected snapshot")
    if tag != "v" + public:
        fail("release_tag does not exactly derive from release_version")


def pom_project_version(text: str) -> str:
    try:
        document = ET.fromstring(text)
    except ET.ParseError as exc:
        fail("baseline pom.xml is not valid XML: " + str(exc))
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    version = document.findtext("m:version", namespaces=ns)
    if not version:
        fail("baseline pom.xml project version is missing")
    return version


def require_selected_baseline(root: Path, selection: dict[str, str]) -> None:
    baseline = selection["release_baseline_revision"]

    exists = run(root, "cat-file", "-e", baseline + "^{commit}", check=False)
    if exists.returncode != 0:
        fail("selected baseline commit is unavailable")

    origin_main = run(root, "rev-parse", "origin/main").stdout.strip()
    ancestry = run(root, "merge-base", "--is-ancestor", baseline, origin_main, check=False)
    if ancestry.returncode == 1:
        fail("selected baseline is not an ancestor of origin/main")
    if ancestry.returncode != 0:
        fail("cannot verify selected baseline ancestry")

    pom = run(root, "show", baseline + ":pom.xml").stdout
    version = pom_project_version(pom)
    if version != selection["release_baseline_version"]:
        fail(
            "selected baseline project version mismatch: "
            + repr(version)
            + " != "
            + repr(selection["release_baseline_version"])
        )


def normalized(path: Path) -> Path:
    return Path(os.path.abspath(os.path.normpath(str(path))))


def inside(path: Path, parent: Path) -> bool:
    try:
        return os.path.commonpath([str(path), str(parent)]) == str(parent)
    except ValueError:
        return False


def existing_worktree_paths(root: Path) -> set[Path]:
    result = run(root, "worktree", "list", "--porcelain").stdout
    paths: set[Path] = set()
    for line in result.splitlines():
        if line.startswith("worktree "):
            paths.add(normalized(Path(line[len("worktree ") :])))
    return paths


def branch_refs(root: Path) -> tuple[str, ...]:
    output = run(root, "for-each-ref", "--format=%(refname)", "refs/heads").stdout
    return tuple(sorted(line for line in output.splitlines() if line))


def verify_created_worktree(
    root: Path,
    destination: Path,
    selection: dict[str, str],
    branches_before: tuple[str, ...],
) -> None:
    baseline = selection["release_baseline_revision"]
    head = run(destination, "rev-parse", "HEAD").stdout.strip()
    if head != baseline:
        fail(f"created worktree HEAD mismatch: {head!r} != {baseline!r}")

    detached = run(destination, "symbolic-ref", "--quiet", "--short", "HEAD", check=False)
    if detached.returncode == 0:
        fail("created worktree is attached to branch: " + detached.stdout.strip())
    if detached.returncode != 1:
        fail("cannot determine created worktree detached state")

    status = run(destination, "status", "--porcelain=v1", "--untracked-files=all").stdout
    if status:
        fail("created worktree is not clean")

    pom = (destination / "pom.xml").read_text(encoding="utf-8")
    version = pom_project_version(pom)
    if version != selection["release_baseline_version"]:
        fail(
            "created worktree project version mismatch: "
            + repr(version)
            + " != "
            + repr(selection["release_baseline_version"])
        )

    branches_after = branch_refs(root)
    if branches_after != branches_before:
        fail("detached worktree creation changed local branch refs")


def prepare_worktree(*, selection_path: Path, destination: Path) -> Path:
    root = repository_root().resolve()
    selection = parse_selection(selection_path.resolve())
    require_selection(selection)
    require_selected_baseline(root, selection)

    destination = normalized(destination)
    git_dir = normalized(root / ".git")

    if inside(destination, root):
        fail("candidate worktree destination must be outside the main checkout")
    if inside(destination, git_dir):
        fail("candidate worktree destination must be outside .git")
    if destination.exists():
        fail("candidate worktree destination already exists")
    if destination in existing_worktree_paths(root):
        fail("candidate worktree destination is already registered")

    parent = destination.parent
    if not parent.exists() or not parent.is_dir():
        fail("candidate worktree parent directory must already exist")

    branches_before = branch_refs(root)
    created = False
    try:
        add = run(
            root,
            "worktree",
            "add",
            "--detach",
            str(destination),
            selection["release_baseline_revision"],
            check=False,
        )
        if add.returncode != 0:
            detail = add.stderr.strip() or add.stdout.strip()
            fail("git worktree add failed: " + detail)
        created = True

        verify_created_worktree(root, destination, selection, branches_before)
    except BaseException:
        if created:
            run(root, "worktree", "remove", "--force", str(destination), check=False)
        raise

    print("E4B1_SELECTION_CHECK: PASS")
    print("E4B1_BASELINE_ANCESTRY_CHECK: PASS")
    print("E4B1_DESTINATION_ISOLATION_CHECK: PASS")
    print("E4B1_DETACHED_HEAD_CHECK: PASS")
    print("E4B1_CLEAN_WORKTREE_CHECK: PASS")
    print("E4B1_BRANCH_CREATION_GUARD: PASS")
    print("E4B1_BASELINE_VERSION_CHECK: PASS")
    print("DIST001_E4B1_WORKTREE: PASS path=" + str(destination))
    return destination


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Create one local detached release-candidate worktree at the exact "
            "DIST001-E4 selected baseline. This does not modify the candidate, "
            "create a branch/tag, or publish anything."
        )
    )
    parser.add_argument(
        "--selection",
        default="docs/project/evidence/DIST001/DIST001_E4_SELECTION.txt",
        help="E4A exact selection record",
    )
    parser.add_argument(
        "--destination",
        required=True,
        help="new worktree directory outside the main checkout",
    )
    args = parser.parse_args()
    prepare_worktree(selection_path=Path(args.selection), destination=Path(args.destination))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
