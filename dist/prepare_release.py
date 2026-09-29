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
import shutil
import subprocess
import sys
from typing import NoReturn

sys.dont_write_bytecode = True

from materialize_release_candidate import resume_or_materialize
from prepare_release_candidate_worktree import parse_selection, require_selection
from release_asset_envelope import (
    FALLBACK_ROLE,
    PUBLIC_NATIVE_RUNTIME_IDENTITY,
    RECOMMENDED_ROLE,
)
from validate_release_candidate import (
    current_specification_revision,
    require_tag_available,
)


_NATIVE = PUBLIC_NATIVE_RUNTIME_IDENTITY
NATIVE_ROLE = "-".join(
    [
        "native",
        _NATIVE["target_os"],
        _NATIVE["target_arch"],
        _NATIVE["libc_family"],
        _NATIVE["libc_abi_min"],
        _NATIVE["linkage"],
        _NATIVE["cpu_isa_assumption"],
    ]
) + "=" + RECOMMENDED_ROLE
PORTABLE_ROLE = "portable-jvm=" + FALLBACK_ROLE


def fail(message: str) -> NoReturn:
    raise SystemExit("release preparation failed: " + message)


def root() -> Path:
    return Path(__file__).resolve().parents[1]


def step(label: str, command: list[str], cwd: Path) -> None:
    print()
    print("phase=" + label)
    result = subprocess.run(
        command,
        cwd=cwd,
        env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
        check=False,
    )
    if result.returncode != 0:
        fail(f"{label} failed with status {result.returncode}")


