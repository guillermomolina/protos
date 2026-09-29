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
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
from typing import NoReturn

sys.dont_write_bytecode = True

from validate_release_candidate import require_release_only_lineage


AUTHORIZATION_FORMAT = "protos-dist005-publication-authorization-v1"
CANONICAL_REPOSITORY = "guillermomolina/protos"
CANONICAL_SOURCE_REPOSITORY = "https://github.com/guillermomolina/protos"
MANIFEST_NAME = "RELEASE_MANIFEST.txt"
NOTES_NAME = "RELEASE_NOTES.md"
DISTRIBUTION_MODEL = "JVM_PLUS_NATIVE"
MULTI_RELEASE_FORMAT = "protos-public-prerelease-envelope-v2"

SHA_RE = re.compile(r"[0-9a-f]{40}")
SHA256_RE = re.compile(r"[0-9a-f]{64}")
VERSION_RE = re.compile(
    r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)"
)

AUTHORIZATION_KEYS = {
    "publication_authorization_format",
    "release_publication_authorized",
    "authorization_basis",
    "candidate_source_revision",
    "release_version",
    "release_tag",
    "release_manifest_sha256",
    "github_release_prerelease",
    "github_release_draft",
}


@dataclass(frozen=True)
class Authorization:
    candidate_source_revision: str
    release_version: str
    release_tag: str
    release_manifest_sha256: str


@dataclass(frozen=True)
class Asset:
    kind: str
    role: str
    archive_name: str
    checksum_name: str
    archive_sha256: str
    checksum_sha256: str
    archive_path: Path
    checksum_path: Path


@dataclass(frozen=True)
class PreparedRelease:
    authorization: Authorization
    candidate: Path
    envelope_dir: Path
    manifest_path: Path
    manifest: dict[str, str]
    notes_path: Path
    assets: tuple[Asset, ...]
    title: str

    def publication_paths(self) -> tuple[Path, ...]:
        by_kind = {asset.kind: asset for asset in self.assets}
        native = by_kind["native"]
        portable = by_kind["portable-jvm"]
        return (
            native.archive_path,
            native.checksum_path,
            portable.archive_path,
            portable.checksum_path,
            self.manifest_path,
        )

    def expected_public_asset_digests(self) -> dict[str, str]:
        result: dict[str, str] = {}
        for asset in self.assets:
            result[asset.archive_name] = asset.archive_sha256
            result[asset.checksum_name] = asset.checksum_sha256
        result[MANIFEST_NAME] = self.authorization.release_manifest_sha256
        return result


@dataclass(frozen=True)
class PublicationState:
    local_tag: bool
    remote_tag: bool
    release_exists: bool
    missing_assets: tuple[str, ...]


def fail(message: str) -> NoReturn:
    raise SystemExit("release publication failed: " + message)


def run_text(
    cwd: Path,
    args: list[str],
    *,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        args,
        cwd=cwd,
        check=False,
        text=True,
        capture_output=True,
    )
    if check and result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip()
        fail("command failed: " + repr(args) + (": " + detail if detail else ""))
    return result


def run_bytes(
    cwd: Path,
    args: list[str],
    *,
    check: bool = True,
) -> subprocess.CompletedProcess[bytes]:
    result = subprocess.run(
        args,
        cwd=cwd,
        check=False,
        capture_output=True,
    )
    if check and result.returncode != 0:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        fail("command failed: " + repr(args) + (": " + detail if detail else ""))
    return result


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def read_key_values(path: Path, *, label: str) -> dict[str, str]:
    if not path.is_file():
        fail(label + " is missing: " + str(path))

    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw or raw.startswith("#"):
            continue
        if "=" not in raw:
            fail(label + " contains malformed line: " + repr(raw))
        key, value = raw.split("=", 1)
        if not key or key.strip() != key:
            fail(label + " contains invalid key: " + repr(key))
        if key in values:
            fail(label + " contains duplicate key: " + key)
        values[key] = value
    return values


