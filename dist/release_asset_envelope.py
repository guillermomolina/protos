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

"""Multi-asset public-prerelease release-envelope support for DIST005-D1."""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
from pathlib import Path, PurePosixPath
import re
from typing import NoReturn
import zipfile

import prepare_release_metadata as legacy


MULTI_RELEASE_FORMAT = "protos-public-prerelease-envelope-v2"
DIST005_MODEL = "JVM_PLUS_NATIVE"

PORTABLE_KIND = "portable-jvm"
NATIVE_KIND = "native"

RECOMMENDED_ROLE = "recommended-first-run"
FALLBACK_ROLE = "compatibility-fallback"
ALLOWED_ROLES = {RECOMMENDED_ROLE, FALLBACK_ROLE}

PORTABLE_FORMAT = "protos-portable-posix-jvm-v1"
NATIVE_FORMAT = "protos-native-image-posix-v1"

PUBLIC_NATIVE_RUNTIME_IDENTITY = {
    "target_os": "linux",
    "target_arch": "x86_64",
    "libc_family": "glibc",
    "libc_abi_min": "2.39",
    "linkage": "dynamic",
    "cpu_isa_assumption": "compatibility",
}

MANIFEST_NAME = "RELEASE_MANIFEST.txt"
NOTES_NAME = "RELEASE_NOTES.md"

TOKEN_RE = re.compile(r"[A-Za-z0-9._+-]+")
CANDIDATE_KEYS = (
    "release_version",
    "release_tag",
    "source_repository",
    "source_revision",
    "release_baseline_revision",
    "release_baseline_version",
)


def fail(message: str) -> NoReturn:
    raise SystemExit("multi-asset release envelope failed: " + message)


@dataclass(frozen=True)
class ReleaseAsset:
    path: Path
    key: str
    kind: str
    source: dict[str, str]
    runtime: dict[str, str]
    platform_identity: str
    archive_sha256: str
    license_path: str
    license_sha256: str
    notice_path: str
    notice_sha256: str


def _token(value: str, *, label: str) -> str:
    if not value or TOKEN_RE.fullmatch(value) is None:
        fail(f"{label} must be a non-empty canonical token: {value!r}")
    return value


def _read_archive_members(
    archive_path: Path,
) -> tuple[dict[str, str], dict[str, str], str, bytes, bytes]:
    if not archive_path.is_file():
        fail("candidate archive is missing: " + str(archive_path))
    if not legacy.safe_archive_basename(archive_path.name):
        fail("candidate archive filename is not portable")

    try:
        with zipfile.ZipFile(archive_path) as archive:
            bad = archive.testzip()
            if bad is not None:
                fail("candidate archive CRC failure: " + bad)

            names = archive.namelist()
            if len(names) != len(set(names)):
                fail("candidate archive contains duplicate entry names")

            roots = {
                PurePosixPath(name).parts[0]
                for name in names
                if PurePosixPath(name).parts
            }
            if len(roots) != 1:
                fail("candidate archive must contain exactly one top-level root")
            root_name = next(iter(roots))

            required = {
                root_name + "/SOURCE.txt",
                root_name + "/RUNTIME.txt",
                root_name + "/LICENSE.TXT",
                root_name + "/DEPENDENCIES.txt",
            }
            missing = sorted(required - set(names))
            if missing:
                fail(
                    "candidate archive is missing required release identity files: "
                    + ", ".join(missing)
                )

            for name in names:
                path = PurePosixPath(name)
                if path.is_absolute() or ".." in path.parts:
                    fail("unsafe archive path: " + name)

            source = legacy.parse_key_values(
                archive.read(root_name + "/SOURCE.txt").decode("utf-8"),
                label="SOURCE.txt",
            )
            runtime = legacy.parse_key_values(
                archive.read(root_name + "/RUNTIME.txt").decode("utf-8"),
                label="RUNTIME.txt",
            )
            license_bytes = archive.read(root_name + "/LICENSE.TXT")
            notice_bytes = archive.read(root_name + "/DEPENDENCIES.txt")
    except (zipfile.BadZipFile, UnicodeDecodeError, OSError) as exc:
        fail("candidate archive cannot be read: " + str(exc))

    legacy.require_release_source(source)
    return source, runtime, root_name, license_bytes, notice_bytes


