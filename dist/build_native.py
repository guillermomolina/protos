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

"""Build the development-only DIST005 Native Image distribution proof."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import platform
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import xml.etree.ElementTree as ET
import zipfile

from release_identity import require_snapshot_version


DIST_FORMAT = "protos-native-image-posix-v1"
ARTIFACT_KIND = "development-native-distribution"
NATIVE_RUNTIME_KIND = "graalvm-native-image-truffle"

PUBLIC_NATIVE_BUILD_CONTAINER = (
    "ghcr.io/graalvm/native-image-community:25i4-25.0.4.1.1-ol10"
)
PUBLIC_NATIVE_TARGET_OS = "linux"
PUBLIC_NATIVE_TARGET_ARCH = "x86_64"
PUBLIC_NATIVE_LINKAGE = "dynamic"
PUBLIC_NATIVE_LIBC_FAMILY = "glibc"
PUBLIC_NATIVE_LIBC_ABI_MIN = "2.39"
PUBLIC_NATIVE_CPU_ISA_ASSUMPTION = "compatibility"


def fail(message: str) -> "NoReturn":
    raise SystemExit("native distribution build failed: " + message)


def run(
    args: list[str],
    *,
    cwd: Path,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=cwd,
        check=check,
        text=True,
        capture_output=True,
    )


def git(root: Path, *args: str) -> str:
    return run(["git", *args], cwd=root).stdout.strip()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.rstrip() + "\n", encoding="utf-8")


def copy_file(source: Path, target: Path) -> None:
    if not source.is_file():
        fail("required file is missing: " + str(source))
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def copy_tree(source: Path, target: Path) -> None:
    if not source.is_dir():
        fail("required tree is missing: " + str(source))
    shutil.copytree(source, target)


def project_version(root: Path) -> str:
    tree = ET.parse(root / "pom.xml")
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    version = tree.findtext("m:version", namespaces=ns)
    if not version:
        fail("pom.xml project version is missing")
    try:
        return require_snapshot_version(version)
    except ValueError as exc:
        fail(str(exc))


def toolchain(root: Path) -> dict[str, object]:
    try:
        data = json.loads((root / "toolchain.json").read_text(encoding="utf-8"))
        graal = data["graalvm"]
        release = str(graal["release"])
        jdk_version = str(graal["jdk_version"])
        components = str(data["graal_components"]["version"])
    except (OSError, KeyError, TypeError, ValueError) as exc:
        fail("cannot read canonical toolchain.json: " + str(exc))

    if release != components:
        fail("GraalVM and Graal component versions are not aligned")
    if str(graal.get("distribution")) != "graalvm-community":
        fail("unexpected canonical GraalVM distribution")
    if not jdk_version:
        fail("canonical JDK version is empty")
    return data


def native_build_container(root: Path) -> str:
    dockerfile = (root / "build/native/Dockerfile").read_text(encoding="utf-8")
    match = re.search(r"^FROM[ \t]+([^ \t\r\n]+)", dockerfile, flags=re.MULTILINE)
    if not match:
        fail("build/native/Dockerfile has no FROM authority")
    return match.group(1)


def normalized_arch() -> str:
    machine = platform.machine().lower()
    aliases = {
        "amd64": "x86_64",
        "x64": "x86_64",
        "arm64": "aarch64",
    }
    return aliases.get(machine, machine)


def binary_observation(root: Path, binary: Path) -> dict[str, str]:
    file_result = run(["file", "-b", str(binary)], cwd=root, check=False)
    if file_result.returncode != 0:
        fail("cannot inspect Native executable with file: " + file_result.stderr.strip())

    description = " ".join(file_result.stdout.strip().split())
    if "ELF" not in description:
        fail("Native executable is not an ELF binary: " + description)
    if "dynamically linked" not in description:
        fail(
            "DIST005 proof preserves the current dynamically-linked Native "
            "artifact; observed: " + description
        )

    ldd_result = run(["ldd", str(binary)], cwd=root, check=False)
    if ldd_result.returncode != 0:
        fail("cannot inspect Native shared-library closure: " + ldd_result.stderr.strip())

    libraries: set[str] = set()
    for raw in ldd_result.stdout.splitlines():
        line = raw.strip()
        if not line:
            continue
        if "=>" in line:
            name = line.split("=>", 1)[0].strip()
        else:
            token = line.split(None, 1)[0]
            name = Path(token).name if token.startswith("/") else token
        if name and not name.startswith("linux-vdso"):
            libraries.add(name)

    libc_family = "glibc" if "libc.so.6" in libraries else "unresolved"
    return {
        "binary_file_description": description,
        "linkage": "dynamic",
        "libc_family": libc_family,
        "shared_libraries": ",".join(sorted(libraries)),
    }


def write_checksums(bundle: Path) -> None:
    lines: list[str] = []
    for path in sorted(bundle.rglob("*")):
        if not path.is_file() or path.name == "SHA256SUMS":
            continue
        rel = path.relative_to(bundle).as_posix()
        lines.append(f"{sha256(path)}  {rel}")
    write_text(bundle / "SHA256SUMS", "\n".join(lines))


def zip_timestamp(root: Path) -> tuple[int, int, int, int, int, int]:
    epoch = int(git(root, "show", "-s", "--format=%ct", "HEAD"))
    stamp = dt.datetime.fromtimestamp(epoch, tz=dt.timezone.utc)
    if stamp.year < 1980:
        stamp = stamp.replace(
            year=1980,
            month=1,
            day=1,
            hour=0,
            minute=0,
            second=0,
        )
    return (
        stamp.year,
        stamp.month,
        stamp.day,
        stamp.hour,
        stamp.minute,
        stamp.second - (stamp.second % 2),
    )


def create_archive(root: Path, bundle: Path, archive_path: Path) -> None:
    timestamp = zip_timestamp(root)
    archive_path.parent.mkdir(parents=True, exist_ok=True)
    if archive_path.exists():
        archive_path.unlink()

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
            info = zipfile.ZipInfo(
                f"{bundle.name}/{rel}",
                date_time=timestamp,
            )
            info.create_system = 3
            info.external_attr = (
                (stat.S_IFREG | stat.S_IMODE(path.stat().st_mode)) << 16
            )
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, path.read_bytes())


def parse_checksums(text: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in text.splitlines():
        if not raw:
            continue
        if "  " not in raw:
            fail("malformed SHA256SUMS line: " + repr(raw))
        digest, rel = raw.split("  ", 1)
        if not re.fullmatch(r"[0-9a-f]{64}", digest):
            fail("invalid SHA256SUMS digest: " + repr(digest))
        if rel in result:
            fail("duplicate SHA256SUMS path: " + rel)
        result[rel] = digest
    return result


def verify_archive(archive_path: Path, root_name: str) -> None:
    prefix = root_name + "/"
    required = {
        prefix + "bin/protos",
        prefix + "libexec/protos-native",
        prefix + "LICENSE.TXT",
        prefix + "README.md",
        prefix + "SOURCE.txt",
        prefix + "RUNTIME.txt",
        prefix + "DEPENDENCIES.txt",
        prefix + "SHA256SUMS",
    }
    required_prefixes = (
        prefix + "protos/lib/",
        prefix + "protos/tools/",
        prefix + "protos/tests/",
    )

    with zipfile.ZipFile(archive_path) as archive:
        bad = archive.testzip()
        if bad is not None:
            fail("archive CRC failure: " + bad)

        names = archive.namelist()
        name_set = set(names)
        missing = sorted(required - name_set)
        if missing:
            fail("archive is missing required entries: " + ", ".join(missing))
        if len(names) != len(name_set):
            fail("archive contains duplicate entry names")

        for name in names:
            candidate = PurePosixPath(name)
            if not name.startswith(prefix):
                fail("archive entry escapes the single distribution root: " + name)
            if candidate.is_absolute() or ".." in candidate.parts:
                fail("unsafe archive path: " + name)
            if "/target/" in f"/{name}/" or "/.git/" in f"/{name}/":
                fail("build/VCS state leaked into archive: " + name)
            if name.endswith(".jar"):
                fail("Native distribution must not contain JVM JARs: " + name)

        for required_prefix in required_prefixes:
            if not any(name.startswith(required_prefix) for name in names):
                fail("archive is missing required tree: " + required_prefix)

        launcher_info = archive.getinfo(prefix + "bin/protos")
        native_info = archive.getinfo(prefix + "libexec/protos-native")
        launcher_mode = (launcher_info.external_attr >> 16) & 0o777
        native_mode = (native_info.external_attr >> 16) & 0o777
        if not (launcher_mode & 0o111):
            fail("bin/protos is not executable in archive metadata")
        if not (native_mode & 0o111):
            fail("libexec/protos-native is not executable in archive metadata")

        runtime = archive.read(prefix + "RUNTIME.txt").decode("utf-8")
        required_runtime = (
            f"distribution_format={DIST_FORMAT}",
            f"artifact_kind={ARTIFACT_KIND}",
            f"native_runtime_kind={NATIVE_RUNTIME_KIND}",
            "external_java_required=false",
            f"target_os={PUBLIC_NATIVE_TARGET_OS}",
            f"target_arch={PUBLIC_NATIVE_TARGET_ARCH}",
            f"linkage={PUBLIC_NATIVE_LINKAGE}",
            f"libc_family={PUBLIC_NATIVE_LIBC_FAMILY}",
            f"libc_abi_min={PUBLIC_NATIVE_LIBC_ABI_MIN}",
            f"cpu_isa_assumption={PUBLIC_NATIVE_CPU_ISA_ASSUMPTION}",
        )
        for needle in required_runtime:
            if needle not in runtime:
                fail("RUNTIME.txt is missing " + repr(needle))

        sums_name = prefix + "SHA256SUMS"
        recorded = parse_checksums(
            archive.read(sums_name).decode("utf-8")
        )
        actual_files = {
            name[len(prefix):]
            for name in names
            if name != sums_name
        }
        if set(recorded) != actual_files:
            fail("internal checksum coverage does not match archive files")

        for rel, expected in sorted(recorded.items()):
            actual = hashlib.sha256(
                archive.read(prefix + rel)
            ).hexdigest()
            if actual != expected:
                fail("internal checksum mismatch: " + rel)

    print("NATIVE_DIST_ARCHIVE_CRC_CHECK: PASS")
    print("NATIVE_DIST_LAYOUT_CHECK: PASS")
    print("NATIVE_DIST_NO_JVM_PLANE_CHECK: PASS")
    print("NATIVE_DIST_INTERNAL_CHECKSUM_CHECK: PASS")


def build(args: argparse.Namespace) -> Path:
    root = Path(__file__).resolve().parents[1]
    if not (root / ".git").is_dir():
        fail("dist/build_native.py must run from a Git checkout")

    version = project_version(root)
    source_revision = git(root, "rev-parse", "HEAD")
    source_dirty = bool(
        git(root, "status", "--porcelain=v1", "--untracked-files=all")
    )
    if source_dirty and not args.allow_dirty:
        fail(
            "worktree is dirty; pass --allow-dirty only for pre-commit "
            "development-proof validation"
        )

    native_binary = root / "target/native/protos"
    if not native_binary.is_file():
        fail(
            "Native executable is missing: target/native/protos; "
            "build the canonical Native Image first"
        )
    if not native_binary.stat().st_mode & stat.S_IXUSR:
        fail("Native executable is not executable: " + str(native_binary))

    contract = toolchain(root)
    graal = contract["graalvm"]
    observation = binary_observation(root, native_binary)

    build_container = native_build_container(root)
    if build_container != PUBLIC_NATIVE_BUILD_CONTAINER:
        fail(
            "Native builder does not match the selected public build base: "
            + build_container
        )

    target_os = platform.system().lower()
    target_arch = normalized_arch()

    if target_os != PUBLIC_NATIVE_TARGET_OS:
        fail(
            "Native target OS does not match the selected public platform: "
            + target_os
        )
    if target_arch != PUBLIC_NATIVE_TARGET_ARCH:
        fail(
            "Native target architecture does not match the selected public "
            "platform: " + target_arch
        )
    if observation["linkage"] != PUBLIC_NATIVE_LINKAGE:
        fail(
            "Native linkage does not match the selected public platform: "
            + observation["linkage"]
        )
    if observation["libc_family"] != PUBLIC_NATIVE_LIBC_FAMILY:
        fail(
            "Native libc family does not match the selected public platform: "
            + observation["libc_family"]
        )

    root_name = f"protos-{version}-native-{target_os}-{target_arch}"
    output_root = root / "target/distributions"
    bundle = output_root / root_name
    archive_path = output_root / f"{root_name}.zip"

    if bundle.exists():
        shutil.rmtree(bundle)
    bundle.mkdir(parents=True)

    copy_file(root / "bin/protos", bundle / "bin/protos")
    (bundle / "bin/protos").chmod(0o755)
    copy_file(native_binary, bundle / "libexec/protos-native")
    (bundle / "libexec/protos-native").chmod(0o755)

    copy_file(root / "LICENSE.TXT", bundle / "LICENSE.TXT")
    copy_file(root / "README.md", bundle / "README.md")
    copy_tree(root / "protos/lib", bundle / "protos/lib")
    copy_tree(root / "protos/tools", bundle / "protos/tools")
    copy_tree(root / "protos/tests", bundle / "protos/tests")

    if (root / "protos/examples").is_dir():
        copy_tree(root / "protos/examples", bundle / "protos/examples")
    if (root / "protos/tutorials").is_dir():
        copy_tree(root / "protos/tutorials", bundle / "protos/tutorials")

    source_lines = [
        f"artifact_kind={ARTIFACT_KIND}",
        "public_release=false",
        f"implementation_version={version}",
        f"source_revision={source_revision}",
        f"source_dirty={'true' if source_dirty else 'false'}",
        "source_repository=https://github.com/guillermomolina/protos",
        f"source_path=/tree/{source_revision}",
    ]
    write_text(bundle / "SOURCE.txt", "\n".join(source_lines))

    runtime_lines = [
        f"distribution_format={DIST_FORMAT}",
        f"artifact_kind={ARTIFACT_KIND}",
        f"native_runtime_kind={NATIVE_RUNTIME_KIND}",
        "external_java_required=false",
        f"graalvm_release={graal['release']}",
        f"native_image_version={graal['release']}",
        f"jdk_version={graal['jdk_version']}",
        f"native_build_container={build_container}",
        "native_build_authority=build/native/Dockerfile",
        "toolchain_authority=toolchain.json",
        f"target_os={target_os}",
        f"target_arch={target_arch}",
        f"linkage={observation['linkage']}",
        f"libc_family={observation['libc_family']}",
        f"libc_abi_min={PUBLIC_NATIVE_LIBC_ABI_MIN}",
        f"shared_libraries={observation['shared_libraries']}",
        f"binary_file_description={observation['binary_file_description']}",
        f"cpu_isa_assumption={PUBLIC_NATIVE_CPU_ISA_ASSUMPTION}",
    ]
    write_text(bundle / "RUNTIME.txt", "\n".join(runtime_lines))

    dependency_lines = [
        "This Native distribution contains no Protos JVM application JAR.",
        "This Native distribution contains no external Graal/Truffle runtime JAR plane.",
        "Java, GraalVM, and Maven are not runtime dependencies of bin/protos.",
        "The external Protos source/resource tree remains under protos/.",
        (
            "Observed dynamic shared libraries: "
            + observation["shared_libraries"]
        ),
        (
            "Third-party notices embedded into the Native Image remain subject "
            "to final public-release compliance review; this artifact is a "
            "development proof only."
        ),
    ]
    write_text(bundle / "DEPENDENCIES.txt", "\n".join(dependency_lines))

    forbidden = [
        bundle / "lib/protos.jar",
        bundle / "lib/runtime",
    ]
    for path in forbidden:
        if path.exists():
            fail("JVM runtime plane leaked into Native bundle: " + str(path))

    write_checksums(bundle)
    create_archive(root, bundle, archive_path)
    verify_archive(archive_path, root_name)

    outer_digest = sha256(archive_path)
    outer_path = archive_path.with_suffix(archive_path.suffix + ".sha256")
    write_text(outer_path, f"{outer_digest}  {archive_path.name}")

    print("NATIVE_DIST_SOURCE_REVISION: " + source_revision)
    print(
        "NATIVE_DIST_SOURCE_DIRTY: "
        + ("true" if source_dirty else "false")
    )
    print("NATIVE_DIST_TARGET_OS: " + target_os)
    print("NATIVE_DIST_TARGET_ARCH: " + target_arch)
    print("NATIVE_DIST_LINKAGE: " + observation["linkage"])
    print("NATIVE_DIST_LIBC_FAMILY: " + observation["libc_family"])
    print("NATIVE_DIST_LIBC_ABI_MIN: " + PUBLIC_NATIVE_LIBC_ABI_MIN)
    print(
        "NATIVE_DIST_CPU_ISA_ASSUMPTION: "
        + PUBLIC_NATIVE_CPU_ISA_ASSUMPTION
    )
    print("NATIVE_DIST_ARCHIVE: " + str(archive_path))
    print("NATIVE_DIST_OUTER_SHA256: " + outer_digest)
    print("NATIVE_DIST_BUILD: PASS")
    return archive_path


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Build the development-only DIST005 Native Image distribution proof."
        )
    )
    parser.add_argument(
        "--allow-dirty",
        action="store_true",
        help="allow dirty source only for pre-commit proof validation",
    )
    args = parser.parse_args()
    build(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
