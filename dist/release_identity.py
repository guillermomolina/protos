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

from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

_NUM = r"(?:0|[1-9][0-9]*)"
PUBLIC_VERSION_RE = re.compile(rf"{_NUM}\.{_NUM}\.{_NUM}")
SNAPSHOT_VERSION_RE = re.compile(rf"{_NUM}\.{_NUM}\.{_NUM}-SNAPSHOT")
FULL_SHA_RE = re.compile(r"[0-9a-f]{40}")


def require_public_version(version: str) -> str:
    if PUBLIC_VERSION_RE.fullmatch(version) is None:
        raise ValueError(
            "public prerelease project version must be canonical MAJOR.MINOR.PATCH"
        )
    return version


def require_snapshot_version(version: str) -> str:
    if SNAPSHOT_VERSION_RE.fullmatch(version) is None:
        raise ValueError(
            "development distribution project version must be canonical "
            "MAJOR.MINOR.PATCH-SNAPSHOT"
        )
    return version


def public_version_from_snapshot(snapshot_version: str) -> str:
    require_snapshot_version(snapshot_version)
    return snapshot_version.removesuffix("-SNAPSHOT")


def snapshot_version_for_public(public_version: str) -> str:
    require_public_version(public_version)
    return public_version + "-SNAPSHOT"


def _git(root: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=root,
        check=check,
        text=True,
        capture_output=True,
    )


def _pom_version_from_text(text: str) -> str:
    try:
        document = ET.fromstring(text)
    except ET.ParseError as exc:
        raise ValueError("baseline pom.xml is not valid XML") from exc
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    version = document.findtext("m:version", namespaces=ns)
    if not version:
        raise ValueError("baseline pom.xml project version is missing")
    return version


def project_version_at_revision(root: Path, revision: str) -> str:
    if FULL_SHA_RE.fullmatch(revision) is None:
        raise ValueError("release baseline revision must be an exact 40-hex commit SHA")
    try:
        result = _git(root, "show", f"{revision}:pom.xml")
    except subprocess.CalledProcessError as exc:
        raise ValueError(
            "release baseline revision does not expose pom.xml: " + revision
        ) from exc
    return _pom_version_from_text(result.stdout)


def validate_release_baseline(
    root: Path,
    *,
    public_version: str,
    baseline_revision: str,
    source_revision: str,
) -> str:
    require_public_version(public_version)
    if FULL_SHA_RE.fullmatch(source_revision) is None:
        raise ValueError("candidate source revision must be an exact 40-hex commit SHA")

    baseline_version = project_version_at_revision(root, baseline_revision)
    expected_snapshot = snapshot_version_for_public(public_version)
    if baseline_version != expected_snapshot:
        raise ValueError(
            "release baseline project version mismatch: "
            f"{baseline_version!r} != {expected_snapshot!r}"
        )

    ancestry = _git(
        root,
        "merge-base",
        "--is-ancestor",
        baseline_revision,
        source_revision,
        check=False,
    )
    if ancestry.returncode == 1:
        raise ValueError(
            "release baseline is not an ancestor of candidate source revision"
        )
    if ancestry.returncode != 0:
        raise ValueError(
            "cannot verify release baseline ancestry: "
            + ancestry.stderr.strip()
        )
    return baseline_version


def build_source_metadata(
    root: Path,
    *,
    version: str,
    source_revision: str,
    source_dirty: bool,
    public_prerelease: bool,
    release_baseline: str | None,
) -> dict[str, str]:
    common: dict[str, str] = {
        "implementation_version": version,
        "source_revision": source_revision,
        "source_dirty": "true" if source_dirty else "false",
        "source_repository": "https://github.com/guillermomolina/protos",
        "source_path": "/tree/" + source_revision,
    }

    if not public_prerelease:
        require_snapshot_version(version)
        if release_baseline is not None:
            raise ValueError(
                "--release-baseline is valid only with --public-prerelease"
            )
        return {
            **common,
            "artifact_kind": "development-distribution",
            "public_release": "false",
        }

    require_public_version(version)
    if source_dirty:
        raise ValueError(
            "public prerelease distribution requires a clean candidate source tree"
        )
    if release_baseline is None:
        raise ValueError("--public-prerelease requires --release-baseline")

    baseline_version = validate_release_baseline(
        root,
        public_version=version,
        baseline_revision=release_baseline,
        source_revision=source_revision,
    )

    return {
        **common,
        "artifact_kind": "public-prerelease",
        "public_release": "true",
        "release_baseline_revision": release_baseline,
        "release_baseline_version": baseline_version,
        "release_version": version,
        "release_tag": "v" + version,
    }