def parse_authorization(path: Path) -> Authorization:
    values = read_key_values(path.resolve(), label="publication authorization")

    actual_keys = set(values)
    if actual_keys != AUTHORIZATION_KEYS:
        missing = sorted(AUTHORIZATION_KEYS - actual_keys)
        extra = sorted(actual_keys - AUTHORIZATION_KEYS)
        fail(
            "publication authorization key set mismatch; missing="
            + repr(missing)
            + " extra="
            + repr(extra)
        )

    expected_literals = {
        "publication_authorization_format": AUTHORIZATION_FORMAT,
        "release_publication_authorized": "true",
        "authorization_basis": "explicit-user-decision",
        "github_release_prerelease": "true",
        "github_release_draft": "false",
    }
    for key, expected in expected_literals.items():
        actual = values[key]
        if actual != expected:
            fail(
                "publication authorization "
                + key
                + " mismatch: "
                + repr(actual)
                + " != "
                + repr(expected)
            )

    candidate = values["candidate_source_revision"]
    version = values["release_version"]
    tag = values["release_tag"]
    manifest_sha = values["release_manifest_sha256"]

    if SHA_RE.fullmatch(candidate) is None:
        fail("authorized candidate_source_revision must be exact lowercase 40-hex")
    if VERSION_RE.fullmatch(version) is None:
        fail("authorized release_version must be canonical MAJOR.MINOR.PATCH")
    if tag != "v" + version:
        fail("authorized release_tag does not derive exactly from release_version")
    if SHA256_RE.fullmatch(manifest_sha) is None:
        fail("authorized release_manifest_sha256 must be exact lowercase SHA-256")

    return Authorization(
        candidate_source_revision=candidate,
        release_version=version,
        release_tag=tag,
        release_manifest_sha256=manifest_sha,
    )


def safe_basename(value: str, *, label: str) -> str:
    path = PurePosixPath(value)
    if (
        not value
        or path.name != value
        or path.is_absolute()
        or ".." in path.parts
    ):
        fail(label + " is not a portable basename: " + repr(value))
    return value


def require_candidate_state(
    candidate: Path,
    authorization: Authorization,
    manifest: dict[str, str],
) -> None:
    if not candidate.is_dir():
        fail("candidate worktree is missing: " + str(candidate))

    top = run_text(
        candidate,
        ["git", "rev-parse", "--show-toplevel"],
    ).stdout.strip()
    if Path(top).resolve() != candidate.resolve():
        fail("candidate path is not the exact Git worktree root")

    head = run_text(
        candidate,
        ["git", "rev-parse", "HEAD"],
    ).stdout.strip()
    if head != authorization.candidate_source_revision:
        fail(
            "candidate HEAD mismatch: "
            + repr(head)
            + " != "
            + repr(authorization.candidate_source_revision)
        )

    detached = run_text(
        candidate,
        ["git", "symbolic-ref", "--quiet", "--short", "HEAD"],
        check=False,
    )
    if detached.returncode == 0:
        fail("candidate worktree is attached to branch: " + detached.stdout.strip())
    if detached.returncode != 1:
        fail("cannot determine candidate detached state")

    status = run_text(
        candidate,
        ["git", "status", "--porcelain=v1", "--untracked-files=all"],
    ).stdout
    if status:
        fail("candidate worktree must be clean")

    source = {
        "release_baseline_revision": manifest["release_baseline_revision"],
        "release_version": authorization.release_version,
    }
    require_release_only_lineage(candidate, source)


def require_canonical_remote(candidate: Path) -> None:
    remote = run_text(
        candidate,
        ["git", "remote", "get-url", "origin"],
    ).stdout.strip()

    normalized = remote.rstrip("/")
    if normalized.endswith(".git"):
        normalized = normalized[:-4]

    accepted = {
        "https://github.com/" + CANONICAL_REPOSITORY,
        "git@github.com:" + CANONICAL_REPOSITORY,
        "ssh://git@github.com/" + CANONICAL_REPOSITORY,
        "git://github.com/" + CANONICAL_REPOSITORY,
    }
    if normalized not in accepted:
        fail("origin is not canonical " + CANONICAL_REPOSITORY + ": " + repr(remote))