def _require_native_runtime(runtime: dict[str, str]) -> None:
    required = [
        "distribution_format",
        "native_runtime_kind",
        "external_java_required",
        "graalvm_release",
        "native_image_version",
        "jdk_version",
        "target_os",
        "target_arch",
        "linkage",
        "libc_family",
        "libc_abi_min",
        "cpu_isa_assumption",
    ]
    missing = [key for key in required if not runtime.get(key)]
    if missing:
        fail("Native RUNTIME.txt missing public-envelope fields: " + ", ".join(missing))

    if runtime["external_java_required"] != "false":
        fail("Native public artifact must declare external_java_required=false")
    if runtime["native_runtime_kind"] != "graalvm-native-image-truffle":
        fail("Native runtime kind is not the ratified GraalVM Native Image/Truffle kind")

    for key, expected in PUBLIC_NATIVE_RUNTIME_IDENTITY.items():
        actual = runtime[key]
        if actual != expected:
            fail(
                f"Native public artifact {key} does not match the selected "
                f"public platform identity: {actual!r} != {expected!r}"
            )

    for key in [
        "target_os",
        "target_arch",
        "linkage",
        "libc_family",
        "libc_abi_min",
        "cpu_isa_assumption",
    ]:
        _token(runtime[key], label="Native " + key)


def read_release_asset(archive_path: Path) -> ReleaseAsset:
    archive_path = archive_path.resolve()
    source, runtime, root_name, license_bytes, notice_bytes = _read_archive_members(
        archive_path
    )

    distribution_format = runtime.get("distribution_format", "")
    if distribution_format == PORTABLE_FORMAT:
        portable_source, portable_runtime, portable_root = legacy.read_candidate_archive(
            archive_path
        )
        legacy.require_runtime(portable_runtime)
        if portable_source != source or portable_runtime != runtime or portable_root != root_name:
            fail("portable candidate archive identity changed during inspection")

        kind = PORTABLE_KIND
        key = PORTABLE_KIND
        platform_identity = "portable-posix-jvm"

    elif distribution_format == NATIVE_FORMAT:
        _require_native_runtime(runtime)

        expected_root = (
            "protos-"
            + source["release_version"]
            + "-native-"
            + runtime["target_os"]
            + "-"
            + runtime["target_arch"]
        )
        if root_name != expected_root:
            fail(
                f"Native candidate archive root mismatch: "
                f"{root_name!r} != {expected_root!r}"
            )
        expected_archive = expected_root + ".zip"
        if archive_path.name != expected_archive:
            fail(
                f"Native candidate archive filename mismatch: "
                f"{archive_path.name!r} != {expected_archive!r}"
            )

        kind = NATIVE_KIND
        key = "-".join(
            [
                NATIVE_KIND,
                runtime["target_os"],
                runtime["target_arch"],
                runtime["libc_family"],
                runtime["libc_abi_min"],
                runtime["linkage"],
                runtime["cpu_isa_assumption"],
            ]
        )
        platform_identity = "/".join(
            [
                runtime["target_os"],
                runtime["target_arch"],
                runtime["libc_family"],
                runtime["libc_abi_min"],
                runtime["linkage"],
                runtime["cpu_isa_assumption"],
            ]
        )
    else:
        fail(
            "unknown/unhandled public artifact distribution_format: "
            + repr(distribution_format)
        )

    return ReleaseAsset(
        path=archive_path,
        key=key,
        kind=kind,
        source=source,
        runtime=runtime,
        platform_identity=platform_identity,
        archive_sha256=legacy.sha256_file(archive_path),
        license_path="LICENSE.TXT",
        license_sha256=hashlib.sha256(license_bytes).hexdigest(),
        notice_path="DEPENDENCIES.txt",
        notice_sha256=hashlib.sha256(notice_bytes).hexdigest(),
    )


def read_release_assets(archive_values: list[str] | tuple[str, ...]) -> list[ReleaseAsset]:
    if len(archive_values) < 2:
        fail("multi-asset release envelope requires at least two archives")

    assets = [read_release_asset(Path(value)) for value in archive_values]
    by_key: dict[str, ReleaseAsset] = {}
    for asset in assets:
        if asset.key in by_key:
            fail("duplicate artifact identity: " + asset.key)
        by_key[asset.key] = asset
    return sorted(assets, key=lambda asset: asset.key)


