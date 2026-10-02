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

"""DIST010-A canonical exact-revision artifact-set construction.

Composes the existing Native, portable-JVM, and D064 producers for one clean
checkout revision and binds their outputs with a deterministic manifest.
Building an artifact set is not a release: no release metadata, tag, GitHub
Release, or upload is produced here.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
from typing import NoReturn
import xml.etree.ElementTree as ET
import zipfile

from release_identity import FULL_SHA_RE, require_snapshot_version

REPOSITORY = "guillermomolina/protos"
MANIFEST_NAME = "ARTIFACT-SET.json"
CHECKSUMS_NAME = "SHA256SUMS"
MANIFEST_FORMAT = "protos-artifact-set"
MANIFEST_FORMAT_VERSION = 1
SET_KIND = "development-artifact-set"

KIND_JVM = "jvm-distribution"
KIND_NATIVE = "native-distribution"
KIND_D064 = "stdlib-documentation"
KIND_D067 = "stdlib-documentation-coverage"

D064_FORMAT_NAME = "protos-documentation"
D064_EXTRACTOR = (
    "com.guillermomolina.protos.documentation."
    "ProtosStandardLibraryDocumentationExtractor"
)

# RUNTIME.txt keys that distinguish one platform/toolchain artifact identity
# from another. Native output is host/toolchain specific; recording these keeps
# two different Native platforms from sharing one logical identity.
JVM_IDENTITY_KEYS = (
    "distribution_format",
    "graalvm_release",
    "java_version",
    "truffle_runtime_version",
)
NATIVE_IDENTITY_KEYS = (
    "distribution_format",
    "graalvm_release",
    "native_build_container",
    "target_os",
    "target_arch",
    "linkage",
    "libc_family",
    "libc_abi_min",
    "cpu_isa_assumption",
)


class ArtifactSetError(Exception):
    pass


def fail(message: str) -> NoReturn:
    raise SystemExit("artifact-set build failed: " + message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def parse_properties(text: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in text.splitlines():
        if not line or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key] = value
    return values


def artifact_names(version: str) -> dict[str, str]:
    """Fixed artifact-set member names, except Native (platform-suffixed)."""
    return {
        KIND_JVM: f"protos-{version}-posix-jvm.zip",
        KIND_D064: f"protos-{version}-stdlib-documentation.json",
        KIND_D067: f"protos-{version}-stdlib-documentation-coverage.txt",
    }


def native_archive(directory: Path, version: str) -> Path:
    matches = sorted(directory.glob(f"protos-{version}-native-*.zip"))
    if len(matches) != 1:
        raise ArtifactSetError(
            "expected exactly one Native distribution archive; found "
            + repr([path.name for path in matches])
        )
    return matches[0]


def archive_identity(
    archive: Path,
    *,
    revision: str,
    version: str,
    identity_keys: tuple[str, ...],
) -> dict[str, str]:
    """Verify in-archive source identity and return its platform identity."""
    if not archive.is_file():
        raise ArtifactSetError("required artifact is missing: " + archive.name)
    root_name = archive.name.removesuffix(".zip").removesuffix("-posix-jvm")
    try:
        with zipfile.ZipFile(archive) as handle:
            source = parse_properties(
                handle.read(f"{root_name}/SOURCE.txt").decode("utf-8")
            )
            runtime = parse_properties(
                handle.read(f"{root_name}/RUNTIME.txt").decode("utf-8")
            )
    except (KeyError, zipfile.BadZipFile, UnicodeDecodeError) as exc:
        raise ArtifactSetError(
            f"{archive.name} has no readable SOURCE.txt/RUNTIME.txt: {exc}"
        ) from exc

    expected = {
        "source_revision": revision,
        "source_dirty": "false",
        "implementation_version": version,
        "public_release": "false",
    }
    for key, value in expected.items():
        if source.get(key) != value:
            raise ArtifactSetError(
                f"{archive.name} SOURCE.txt {key}={source.get(key)!r} "
                f"does not match artifact set {value!r}"
            )
    missing = [key for key in identity_keys if not runtime.get(key)]
    if missing:
        raise ArtifactSetError(
            f"{archive.name} RUNTIME.txt is missing identity keys: "
            + ", ".join(missing)
        )
    return {key: runtime[key] for key in identity_keys}


def d064_identity(path: Path, *, revision: str) -> dict[str, str]:
    if not path.is_file():
        raise ArtifactSetError("required artifact is missing: " + path.name)
    data = path.read_bytes()
    if not data.endswith(b"\n") or b"\r" in data:
        raise ArtifactSetError(path.name + " is not LF-terminated D064 output")
    try:
        document = json.loads(data.decode("utf-8"))
        form = document["format"]
        provenance = document["provenance"]
    except (UnicodeDecodeError, ValueError, KeyError, TypeError) as exc:
        raise ArtifactSetError(path.name + " is not a D064 document") from exc
    if form.get("name") != D064_FORMAT_NAME:
        raise ArtifactSetError(path.name + " is not a D064 document")
    if (
        provenance.get("kind") != "repositoryRevision"
        or provenance.get("repository") != REPOSITORY
        or provenance.get("revision") != revision
    ):
        raise ArtifactSetError(
            f"{path.name} provenance {provenance!r} does not match "
            f"{REPOSITORY}@{revision}"
        )
    return {
        "format": f"{form['name']}/{form['major']}.{form['minor']}",
        "provenance_repository": provenance["repository"],
        "provenance_revision": provenance["revision"],
    }


def d067_identity(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise ArtifactSetError("required artifact is missing: " + path.name)
    text = path.read_text(encoding="utf-8")
    if not text.startswith("modules.total="):
        raise ArtifactSetError(path.name + " is not a D067 coverage report")
    return {}


def describe_artifacts(
    directory: Path, *, revision: str, version: str
) -> list[dict[str, object]]:
    """Derive manifest entries from the bytes present in ``directory``."""
    names = artifact_names(version)
    jvm = directory / names[KIND_JVM]
    native = native_archive(directory, version)
    d064 = directory / names[KIND_D064]
    d067 = directory / names[KIND_D067]
    identities = {
        KIND_JVM: (jvm, archive_identity(
            jvm, revision=revision, version=version,
            identity_keys=JVM_IDENTITY_KEYS,
        )),
        KIND_NATIVE: (native, archive_identity(
            native, revision=revision, version=version,
            identity_keys=NATIVE_IDENTITY_KEYS,
        )),
        KIND_D064: (d064, d064_identity(d064, revision=revision)),
        KIND_D067: (d067, d067_identity(d067)),
    }
    entries = [
        {
            "kind": kind,
            "path": path.name,
            "sha256": sha256(path),
            "identity": identity,
        }
        for kind, (path, identity) in identities.items()
    ]
    return sorted(entries, key=lambda entry: str(entry["path"]))


def render_manifest(
    *, revision: str, version: str, artifacts: list[dict[str, object]]
) -> bytes:
    # No wall-clock data: the same artifact bytes always render the same manifest.
    document = {
        "format": {"name": MANIFEST_FORMAT, "version": MANIFEST_FORMAT_VERSION},
        "set_kind": SET_KIND,
        "public_release": False,
        "repository": REPOSITORY,
        "revision": revision,
        "version": version,
        "artifacts": sorted(artifacts, key=lambda entry: str(entry["path"])),
    }
    text = json.dumps(document, indent=2, sort_keys=True, ensure_ascii=False)
    return (text + "\n").encode("utf-8")


def render_checksums(artifacts: list[dict[str, object]]) -> bytes:
    lines = sorted(f"{entry['sha256']}  {entry['path']}" for entry in artifacts)
    return ("\n".join(lines) + "\n").encode("utf-8")


def write_envelope(directory: Path, *, revision: str, version: str) -> None:
    artifacts = describe_artifacts(directory, revision=revision, version=version)
    (directory / CHECKSUMS_NAME).write_bytes(render_checksums(artifacts))
    # The manifest is written last; its presence marks a completed envelope.
    (directory / MANIFEST_NAME).write_bytes(
        render_manifest(revision=revision, version=version, artifacts=artifacts)
    )


def verify_artifact_set(
    directory: Path, *, expect_revision: str | None = None
) -> dict[str, object]:
    """Re-derive the envelope from current bytes and require exact agreement."""
    manifest_path = directory / MANIFEST_NAME
    if not manifest_path.is_file():
        raise ArtifactSetError("artifact set has no " + MANIFEST_NAME)
    recorded = manifest_path.read_bytes()
    try:
        manifest = json.loads(recorded.decode("utf-8"))
        revision = manifest["revision"]
        version = manifest["version"]
    except (UnicodeDecodeError, ValueError, KeyError, TypeError) as exc:
        raise ArtifactSetError(MANIFEST_NAME + " is malformed") from exc
    if (
        manifest.get("format")
        != {"name": MANIFEST_FORMAT, "version": MANIFEST_FORMAT_VERSION}
        or manifest.get("repository") != REPOSITORY
        or not isinstance(revision, str)
        or FULL_SHA_RE.fullmatch(revision) is None
    ):
        raise ArtifactSetError(MANIFEST_NAME + " has no exact Protos revision")
    if expect_revision is not None and revision != expect_revision:
        raise ArtifactSetError(
            f"artifact set revision {revision} is not expected {expect_revision}"
        )

    artifacts = describe_artifacts(directory, revision=revision, version=version)
    recorded_by_path = {
        entry.get("path"): entry for entry in manifest.get("artifacts", [])
    }
    for entry in artifacts:
        if recorded_by_path.get(entry["path"]) != entry:
            raise ArtifactSetError(
                f"{entry['path']} does not match its manifest record"
            )
    expected = render_manifest(
        revision=revision, version=version, artifacts=artifacts
    )
    if recorded != expected:
        raise ArtifactSetError(
            MANIFEST_NAME + " is not the canonical envelope for these bytes"
        )
    checksums = directory / CHECKSUMS_NAME
    if (
        not checksums.is_file()
        or checksums.read_bytes() != render_checksums(artifacts)
    ):
        raise ArtifactSetError(CHECKSUMS_NAME + " does not match the manifest")

    allowed = {MANIFEST_NAME, CHECKSUMS_NAME} | {
        str(entry["path"]) for entry in artifacts
    }
    unexpected = sorted(
        path.name for path in directory.iterdir() if path.name not in allowed
    )
    if unexpected:
        raise ArtifactSetError(
            "artifact set contains unrecorded entries: " + ", ".join(unexpected)
        )
    return manifest


def construction_steps() -> list[tuple[str, list[str]]]:
    """Ordinary development builders only; never release/publication tools.

    ``mvn clean`` first so no class, jar, or Native binary from an earlier
    revision can be packaged into this set.
    """
    python = sys.executable
    return [
        ("clean", ["mvn", "-q", "clean"]),
        ("native-image-build", ["make", "-C", "build/native", "build"]),
        ("native-archive-build", [python, "dist/build_native.py"]),
        ("portable-jvm-build", [python, "dist/build_portable.py"]),
    ]


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args], cwd=root, text=True, capture_output=True, check=False
    )
    if result.returncode != 0:
        fail("git " + " ".join(args) + " failed: " + result.stderr.strip())
    return result.stdout.strip()


def require_clean_revision(root: Path, revision: str) -> None:
    if git(root, "rev-parse", "HEAD") != revision:
        fail("checkout HEAD changed during artifact-set construction")
    if git(root, "status", "--porcelain=v1", "--untracked-files=all"):
        fail("checkout is dirty; an artifact set is built only from a clean revision")


def project_version(root: Path) -> str:
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    version = ET.parse(root / "pom.xml").findtext("m:version", namespaces=ns)
    if not version:
        fail("pom.xml project version is missing")
    return version


def extract_documentation(root: Path, version: str) -> tuple[bytes, bytes]:
    jar = root / "target" / f"protos-{version}.jar"
    runtime = root / "target" / "runtime"
    if not jar.is_file() or not runtime.is_dir():
        fail("D064 producer classpath is missing after the portable build")
    result = subprocess.run(
        [
            "java",
            "--enable-native-access=ALL-UNNAMED",
            "--sun-misc-unsafe-memory-access=allow",
            "-cp",
            f"{jar}{os.pathsep}{runtime}/*",
            D064_EXTRACTOR,
            str(root),
        ],
        cwd=root,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        fail(
            "D064 extraction failed: "
            + result.stderr.decode("utf-8", "replace").strip()
        )
    return result.stdout, result.stderr


def build(root: Path, output: Path) -> Path:
    if not (root / ".git").exists():
        fail("dist/build_artifact_set.py must run from a Git checkout")
    revision = git(root, "rev-parse", "HEAD")
    if FULL_SHA_RE.fullmatch(revision) is None:
        fail("checkout HEAD is not an exact 40-hex revision: " + revision)
    require_clean_revision(root, revision)
    version = project_version(root)
    try:
        require_snapshot_version(version)
    except ValueError as exc:
        fail(str(exc))

    staging = output.with_name(output.name + ".partial")
    # Remove any previous set before building so a failure can never leave an
    # older complete-looking set at the canonical output path.
    for path in (output, staging):
        if path.exists():
            shutil.rmtree(path)

    print(f"ARTIFACT_SET_REVISION: {revision}")
    print(f"ARTIFACT_SET_VERSION: {version}")
    published = False
    try:
        for label, command in construction_steps():
            print("\nphase=" + label, flush=True)
            if subprocess.run(command, cwd=root, check=False).returncode != 0:
                fail(f"{label} failed")
            require_clean_revision(root, revision)

        print("\nphase=stdlib-documentation", flush=True)
        first, coverage = extract_documentation(root, version)
        second, coverage_again = extract_documentation(root, version)
        if first != second or coverage != coverage_again:
            fail("D064 producer output is not deterministic for one revision")
        print("D064_DETERMINISTIC_REPRODUCIBILITY: PASS")

        print("\nphase=artifact-set-envelope", flush=True)
        staging.mkdir(parents=True)
        distributions = root / "target" / "distributions"
        names = artifact_names(version)
        shutil.copyfile(
            distributions / names[KIND_JVM], staging / names[KIND_JVM]
        )
        try:
            native = native_archive(distributions, version)
        except ArtifactSetError as exc:
            fail(str(exc))
        shutil.copyfile(native, staging / native.name)
        (staging / names[KIND_D064]).write_bytes(first)
        (staging / names[KIND_D067]).write_bytes(coverage)

        try:
            write_envelope(staging, revision=revision, version=version)
            verify_artifact_set(staging, expect_revision=revision)
        except ArtifactSetError as exc:
            fail(str(exc))
        require_clean_revision(root, revision)
        staging.rename(output)
        published = True
    finally:
        if not published and staging.exists():
            shutil.rmtree(staging)

    print(f"ARTIFACT_SET: {output}")
    print("ARTIFACT_SET_PUBLIC_RELEASE: false")
    print("ARTIFACT_SET_BUILD: PASS")
    return output


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Build or verify the DIST010 exact-revision artifact set "
            "(Native + portable JVM + D064). Never creates a release."
        )
    )
    parser.add_argument(
        "--verify",
        metavar="DIR",
        help="verify an existing artifact set instead of building one",
    )
    parser.add_argument(
        "--expect-revision",
        help="with --verify, require this exact 40-hex source revision",
    )
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]

    if args.verify is None:
        if args.expect_revision is not None:
            parser.error("--expect-revision requires --verify")
        build(root, root / "target" / "artifact-set")
        return 0

    try:
        manifest = verify_artifact_set(
            Path(args.verify), expect_revision=args.expect_revision
        )
    except ArtifactSetError as exc:
        raise SystemExit("artifact-set verification failed: " + str(exc))
    print(f"ARTIFACT_SET_REVISION: {manifest['revision']}")
    print("ARTIFACT_SET_VERIFY: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