def require_manifest_identity(
    manifest: dict[str, str],
    authorization: Authorization,
) -> None:
    required = {
        "release_envelope_format": MULTI_RELEASE_FORMAT,
        "distribution_model": DISTRIBUTION_MODEL,
        "release_version": authorization.release_version,
        "release_tag": authorization.release_tag,
        "prerelease": "true",
        "source_repository": CANONICAL_SOURCE_REPOSITORY,
        "source_revision": authorization.candidate_source_revision,
        "release_notes": NOTES_NAME,
    }
    for key, expected in required.items():
        actual = manifest.get(key)
        if actual != expected:
            fail(
                "release manifest "
                + key
                + " mismatch: "
                + repr(actual)
                + " != "
                + repr(expected)
            )

    baseline = manifest.get("release_baseline_revision", "")
    if SHA_RE.fullmatch(baseline) is None:
        fail("release manifest release_baseline_revision is not exact lowercase 40-hex")


def read_assets(
    candidate: Path,
    envelope_dir: Path,
    manifest: dict[str, str],
) -> tuple[Asset, ...]:
    try:
        count = int(manifest.get("asset_count", ""))
    except ValueError:
        fail("release manifest asset_count is not an integer")

    if count != 2:
        fail("JVM_PLUS_NATIVE publication requires exactly two release artifacts")

    assets: list[Asset] = []
    seen_kinds: set[str] = set()
    seen_names: set[str] = set()

    for index in range(count):
        prefix = f"asset.{index}."
        kind = manifest.get(prefix + "kind", "")
        role = manifest.get(prefix + "role", "")
        archive_name = safe_basename(
            manifest.get(prefix + "archive", ""),
            label=prefix + "archive",
        )
        checksum_name = safe_basename(
            manifest.get(prefix + "checksum", ""),
            label=prefix + "checksum",
        )
        archive_sha = manifest.get(prefix + "archive_sha256", "")
        checksum_sha = manifest.get(prefix + "checksum_sha256", "")

        if kind not in {"native", "portable-jvm"}:
            fail("release manifest contains unsupported artifact kind: " + repr(kind))
        if kind in seen_kinds:
            fail("release manifest contains duplicate artifact kind: " + kind)
        seen_kinds.add(kind)

        expected_role = (
            "recommended-first-run"
            if kind == "native"
            else "compatibility-fallback"
        )
        if role != expected_role:
            fail(
                "release manifest role mismatch for "
                + kind
                + ": "
                + repr(role)
                + " != "
                + repr(expected_role)
            )

        if checksum_name != archive_name + ".sha256":
            fail("release manifest checksum basename does not derive from archive")
        if SHA256_RE.fullmatch(archive_sha) is None:
            fail("release manifest archive SHA-256 is malformed: " + archive_name)
        if SHA256_RE.fullmatch(checksum_sha) is None:
            fail("release manifest checksum SHA-256 is malformed: " + checksum_name)

        if archive_name in seen_names or checksum_name in seen_names:
            fail("release manifest contains duplicate public asset name")
        seen_names.add(archive_name)
        seen_names.add(checksum_name)

        archive_path = (
            candidate
            / "target"
            / "distributions"
            / archive_name
        ).resolve()
        checksum_path = (envelope_dir / checksum_name).resolve()

        if not archive_path.is_file():
            fail("prepared release archive is missing: " + str(archive_path))
        if not checksum_path.is_file():
            fail("prepared external checksum is missing: " + str(checksum_path))

        actual_archive_sha = sha256_file(archive_path)
        if actual_archive_sha != archive_sha:
            fail(
                "prepared archive digest mismatch: "
                + archive_name
                + " "
                + actual_archive_sha
                + " != "
                + archive_sha
            )

        expected_checksum = (
            archive_sha + "  " + archive_name + "\n"
        ).encode("utf-8")
        actual_checksum = checksum_path.read_bytes()
        if actual_checksum != expected_checksum:
            fail("prepared external checksum content mismatch: " + checksum_name)

        actual_checksum_sha = sha256_bytes(actual_checksum)
        if actual_checksum_sha != checksum_sha:
            fail(
                "prepared external checksum digest mismatch: "
                + checksum_name
            )

        assets.append(
            Asset(
                kind=kind,
                role=role,
                archive_name=archive_name,
                checksum_name=checksum_name,
                archive_sha256=archive_sha,
                checksum_sha256=checksum_sha,
                archive_path=archive_path,
                checksum_path=checksum_path,
            )
        )

    if seen_kinds != {"native", "portable-jvm"}:
        fail("JVM_PLUS_NATIVE artifact kind set is incomplete")

    return tuple(assets)


