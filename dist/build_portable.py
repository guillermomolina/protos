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
import datetime as dt
import hashlib
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

from release_identity import build_source_metadata

DIST_FORMAT = "protos-portable-posix-jvm-v1"
SUPPORTED_JAVA_FEATURE = "25"
SUPPORTED_JAVA_VERSION = "25.0.4.1"
SUPPORTED_GRAALVM_RELEASE = "25.3.4.1"
SUPPORTED_VENDOR_TOKEN = "GraalVM"
EXPECTED_TRUFFLE_VERSION = "25.3.4.1"
EXPECTED_OPTIMIZING_RUNTIME = "HotSpotTruffleRuntime"
DEPENDENCY_PLUGIN = "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies"


def fail(message: str) -> "NoReturn":
    raise SystemExit(f"distribution build failed: {message}")


def run(
    args: list[str],
    *,
    cwd: Path,
    capture: bool = False,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=cwd,
        check=True,
        text=True,
        capture_output=capture,
    )


def git(root: Path, *args: str) -> str:
    return run(["git", *args], cwd=root, capture=True).stdout.strip()


def project_version(root: Path) -> tuple[str, str]:
    tree = ET.parse(root / "pom.xml")
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    version = tree.findtext("m:version", namespaces=ns)
    truffle = tree.findtext("m:properties/m:graalvm.version", namespaces=ns)
    if not version:
        fail("pom.xml project version is missing")
    if not truffle:
        fail("pom.xml graalvm.version is missing")
    return version, truffle


def copy_tree(source: Path, target: Path) -> None:
    if not source.is_dir():
        fail(f"required distribution tree is missing: {source}")
    shutil.copytree(source, target)


def copy_file(source: Path, target: Path) -> None:
    if not source.is_file():
        fail(f"required distribution file is missing: {source}")
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.rstrip() + "\n", encoding="utf-8")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_checksums(bundle: Path) -> None:
    lines: list[str] = []
    for path in sorted(bundle.rglob("*")):
        if not path.is_file() or path.name == "SHA256SUMS":
            continue
        rel = path.relative_to(bundle).as_posix()
        lines.append(f"{sha256(path)}  {rel}")
    write_text(bundle / "SHA256SUMS", "\n".join(lines))


def commit_zip_timestamp(root: Path) -> tuple[int, int, int, int, int, int]:
    epoch = int(git(root, "show", "-s", "--format=%ct", "HEAD"))
    stamp = dt.datetime.fromtimestamp(epoch, tz=dt.timezone.utc)
    if stamp.year < 1980:
        stamp = stamp.replace(year=1980, month=1, day=1, hour=0, minute=0, second=0)
    # ZIP stores seconds at two-second granularity.
    return (
        stamp.year,
        stamp.month,
        stamp.day,
        stamp.hour,
        stamp.minute,
        stamp.second - (stamp.second % 2),
    )


def add_zip_entry(
    archive: zipfile.ZipFile,
    path: Path,
    arcname: str,
    timestamp: tuple[int, int, int, int, int, int],
) -> None:
    data = path.read_bytes()
    info = zipfile.ZipInfo(arcname, date_time=timestamp)
    mode = path.stat().st_mode
    info.create_system = 3
    info.external_attr = ((stat.S_IFREG | stat.S_IMODE(mode)) << 16)
    info.compress_type = zipfile.ZIP_DEFLATED
    archive.writestr(info, data)


def create_archive(root: Path, bundle: Path, archive_path: Path) -> None:
    archive_path.parent.mkdir(parents=True, exist_ok=True)
    if archive_path.exists():
        archive_path.unlink()
    timestamp = commit_zip_timestamp(root)
    with zipfile.ZipFile(
        archive_path,
        "w",
        compression=zipfile.ZIP_DEFLATED,
        compresslevel=9,
    ) as archive:
        for path in sorted(bundle.rglob("*")):
            if not path.is_file():
                continue
            rel = path.relative_to(bundle).as_posix()
            add_zip_entry(
                archive,
                path,
                f"{bundle.name}/{rel}",
                timestamp,
            )


