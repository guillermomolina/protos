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
import io
from pathlib import Path, PurePosixPath
import re
import string
import subprocess
import sys
import zipfile

sys.dont_write_bytecode = True

SHA40_RE = re.compile(r"[0-9a-f]{40}")
SHA64_RE = re.compile(r"[0-9a-f]{64}")

ARTIFACT_KEYS = {
    "artifact_record_format",
    "release_baseline_revision",
    "candidate_source_revision",
    "release_version",
    "archive_name",
    "archive_sha256",
    "archive_built",
    "archive_identity_independently_verified",
    "release_envelope_generated",
    "candidate_audit_materialized",
    "release_publication_authorized",
    "git_tag_created",
    "github_release_created",
    "release_assets_published",
}

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

SOURCE_KEYS = {
    "implementation_version",
    "source_revision",
    "source_dirty",
    "source_repository",
    "source_path",
    "artifact_kind",
    "public_release",
    "release_baseline_revision",
    "release_baseline_version",
    "release_version",
    "release_tag",
}

RUNTIME_KEYS = {
    "distribution_format",
    "java_feature",
    "java_vendor_contains",
    "java_distribution",
    "truffle_runtime_version",
    "optimizing_runtime",
    "runtime_evidence",
    "unsupported_runtime_override",
}


def fail(message: str) -> "NoReturn":
    raise SystemExit("candidate archive identity verification failed: " + message)


def git(root: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=root,
        check=check,
        text=True,
        capture_output=True,
    )


def parse_key_values(
    text: str,
    *,
    expected_keys: set[str] | None = None,
    label: str,
) -> dict[str, str]:
    values: dict[str, str] = {}
    for line_number, raw in enumerate(text.splitlines(), start=1):
        if not raw:
            continue
        if raw.startswith("#"):
            continue
        if "=" not in raw:
            fail(f"{label} line {line_number} is not key=value")
        key, value = raw.split("=", 1)
        if not key or not value:
            fail(f"{label} line {line_number} has empty key/value")
        if key in values:
            fail(f"{label} duplicate key: {key}")
        values[key] = value
    if expected_keys is not None and set(values) != expected_keys:
        fail(
            f"{label} key set mismatch; missing="
            + repr(sorted(expected_keys - set(values)))
            + " extra="
            + repr(sorted(set(values) - expected_keys))
        )
    return values


def safe_relative(path: str) -> bool:
    p = PurePosixPath(path)
    return bool(path) and not p.is_absolute() and ".." not in p.parts


