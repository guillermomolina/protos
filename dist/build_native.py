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

"""Build DIST005 Native Image development and public-prerelease distributions."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import xml.etree.ElementTree as ET
import zipfile

from native_elf import NativeElfError, inspect_native_elf
from release_identity import build_source_metadata, require_snapshot_version


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
PUBLIC_NATIVE_BUILD_OS_ID = "ol"
PUBLIC_NATIVE_BUILD_OS_MAJOR = "10"


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
    return version


def native_source_metadata(
    root: Path,
    *,
    version: str,
    source_revision: str,
    source_dirty: bool,
    public_prerelease: bool,
    release_baseline: str | None,
) -> dict[str, str]:
    if public_prerelease:
        return build_source_metadata(
            root,
            version=version,
            source_revision=source_revision,
            source_dirty=source_dirty,
            public_prerelease=True,
            release_baseline=release_baseline,
        )

    require_snapshot_version(version)
    if release_baseline is not None:
        raise ValueError(
            "--release-baseline is valid only with --public-prerelease"
        )
    return {
        "artifact_kind": ARTIFACT_KIND,
        "public_release": "false",
        "implementation_version": version,
        "source_revision": source_revision,
        "source_dirty": "true" if source_dirty else "false",
        "source_repository": "https://github.com/guillermomolina/protos",
        "source_path": "/tree/" + source_revision,
    }


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


def parse_os_release(text: str) -> dict[str, str]:
    values: dict[str, str] = {}

    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue

        key, separator, value = line.partition("=")
        if not separator or not key:
            raise ValueError(
                "malformed /etc/os-release line: " + repr(raw)
            )

        value = value.strip()
        if (
            len(value) >= 2
            and value[0] == value[-1]
            and value[0] in ("'", '"')
        ):
            value = value[1:-1]

        values[key] = value

    return values


def require_native_build_os(
    os_id: str,
    version_id: str,
) -> None:
    if os_id != PUBLIC_NATIVE_BUILD_OS_ID:
        raise ValueError(
            "Native build host is not Oracle Linux: "
            + repr(os_id)
        )

    if not (
        version_id == PUBLIC_NATIVE_BUILD_OS_MAJOR
        or version_id.startswith(PUBLIC_NATIVE_BUILD_OS_MAJOR + ".")
    ):
        raise ValueError(
            "Native build host is not Oracle Linux 10: "
            + repr(version_id)
        )


def native_build_environment(root: Path) -> dict[str, str]:
    try:
        values = parse_os_release(
            Path("/etc/os-release").read_text(encoding="utf-8")
        )
    except OSError as exc:
        fail("cannot inspect Native build host OS: " + str(exc))

    os_id = values.get("ID", "")
    version_id = values.get("VERSION_ID", "")

    try:
        require_native_build_os(os_id, version_id)
    except ValueError as exc:
        fail(str(exc))

    glibc_result = run(
        ["getconf", "GNU_LIBC_VERSION"],
        cwd=root,
        check=False,
    )
    if glibc_result.returncode != 0:
        fail(
            "cannot inspect Native build host glibc: "
            + glibc_result.stderr.strip()
        )

    glibc_text = glibc_result.stdout.strip()
    match = re.fullmatch(r"glibc ([0-9]+(?:\.[0-9]+)+)", glibc_text)
    if match is None:
        fail(
            "unexpected Native build host glibc identity: "
            + repr(glibc_text)
        )

    return {
        "os_id": os_id,
        "os_version_id": version_id,
        "glibc_version": match.group(1),
    }


def native_build_container(root: Path) -> str:
    dockerfile = (root / "build/native/Dockerfile").read_text(encoding="utf-8")
    match = re.search(r"^FROM[ \t]+([^ \t\r\n]+)", dockerfile, flags=re.MULTILINE)
    if not match:
        fail("build/native/Dockerfile has no FROM authority")
    return match.group(1)


def native_build_march(root: Path) -> str:
    try:
        tree = ET.parse(root / "pom.xml")
    except ET.ParseError as exc:
        fail("pom.xml is not valid XML: " + str(exc))

    matches: list[str] = []
    for element in tree.iter():
        if element.tag.rsplit("}", 1)[-1] != "buildArg":
            continue
        value = (element.text or "").strip()
        if value.startswith("-march="):
            matches.append(value)

    if len(matches) != 1:
        fail(
            "expected exactly one Native Image -march build argument; found "
            + repr(matches)
        )

    value = matches[0]
    expected = "-march=" + PUBLIC_NATIVE_CPU_ISA_ASSUMPTION
    if value != expected:
        fail(
            "Native Image CPU build policy does not match selected policy: "
            + repr(value)
            + " != "
            + repr(expected)
        )
    return value


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


def verify_archive(
    archive_path: Path,
    root_name: str,
    *,
    source_metadata: dict[str, str],
    observation: dict[str, str],
    build_march: str,
) -> None:
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

        source = archive.read(prefix + "SOURCE.txt").decode("utf-8")
        for key, value in source_metadata.items():
            needle = f"{key}={value}"
            if needle not in source:
                fail("SOURCE.txt is missing " + repr(needle))

        runtime = archive.read(prefix + "RUNTIME.txt").decode("utf-8")
        required_runtime = (
            f"distribution_format={DIST_FORMAT}",
            f"artifact_kind={source_metadata['artifact_kind']}",
            f"native_runtime_kind={NATIVE_RUNTIME_KIND}",
            "external_java_required=false",
            f"target_os={PUBLIC_NATIVE_TARGET_OS}",
            f"target_arch={PUBLIC_NATIVE_TARGET_ARCH}",
            f"linkage={PUBLIC_NATIVE_LINKAGE}",
            f"libc_family={PUBLIC_NATIVE_LIBC_FAMILY}",
            f"libc_abi_min={PUBLIC_NATIVE_LIBC_ABI_MIN}",
            (
                "libc_abi_observed_max="
                + observation["libc_abi_observed_max"]
            ),
            (
                "glibc_symbol_versions="
                + observation["glibc_symbol_versions"]
            ),
            f"elf_interpreter={observation['elf_interpreter']}",
            f"dt_needed={observation['dt_needed']}",
            (
                "shared_library_closure="
                + observation["shared_library_closure"]
            ),
            f"cpu_isa_assumption={PUBLIC_NATIVE_CPU_ISA_ASSUMPTION}",
            f"native_build_march={build_march}",
            (
                "post_link_cpu_isa_evidence="
                + observation["post_link_cpu_isa_evidence"]
            ),
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
    if not (root / ".git").exists():
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

    try:
        source_metadata = native_source_metadata(
            root,
            version=version,
            source_revision=source_revision,
            source_dirty=source_dirty,
            public_prerelease=args.public_prerelease,
            release_baseline=args.release_baseline,
        )
    except ValueError as exc:
        fail(str(exc))

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

    try:
        observation = inspect_native_elf(
            native_binary,
            policy_glibc_max=PUBLIC_NATIVE_LIBC_ABI_MIN,
        )
    except NativeElfError as exc:
        fail(str(exc))

    build_march = native_build_march(root)
    build_environment = native_build_environment(root)
    build_container = native_build_container(root)
    if build_container != PUBLIC_NATIVE_BUILD_CONTAINER:
        fail(
            "Native builder does not match the selected public build base: "
            + build_container
        )

    target_os = observation["target_os"]
    target_arch = observation["target_arch"]

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
        f"{key}={value}" for key, value in source_metadata.items()
    ]
    write_text(bundle / "SOURCE.txt", "\n".join(source_lines))

    runtime_lines = [
        f"distribution_format={DIST_FORMAT}",
        f"artifact_kind={source_metadata['artifact_kind']}",
        f"native_runtime_kind={NATIVE_RUNTIME_KIND}",
        "external_java_required=false",
        f"graalvm_release={graal['release']}",
        f"native_image_version={graal['release']}",
        f"jdk_version={graal['jdk_version']}",
        f"native_build_container={build_container}",
        "native_build_container_role=canonical-authority",
        "native_build_authority=build/native/Dockerfile",
        (
            "native_build_host_os_id="
            + build_environment["os_id"]
        ),
        (
            "native_build_host_os_version="
            + build_environment["os_version_id"]
        ),
        (
            "native_build_host_glibc="
            + build_environment["glibc_version"]
        ),
        "toolchain_authority=toolchain.json",
        f"target_os={target_os}",
        f"target_arch={target_arch}",
        f"linkage={observation['linkage']}",
        f"libc_family={observation['libc_family']}",
        f"libc_abi_min={PUBLIC_NATIVE_LIBC_ABI_MIN}",
        f"libc_abi_observed_max={observation['libc_abi_observed_max']}",
        f"glibc_symbol_versions={observation['glibc_symbol_versions']}",
        f"elf_interpreter={observation['elf_interpreter']}",
        f"dt_needed={observation['dt_needed']}",
        f"shared_library_closure={observation['shared_library_closure']}",
        f"binary_file_description={observation['binary_file_description']}",
        f"cpu_isa_assumption={PUBLIC_NATIVE_CPU_ISA_ASSUMPTION}",
        f"native_build_march={build_march}",
        (
            "post_link_cpu_isa_evidence="
            + observation["post_link_cpu_isa_evidence"]
        ),
    ]
    write_text(bundle / "RUNTIME.txt", "\n".join(runtime_lines))

    dependency_lines = [
        "This Native distribution contains no Protos JVM application JAR.",
        "This Native distribution contains no external Graal/Truffle runtime JAR plane.",
        "Java, GraalVM, and Maven are not runtime dependencies of bin/protos.",
        "The external Protos source/resource tree remains under protos/.",
        (
            "Observed ELF DT_NEEDED libraries: "
            + observation["dt_needed"]
        ),
        (
            "Observed resolved dynamic-library closure: "
            + observation["shared_library_closure"]
        ),
        (
            "Observed GLIBC symbol versions: "
            + observation["glibc_symbol_versions"]
        ),
        (
            "Observed maximum GLIBC symbol requirement: "
            + observation["libc_abi_observed_max"]
        ),
        (
            "Third-party notices embedded into the Native Image remain subject "
            "to final public-release compliance review; this artifact does not "
            "itself authorize public release."
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
    verify_archive(
        archive_path,
        root_name,
        source_metadata=source_metadata,
        observation=observation,
        build_march=build_march,
    )

    outer_digest = sha256(archive_path)
    outer_path = archive_path.with_suffix(archive_path.suffix + ".sha256")
    write_text(outer_path, f"{outer_digest}  {archive_path.name}")

    print("NATIVE_DIST_SOURCE_REVISION: " + source_revision)
    print("NATIVE_DIST_ARTIFACT_KIND: " + source_metadata["artifact_kind"])
    print("NATIVE_DIST_PUBLIC_RELEASE: " + source_metadata["public_release"])
    if "release_tag" in source_metadata:
        print("NATIVE_DIST_RELEASE_TAG: " + source_metadata["release_tag"])
    print(
        "NATIVE_DIST_SOURCE_DIRTY: "
        + ("true" if source_dirty else "false")
    )
    print(
        "NATIVE_DIST_BUILD_HOST_OS: "
        + build_environment["os_id"]
        + "-"
        + build_environment["os_version_id"]
    )
    print(
        "NATIVE_DIST_BUILD_HOST_GLIBC: "
        + build_environment["glibc_version"]
    )
    print(
        "NATIVE_DIST_CANONICAL_BUILD_CONTAINER: "
        + build_container
    )
    print("NATIVE_DIST_TARGET_OS: " + target_os)
    print("NATIVE_DIST_TARGET_ARCH: " + target_arch)
    print("NATIVE_DIST_LINKAGE: " + observation["linkage"])
    print("NATIVE_DIST_LIBC_FAMILY: " + observation["libc_family"])
    print("NATIVE_DIST_LIBC_ABI_MIN: " + PUBLIC_NATIVE_LIBC_ABI_MIN)
    print(
        "NATIVE_DIST_GLIBC_SYMBOL_VERSIONS: "
        + observation["glibc_symbol_versions"]
    )
    print(
        "NATIVE_DIST_GLIBC_OBSERVED_MAX: "
        + observation["libc_abi_observed_max"]
    )
    print(
        "NATIVE_DIST_ELF_INTERPRETER: "
        + observation["elf_interpreter"]
    )
    print("NATIVE_DIST_DT_NEEDED: " + observation["dt_needed"])
    print(
        "NATIVE_DIST_DYNAMIC_LIBRARY_CLOSURE: "
        + observation["shared_library_closure"]
    )
    print(
        "NATIVE_DIST_CPU_ISA_ASSUMPTION: "
        + PUBLIC_NATIVE_CPU_ISA_ASSUMPTION
    )
    print("NATIVE_DIST_BUILD_MARCH: " + build_march)
    print(
        "NATIVE_DIST_POST_LINK_CPU_ISA_EVIDENCE: "
        + observation["post_link_cpu_isa_evidence"]
    )
    print("NATIVE_DIST_ARCHIVE: " + str(archive_path))
    print("NATIVE_DIST_OUTER_SHA256: " + outer_digest)
    print("NATIVE_DIST_BUILD: PASS")
    return archive_path


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Build the DIST005 Native Image distribution."
    )
    parser.add_argument(
        "--allow-dirty",
        action="store_true",
        help="allow dirty source only for pre-commit proof validation",
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
