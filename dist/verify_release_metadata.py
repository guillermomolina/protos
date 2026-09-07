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
import zipfile

from prepare_release_metadata import (
    RELEASE_FORMAT,
    parse_key_values,
    read_candidate_archive,
    require_runtime,
    sha256_file,
)


MANIFEST_NAME = "RELEASE_MANIFEST.txt"
NOTES_NAME = "RELEASE_NOTES.md"


def fail(message: str) -> "NoReturn":
    raise SystemExit("release envelope verification failed: " + message)


def require_exact_manifest_keys(manifest: dict[str, str]) -> None:
    expected = {
        "release_envelope_format",
        "release_version",
        "release_tag",
        "prerelease",
        "source_repository",
        "source_revision",
        "release_baseline_revision",
        "release_baseline_version",
        "specification_revision",
        "java_distribution",
        "java_feature",
        "truffle_runtime_version",
        "optimizing_runtime",
        "portable_archive",
        "portable_archive_sha256",
        "portable_checksum",
        "portable_checksum_sha256",
        "release_notes",
        "release_notes_sha256",
    }
    actual = set(manifest)
    if actual != expected:
        fail(
            "manifest key set mismatch; missing="
            + repr(sorted(expected - actual))
            + " extra="
            + repr(sorted(actual - expected))
        )


def require_notes_structure(
    notes: str,
    *,
    source: dict[str, str],
    runtime: dict[str, str],
    manifest: dict[str, str],
) -> None:
    required_fragments = [
        f"# Protos {source['release_version']}",
        "This GitHub Release is a **pre-release**",
        f"- Release version: `{source['release_version']}`",
        f"- Git tag: `{source['release_tag']}`",
        f"- Candidate source revision: `{source['source_revision']}`",
        f"- Development baseline revision: `{source['release_baseline_revision']}`",
        f"- Development baseline version: `{source['release_baseline_version']}`",
        f"- Core specification revision: `{manifest['specification_revision']}`",
        f"- Source repository: `{source['source_repository']}`",
        f"- Java distribution: `{runtime['java_distribution']}`",
        f"- Java feature: `{runtime['java_feature']}`",
        f"- Truffle runtime: `{runtime['truffle_runtime_version']}`",
        f"- Expected optimizing runtime: `{runtime['optimizing_runtime']}`",
        "## Important capabilities",
        "## Important limitations",
        "## Download and verification",
        f"- Portable archive: `{manifest['portable_archive']}`",
        f"- SHA-256: `{manifest['portable_archive_sha256']}`",
        f"- External checksum: `{manifest['portable_checksum']}`",
        f"sha256sum -c {manifest['portable_checksum']}",
    ]
    for fragment in required_fragments:
        if fragment not in notes:
            fail("release notes identity/structure mismatch: " + repr(fragment))

    capability_section = notes.split("## Important capabilities", 1)[1].split(
        "## Important limitations", 1
    )[0]
    limitation_section = notes.split("## Important limitations", 1)[1].split(
        "## Download and verification", 1
    )[0]
    if not any(line.startswith("- ") for line in capability_section.splitlines()):
        fail("release notes contain no explicit capability claim")
    if not any(line.startswith("- ") for line in limitation_section.splitlines()):
        fail("release notes contain no explicit limitation claim")


def verify_envelope(archive: Path, envelope_dir: Path) -> None:
    archive = archive.resolve()
    envelope_dir = envelope_dir.resolve()

    try:
        source, runtime, _ = read_candidate_archive(archive)
    except (zipfile.BadZipFile, UnicodeDecodeError, OSError) as exc:
        fail("candidate archive cannot be read: " + str(exc))
    require_runtime(runtime)

    manifest_path = envelope_dir / MANIFEST_NAME
    notes_path = envelope_dir / NOTES_NAME
    checksum_path = envelope_dir / (archive.name + ".sha256")

    for path in [manifest_path, notes_path, checksum_path]:
        if not path.is_file():
            fail("required envelope file is missing: " + path.name)

    expected_names = {
        MANIFEST_NAME,
        NOTES_NAME,
        archive.name + ".sha256",
    }
    actual_names = {p.name for p in envelope_dir.iterdir() if p.is_file()}
    if actual_names != expected_names:
        fail(
            "release envelope file set mismatch; missing="
            + repr(sorted(expected_names - actual_names))
            + " extra="
            + repr(sorted(actual_names - expected_names))
        )

    manifest = parse_key_values(
        manifest_path.read_text(encoding="utf-8"),
        label=MANIFEST_NAME,
    )
    require_exact_manifest_keys(manifest)

    archive_sha = sha256_file(archive)
    checksum_sha = sha256_file(checksum_path)
    notes_sha = sha256_file(notes_path)

    expected_manifest = {
        "release_envelope_format": RELEASE_FORMAT,
        "release_version": source["release_version"],
        "release_tag": source["release_tag"],
        "prerelease": "true",
        "source_repository": source["source_repository"],
        "source_revision": source["source_revision"],
        "release_baseline_revision": source["release_baseline_revision"],
        "release_baseline_version": source["release_baseline_version"],
        "java_distribution": runtime["java_distribution"],
        "java_feature": runtime["java_feature"],
        "truffle_runtime_version": runtime["truffle_runtime_version"],
        "optimizing_runtime": runtime["optimizing_runtime"],
        "portable_archive": archive.name,
        "portable_archive_sha256": archive_sha,
        "portable_checksum": checksum_path.name,
        "portable_checksum_sha256": checksum_sha,
        "release_notes": notes_path.name,
        "release_notes_sha256": notes_sha,
    }
    for key, expected in expected_manifest.items():
        actual = manifest.get(key)
        if actual != expected:
            fail(f"manifest {key} mismatch: {actual!r} != {expected!r}")

    spec_revision = manifest.get("specification_revision", "")
    parts = spec_revision.split(".")
    if (
        len(parts) != 3
        or any(not part.isdigit() for part in parts)
        or any(len(part) > 1 and part.startswith("0") for part in parts)
    ):
        fail("manifest specification_revision is not canonical numeric X.Y.Z")

    expected_checksum = f"{archive_sha}  {archive.name}\n"
    actual_checksum = checksum_path.read_text(encoding="utf-8")
    if actual_checksum != expected_checksum:
        fail("portable external checksum content mismatch")

    notes = notes_path.read_text(encoding="utf-8")
    require_notes_structure(
        notes,
        source=source,
        runtime=runtime,
        manifest=manifest,
    )

    print("RELEASE_ENVELOPE_FILESET_CHECK: PASS")
    print("RELEASE_ENVELOPE_MANIFEST_IDENTITY_CHECK: PASS")
    print("RELEASE_ENVELOPE_ARCHIVE_DIGEST_CHECK: PASS sha256=" + archive_sha)
    print("RELEASE_ENVELOPE_CHECKSUM_DIGEST_CHECK: PASS sha256=" + checksum_sha)
    print("RELEASE_ENVELOPE_NOTES_DIGEST_CHECK: PASS sha256=" + notes_sha)
    print("RELEASE_ENVELOPE_NOTES_IDENTITY_CHECK: PASS")
    print("DIST001_E3C2_VERIFY: PASS")


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Verify an E3B release metadata envelope against one exact "
            "DIST001 public-prerelease candidate archive."
        )
    )
    parser.add_argument("--archive", required=True)
    parser.add_argument("--envelope-dir", required=True)
    args = parser.parse_args()
    verify_envelope(Path(args.archive), Path(args.envelope_dir))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