def sha256_path(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def registered_candidate_worktree(root: Path, candidate: str) -> Path:
    output = git(root, "worktree", "list", "--porcelain").stdout
    matches: list[Path] = []
    path: Path | None = None
    head: str | None = None

    def flush() -> None:
        nonlocal path, head
        if path is not None and head == candidate:
            matches.append(path.resolve())
        path = None
        head = None

    for line in output.splitlines() + [""]:
        if not line:
            flush()
        elif line.startswith("worktree "):
            path = Path(line[len("worktree "):])
        elif line.startswith("HEAD "):
            head = line[len("HEAD "):]

    if len(matches) != 1:
        fail(
            "expected exactly one registered worktree at candidate SHA; found "
            + repr([str(x) for x in matches])
        )
    return matches[0]


def parse_manifest(data: bytes) -> dict[str, str]:
    text = data.decode("utf-8", errors="strict").replace("\r\n", "\n")
    physical = text.split("\n")
    logical: list[str] = []
    for raw in physical:
        if raw.startswith(" "):
            if not logical:
                fail("JAR manifest begins with continuation line")
            logical[-1] += raw[1:]
        else:
            logical.append(raw)

    result: dict[str, str] = {}
    for raw in logical:
        if not raw:
            continue
        if ": " not in raw:
            fail("malformed JAR manifest line: " + repr(raw))
        key, value = raw.split(": ", 1)
        if key in result:
            fail("duplicate JAR manifest key: " + key)
        result[key] = value
    return result


def verify(
    *,
    repository_root: Path,
    selection_path: Path,
    artifact_record_path: Path,
    expected_verification_state: str,
    archive_override: Path | None = None,
) -> Path:
    root = repository_root.resolve()
    selection = parse_key_values(
        selection_path.resolve().read_text(encoding="utf-8"),
        expected_keys=SELECTION_KEYS,
        label="selection",
    )
    artifact = parse_key_values(
        artifact_record_path.resolve().read_text(encoding="utf-8"),
        expected_keys=ARTIFACT_KEYS,
        label="artifact record",
    )

    required_selection = {
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
    for key, expected in required_selection.items():
        if selection[key] != expected:
            fail(f"selection {key} mismatch: {selection[key]!r} != {expected!r}")

    if expected_verification_state not in {"false", "true"}:
        fail("expected verification state must be false or true")

    required_artifact = {
        "artifact_record_format": "protos-dist001-e4-candidate-artifact-v1",
        "archive_built": "true",
        "archive_identity_independently_verified": expected_verification_state,
        "release_envelope_generated": "false",
        "candidate_audit_materialized": "false",
        "release_publication_authorized": "false",
        "git_tag_created": "false",
        "github_release_created": "false",
        "release_assets_published": "false",
    }
    for key, expected in required_artifact.items():
        if artifact[key] != expected:
            fail(f"artifact record {key} mismatch: {artifact[key]!r} != {expected!r}")

    baseline = selection["release_baseline_revision"]
    candidate = selection["candidate_source_revision"]
    public_version = selection["release_version"]
    snapshot_version = selection["release_baseline_version"]
    future_tag = selection["release_tag"]

    if SHA40_RE.fullmatch(baseline) is None or SHA40_RE.fullmatch(candidate) is None:
        fail("selection baseline/candidate must be exact lowercase 40-hex")
    if snapshot_version != public_version + "-SNAPSHOT":
        fail("snapshot/public version relation is incoherent")
    if future_tag != "v" + public_version:
        fail("future tag does not derive from public version")

    cross = {
        "release_baseline_revision": baseline,
        "candidate_source_revision": candidate,
        "release_version": public_version,
    }
    for key, expected in cross.items():
        if artifact[key] != expected:
            fail(f"artifact/selection mismatch for {key}")

    expected_archive_name = f"protos-{public_version}-posix-jvm.zip"
    if artifact["archive_name"] != expected_archive_name:
        fail("artifact archive_name mismatch")
    if SHA64_RE.fullmatch(artifact["archive_sha256"]) is None:
        fail("artifact archive_sha256 must be lowercase 64-hex")

    worktree = registered_candidate_worktree(root, candidate)
    if git(worktree, "rev-parse", "HEAD").stdout.strip() != candidate:
        fail("candidate worktree HEAD mismatch")
    if git(
        worktree,
        "symbolic-ref",
        "--quiet",
        "--short",
        "HEAD",
        check=False,
    ).returncode != 1:
        fail("candidate worktree is not detached")
    if git(
        worktree,
        "status",
        "--porcelain=v1",
        "--untracked-files=all",
    ).stdout:
        fail("candidate worktree is not clean")

    archive_path = (
        archive_override.resolve()
        if archive_override is not None
        else worktree / "target" / "distributions" / expected_archive_name
    )
    if not archive_path.is_file():
        fail("persisted archive bytes are missing: " + str(archive_path))

    actual_outer = sha256_path(archive_path)
    if actual_outer != artifact["archive_sha256"]:
        fail(
            "external archive SHA-256 mismatch: "
            + actual_outer
            + " != "
            + artifact["archive_sha256"]
        )

    root_name = f"protos-{public_version}"
    root_prefix = root_name + "/"
    required_entries = {
        root_prefix + "SOURCE.txt",
        root_prefix + "RUNTIME.txt",
        root_prefix + "DEPENDENCIES.txt",
        root_prefix + "SHA256SUMS",
        root_prefix + "LICENSE.TXT",
        root_prefix + "README.md",
        root_prefix + "bin/protos",
        root_prefix + "lib/protos.jar",
        root_prefix + "lib/runtime/truffle-runtime-24.0.0.jar",
    }

    with zipfile.ZipFile(archive_path) as archive:
        bad = archive.testzip()
        if bad is not None:
            fail("ZIP CRC failure: " + bad)
        names = archive.namelist()
        if not names:
            fail("ZIP is empty")
        if len(names) != len(set(names)):
            fail("ZIP contains duplicate entry names")
        for name in names:
            if not name.startswith(root_prefix):
                fail("ZIP entry escapes exact single root: " + name)
            if not safe_relative(name):
                fail("unsafe ZIP path: " + name)
            if name.endswith("/"):
                fail("archive must contain file entries only: " + name)
        missing = sorted(required_entries - set(names))
        if missing:
            fail("required archive identity entries missing: " + repr(missing))

        source = parse_key_values(
            archive.read(root_prefix + "SOURCE.txt").decode("utf-8"),
            expected_keys=SOURCE_KEYS,
            label="SOURCE.txt",
        )
        expected_source = {
            "implementation_version": public_version,
            "source_revision": candidate,
            "source_dirty": "false",
            "source_repository": "https://github.com/guillermomolina/protos",
            "source_path": "/tree/" + candidate,
            "artifact_kind": "public-prerelease",
            "public_release": "true",
            "release_baseline_revision": baseline,
            "release_baseline_version": snapshot_version,
            "release_version": public_version,
            "release_tag": future_tag,
        }
        if source != expected_source:
            differences = {
                key: (source.get(key), expected_source.get(key))
                for key in sorted(SOURCE_KEYS)
                if source.get(key) != expected_source.get(key)
            }
            fail("SOURCE.txt exact identity mismatch: " + repr(differences))

        runtime = parse_key_values(
            archive.read(root_prefix + "RUNTIME.txt").decode("utf-8"),
            expected_keys=RUNTIME_KEYS,
            label="RUNTIME.txt",
        )
        expected_runtime = {
            "distribution_format": "protos-portable-posix-jvm-v1",
            "java_feature": "22",
            "java_vendor_contains": "GraalVM",
            "java_distribution": "GraalVM Community Edition for JDK 22",
            "truffle_runtime_version": "24.0.0",
            "optimizing_runtime": "HotSpotTruffleRuntime",
            "runtime_evidence": "docs/project/PERF002_TRUFFLE_COMPILABILITY.md",
            "unsupported_runtime_override": "PROTOS_ALLOW_UNSUPPORTED_RUNTIME=1",
        }
        if runtime != expected_runtime:
            differences = {
                key: (runtime.get(key), expected_runtime.get(key))
                for key in sorted(RUNTIME_KEYS)
                if runtime.get(key) != expected_runtime.get(key)
            }
            fail("RUNTIME.txt exact contract mismatch: " + repr(differences))

        sums_name = root_prefix + "SHA256SUMS"
        recorded: dict[str, str] = {}
        hexchars = set(string.hexdigits.lower())
        for raw in archive.read(sums_name).decode("utf-8").splitlines():
            if not raw:
                continue
            if "  " not in raw:
                fail("malformed SHA256SUMS line: " + repr(raw))
            digest, rel = raw.split("  ", 1)
            if len(digest) != 64 or any(ch.lower() not in hexchars for ch in digest):
                fail("invalid internal SHA-256 digest: " + repr(digest))
            if not safe_relative(rel) or rel == "SHA256SUMS":
                fail("unsafe/self-referential checksum path: " + repr(rel))
            if rel in recorded:
                fail("duplicate internal checksum path: " + rel)
            recorded[rel] = digest.lower()

        archive_rel = {
            name[len(root_prefix):]
            for name in names
            if name != sums_name
        }
        if set(recorded) != archive_rel:
            fail(
                "internal checksum coverage mismatch; missing="
                + repr(sorted(archive_rel - set(recorded)))
                + " extra="
                + repr(sorted(set(recorded) - archive_rel))
            )
        for rel, digest in sorted(recorded.items()):
            actual = sha256_bytes(archive.read(root_prefix + rel))
            if actual != digest:
                fail("internal checksum value mismatch for " + rel)

        jar_bytes = archive.read(root_prefix + "lib/protos.jar")
        with zipfile.ZipFile(io.BytesIO(jar_bytes)) as jar:
            manifest_name = "META-INF/MANIFEST.MF"
            if manifest_name not in jar.namelist():
                fail("lib/protos.jar manifest missing")
            manifest = parse_manifest(jar.read(manifest_name))
            if manifest.get("Implementation-Version") != public_version:
                fail(
                    "lib/protos.jar Implementation-Version mismatch: "
                    + repr(manifest.get("Implementation-Version"))
                )

    print("E4C2_ARTIFACT_RECORD_CROSSCHECK: PASS")
    print("E4C2_EXTERNAL_SHA256_CHECK: PASS sha256=" + actual_outer)
    print("E4C2_SINGLE_ROOT_CRC_CHECK: PASS")
    print("E4C2_SOURCE_EXACT_IDENTITY_CHECK: PASS")
    print("E4C2_RUNTIME_EXACT_CONTRACT_CHECK: PASS")
    print("E4C2_INTERNAL_CHECKSUM_COVERAGE_CHECK: PASS")
    print("E4C2_INTERNAL_CHECKSUM_VALUE_CHECK: PASS")
    print("E4C2_JAR_IMPLEMENTATION_VERSION_CHECK: PASS")
    print("E4C2_CANDIDATE_WORKTREE_CHECK: PASS path=" + str(worktree))
    print("DIST001_E4C2_ARCHIVE_IDENTITY: PASS archive=" + str(archive_path))
    return archive_path


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Independently verify the exact persisted DIST001-E4C public-"
            "prerelease archive without regenerating it."
        )
    )
    parser.add_argument("--repository-root", required=True)
    parser.add_argument("--selection", required=True)
    parser.add_argument("--artifact-record", required=True)
    parser.add_argument(
        "--expect-verification-state",
        required=True,
        choices=("false", "true"),
    )
    parser.add_argument("--archive", default=None)
    args = parser.parse_args()

    verify(
        repository_root=Path(args.repository_root),
        selection_path=Path(args.selection),
        artifact_record_path=Path(args.artifact_record),
        expected_verification_state=args.expect_verification_state,
        archive_override=Path(args.archive) if args.archive else None,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