def common_candidate_source(assets: list[ReleaseAsset]) -> dict[str, str]:
    first = assets[0].source
    for asset in assets[1:]:
        for key in CANDIDATE_KEYS:
            if asset.source.get(key) != first.get(key):
                fail(
                    "candidate/source mismatch between assets for "
                    + key
                    + ": "
                    + repr(asset.source.get(key))
                    + " != "
                    + repr(first.get(key))
                )
    return first


def parse_roles(values: list[str], assets: list[ReleaseAsset]) -> dict[str, str]:
    roles: dict[str, str] = {}
    for raw in values:
        if "=" not in raw:
            fail("--asset-role must use ARTIFACT_KEY=ROLE")
        key, role = raw.split("=", 1)
        if not key or key in roles:
            fail("duplicate/empty --asset-role artifact key: " + repr(key))
        if role not in ALLOWED_ROLES:
            fail("unknown artifact role: " + repr(role))
        roles[key] = role

    expected = {asset.key for asset in assets}
    actual = set(roles)
    if actual != expected:
        fail(
            "asset-role key set mismatch; missing="
            + repr(sorted(expected - actual))
            + " extra="
            + repr(sorted(actual - expected))
        )
    require_selected_model_roles(assets, roles)
    return roles


def require_selected_model_roles(
    assets: list[ReleaseAsset],
    roles: dict[str, str],
) -> None:
    recommended = [key for key, role in roles.items() if role == RECOMMENDED_ROLE]
    if len(recommended) != 1:
        fail("multi-asset release must have exactly one recommended-first-run artifact")

    recommended_asset = next(asset for asset in assets if asset.key == recommended[0])
    if recommended_asset.kind != NATIVE_KIND:
        fail("selected JVM_PLUS_NATIVE model requires Native as recommended-first-run")

    portable = [asset for asset in assets if asset.kind == PORTABLE_KIND]
    native = [asset for asset in assets if asset.kind == NATIVE_KIND]
    if len(portable) != 1 or len(native) < 1:
        fail(
            "selected JVM_PLUS_NATIVE envelope requires exactly one portable JVM "
            "asset and at least one Native asset"
        )

    if roles[portable[0].key] != FALLBACK_ROLE:
        fail(
            "selected JVM_PLUS_NATIVE model requires portable JVM role="
            + FALLBACK_ROLE
        )


def _checksum_bytes(asset: ReleaseAsset) -> bytes:
    return f"{asset.archive_sha256}  {asset.path.name}\n".encode("utf-8")


def _asset_rows(
    index: int,
    asset: ReleaseAsset,
    *,
    role: str,
) -> list[tuple[str, str]]:
    prefix = f"asset.{index}."
    rows = [
        (prefix + "key", asset.key),
        (prefix + "kind", asset.kind),
        (prefix + "role", role),
        (prefix + "platform_identity", asset.platform_identity),
        (prefix + "distribution_format", asset.runtime["distribution_format"]),
        (prefix + "archive", asset.path.name),
        (prefix + "archive_sha256", asset.archive_sha256),
        (prefix + "checksum", asset.path.name + ".sha256"),
        (prefix + "checksum_sha256", legacy.sha256_bytes(_checksum_bytes(asset))),
        (prefix + "license_path", asset.license_path),
        (prefix + "license_sha256", asset.license_sha256),
        (prefix + "notice_path", asset.notice_path),
        (prefix + "notice_sha256", asset.notice_sha256),
    ]

    if asset.kind == PORTABLE_KIND:
        rows.extend(
            [
                (prefix + "external_java_required", "true"),
                (prefix + "java_distribution", asset.runtime["java_distribution"]),
                (prefix + "java_feature", asset.runtime["java_feature"]),
                (
                    prefix + "truffle_runtime_version",
                    asset.runtime["truffle_runtime_version"],
                ),
                (prefix + "optimizing_runtime", asset.runtime["optimizing_runtime"]),
            ]
        )
    elif asset.kind == NATIVE_KIND:
        rows.extend(
            [
                (prefix + "external_java_required", "false"),
                (prefix + "native_runtime_kind", asset.runtime["native_runtime_kind"]),
                (prefix + "graalvm_release", asset.runtime["graalvm_release"]),
                (prefix + "native_image_version", asset.runtime["native_image_version"]),
                (prefix + "jdk_version", asset.runtime["jdk_version"]),
                (prefix + "target_os", asset.runtime["target_os"]),
                (prefix + "target_arch", asset.runtime["target_arch"]),
                (prefix + "libc_family", asset.runtime["libc_family"]),
                (prefix + "libc_abi_min", asset.runtime["libc_abi_min"]),
                (prefix + "linkage", asset.runtime["linkage"]),
                (prefix + "cpu_isa_assumption", asset.runtime["cpu_isa_assumption"]),
            ]
        )
    else:
        fail("internal error: unhandled artifact kind " + asset.kind)

    return rows