def git(cwd: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=cwd,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        fail(
            "git "
            + " ".join(args)
            + " failed: "
            + (result.stderr.strip() or result.stdout.strip())
        )
    return result.stdout.strip()


def candidate_path(version: str) -> Path:
    cache = os.environ.get("XDG_CACHE_HOME")
    base = Path(cache).expanduser() if cache else Path.home() / ".cache"
    return (
        base
        / "protos"
        / "release-candidates"
        / f"protos-{version}-candidate"
    ).resolve()


def reset_generated_envelope(
    candidate: Path,
    envelope: Path,
) -> None:
    candidate = candidate.resolve()
    envelope = envelope.resolve()
    owned_root = (candidate / "target").resolve()

    if envelope.parent != owned_root:
        fail(
            "release envelope is outside the owned candidate target "
            "directory: "
            + str(envelope)
        )

    if envelope.exists():
        if not envelope.is_dir():
            fail(
                "release envelope path exists but is not a directory: "
                + str(envelope)
            )
        shutil.rmtree(envelope)


def prepare(
    selection_path: Path,
    capabilities: list[str],
    limitations: list[str],
) -> None:
    if not capabilities:
        fail("at least one explicit --capability is required")
    if not limitations:
        fail("at least one explicit --limitation is required")

    repo = root()
    selection_path = selection_path.resolve()
    selection = parse_selection(selection_path)
    require_selection(selection)

    spec = current_specification_revision(repo)
    if selection["specification_revision"] != spec:
        fail(
            "selection specification revision does not match "
            "current repository"
        )

    step(
        "refresh-origin-main",
        ["git", "fetch", "--quiet", "origin", "main"],
        repo,
    )
    require_tag_available(repo, selection["release_tag"])

    version = selection["release_version"]
    baseline = selection["release_baseline_revision"]

    candidate = candidate_path(version)
    candidate.parent.mkdir(parents=True, exist_ok=True)

    revision = resume_or_materialize(
        selection_path=selection_path,
        candidate=candidate,
    )

    target = candidate / "target" / "distributions"
    native = target / (
        f"protos-{version}-native-"
        f"{_NATIVE['target_os']}-{_NATIVE['target_arch']}.zip"
    )
    portable = target / f"protos-{version}-posix-jvm.zip"
    envelope = candidate / "target" / f"release-envelope-{version}"

    step(
        "native-image-build",
        ["make", "-C", "build/native", "build"],
        candidate,
    )
    step(
        "native-archive-build",
        [
            sys.executable,
            "dist/build_native.py",
            "--public-prerelease",
            "--release-baseline",
            baseline,
        ],
        candidate,
    )
    step(
        "native-complete-admission",
        [
            sys.executable,
            "dist/validate_native.py",
            "--archive",
            str(native),
        ],
        candidate,
    )

    step(
        "portable-jvm-build",
        [
            sys.executable,
            "dist/build_portable.py",
            "--public-prerelease",
            "--release-baseline",
            baseline,
        ],
        candidate,
    )
    step(
        "portable-jvm-complete-admission",
        [
            "sh",
            "dist/validate_portable.sh",
            "--archive",
            str(portable),
            "--public-prerelease",
            "--release-baseline",
            baseline,
            "--require-clean-source",
        ],
        candidate,
    )

    reset_generated_envelope(
        candidate,
        envelope,
    )

    command = [
        sys.executable,
        "dist/prepare_release_metadata.py",
        "--archive",
        str(native),
        "--archive",
        str(portable),
        "--asset-role",
        NATIVE_ROLE,
        "--asset-role",
        PORTABLE_ROLE,
        "--spec-revision",
        spec,
        "--output-dir",
        str(envelope),
    ]

    for value in capabilities:
        command.extend(["--capability", value])

    for value in limitations:
        command.extend(["--limitation", value])

    step(
        "multi-asset-envelope-prepare",
        command,
        candidate,
    )
    step(
        "multi-asset-envelope-verify",
        [
            sys.executable,
            "dist/verify_release_metadata.py",
            "--archive",
            str(native),
            "--archive",
            str(portable),
            "--envelope-dir",
            str(envelope),
        ],
        candidate,
    )

    if git(candidate, "rev-parse", "HEAD") != revision:
        fail("candidate HEAD changed during preparation")

    status = git(
        candidate,
        "status",
        "--porcelain=v1",
        "--untracked-files=all",
    )
    if status:
        fail(
            "candidate worktree is dirty after preparation: "
            + repr(status)
        )

    require_tag_available(repo, selection["release_tag"])

    print()
    print("DIST005_RELEASE_PREPARATION: PASS")
    print("RELEASE_CANDIDATE_SOURCE_REVISION=" + revision)
    print("RELEASE_BASELINE_REVISION=" + baseline)
    print(
        "RELEASE_BASELINE_VERSION="
        + selection["release_baseline_version"]
    )
    print("RELEASE_VERSION=" + version)
    print("RELEASE_TAG=" + selection["release_tag"])
    print("SPECIFICATION_REVISION=" + spec)
    print("NATIVE_ARCHIVE=" + str(native))
    print("PORTABLE_JVM_ARCHIVE=" + str(portable))
    print("RELEASE_ENVELOPE=" + str(envelope))
    print("COMMON_CANDIDATE_IDENTITY=PASS")
    print("CANDIDATE_FINAL_STATUS=CLEAN")
    print("RECOMMENDED_FIRST_RUN_ARTIFACT=NATIVE")
    print("PORTABLE_JVM_ROLE=COMPATIBILITY_FALLBACK")
    print("RELEASE_PUBLICATION_AUTHORIZED=NO")
    print("TAG_CREATED=NO")
    print("PUBLIC_RELEASE_CREATED=NO")


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Prepare and fully validate one selected JVM_PLUS_NATIVE "
            "release candidate without publishing it."
        )
    )
    parser.add_argument(
        "--selection",
        required=True,
        help="explicit pre-publication DIST001 selection record",
    )
    parser.add_argument(
        "--capability",
        action="append",
        default=[],
        help="explicit release capability claim; repeat as needed",
    )
    parser.add_argument(
        "--limitation",
        action="append",
        default=[],
        help="explicit release limitation claim; repeat as needed",
    )
    args = parser.parse_args()

    prepare(
        Path(args.selection),
        args.capability,
        args.limitation,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