def verify_release_metadata(prepared: PreparedRelease) -> None:
    verifier = prepared.candidate / "dist" / "verify_release_metadata.py"
    if not verifier.is_file():
        fail("candidate release metadata verifier is missing")

    command = [sys.executable, str(verifier)]
    for asset in prepared.assets:
        command.extend(["--archive", str(asset.archive_path)])
    command.extend(["--envelope-dir", str(prepared.envelope_dir)])

    run_text(prepared.candidate, command)


def prepare_publication(
    *,
    authorization_path: Path,
    candidate: Path,
    envelope_dir: Path,
) -> PreparedRelease:
    authorization = parse_authorization(authorization_path)
    candidate = candidate.resolve()
    envelope_dir = envelope_dir.resolve()

    manifest_path = envelope_dir / MANIFEST_NAME
    if not manifest_path.is_file():
        fail("release manifest is missing: " + str(manifest_path))

    manifest_sha = sha256_file(manifest_path)
    if manifest_sha != authorization.release_manifest_sha256:
        fail(
            "authorized release manifest digest mismatch: "
            + manifest_sha
            + " != "
            + authorization.release_manifest_sha256
        )

    manifest = read_key_values(manifest_path, label=MANIFEST_NAME)
    require_manifest_identity(manifest, authorization)

    require_candidate_state(candidate, authorization, manifest)
    require_canonical_remote(candidate)

    notes_path = envelope_dir / NOTES_NAME
    if not notes_path.is_file():
        fail("prepared release notes are missing")

    notes_sha = manifest.get("release_notes_sha256", "")
    if SHA256_RE.fullmatch(notes_sha) is None:
        fail("release manifest release_notes_sha256 is malformed")
    if sha256_file(notes_path) != notes_sha:
        fail("prepared release notes digest mismatch")

    assets = read_assets(candidate, envelope_dir, manifest)

    prepared = PreparedRelease(
        authorization=authorization,
        candidate=candidate,
        envelope_dir=envelope_dir,
        manifest_path=manifest_path,
        manifest=manifest,
        notes_path=notes_path,
        assets=assets,
        title="Protos " + authorization.release_version,
    )

    verify_release_metadata(prepared)
    require_local_identity(prepared)
    return prepared


def require_local_identity(prepared: PreparedRelease) -> None:
    if sha256_file(prepared.manifest_path) != prepared.authorization.release_manifest_sha256:
        fail("release manifest changed after publication preflight")

    notes_sha = prepared.manifest["release_notes_sha256"]
    if sha256_file(prepared.notes_path) != notes_sha:
        fail("release notes changed after publication preflight")

    for asset in prepared.assets:
        if sha256_file(asset.archive_path) != asset.archive_sha256:
            fail("release archive changed after publication preflight: " + asset.archive_name)
        if sha256_file(asset.checksum_path) != asset.checksum_sha256:
            fail(
                "release checksum changed after publication preflight: "
                + asset.checksum_name
            )

    require_candidate_state(
        prepared.candidate,
        prepared.authorization,
        prepared.manifest,
    )


def local_tag_exists_exact(prepared: PreparedRelease) -> bool:
    ref = "refs/tags/" + prepared.authorization.release_tag
    result = run_text(
        prepared.candidate,
        ["git", "show-ref", "--verify", "--hash", ref],
        check=False,
    )
    if result.returncode == 1:
        return False
    if result.returncode != 0:
        fail("cannot inspect local release tag")

    object_type = run_text(
        prepared.candidate,
        ["git", "cat-file", "-t", ref],
    ).stdout.strip()
    if object_type != "commit":
        fail("local release tag is not lightweight")

    target = result.stdout.strip()
    if target != prepared.authorization.candidate_source_revision:
        fail(
            "local release tag points to conflicting commit: "
            + target
        )
    return True