def verify_archive(
    archive_path: Path,
    *,
    version: str,
    source_revision: str,
    source_dirty: bool,
    source_metadata: dict[str, str],
) -> None:
    if not archive_path.is_file():
        fail(f"archive was not created: {archive_path}")
    root_name = f"protos-{version}"
    required = {
        f"{root_name}/LICENSE.TXT",
        f"{root_name}/README.md",
        f"{root_name}/SOURCE.txt",
        f"{root_name}/RUNTIME.txt",
        f"{root_name}/DEPENDENCIES.txt",
        f"{root_name}/SHA256SUMS",
        f"{root_name}/bin/protos",
        f"{root_name}/lib/protos.jar",
    }
    required_prefixes = [
        f"{root_name}/lib/runtime/truffle-runtime-{EXPECTED_TRUFFLE_VERSION}.jar",
        f"{root_name}/protos/lib/core/",
        f"{root_name}/protos/tools/package/",
        f"{root_name}/protos/tools/test/",
        f"{root_name}/protos/tests/conformance/",
        f"{root_name}/protos/examples/",
        f"{root_name}/protos/tutorials/",
    ]

    with zipfile.ZipFile(archive_path) as archive:
        bad = archive.testzip()
        if bad is not None:
            fail(f"archive CRC failure: {bad}")
        names = archive.namelist()
        name_set = set(names)
        missing = sorted(required - name_set)
        if missing:
            fail("archive is missing required entries: " + ", ".join(missing))
        for prefix in required_prefixes:
            if prefix.endswith(".jar"):
                if prefix not in name_set:
                    fail(f"archive is missing required runtime: {prefix}")
            elif not any(name.startswith(prefix) for name in names):
                fail(f"archive is missing required tree: {prefix}")

        for name in names:
            posix = PurePosixPath(name)
            if posix.is_absolute() or ".." in posix.parts:
                fail(f"unsafe archive path: {name}")
            if "/target/" in f"/{name}/" or "/.git/" in f"/{name}/":
                fail(f"build/VCS state leaked into archive: {name}")

        source = archive.read(f"{root_name}/SOURCE.txt").decode("utf-8")
        for key, value in source_metadata.items():
            needle = f"{key}={value}"
            if needle not in source:
                fail(f"SOURCE.txt is missing {needle!r}")

        runtime = archive.read(f"{root_name}/RUNTIME.txt").decode("utf-8")
        for needle in [
            f"distribution_format={DIST_FORMAT}",
            f"java_feature={SUPPORTED_JAVA_FEATURE}",
            f"java_version={SUPPORTED_JAVA_VERSION}",
            f"java_vendor_contains={SUPPORTED_VENDOR_TOKEN}",
            f"graalvm_release={SUPPORTED_GRAALVM_RELEASE}",
            f"truffle_runtime_version={EXPECTED_TRUFFLE_VERSION}",
            f"optimizing_runtime={EXPECTED_OPTIMIZING_RUNTIME}",
        ]:
            if needle not in runtime:
                fail(f"RUNTIME.txt is missing {needle!r}")

        launcher = archive.getinfo(f"{root_name}/bin/protos")
        mode = (launcher.external_attr >> 16) & 0o777
        if not (mode & 0o111):
            fail("bin/protos is not executable in archive metadata")

    print("DIST_ARCHIVE_CRC_CHECK: PASS")
    print("DIST_LAYOUT_CHECK: PASS")
    print("DIST_SOURCE_IDENTITY_CHECK: PASS")
    print("DIST_RUNTIME_METADATA_CHECK: PASS")
    print("DIST_LAUNCHER_MODE_CHECK: PASS")


