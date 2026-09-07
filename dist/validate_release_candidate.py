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

# The candidate gate inspects Git cleanliness while importing repository-local
# release tooling. Prevent Python from creating __pycache__ entries that would
# make an otherwise clean candidate checkout appear dirty during validation.
sys.dont_write_bytecode = True

from prepare_release_metadata import parse_key_values, read_candidate_archive
from verify_release_metadata import verify_envelope


AUDIT_FORMAT = "protos-release-candidate-audit-v1"
PASS_VALUE = "PASS"
SPEC_HEADING_RE = re.compile(r"^## \[([0-9]+\.[0-9]+\.[0-9]+)\]", re.MULTILINE)


def fail(message: str) -> "NoReturn":
    raise SystemExit("release candidate validation failed: " + message)


def git(
    root: Path,
    *args: str,
    check: bool = True,
    capture_output: bool = True,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=root,
        check=check,
        text=True,
        capture_output=capture_output,
    )


def repository_root() -> Path:
    return Path(__file__).resolve().parents[1]


def current_specification_revision(root: Path) -> str:
    path = root / "spec/PROTOS_SPEC_CHANGELOG.md"
    if not path.is_file():
        fail("specification changelog is missing")
    match = SPEC_HEADING_RE.search(path.read_text(encoding="utf-8"))
    if match is None:
        fail("current specification revision cannot be determined")
    return match.group(1)


def require_exact_audit_keys(audit: dict[str, str]) -> None:
    expected = {
        "release_candidate_audit_format",
        "candidate_selection_authorized",
        "selection_authorization_basis",
        "release_publication_authorized",
        "source_revision",
        "release_baseline_revision",
        "release_version",
        "release_tag",
        "specification_revision",
        "capabilities_review",
        "limitations_review",
        "known_blockers_review",
    }
    actual = set(audit)
    if actual != expected:
        fail(
            "candidate audit key set mismatch; missing="
            + repr(sorted(expected - actual))
            + " extra="
            + repr(sorted(actual - expected))
        )


def verify_candidate_audit(
    audit_path: Path,
    *,
    source: dict[str, str],
    specification_revision: str,
) -> None:
    if not audit_path.is_file():
        fail("candidate audit file is missing: " + str(audit_path))

    audit = parse_key_values(
        audit_path.read_text(encoding="utf-8"),
        label=audit_path.name,
    )
    require_exact_audit_keys(audit)

    expected = {
        "release_candidate_audit_format": AUDIT_FORMAT,
        "candidate_selection_authorized": "true",
        "selection_authorization_basis": "explicit-user-decision",
        "release_publication_authorized": "false",
        "source_revision": source["source_revision"],
        "release_baseline_revision": source["release_baseline_revision"],
        "release_version": source["release_version"],
        "release_tag": source["release_tag"],
        "specification_revision": specification_revision,
        "capabilities_review": PASS_VALUE,
        "limitations_review": PASS_VALUE,
        "known_blockers_review": PASS_VALUE,
    }
    for key, value in expected.items():
        actual = audit.get(key)
        if actual != value:
            fail(f"candidate audit {key} mismatch: {actual!r} != {value!r}")


def require_candidate_checkout(root: Path, source: dict[str, str]) -> None:
    head = git(root, "rev-parse", "HEAD").stdout.strip()
    if source["source_revision"] != head:
        fail(
            "candidate SOURCE revision does not match candidate checkout HEAD: "
            + repr(source["source_revision"])
            + " != "
            + repr(head)
        )

    status = git(root, "status", "--porcelain=v1", "--untracked-files=all").stdout
    if status:
        fail("candidate checkout must be clean")


def require_release_only_lineage(root: Path, source: dict[str, str]) -> None:
    baseline = source["release_baseline_revision"]
    version = source["release_version"]

    changed = [
        line
        for line in git(root, "diff", "--name-only", baseline + "..HEAD").stdout.splitlines()
        if line
    ]
    if changed != ["pom.xml"]:
        fail(
            "candidate lineage contains non-release-preparation paths: "
            + repr(changed)
        )

    baseline_pom = git(root, "show", baseline + ":pom.xml").stdout
    candidate_pom = (root / "pom.xml").read_text(encoding="utf-8")
    old = f"<version>{version}-SNAPSHOT</version>"
    new = f"<version>{version}</version>"
    if baseline_pom.count(old) != 1:
        fail(
            "baseline pom.xml does not contain one unambiguous project "
            "release-version transition"
        )
    expected_candidate_pom = baseline_pom.replace(old, new, 1)
    if candidate_pom != expected_candidate_pom:
        fail(
            "candidate pom.xml differs from baseline by more than the exact "
            "V-SNAPSHOT -> V transition"
        )


