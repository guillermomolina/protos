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
import re
import zipfile

FULL_SHA_RE = re.compile(r"[0-9a-f]{40}")
SPEC_REVISION_RE = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+")
RELEASE_FORMAT = "protos-public-prerelease-envelope-v1"


def fail(message: str) -> "NoReturn":
    raise SystemExit("release metadata preparation failed: " + message)


def parse_key_values(text: str, *, label: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in text.splitlines():
        if not raw or raw.startswith("#"):
            continue
        if "=" not in raw:
            fail(f"{label}: malformed key/value line: {raw!r}")
        key, value = raw.split("=", 1)
        if not key or key in values:
            fail(f"{label}: duplicate/empty metadata key: {key!r}")
        values[key] = value
    return values


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def safe_archive_basename(name: str) -> bool:
    path = PurePosixPath(name)
    return bool(name) and path.name == name and not path.is_absolute() and ".." not in path.parts


def require_release_source(source: dict[str, str]) -> None:
    required = {
        "artifact_kind": "public-prerelease",
        "public_release": "true",
        "source_dirty": "false",
    }
    for key, expected in required.items():
        actual = source.get(key)
        if actual != expected:
            fail(f"SOURCE.txt {key} mismatch: {actual!r} != {expected!r}")

    version = source.get("implementation_version", "")
    release_version = source.get("release_version", "")
    if not version or version != release_version:
        fail("SOURCE.txt implementation_version/release_version mismatch")
    if source.get("release_tag") != "v" + release_version:
        fail("SOURCE.txt release_tag mismatch")

    for key in ["source_revision", "release_baseline_revision"]:
        value = source.get(key, "")
        if FULL_SHA_RE.fullmatch(value) is None:
            fail(f"SOURCE.txt {key} must be exact 40-hex SHA")

    if source.get("source_path") != "/tree/" + source["source_revision"]:
        fail("SOURCE.txt source_path mismatch")
    if source.get("source_repository") != "https://github.com/guillermomolina/protos":
        fail("SOURCE.txt source_repository mismatch")


def read_candidate_archive(archive_path: Path) -> tuple[dict[str, str], dict[str, str], str]:
    if not archive_path.is_file():
        fail("candidate archive is missing: " + str(archive_path))
    if not safe_archive_basename(archive_path.name):
        fail("candidate archive filename is not portable")

    with zipfile.ZipFile(archive_path) as archive:
        bad = archive.testzip()
        if bad is not None:
            fail("candidate archive CRC failure: " + bad)
        names = archive.namelist()
        roots = {PurePosixPath(name).parts[0] for name in names if PurePosixPath(name).parts}
        if len(roots) != 1:
            fail("candidate archive must contain exactly one top-level root")
        root_name = next(iter(roots))
        source_name = root_name + "/SOURCE.txt"
        runtime_name = root_name + "/RUNTIME.txt"
        if source_name not in names or runtime_name not in names:
            fail("candidate archive is missing SOURCE.txt or RUNTIME.txt")
        source = parse_key_values(
            archive.read(source_name).decode("utf-8"),
            label="SOURCE.txt",
        )
        runtime = parse_key_values(
            archive.read(runtime_name).decode("utf-8"),
            label="RUNTIME.txt",
        )

    require_release_source(source)
    expected_root = "protos-" + source["release_version"]
    if root_name != expected_root:
        fail(f"candidate archive root mismatch: {root_name!r} != {expected_root!r}")
    expected_archive = expected_root + "-posix-jvm.zip"
    if archive_path.name != expected_archive:
        fail(
            f"candidate archive filename mismatch: "
            f"{archive_path.name!r} != {expected_archive!r}"
        )
    return source, runtime, root_name


def require_runtime(runtime: dict[str, str]) -> None:
    required = [
        "distribution_format",
        "java_feature",
        "java_distribution",
        "truffle_runtime_version",
        "optimizing_runtime",
    ]
    missing = [key for key in required if not runtime.get(key)]
    if missing:
        fail("RUNTIME.txt missing release-note fields: " + ", ".join(missing))


def normalize_claims(values: list[str], *, label: str) -> list[str]:
    normalized: list[str] = []
    seen: set[str] = set()
    for raw in values:
        value = " ".join(raw.split())
        if not value:
            fail(f"{label} must not be empty")
        if "\n" in raw or "\r" in raw:
            fail(f"{label} must be a single logical line")
        if value not in seen:
            seen.add(value)
            normalized.append(value)
    if not normalized:
        fail(f"at least one {label} is required")
    return normalized


def write_text(path: Path, text: str) -> None:
    path.write_text(text.rstrip() + "\n", encoding="utf-8")


def render_notes(
    *,
    source: dict[str, str],
    runtime: dict[str, str],
    spec_revision: str,
    capabilities: list[str],
    limitations: list[str],
    archive_name: str,
    archive_sha256: str,
) -> str:
    capability_lines = "\n".join(f"- {item}" for item in capabilities)
    limitation_lines = "\n".join(f"- {item}" for item in limitations)
    return f"""# Protos {source['release_version']}

This GitHub Release is a **pre-release** of the Protos reference implementation.

## Identity

- Release version: `{source['release_version']}`
- Git tag: `{source['release_tag']}`
- Candidate source revision: `{source['source_revision']}`
- Development baseline revision: `{source['release_baseline_revision']}`
- Development baseline version: `{source['release_baseline_version']}`
- Core specification revision: `{spec_revision}`
- Source repository: `{source['source_repository']}`

## Supported runtime

- Java distribution: `{runtime['java_distribution']}`
- Java feature: `{runtime['java_feature']}`
- Truffle runtime: `{runtime['truffle_runtime_version']}`
- Expected optimizing runtime: `{runtime['optimizing_runtime']}`

The current portable bundle does not include the JDK. Runtime support is limited
to the declared DIST001 runtime contract; the Java bytecode target alone is not
a broader support claim.

## Important capabilities

{capability_lines}

## Important limitations

{limitation_lines}

## Download and verification

- Portable archive: `{archive_name}`
- SHA-256: `{archive_sha256}`
- External checksum: `{archive_name}.sha256`

After downloading the ZIP and checksum into the same directory:

```sh
sha256sum -c {archive_name}.sha256
```

The bundle itself also contains `SOURCE.txt`, `RUNTIME.txt`, `DEPENDENCIES.txt`,
`LICENSE.TXT`, and internal `SHA256SUMS` metadata.
"""


def render_manifest(
    *,
    source: dict[str, str],
    runtime: dict[str, str],
    spec_revision: str,
    archive_name: str,
    archive_sha256: str,
    notes_name: str,
    notes_sha256: str,
    checksum_name: str,
    checksum_sha256: str,
) -> str:
    rows = [
        ("release_envelope_format", RELEASE_FORMAT),
        ("release_version", source["release_version"]),
        ("release_tag", source["release_tag"]),
        ("prerelease", "true"),
        ("source_repository", source["source_repository"]),
        ("source_revision", source["source_revision"]),
        ("release_baseline_revision", source["release_baseline_revision"]),
        ("release_baseline_version", source["release_baseline_version"]),
        ("specification_revision", spec_revision),
        ("java_distribution", runtime["java_distribution"]),
        ("java_feature", runtime["java_feature"]),
        ("truffle_runtime_version", runtime["truffle_runtime_version"]),
        ("optimizing_runtime", runtime["optimizing_runtime"]),
        ("portable_archive", archive_name),
        ("portable_archive_sha256", archive_sha256),
        ("portable_checksum", checksum_name),
        ("portable_checksum_sha256", checksum_sha256),
        ("release_notes", notes_name),
        ("release_notes_sha256", notes_sha256),
    ]
    return "\n".join(f"{key}={value}" for key, value in rows)


def prepare(args: argparse.Namespace) -> Path:
    archive = Path(args.archive).resolve()
    source, runtime, _ = read_candidate_archive(archive)
    require_runtime(runtime)

    if SPEC_REVISION_RE.fullmatch(args.spec_revision) is None:
        fail("spec revision must be canonical numeric X.Y.Z")

    capabilities = normalize_claims(args.capability, label="capability")
    limitations = normalize_claims(args.limitation, label="limitation")

    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    archive_sha = sha256_file(archive)
    checksum_name = archive.name + ".sha256"
    notes_name = "RELEASE_NOTES.md"
    manifest_name = "RELEASE_MANIFEST.txt"

    checksum_bytes = f"{archive_sha}  {archive.name}\n".encode("utf-8")
    notes_text = render_notes(
        source=source,
        runtime=runtime,
        spec_revision=args.spec_revision,
        capabilities=capabilities,
        limitations=limitations,
        archive_name=archive.name,
        archive_sha256=archive_sha,
    )
    notes_bytes = (notes_text.rstrip() + "\n").encode("utf-8")

    checksum_path = output_dir / checksum_name
    notes_path = output_dir / notes_name
    manifest_path = output_dir / manifest_name

    checksum_path.write_bytes(checksum_bytes)
    notes_path.write_bytes(notes_bytes)

    manifest_text = render_manifest(
        source=source,
        runtime=runtime,
        spec_revision=args.spec_revision,
        archive_name=archive.name,
        archive_sha256=archive_sha,
        notes_name=notes_name,
        notes_sha256=sha256_bytes(notes_bytes),
        checksum_name=checksum_name,
        checksum_sha256=sha256_bytes(checksum_bytes),
    )
    write_text(manifest_path, manifest_text)

    print("RELEASE_ENVELOPE_IDENTITY_CHECK: PASS")
    print("RELEASE_ENVELOPE_RUNTIME_CHECK: PASS")
    print("RELEASE_ENVELOPE_CLAIMS_CHECK: PASS")
    print("RELEASE_ENVELOPE_CHECKSUM_CHECK: PASS sha256=" + archive_sha)
    print("RELEASE_ENVELOPE_NOTES: " + str(notes_path))
    print("RELEASE_ENVELOPE_MANIFEST: " + str(manifest_path))
    print("RELEASE_METADATA_PREPARE: PASS")
    return manifest_path


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Prepare deterministic release notes/checksum/manifest metadata for "
            "an already-built DIST001 public-prerelease candidate archive."
        )
    )
    parser.add_argument("--archive", required=True)
    parser.add_argument("--spec-revision", required=True)
    parser.add_argument("--capability", action="append", default=[])
    parser.add_argument("--limitation", action="append", default=[])
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()
    prepare(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