def render_manifest(
    *,
    source: dict[str, str],
    assets: list[ReleaseAsset],
    roles: dict[str, str],
    spec_revision: str,
    notes_sha256: str,
) -> str:
    rows: list[tuple[str, str]] = [
        ("release_envelope_format", MULTI_RELEASE_FORMAT),
        ("distribution_model", DIST005_MODEL),
        ("release_version", source["release_version"]),
        ("release_tag", source["release_tag"]),
        ("prerelease", "true"),
        ("source_repository", source["source_repository"]),
        ("source_revision", source["source_revision"]),
        ("release_baseline_revision", source["release_baseline_revision"]),
        ("release_baseline_version", source["release_baseline_version"]),
        ("specification_revision", spec_revision),
        ("asset_count", str(len(assets))),
    ]
    for index, asset in enumerate(assets):
        rows.extend(_asset_rows(index, asset, role=roles[asset.key]))
    rows.extend(
        [
            ("release_notes", NOTES_NAME),
            ("release_notes_sha256", notes_sha256),
        ]
    )
    return "\n".join(f"{key}={value}" for key, value in rows)


def render_notes(
    *,
    source: dict[str, str],
    assets: list[ReleaseAsset],
    roles: dict[str, str],
    spec_revision: str,
    capabilities: list[str],
    limitations: list[str],
) -> str:
    artifact_sections: list[str] = []
    for asset in assets:
        lines = [
            f"### `{asset.key}`",
            "",
            f"- Kind: `{asset.kind}`",
            f"- Role: `{roles[asset.key]}`",
            f"- Platform identity: `{asset.platform_identity}`",
            f"- Archive: `{asset.path.name}`",
            f"- SHA-256: `{asset.archive_sha256}`",
            f"- External checksum: `{asset.path.name}.sha256`",
            (
                f"- License identity: `{asset.license_path}` / "
                f"`{asset.license_sha256}`"
            ),
            (
                f"- Notice identity: `{asset.notice_path}` / "
                f"`{asset.notice_sha256}`"
            ),
        ]
        if asset.kind == PORTABLE_KIND:
            lines.extend(
                [
                    "- External Java required: `true`",
                    f"- Java distribution: `{asset.runtime['java_distribution']}`",
                    f"- Java feature: `{asset.runtime['java_feature']}`",
                    f"- Truffle runtime: `{asset.runtime['truffle_runtime_version']}`",
                    f"- Expected optimizing runtime: `{asset.runtime['optimizing_runtime']}`",
                ]
            )
        else:
            lines.extend(
                [
                    "- External Java required: `false`",
                    f"- Native runtime kind: `{asset.runtime['native_runtime_kind']}`",
                    f"- GraalVM release: `{asset.runtime['graalvm_release']}`",
                    f"- Native Image version: `{asset.runtime['native_image_version']}`",
                    f"- JDK version: `{asset.runtime['jdk_version']}`",
                    f"- Target OS: `{asset.runtime['target_os']}`",
                    f"- Target architecture: `{asset.runtime['target_arch']}`",
                    f"- libc family: `{asset.runtime['libc_family']}`",
                    f"- libc ABI minimum: `{asset.runtime['libc_abi_min']}`",
                    f"- Linkage: `{asset.runtime['linkage']}`",
                    f"- CPU ISA assumption: `{asset.runtime['cpu_isa_assumption']}`",
                ]
            )
        artifact_sections.append("\n".join(lines))

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
- Distribution model: `{DIST005_MODEL}`

All assets below belong to this one exact release identity and candidate.

## Artifacts

{chr(10).join(artifact_sections)}