def require_envelope_specification(
    envelope_dir: Path,
    *,
    specification_revision: str,
) -> None:
    manifest_path = envelope_dir / "RELEASE_MANIFEST.txt"
    if not manifest_path.is_file():
        fail("release envelope manifest is missing")
    manifest = parse_key_values(
        manifest_path.read_text(encoding="utf-8"),
        label=manifest_path.name,
    )
    actual = manifest.get("specification_revision")
    if actual != specification_revision:
        fail(
            "release envelope specification_revision mismatch: "
            + repr(actual)
            + " != "
            + repr(specification_revision)
        )


def require_tag_available(root: Path, tag: str) -> None:
    local = git(
        root,
        "show-ref",
        "--verify",
        "--quiet",
        "refs/tags/" + tag,
        check=False,
    )
    if local.returncode == 0:
        fail("release tag already exists locally: " + tag)
    if local.returncode != 1:
        fail("cannot determine local release-tag availability: " + tag)

    remote = git(
        root,
        "ls-remote",
        "--exit-code",
        "--tags",
        "origin",
        "refs/tags/" + tag,
        check=False,
    )
    if remote.returncode == 0:
        fail("release tag already exists on origin: " + tag)
    if remote.returncode != 2:
        detail = remote.stderr.strip() or remote.stdout.strip()
        fail("cannot determine origin release-tag availability: " + detail)


def run_release_b5(
    root: Path,
    *,
    archive: Path,
    baseline_revision: str,
) -> None:
    command = [
        "sh",
        str(root / "dist/validate_portable.sh"),
        "--archive",
        str(archive),
        "--public-prerelease",
        "--release-baseline",
        baseline_revision,
        "--require-clean-source",
    ]
    result = subprocess.run(
        command,
        cwd=root,
        env=os.environ.copy(),
        check=False,
    )
    if result.returncode != 0:
        fail("release-aware B5 validation failed")


def validate_candidate(
    *,
    archive: Path,
    envelope_dir: Path,
    audit_path: Path,
) -> None:
    root = repository_root()
    archive = archive.resolve()
    envelope_dir = envelope_dir.resolve()
    audit_path = audit_path.resolve()

    source, _runtime, _root_name = read_candidate_archive(archive)
    specification_revision = current_specification_revision(root)

    require_candidate_checkout(root, source)
    require_release_only_lineage(root, source)
    verify_envelope(archive, envelope_dir)
    require_envelope_specification(
        envelope_dir,
        specification_revision=specification_revision,
    )
    verify_candidate_audit(
        audit_path,
        source=source,
        specification_revision=specification_revision,
    )
    require_tag_available(root, source["release_tag"])

    run_release_b5(
        root,
        archive=archive,
        baseline_revision=source["release_baseline_revision"],
    )

    print("RELEASE_CANDIDATE_CHECKOUT_IDENTITY_CHECK: PASS")
    print("RELEASE_CANDIDATE_RELEASE_ONLY_LINEAGE_CHECK: PASS")
    print("RELEASE_CANDIDATE_ENVELOPE_CHECK: PASS")
    print("RELEASE_CANDIDATE_SELECTION_AUTHORIZATION_CHECK: PASS")
    print("RELEASE_CANDIDATE_CLAIMS_AUDIT_CHECK: PASS")
    print("RELEASE_CANDIDATE_SPEC_REVISION_CHECK: PASS revision=" + specification_revision)
    print("RELEASE_CANDIDATE_TAG_AVAILABILITY_CHECK: PASS tag=" + source["release_tag"])
    print("RELEASE_CANDIDATE_B5_CHECK: PASS")
    print("RELEASE_PUBLICATION_AUTHORIZED: NO")
    print("DIST001_E3C3_CANDIDATE_GATE: PASS")


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Compose the DIST001 public-prerelease candidate validation gate. "
            "This validates only; it never creates a tag or GitHub Release."
        )
    )
    parser.add_argument("--archive", required=True)
    parser.add_argument("--envelope-dir", required=True)
    parser.add_argument("--candidate-audit", required=True)
    args = parser.parse_args()

    validate_candidate(
        archive=Path(args.archive),
        envelope_dir=Path(args.envelope_dir),
        audit_path=Path(args.candidate_audit),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