def remote_tag_exists_exact(prepared: PreparedRelease) -> bool:
    ref = "refs/tags/" + prepared.authorization.release_tag
    result = run_text(
        prepared.candidate,
        ["git", "ls-remote", "--refs", "--tags", "origin", ref],
    )
    lines = [line for line in result.stdout.splitlines() if line.strip()]
    if not lines:
        return False
    if len(lines) != 1:
        fail("remote release tag query returned ambiguous results")

    parts = lines[0].split()
    if len(parts) != 2 or parts[1] != ref:
        fail("remote release tag query returned malformed identity")

    if parts[0] != prepared.authorization.candidate_source_revision:
        fail(
            "remote release tag points to conflicting commit: "
            + parts[0]
        )
    return True


def fetch_release(prepared: PreparedRelease) -> dict[str, object] | None:
    endpoint = (
        "repos/"
        + CANONICAL_REPOSITORY
        + "/releases/tags/"
        + prepared.authorization.release_tag
    )
    result = run_text(
        prepared.candidate,
        ["gh", "api", endpoint],
        check=False,
    )
    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip()
        if "404" in detail or "Not Found" in detail:
            return None
        fail("cannot inspect GitHub Release: " + detail)

    try:
        value = json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        fail("GitHub Release response is not valid JSON: " + str(exc))

    if not isinstance(value, dict):
        fail("GitHub Release response is not an object")
    return value


def fetch_asset_bytes(
    prepared: PreparedRelease,
    asset_id: int,
) -> bytes:
    endpoint = (
        "repos/"
        + CANONICAL_REPOSITORY
        + "/releases/assets/"
        + str(asset_id)
    )
    result = run_bytes(
        prepared.candidate,
        [
            "gh",
            "api",
            "-H",
            "Accept: application/octet-stream",
            endpoint,
        ],
    )
    return result.stdout


def inspect_release_assets(
    prepared: PreparedRelease,
    release: dict[str, object],
) -> tuple[str, ...]:
    expected = prepared.expected_public_asset_digests()
    raw_assets = release.get("assets")
    if not isinstance(raw_assets, list):
        fail("GitHub Release assets field is not a list")

    actual: dict[str, int] = {}
    for item in raw_assets:
        if not isinstance(item, dict):
            fail("GitHub Release contains malformed asset metadata")
        name = item.get("name")
        asset_id = item.get("id")
        if not isinstance(name, str) or not isinstance(asset_id, int):
            fail("GitHub Release asset metadata lacks exact name/id")
        if name in actual:
            fail("GitHub Release contains duplicate asset name: " + name)
        actual[name] = asset_id

    unexpected = sorted(set(actual) - set(expected))
    if unexpected:
        fail("GitHub Release contains unexpected assets: " + repr(unexpected))

    for name, asset_id in actual.items():
        published = fetch_asset_bytes(prepared, asset_id)
        actual_sha = sha256_bytes(published)
        expected_sha = expected[name]
        if actual_sha != expected_sha:
            fail(
                "published asset digest mismatch: "
                + name
                + " "
                + actual_sha
                + " != "
                + expected_sha
            )

    return tuple(
        path.name
        for path in prepared.publication_paths()
        if path.name not in actual
    )


def inspect_release_identity(
    prepared: PreparedRelease,
    release: dict[str, object],
) -> tuple[str, ...]:
    expected = {
        "tag_name": prepared.authorization.release_tag,
        "name": prepared.title,
        "prerelease": True,
        "draft": False,
        "body": prepared.notes_path.read_text(encoding="utf-8"),
    }
    for key, value in expected.items():
        actual = release.get(key)
        if actual != value:
            fail(
                "GitHub Release "
                + key
                + " mismatch: "
                + repr(actual)
                + " != "
                + repr(value)
            )

    return inspect_release_assets(prepared, release)


def inspect_public_state(prepared: PreparedRelease) -> PublicationState:
    local = local_tag_exists_exact(prepared)
    remote = remote_tag_exists_exact(prepared)
    release = fetch_release(prepared)

    if release is not None and not remote:
        fail("GitHub Release exists without the exact public tag")

    if release is None:
        missing = tuple(path.name for path in prepared.publication_paths())
    else:
        missing = inspect_release_identity(prepared, release)

    return PublicationState(
        local_tag=local,
        remote_tag=remote,
        release_exists=release is not None,
        missing_assets=missing,
    )


