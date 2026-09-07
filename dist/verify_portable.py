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
import hashlib
from pathlib import Path, PurePosixPath
import string
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

from release_identity import (
    require_public_version,
    require_snapshot_version,
    validate_release_baseline,
)


def fail(message: str) -> "NoReturn":
    raise SystemExit("distribution identity verification failed: " + message)


def run_git(root: Path, *args: str) -> str:
    return subprocess.run(
        ["git", *args],
        cwd=root,
        check=True,
        text=True,
        capture_output=True,
    ).stdout.strip()


def project_version(root: Path) -> str:
    tree = ET.parse(root / "pom.xml")
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    version = tree.findtext("m:version", namespaces=ns)
    if not version:
        fail("pom.xml project version is missing")
    return version


def parse_key_values(text: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in text.splitlines():
        if not raw or raw.startswith("#"):
            continue
        if "=" not in raw:
            fail("malformed key/value metadata line: " + repr(raw))
        key, value = raw.split("=", 1)
        if not key or key in values:
            fail("duplicate/empty metadata key: " + repr(key))
        values[key] = value
    return values


def safe_relative(path: str) -> bool:
    candidate = PurePosixPath(path)
    return bool(path) and not candidate.is_absolute() and ".." not in candidate.parts


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def verify(args: argparse.Namespace) -> None:
    root = Path(__file__).resolve().parents[1]
    version = project_version(root)
    head = run_git(root, "rev-parse", "HEAD")

    if args.public_prerelease:
        try:
            require_public_version(version)
        except ValueError as exc:
            fail(str(exc))
        if not args.release_baseline:
            fail("--public-prerelease requires --release-baseline")
        if args.allow_dirty_source:
            fail("--public-prerelease cannot be combined with --allow-dirty-source")
        try:
            baseline_version = validate_release_baseline(
                root,
                public_version=version,
                baseline_revision=args.release_baseline,
                source_revision=head,
            )
        except ValueError as exc:
            fail(str(exc))
        artifact_mode = "public-prerelease"
    else:
        try:
            require_snapshot_version(version)
        except ValueError as exc:
            fail(str(exc))
        if args.release_baseline:
            fail("--release-baseline is valid only with --public-prerelease")
        baseline_version = None
        artifact_mode = "development-distribution"

    archive_path = (
        Path(args.archive).resolve()
        if args.archive
        else root / "target" / "distributions" / f"protos-{version}-posix-jvm.zip"
    )
    if not archive_path.is_file():
        fail("archive is missing: " + str(archive_path))

    root_name = f"protos-{version}"
    root_prefix = root_name + "/"
    source_name = root_prefix + "SOURCE.txt"
    sums_name = root_prefix + "SHA256SUMS"

    with zipfile.ZipFile(archive_path) as archive:
        bad = archive.testzip()
        if bad is not None:
            fail("ZIP CRC failure: " + bad)

        names = archive.namelist()
        if len(names) != len(set(names)):
            fail("ZIP contains duplicate entry names")
        if not names:
            fail("ZIP is empty")

        for name in names:
            if not name.startswith(root_prefix):
                fail("entry escapes the single distribution root: " + name)
            if not safe_relative(name):
                fail("unsafe ZIP entry path: " + name)
            if name.endswith("/"):
                fail("DIST001 archive is expected to contain file entries only: " + name)

        if source_name not in names:
            fail("SOURCE.txt is missing")
        if sums_name not in names:
            fail("SHA256SUMS is missing")

        source = parse_key_values(archive.read(source_name).decode("utf-8"))
        expected = {
            "implementation_version": version,
            "source_repository": "https://github.com/guillermomolina/protos",
            "source_path": "/tree/" + source.get("source_revision", ""),
        }
        if args.public_prerelease:
            expected.update(
                {
                    "artifact_kind": "public-prerelease",
                    "public_release": "true",
                    "release_baseline_revision": args.release_baseline,
                    "release_baseline_version": baseline_version,
                    "release_version": version,
                    "release_tag": "v" + version,
                }
            )
        else:
            expected.update(
                {
                    "artifact_kind": "development-distribution",
                    "public_release": "false",
                }
            )
        for key, value in expected.items():
            if source.get(key) != value:
                fail(
                    "SOURCE.txt mismatch for "
                    + key
                    + ": "
                    + repr(source.get(key))
                    + " != "
                    + repr(value)
                )

        if source.get("source_revision") != head:
            fail(
                "SOURCE.txt revision does not match repository HEAD: "
                + repr(source.get("source_revision"))
                + " != "
                + repr(head)
            )

        dirty = source.get("source_dirty")
        if dirty not in {"true", "false"}:
            fail("SOURCE.txt source_dirty is not true/false")
        if args.public_prerelease and dirty != "false":
            fail("public prerelease archive must identify clean source")
        if args.require_clean_source and dirty != "false":
            fail("definitive archive was not built from a clean source tree")
        if not args.allow_dirty_source and not args.require_clean_source and dirty == "true":
            fail("dirty source archive requires --allow-dirty-source")

        sums_text = archive.read(sums_name).decode("utf-8")
        recorded: dict[str, str] = {}
        hexchars = set(string.hexdigits.lower())
        for raw in sums_text.splitlines():
            if not raw:
                continue
            if "  " not in raw:
                fail("malformed SHA256SUMS line: " + repr(raw))
            digest, rel = raw.split("  ", 1)
            if len(digest) != 64 or any(ch.lower() not in hexchars for ch in digest):
                fail("invalid SHA-256 digest in SHA256SUMS: " + repr(digest))
            if not safe_relative(rel):
                fail("unsafe checksum path: " + repr(rel))
            if rel == "SHA256SUMS":
                fail("SHA256SUMS must not checksum itself")
            if rel in recorded:
                fail("duplicate checksum path: " + rel)
            recorded[rel] = digest.lower()

        archive_rel_files = {
            name[len(root_prefix):]
            for name in names
            if name != sums_name
        }
        recorded_files = set(recorded)
        missing = sorted(archive_rel_files - recorded_files)
        extra = sorted(recorded_files - archive_rel_files)
        if missing or extra:
            fail(
                "checksum coverage mismatch; missing="
                + repr(missing)
                + " extra="
                + repr(extra)
            )

        for rel in sorted(recorded):
            actual = sha256(archive.read(root_prefix + rel))
            if actual != recorded[rel]:
                fail(
                    "checksum mismatch for "
                    + rel
                    + ": "
                    + actual
                    + " != "
                    + recorded[rel]
                )

    print("DIST_ARCHIVE_CRC_CHECK: PASS")
    print("DIST_SINGLE_ROOT_CHECK: PASS")
    print("DIST_SOURCE_REVISION_CHECK: PASS revision=" + head)
    print("DIST_ARTIFACT_MODE_CHECK: PASS mode=" + artifact_mode)
    print("DIST_SOURCE_CLEAN_CHECK: " + ("PASS" if dirty == "false" else "DIRTY_ALLOWED"))
    print("DIST_INTERNAL_CHECKSUM_COVERAGE_CHECK: PASS files=" + str(len(recorded)))
    print("DIST_INTERNAL_CHECKSUM_VALUE_CHECK: PASS")
    print("DIST001_B2_VERIFY: PASS")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify DIST001-B2 archive identity and internal checksums without executing Protos."
    )
    parser.add_argument("--archive", help="explicit portable ZIP path")
    parser.add_argument(
        "--public-prerelease",
        action="store_true",
        help=(
            "verify public-prerelease SOURCE identity against the current "
            "non-SNAPSHOT candidate checkout"
        ),
    )
    parser.add_argument(
        "--release-baseline",
        help=(
            "exact 40-hex V-SNAPSHOT development baseline for "
            "--public-prerelease"
        ),
    )
    group = parser.add_mutually_exclusive_group()
    group.add_argument(
        "--allow-dirty-source",
        action="store_true",
        help="allow source_dirty=true for the pre-commit candidate only",
    )
    group.add_argument(
        "--require-clean-source",
        action="store_true",
        help="require source_dirty=false for the definitive committed candidate",
    )
    args = parser.parse_args()
    verify(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