def build(args: argparse.Namespace) -> Path:
    root = Path(__file__).resolve().parents[1]
    if not (root / ".git").exists():
        fail("dist/build_portable.py must run from a Git checkout")

    version, truffle_version = project_version(root)
    if truffle_version != EXPECTED_TRUFFLE_VERSION:
        fail(
            "project graalvm.version changed from the DIST002 canonical runtime contract "
            f"({EXPECTED_TRUFFLE_VERSION} -> {truffle_version}); revalidate the "
            "distribution runtime before changing the bundle"
        )

    status = git(root, "status", "--porcelain=v1", "--untracked-files=all")
    source_dirty = bool(status)
    if source_dirty and not args.allow_dirty:
        fail("worktree is dirty; pass --allow-dirty only for pre-commit validation")

    source_revision = git(root, "rev-parse", "HEAD")
    try:
        source_metadata = build_source_metadata(
            root,
            version=version,
            source_revision=source_revision,
            source_dirty=source_dirty,
            public_prerelease=args.public_prerelease,
            release_baseline=args.release_baseline,
        )
    except ValueError as exc:
        fail(str(exc))
    if not args.skip_project_build:
        print("phase=dist01 build shaded Protos jar")
        run(["mvn", "-DskipTests", "package"], cwd=root)

    project_jar = root / "target" / f"protos-{version}.jar"
    if not project_jar.is_file():
        fail(f"shaded project jar not found: {project_jar}")

    output_root = root / "target" / "distributions"
    bundle = output_root / f"protos-{version}"
    archive_path = output_root / f"protos-{version}-posix-jvm.zip"
    if bundle.exists():
        shutil.rmtree(bundle)
    bundle.mkdir(parents=True)

    print("phase=dist02 materialize toolchain tree")
    copy_file(root / "bin/protos", bundle / "bin/protos")
    (bundle / "bin/protos").chmod(0o755)
    copy_file(project_jar, bundle / "lib/protos.jar")
    copy_file(root / "LICENSE.TXT", bundle / "LICENSE.TXT")
    copy_file(root / "README.md", bundle / "README.md")

    copy_tree(root / "protos/lib", bundle / "protos/lib")
    copy_tree(root / "protos/tools", bundle / "protos/tools")
    copy_tree(root / "protos/tests", bundle / "protos/tests")
    copy_tree(root / "protos/examples", bundle / "protos/examples")
    copy_tree(root / "protos/tutorials", bundle / "protos/tutorials")

    print("phase=dist03 resolve optimizing Truffle runtime")
    runtime_dir = bundle / "lib/runtime"
    runtime_dir.mkdir(parents=True)
    with tempfile.TemporaryDirectory(prefix="protos-runtime-deps-") as tmp:
        tmp_path = Path(tmp)
        run(
            [
                "mvn",
                "-q",
                "-f",
                str(root / "dist/runtime-pom.xml"),
                DEPENDENCY_PLUGIN,
                "-DincludeScope=runtime",
                f"-DoutputDirectory={tmp_path}",
            ],
            cwd=root,
        )
        jars = sorted(tmp_path.glob("*.jar"))
        if not jars:
            fail("runtime dependency resolution produced no jars")
        for jar in jars:
            shutil.copy2(jar, runtime_dir / jar.name)

    required_runtime = runtime_dir / f"truffle-runtime-{EXPECTED_TRUFFLE_VERSION}.jar"
    if not required_runtime.is_file():
        fail(f"required optimizing runtime jar was not resolved: {required_runtime.name}")

    runtime_jars = sorted(path.name for path in runtime_dir.glob("*.jar"))
    write_text(
        bundle / "SOURCE.txt",
        "\n".join(f"{key}={value}" for key, value in source_metadata.items()),
    )
    write_text(
        bundle / "RUNTIME.txt",
        "\n".join(
            [
                f"distribution_format={DIST_FORMAT}",
                f"java_feature={SUPPORTED_JAVA_FEATURE}",
                f"java_version={SUPPORTED_JAVA_VERSION}",
                f"java_vendor_contains={SUPPORTED_VENDOR_TOKEN}",
                f"graalvm_release={SUPPORTED_GRAALVM_RELEASE}",
                f"java_distribution=GraalVM Community Edition {SUPPORTED_GRAALVM_RELEASE} for JDK {SUPPORTED_JAVA_VERSION}",
                f"truffle_runtime_version={EXPECTED_TRUFFLE_VERSION}",
                f"optimizing_runtime={EXPECTED_OPTIMIZING_RUNTIME}",
                "runtime_evidence=docs/project/work/DIST002/DIST002_TOOLCHAIN_ALIGNMENT.md",
                "unsupported_runtime_override=PROTOS_ALLOW_UNSUPPORTED_RUNTIME=1",
            ]
        ),
    )
    write_text(
        bundle / "DEPENDENCIES.txt",
        "\n".join(
            [
                "Protos application dependencies are contained in lib/protos.jar.",
                (
                    "The optimizing distribution runtime is resolved from "
                    f"org.graalvm.truffle:truffle-runtime:{EXPECTED_TRUFFLE_VERSION} "
                    "and its Maven runtime dependency closure."
                ),
                "Runtime jars:",
                *[f"  {name}" for name in runtime_jars],
                "",
                (
                    "Third-party license metadata remains in the corresponding "
                    "JAR META-INF content. Public release compliance is rechecked "
                    "again at DIST001-E."
                ),
            ]
        ),
    )
    write_checksums(bundle)

    print("phase=dist04 create archive")
    create_archive(root, bundle, archive_path)
    verify_archive(
        archive_path,
        version=version,
        source_revision=source_revision,
        source_dirty=source_dirty,
        source_metadata=source_metadata,
    )
    print(f"DIST_ARCHIVE: {archive_path}")
    print("DIST_ARTIFACT_KIND: " + source_metadata["artifact_kind"])
    print("DIST_BUILD: PASS")
    return archive_path


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Build the DIST001 portable POSIX/JVM Protos distribution."
    )
    parser.add_argument(
        "--allow-dirty",
        action="store_true",
        help="allow a dirty worktree for pre-commit validation only",
    )
    parser.add_argument(
        "--skip-project-build",
        action="store_true",
        help="reuse the already-built shaded project jar",
    )
    parser.add_argument(
        "--public-prerelease",
        action="store_true",
        help=(
            "build explicit public-prerelease metadata; requires a clean "
            "non-SNAPSHOT project version and --release-baseline"
        ),
    )
    parser.add_argument(
        "--release-baseline",
        help=(
            "exact 40-hex main baseline SHA whose project version is the "
            "candidate public version plus -SNAPSHOT"
        ),
    )
    args = parser.parse_args()
    build(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
