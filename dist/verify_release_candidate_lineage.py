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

SHA_RE = re.compile(r"[0-9a-f]{40}")
SNAPSHOT_RE = re.compile(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)-SNAPSHOT")
PUBLIC_RE = re.compile(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)")
PROJECT_VERSION_RE = re.compile(
    r"(<artifactId>protos</artifactId>\s*<version>)"
    r"([^<]+)"
    r"(</version>)"
)

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


def fail(message: str) -> "NoReturn":
    raise SystemExit("release candidate lineage verification failed: " + message)


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


def parse_selection(path: Path) -> dict[str, str]:
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        fail("cannot read selection file: " + str(exc))

    values: dict[str, str] = {}
    for line_number, raw in enumerate(text.splitlines(), start=1):
        if not raw:
            continue
        if "=" not in raw:
            fail(f"selection line {line_number} is not key=value")
        key, value = raw.split("=", 1)
        if not key or not value:
            fail(f"selection line {line_number} has empty key/value")
        if key in values:
            fail("duplicate selection key: " + key)
        values[key] = value

    actual = set(values)
    if actual != SELECTION_KEYS:
        fail(
            "selection key set mismatch; missing="
            + repr(sorted(SELECTION_KEYS - actual))
            + " extra="
            + repr(sorted(actual - SELECTION_KEYS))
        )
    return values


def require_selection(selection: dict[str, str]) -> None:
    fixed = {
        "selection_format": "protos-dist001-e4-selection-v1",
        "selection_authorized": "true",
        "selection_authorization_basis": "explicit-user-decision",
        "release_publication_authorized": "false",
        "i023_status": "CLOSED",
        "b007_status": "CLOSED",
        "git_tag_created": "false",
        "github_release_created": "false",
        "release_assets_published": "false",
    }
    for key, expected in fixed.items():
        if selection[key] != expected:
            fail(f"selection {key} mismatch: {selection[key]!r} != {expected!r}")

    baseline = selection["release_baseline_revision"]
    candidate = selection["candidate_source_revision"]
    snapshot = selection["release_baseline_version"]
    public = selection["release_version"]

    if SHA_RE.fullmatch(baseline) is None:
        fail("release_baseline_revision must be exact lowercase 40-hex")
    if SHA_RE.fullmatch(candidate) is None:
        fail("candidate_source_revision must be exact lowercase 40-hex")
    if candidate == baseline:
        fail("candidate_source_revision must differ from baseline")
    if SNAPSHOT_RE.fullmatch(snapshot) is None:
        fail("release_baseline_version is not canonical V-SNAPSHOT")
    if PUBLIC_RE.fullmatch(public) is None:
        fail("release_version is not canonical V")
    if snapshot != public + "-SNAPSHOT":
        fail("release version does not derive exactly from selected snapshot")
    if selection["release_tag"] != "v" + public:
        fail("release_tag does not derive exactly from release_version")


def project_version(text: str) -> str:
    try:
        root = ET.fromstring(text)
    except ET.ParseError as exc:
        fail("pom.xml is not valid XML: " + str(exc))
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    version = root.findtext("m:version", namespaces=ns)
    if not version:
        fail("pom.xml project version missing")
    return version


def exact_expected_candidate_pom(
    baseline_pom: str,
    snapshot_version: str,
    public_version: str,
) -> str:
    matches = list(PROJECT_VERSION_RE.finditer(baseline_pom))
    if len(matches) != 1:
        fail(
            "baseline pom.xml must have exactly one root Protos project-version "
            f"surface; found {len(matches)}"
        )
    match = matches[0]
    if match.group(2) != snapshot_version:
        fail(
            "baseline root project version mismatch: "
            + repr(match.group(2))
            + " != "
            + repr(snapshot_version)
        )
    return (
        baseline_pom[: match.start(2)]
        + public_version
        + baseline_pom[match.end(2) :]
    )


def registered_worktree_for_candidate(root: Path, candidate: str) -> Path:
    output = git(root, "worktree", "list", "--porcelain").stdout
    paths: list[Path] = []
    current_path: Path | None = None
    current_head: str | None = None

    def flush() -> None:
        nonlocal current_path, current_head
        if current_path is not None and current_head == candidate:
            paths.append(current_path.resolve())
        current_path = None
        current_head = None

    for line in output.splitlines() + [""]:
        if not line:
            flush()
        elif line.startswith("worktree "):
            current_path = Path(line[len("worktree "):])
        elif line.startswith("HEAD "):
            current_head = line[len("HEAD "):]

    if len(paths) != 1:
        fail(
            "candidate must have exactly one registered worktree; found "
            + repr([str(path) for path in paths])
        )
    return paths[0]


def refs_pointing_at(root: Path, candidate: str) -> tuple[str, ...]:
    output = git(
        root,
        "for-each-ref",
        "--points-at",
        candidate,
        "--format=%(refname)",
        "refs/heads",
        "refs/tags",
        "refs/remotes",
    ).stdout
    return tuple(sorted(line for line in output.splitlines() if line))