def create_local_tag(prepared: PreparedRelease) -> None:
    run_text(
        prepared.candidate,
        [
            "git",
            "tag",
            prepared.authorization.release_tag,
            prepared.authorization.candidate_source_revision,
        ],
    )
    if not local_tag_exists_exact(prepared):
        fail("local lightweight release tag was not created exactly")


def push_remote_tag(prepared: PreparedRelease) -> None:
    ref = "refs/tags/" + prepared.authorization.release_tag
    run_text(
        prepared.candidate,
        ["git", "push", "origin", ref + ":" + ref],
    )
    if not remote_tag_exists_exact(prepared):
        fail("remote release tag did not resolve to the authorized candidate")


def create_release(prepared: PreparedRelease) -> None:
    run_text(
        prepared.candidate,
        [
            "gh",
            "release",
            "create",
            prepared.authorization.release_tag,
            "--repo",
            CANONICAL_REPOSITORY,
            "--verify-tag",
            "--title",
            prepared.title,
            "--notes-file",
            str(prepared.notes_path),
            "--prerelease",
        ],
    )


def upload_missing_assets(
    prepared: PreparedRelease,
    missing_names: tuple[str, ...],
) -> None:
    if not missing_names:
        return

    path_by_name = {
        path.name: path
        for path in prepared.publication_paths()
    }
    missing_set = set(missing_names)
    if not missing_set.issubset(path_by_name):
        fail("internal publication recovery contains unknown missing asset")

    ordered = [
        path_by_name[path.name]
        for path in prepared.publication_paths()
        if path.name in missing_set
    ]

    run_text(
        prepared.candidate,
        [
            "gh",
            "release",
            "upload",
            prepared.authorization.release_tag,
            "--repo",
            CANONICAL_REPOSITORY,
            *[str(path) for path in ordered],
        ],
    )


def verify_published(prepared: PreparedRelease) -> None:
    if not remote_tag_exists_exact(prepared):
        fail("post-publication public tag is missing")

    release = fetch_release(prepared)
    if release is None:
        fail("post-publication GitHub Release is missing")

    missing = inspect_release_identity(prepared, release)
    if missing:
        fail("post-publication asset set is incomplete: " + repr(missing))

    if sha256_file(prepared.manifest_path) != prepared.authorization.release_manifest_sha256:
        fail("post-publication release manifest identity changed")


def report_partial_state(prepared: PreparedRelease) -> None:
    print("DIST005_RELEASE_PUBLICATION: PARTIAL")

    try:
        remote = remote_tag_exists_exact(prepared)
    except BaseException:
        print("PARTIAL_REMOTE_TAG=UNKNOWN")
        print("PARTIAL_GITHUB_RELEASE=UNKNOWN")
        print("PARTIAL_PUBLISHED_ASSETS=UNKNOWN")
        print("PARTIAL_MISSING_ASSETS=UNKNOWN")
        print("PARTIAL_STATE_VERIFIED=FAIL_CLOSED")
        print("SAFE_RESUME=NO_UNTIL_PUBLIC_STATE_IS_INSPECTABLE")
        return

    print("PARTIAL_REMOTE_TAG=" + ("EXACT" if remote else "ABSENT"))

    try:
        release = fetch_release(prepared)
    except BaseException:
        print("PARTIAL_GITHUB_RELEASE=UNKNOWN")
        print("PARTIAL_PUBLISHED_ASSETS=UNKNOWN")
        print("PARTIAL_MISSING_ASSETS=UNKNOWN")
        print("PARTIAL_STATE_VERIFIED=FAIL_CLOSED")
        print("SAFE_RESUME=NO_UNTIL_PUBLIC_STATE_IS_INSPECTABLE")
        return

    expected_names = tuple(path.name for path in prepared.publication_paths())

    if release is None:
        print("PARTIAL_GITHUB_RELEASE=ABSENT")
        print("PARTIAL_PUBLISHED_ASSETS=NONE")
        print("PARTIAL_MISSING_ASSETS=" + ",".join(expected_names))
        if remote:
            print("PARTIAL_STATE_VERIFIED=PASS")
            print("SAFE_RESUME=RERUN_SAME_AUTHORIZED_PUBLICATION")
        else:
            print("PARTIAL_STATE_VERIFIED=FAIL_CLOSED")
            print("SAFE_RESUME=NO_UNTIL_EXACT_REMOTE_TAG_STATE_IS_REESTABLISHED")
        return

    if not remote:
        print("PARTIAL_GITHUB_RELEASE=PRESENT_WITHOUT_EXACT_REMOTE_TAG")
        print("PARTIAL_PUBLISHED_ASSETS=UNKNOWN")
        print("PARTIAL_MISSING_ASSETS=UNKNOWN")
        print("PARTIAL_STATE_VERIFIED=FAIL_CLOSED")
        print("SAFE_RESUME=NO_UNTIL_REMOTE_TAG_CONFLICT_IS_RESOLVED")
        return

    try:
        missing = inspect_release_identity(prepared, release)
    except BaseException:
        print("PARTIAL_GITHUB_RELEASE=CONFLICT_OR_UNVERIFIED")
        print("PARTIAL_PUBLISHED_ASSETS=UNKNOWN")
        print("PARTIAL_MISSING_ASSETS=UNKNOWN")
        print("PARTIAL_STATE_VERIFIED=FAIL_CLOSED")
        print("SAFE_RESUME=NO_UNTIL_RELEASE_CONFLICT_IS_RESOLVED")
        return

    missing_set = set(missing)
    published = tuple(name for name in expected_names if name not in missing_set)

    print("PARTIAL_GITHUB_RELEASE=EXACT")
    print(
        "PARTIAL_PUBLISHED_ASSETS="
        + (",".join(published) if published else "NONE")
    )
    print(
        "PARTIAL_MISSING_ASSETS="
        + (",".join(missing) if missing else "NONE")
    )
    print("PARTIAL_STATE_VERIFIED=PASS")
    print("SAFE_RESUME=RERUN_SAME_AUTHORIZED_PUBLICATION")