## Important capabilities

{capability_lines}

## Important limitations

{limitation_lines}

## Download and verification

Each archive has its own adjacent `.sha256` file. Verify the downloaded asset
with `sha256sum -c <archive>.sha256`.

Each archive also carries `SOURCE.txt`, `RUNTIME.txt`, `DEPENDENCIES.txt`,
`LICENSE.TXT`, and its own internal distribution integrity metadata.
"""


def prepare_multi(args: object) -> Path:
    archive_values = list(getattr(args, "archive"))
    assets = read_release_assets(archive_values)
    source = common_candidate_source(assets)

    spec_revision = str(getattr(args, "spec_revision"))
    if legacy.SPEC_REVISION_RE.fullmatch(spec_revision) is None:
        fail("spec revision must be canonical numeric X.Y.Z")

    capabilities = legacy.normalize_claims(
        list(getattr(args, "capability")),
        label="capability",
    )
    limitations = legacy.normalize_claims(
        list(getattr(args, "limitation")),
        label="limitation",
    )
    roles = parse_roles(list(getattr(args, "asset_role")), assets)

    output_dir = Path(str(getattr(args, "output_dir"))).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    for asset in assets:
        (output_dir / (asset.path.name + ".sha256")).write_bytes(_checksum_bytes(asset))

    notes_text = render_notes(
        source=source,
        assets=assets,
        roles=roles,
        spec_revision=spec_revision,
        capabilities=capabilities,
        limitations=limitations,
    )
    notes_bytes = (notes_text.rstrip() + "\n").encode("utf-8")
    notes_path = output_dir / NOTES_NAME
    notes_path.write_bytes(notes_bytes)

    manifest_text = render_manifest(
        source=source,
        assets=assets,
        roles=roles,
        spec_revision=spec_revision,
        notes_sha256=legacy.sha256_bytes(notes_bytes),
    )
    manifest_path = output_dir / MANIFEST_NAME
    legacy.write_text(manifest_path, manifest_text)

    print("MULTI_ASSET_ENVELOPE_GENERATION: PASS")
    print("COMMON_CANDIDATE_IDENTITY: PASS")
    print("DETERMINISTIC_ASSET_ORDERING: PASS")
    print("UNAMBIGUOUS_RECOMMENDED_FIRST_RUN: PASS")
    print("RELEASE_ENVELOPE_MANIFEST: " + str(manifest_path))
    print("RELEASE_METADATA_PREPARE: PASS")
    return manifest_path


def _roles_from_manifest(
    manifest: dict[str, str],
    assets: list[ReleaseAsset],
) -> dict[str, str]:
    try:
        count = int(manifest.get("asset_count", ""))
    except ValueError:
        fail("manifest asset_count is not an integer")
    if count != len(assets):
        fail(f"manifest asset_count mismatch: {count!r} != {len(assets)!r}")

    roles: dict[str, str] = {}
    for index in range(count):
        key = manifest.get(f"asset.{index}.key", "")
        role = manifest.get(f"asset.{index}.role", "")
        if not key or key in roles:
            fail("manifest contains duplicate/empty artifact identity")
        if role not in ALLOWED_ROLES:
            fail("manifest contains unknown artifact role: " + repr(role))
        roles[key] = role

    actual = {asset.key for asset in assets}
    if set(roles) != actual:
        fail(
            "manifest artifact identity set mismatch; missing="
            + repr(sorted(actual - set(roles)))
            + " extra="
            + repr(sorted(set(roles) - actual))
        )
    require_selected_model_roles(assets, roles)
    return roles


def _require_notes_structure(
    notes: str,
    *,
    source: dict[str, str],
    assets: list[ReleaseAsset],
    roles: dict[str, str],
    spec_revision: str,
) -> None:
    fragments = [
        f"# Protos {source['release_version']}",
        "This GitHub Release is a **pre-release**",
        f"- Release version: `{source['release_version']}`",
        f"- Git tag: `{source['release_tag']}`",
        f"- Candidate source revision: `{source['source_revision']}`",
        f"- Core specification revision: `{spec_revision}`",
        f"- Distribution model: `{DIST005_MODEL}`",
        "## Artifacts",
        "## Important capabilities",
        "## Important limitations",
        "## Download and verification",
    ]
    for asset in assets:
        fragments.extend(
            [
                f"### `{asset.key}`",
                f"- Kind: `{asset.kind}`",
                f"- Role: `{roles[asset.key]}`",
                f"- Archive: `{asset.path.name}`",
                f"- SHA-256: `{asset.archive_sha256}`",
                f"- License identity: `{asset.license_path}` / `{asset.license_sha256}`",
                f"- Notice identity: `{asset.notice_path}` / `{asset.notice_sha256}`",
            ]
        )
    for fragment in fragments:
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


def verify_multi(archive_paths: list[Path], envelope_dir: Path) -> None:
    assets = read_release_assets([str(path) for path in archive_paths])
    source = common_candidate_source(assets)
    envelope_dir = envelope_dir.resolve()

    manifest_path = envelope_dir / MANIFEST_NAME
    notes_path = envelope_dir / NOTES_NAME
    if not manifest_path.is_file() or not notes_path.is_file():
        fail("release envelope is missing manifest or notes")

    manifest_text = manifest_path.read_text(encoding="utf-8")
    manifest = legacy.parse_key_values(manifest_text, label=MANIFEST_NAME)

    if manifest.get("release_envelope_format") != MULTI_RELEASE_FORMAT:
        fail("manifest release_envelope_format mismatch")
    if manifest.get("distribution_model") != DIST005_MODEL:
        fail("manifest distribution_model mismatch")

    expected_release = {
        "release_version": source["release_version"],
        "release_tag": source["release_tag"],
        "prerelease": "true",
        "source_repository": source["source_repository"],
        "source_revision": source["source_revision"],
        "release_baseline_revision": source["release_baseline_revision"],
        "release_baseline_version": source["release_baseline_version"],
    }
    for key, expected in expected_release.items():
        actual = manifest.get(key)
        if actual != expected:
            fail(f"manifest {key} mismatch: {actual!r} != {expected!r}")

    spec_revision = manifest.get("specification_revision", "")
    if legacy.SPEC_REVISION_RE.fullmatch(spec_revision) is None:
        fail("manifest specification_revision is not canonical numeric X.Y.Z")

    roles = _roles_from_manifest(manifest, assets)

    expected_names = {MANIFEST_NAME, NOTES_NAME}
    for asset in assets:
        expected_names.add(asset.path.name + ".sha256")
    actual_names = {p.name for p in envelope_dir.iterdir() if p.is_file()}
    if actual_names != expected_names:
        fail(
            "release envelope file set mismatch; missing="
            + repr(sorted(expected_names - actual_names))
            + " extra="
            + repr(sorted(actual_names - expected_names))
        )

    for asset in assets:
        checksum_path = envelope_dir / (asset.path.name + ".sha256")
        expected_checksum = _checksum_bytes(asset)
        if checksum_path.read_bytes() != expected_checksum:
            fail("external checksum content mismatch: " + asset.path.name)

    notes = notes_path.read_text(encoding="utf-8")
    _require_notes_structure(
        notes,
        source=source,
        assets=assets,
        roles=roles,
        spec_revision=spec_revision,
    )

    expected_manifest = render_manifest(
        source=source,
        assets=assets,
        roles=roles,
        spec_revision=spec_revision,
        notes_sha256=legacy.sha256_file(notes_path),
    )
    if manifest_text != expected_manifest.rstrip() + "\n":
        fail("manifest is not the exact canonical multi-asset representation")

    print("MULTI_ASSET_ENVELOPE_VERIFICATION: PASS")
    print("PER_ARTIFACT_KIND_IDENTITY: PASS")
    print("PER_ARTIFACT_ROLE_IDENTITY: PASS")
    print("PER_ARTIFACT_PLATFORM_IDENTITY: PASS")
    print("PER_ARTIFACT_RUNTIME_IDENTITY: PASS")
    print("PER_ARTIFACT_CHECKSUM_IDENTITY: PASS")
    print("PER_ARTIFACT_LICENSE_NOTICE_IDENTITY: PASS")
    print("COMMON_CANDIDATE_IDENTITY: PASS")
    print("UNAMBIGUOUS_RECOMMENDED_FIRST_RUN: PASS")
    print("DETERMINISTIC_ASSET_ORDERING: PASS")
    print("DIST005_D1_MULTI_ASSET_ENVELOPE: PASS")