def verify_lineage(
    *,
    selection_path: Path,
    repository_root_override: Path | None = None,
) -> Path:
    root = (
        repository_root_override.resolve()
        if repository_root_override is not None
        else repository_root().resolve()
    )
    selection = parse_selection(selection_path.resolve())
    require_selection(selection)

    baseline = selection["release_baseline_revision"]
    candidate = selection["candidate_source_revision"]

    for revision, label in ((baseline, "selected baseline"), (candidate, "candidate")):
        exists = git(root, "cat-file", "-e", revision + "^{commit}", check=False)
        if exists.returncode != 0:
            fail(label + " commit object is unavailable locally")

    origin_main = git(root, "rev-parse", "origin/main").stdout.strip()
    ancestry = git(
        root,
        "merge-base",
        "--is-ancestor",
        baseline,
        origin_main,
        check=False,
    )
    if ancestry.returncode != 0:
        fail("selected baseline is not an ancestor of origin/main")

    parents = git(root, "rev-list", "--parents", "-n", "1", candidate).stdout.split()
    if len(parents) != 2:
        fail("candidate must have exactly one parent")
    if parents[1] != baseline:
        fail(f"candidate parent mismatch: {parents[1]!r} != {baseline!r}")

    name_status = [
        line
        for line in git(
            root,
            "diff",
            "--name-status",
            baseline,
            candidate,
        ).stdout.splitlines()
        if line
    ]
    if name_status != ["M\tpom.xml"]:
        fail("baseline -> candidate path/status diff is not exactly M pom.xml: " + repr(name_status))

    baseline_pom = git(root, "show", baseline + ":pom.xml").stdout
    candidate_pom = git(root, "show", candidate + ":pom.xml").stdout
    if project_version(baseline_pom) != selection["release_baseline_version"]:
        fail("baseline POM project version does not match frozen selection")
    if project_version(candidate_pom) != selection["release_version"]:
        fail("candidate POM project version does not match frozen public version")

    expected_candidate_pom = exact_expected_candidate_pom(
        baseline_pom,
        selection["release_baseline_version"],
        selection["release_version"],
    )
    if candidate_pom != expected_candidate_pom:
        fail(
            "candidate pom.xml is not byte-exact baseline POM with only the "
            "selected root project-version transition"
        )

    worktree = registered_worktree_for_candidate(root, candidate)
    head = git(worktree, "rev-parse", "HEAD").stdout.strip()
    if head != candidate:
        fail("candidate worktree HEAD differs from persisted candidate SHA")

    detached = git(
        worktree,
        "symbolic-ref",
        "--quiet",
        "--short",
        "HEAD",
        check=False,
    )
    if detached.returncode != 1:
        fail("candidate worktree is not detached")

    status = git(
        worktree,
        "status",
        "--porcelain=v1",
        "--untracked-files=all",
    ).stdout
    if status:
        fail("candidate worktree is not clean")

    refs = refs_pointing_at(root, candidate)
    if refs:
        fail("candidate has forbidden local/remote refs: " + repr(refs))

    local_tag = git(
        root,
        "show-ref",
        "--verify",
        "--quiet",
        "refs/tags/" + selection["release_tag"],
        check=False,
    )
    if local_tag.returncode == 0:
        fail("future release tag already exists locally")
    if local_tag.returncode not in (0, 1):
        fail("cannot determine local future release tag availability")

    remote_tag = git(
        root,
        "ls-remote",
        "--exit-code",
        "--tags",
        "origin",
        "refs/tags/" + selection["release_tag"],
        check=False,
    )
    if remote_tag.returncode == 0:
        fail("future release tag already exists on origin")
    if remote_tag.returncode != 2:
        fail("cannot determine origin future release tag availability")

    print("E4B4_SELECTION_IDENTITY_CHECK: PASS")
    print("E4B4_BASELINE_ANCESTRY_CHECK: PASS")
    print("E4B4_SINGLE_PARENT_CHECK: PASS")
    print("E4B4_RELEASE_ONLY_PATH_CHECK: PASS")
    print("E4B4_EXACT_POM_TRANSITION_CHECK: PASS")
    print("E4B4_REGISTERED_WORKTREE_CHECK: PASS path=" + str(worktree))
    print("E4B4_DETACHED_CLEAN_WORKTREE_CHECK: PASS")
    print("E4B4_NO_BRANCH_TAG_REMOTE_REF_CHECK: PASS")
    print("E4B4_FUTURE_TAG_AVAILABILITY_CHECK: PASS")
    print("E4B4_PUBLICATION_AUTHORIZATION_GUARD: PASS")
    print("DIST001_E4B4_LINEAGE: PASS candidate=" + candidate)
    return worktree


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Independently verify the persisted DIST001-E4 release candidate "
            "lineage and local-only detached reachability. This performs no "
            "materialization, mutation, branch/tag creation, push or release."
        )
    )
    parser.add_argument(
        "--selection",
        default="docs/project/evidence/DIST001/DIST001_E4_SELECTION.txt",
    )
    parser.add_argument(
        "--repository-root",
        default=None,
        help=(
            "Explicit repository root. Required when invoking this verifier "
            "directly from an external patch bundle before it is installed "
            "under the repository dist/ directory."
        ),
    )
    args = parser.parse_args()
    verify_lineage(
        selection_path=Path(args.selection),
        repository_root_override=(
            Path(args.repository_root)
            if args.repository_root is not None
            else None
        ),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