def emit_pass(prepared: PreparedRelease) -> None:
    print("DIST005_RELEASE_PUBLICATION: PASS")
    print(
        "RELEASE_CANDIDATE_SOURCE_REVISION="
        + prepared.authorization.candidate_source_revision
    )
    print("RELEASE_VERSION=" + prepared.authorization.release_version)
    print("RELEASE_TAG=" + prepared.authorization.release_tag)
    print("PUBLIC_TAG_IDENTITY=PASS")
    print("GITHUB_PRERELEASE_METADATA=PASS")
    print("PUBLISHED_ASSET_SET=PASS")
    print("PUBLISHED_ASSET_DIGESTS=PASS")
    print("RELEASE_MANIFEST_IDENTITY=PASS")
    print("RELEASE_PUBLICATION_AUTHORIZED=YES")
    print("TAG_CREATED_OR_REUSED=YES")
    print("PUBLIC_RELEASE_CREATED_OR_REUSED=YES")


def publish(
    *,
    authorization_path: Path,
    candidate: Path,
    envelope_dir: Path,
) -> None:
    prepared = prepare_publication(
        authorization_path=authorization_path,
        candidate=candidate,
        envelope_dir=envelope_dir,
    )

    state = inspect_public_state(prepared)

    if state.remote_tag and state.release_exists and not state.missing_assets:
        verify_published(prepared)
        emit_pass(prepared)
        return

    require_local_identity(prepared)
    public_state_possible = state.remote_tag

    try:
        if not state.remote_tag:
            if not state.local_tag:
                create_local_tag(prepared)

            # A failed push can still have reached the remote. From this point
            # forward every failure receives a fresh read-only public-state
            # inspection before the original failure is propagated.
            public_state_possible = True
            push_remote_tag(prepared)

        if not state.release_exists:
            create_release(prepared)
            missing = tuple(path.name for path in prepared.publication_paths())
        else:
            missing = state.missing_assets

        upload_missing_assets(prepared, missing)
        verify_published(prepared)
    except BaseException:
        if public_state_possible:
            report_partial_state(prepared)
        raise

    emit_pass(prepared)


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Publish one explicitly authorized, already-prepared and "
            "already-verified Protos JVM_PLUS_NATIVE prerelease."
        )
    )
    parser.add_argument("--authorization", required=True)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--envelope-dir", required=True)
    args = parser.parse_args()

    publish(
        authorization_path=Path(args.authorization),
        candidate=Path(args.candidate),
        envelope_dir=Path(args.envelope_dir),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
