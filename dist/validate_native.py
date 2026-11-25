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

"""Validate the extracted DIST005 Native Image distribution proof."""

from __future__ import annotations

import argparse
import fcntl
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import pty
import re
import select
import selectors
import shutil
import signal
import socket
import struct
import subprocess
import sys
import tempfile
import termios
import time
import zipfile

from native_elf import NativeElfError, inspect_native_elf


PUBLIC_NATIVE_TARGET_OS = "linux"
PUBLIC_NATIVE_TARGET_ARCH = "x86_64"
PUBLIC_NATIVE_LINKAGE = "dynamic"
PUBLIC_NATIVE_LIBC_FAMILY = "glibc"
PUBLIC_NATIVE_LIBC_ABI_MIN = "2.39"
PUBLIC_NATIVE_CPU_ISA_ASSUMPTION = "compatibility"
PUBLIC_NATIVE_BUILD_MARCH = "-march=compatibility"

OUTER_SUM_RE = re.compile(r"^([0-9a-f]{64})  ([^\r\n]+)\n?$")

ANSI_TERMINAL_RE = re.compile(
    rb"\x1b(?:"
    rb"\[[0-?]*[ -/]*[@-~]"
    rb"|\][^\x07]*(?:\x07|\x1b\\)"
    rb"|[=>]"
    rb")"
)


def fail(message: str) -> "NoReturn":
    raise SystemExit("native distribution validation failed: " + message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def read_outer_checksum(archive: Path) -> str:
    checksum_path = archive.with_suffix(archive.suffix + ".sha256")
    if not checksum_path.is_file():
        fail("outer checksum file is missing: " + str(checksum_path))

    text = checksum_path.read_text(encoding="utf-8")
    match = OUTER_SUM_RE.fullmatch(text)
    if not match:
        fail("outer checksum file is malformed: " + str(checksum_path))

    digest, filename = match.groups()
    if filename != archive.name:
        fail(
            "outer checksum filename mismatch: "
            + repr(filename)
            + " != "
            + repr(archive.name)
        )
    return digest


def extract_preserving_modes(archive: Path, target: Path) -> Path:
    with zipfile.ZipFile(archive) as z:
        bad = z.testzip()
        if bad is not None:
            fail("ZIP CRC failure: " + bad)

        for info in z.infolist():
            z.extract(info, target)
            extracted = target / info.filename
            mode = (info.external_attr >> 16) & 0o777
            if extracted.is_file() and mode:
                extracted.chmod(mode)

    roots = [path for path in target.iterdir() if path.is_dir()]
    if len(roots) != 1:
        fail(
            "archive must extract to exactly one distribution root; found "
            + str(len(roots))
        )
    return roots[0]


def require_executable(path: Path, label: str) -> None:
    if not path.is_file():
        fail(label + " is missing: " + str(path))
    if not os.access(path, os.X_OK):
        fail(label + " is not executable after extraction: " + str(path))


def read_key_values(path: Path, label: str) -> dict[str, str]:
    if not path.is_file():
        fail(label + " is missing: " + str(path))

    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw:
            continue
        key, separator, value = raw.partition("=")
        if not separator or not key or not value:
            fail(label + " contains malformed key/value line: " + repr(raw))
        if key in values:
            fail(label + " contains duplicate key: " + key)
        values[key] = value

    return values


def validate_native_elf_metadata(
    native: Path,
    runtime: dict[str, str],
) -> dict[str, str]:
    declared = {
        "target_os": PUBLIC_NATIVE_TARGET_OS,
        "target_arch": PUBLIC_NATIVE_TARGET_ARCH,
        "linkage": PUBLIC_NATIVE_LINKAGE,
        "libc_family": PUBLIC_NATIVE_LIBC_FAMILY,
        "libc_abi_min": PUBLIC_NATIVE_LIBC_ABI_MIN,
        "cpu_isa_assumption": PUBLIC_NATIVE_CPU_ISA_ASSUMPTION,
        "native_build_march": PUBLIC_NATIVE_BUILD_MARCH,
    }

    for key, expected in declared.items():
        actual = runtime.get(key)
        if actual != expected:
            fail(
                "Native RUNTIME.txt declared policy mismatch for "
                + key
                + ": "
                + repr(actual)
                + " != "
                + repr(expected)
            )

    build_os_id = runtime.get("native_build_host_os_id")
    build_os_version = runtime.get("native_build_host_os_version")
    build_host_glibc = runtime.get("native_build_host_glibc")
    build_container_role = runtime.get("native_build_container_role")

    if build_os_id != "ol":
        fail(
            "Native build provenance does not identify Oracle Linux: "
            + repr(build_os_id)
        )
    if not (
        build_os_version == "10"
        or (
            build_os_version is not None
            and build_os_version.startswith("10.")
        )
    ):
        fail(
            "Native build provenance does not identify Oracle Linux 10: "
            + repr(build_os_version)
        )
    if (
        build_host_glibc is None
        or re.fullmatch(
            r"[0-9]+(?:\.[0-9]+)+",
            build_host_glibc,
        )
        is None
    ):
        fail(
            "Native build provenance has malformed glibc identity: "
            + repr(build_host_glibc)
        )
    if build_container_role != "canonical-authority":
        fail(
            "Native build container provenance role changed: "
            + repr(build_container_role)
        )

    observed_fields = (
        "libc_abi_observed_max",
        "glibc_symbol_versions",
        "elf_interpreter",
        "dt_needed",
        "shared_library_closure",
        "post_link_cpu_isa_evidence",
        "binary_file_description",
    )

    missing = [
        key
        for key in observed_fields
        if not runtime.get(key)
    ]
    if missing:
        fail(
            "Native RUNTIME.txt is missing empirical ELF evidence: "
            + ",".join(missing)
        )

    try:
        observation = inspect_native_elf(
            native,
            policy_glibc_max=PUBLIC_NATIVE_LIBC_ABI_MIN,
        )
    except NativeElfError as exc:
        fail(str(exc))

    for key in observed_fields:
        actual = observation[key]
        recorded = runtime[key]
        if recorded != actual:
            fail(
                "Native empirical ELF evidence mismatch for "
                + key
                + ": "
                + repr(recorded)
                + " != "
                + repr(actual)
            )

    print("NATIVE_DIST_ELF_OS=" + observation["target_os"])
    print("NATIVE_DIST_ELF_ARCH=" + observation["target_arch"])
    print("NATIVE_DIST_ELF_INTERPRETER=" + observation["elf_interpreter"])
    print("NATIVE_DIST_DT_NEEDED=" + observation["dt_needed"])
    print(
        "NATIVE_DIST_GLIBC_SYMBOL_VERSIONS="
        + observation["glibc_symbol_versions"]
    )
    print(
        "NATIVE_DIST_GLIBC_OBSERVED_MAX="
        + observation["libc_abi_observed_max"]
    )
    print(
        "NATIVE_DIST_POST_LINK_CPU_ISA_EVIDENCE="
        + observation["post_link_cpu_isa_evidence"]
    )
    print("NATIVE_DIST_BUILD_CPU_POLICY_PROVEN=YES")
    print("NATIVE_DIST_GLIBC_ABI_FLOOR_PROOF: PASS")
    print("NATIVE_DIST_DYNAMIC_LIBRARY_CLOSURE_CHECK: PASS")
    print("NATIVE_DIST_EMPIRICAL_ELF_ADMISSION: PASS")

    return observation


def isolated_path(root: Path) -> str:
    path = root / "no-java-path"
    path.mkdir()

    for name in ("sh", "dirname"):
        source = shutil.which(name)
        if source is None:
            fail("required host utility is unavailable: " + name)
        os.symlink(source, path / name)

    # JLine may consult these ordinary terminal helpers depending on the host
    # terminal provider. They are allowed in the runtime proof; Java and Maven
    # remain deliberately unavailable.
    for name in ("stty", "tty", "infocmp", "tput"):
        source = shutil.which(name)
        if source is not None:
            os.symlink(source, path / name)

    value = str(path)

    for forbidden in ("java", "mvn"):
        if shutil.which(forbidden, path=value) is not None:
            fail(forbidden + " unexpectedly remains available on proof PATH")

    return value


def run_case(
    label: str,
    command: list[str],
    *,
    cwd: Path,
    env: dict[str, str],
    expected_stdout: str | None = None,
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        command,
        cwd=cwd,
        env=env,
        text=True,
        capture_output=True,
        check=False,
    )

    if result.returncode != 0:
        fail(
            label
            + " failed with status "
            + str(result.returncode)
            + "\nstdout:\n"
            + result.stdout
            + "\nstderr:\n"
            + result.stderr
        )

    if expected_stdout is not None and expected_stdout not in result.stdout:
        fail(
            label
            + " output is missing "
            + repr(expected_stdout)
            + "\nstdout:\n"
            + result.stdout
        )

    print("NATIVE_DIST_" + label + "_CHECK: PASS")
    return result


FILESYSTEM_WRITE = (
    'Files: import("std:io/files")\n'
    'Files.writeAllText(filesystem, Path.relative().child("note.txt"),'
    ' "dist-granted", Encoding.UTF8).value()\n'
    'print("written")\n'
)
FILESYSTEM_READ = (
    'Files: import("std:io/files")\n'
    'print(Files.readAllText(filesystem, Path.relative().child("note.txt"),'
    " Encoding.UTF8).value())\n"
)
FILESYSTEM_REFUSAL = "cannot authorize Filesystem root"


def run_refused(
    label: str,
    command: list[str],
    *,
    cwd: Path,
    env: dict[str, str],
    expected_status: int,
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        command, cwd=cwd, env=env, text=True, capture_output=True, check=False
    )
    if result.returncode != expected_status:
        fail(
            label
            + " exited with "
            + str(result.returncode)
            + " instead of "
            + str(expected_status)
            + "\nstdout:\n"
            + result.stdout
            + "\nstderr:\n"
            + result.stderr
        )
    if "Internal error" in result.stderr or "ran" in result.stdout:
        fail(label + " ran guest code or leaked an internal error:\n" + result.stderr)
    print("NATIVE_DIST_" + label + "_CHECK: PASS")
    return result


def validate_filesystem_grant(
    launcher: Path, temp: Path, cwd: Path, env: dict[str, str]
) -> None:
    """I156: the installed executable serves --allow-filesystem=<directory>.

    Writes in one Process, reads in a second one (persistence), and checks the
    denials. A host kernel without the required Landlock ABI must refuse the
    grant before guest code; that is reported as a distinct verdict, never as
    a pass of the grant itself.
    """
    root = temp / "filesystem-grant"
    root.mkdir()
    grant = "--allow-filesystem=" + str(root)

    run_refused(
        "FILESYSTEM_NO_GRANT",
        [str(launcher), "-e", "filesystem"],
        cwd=cwd,
        env=env,
        expected_status=1,
    )
    run_refused(
        "FILESYSTEM_MISSING_ROOT",
        [str(launcher), "--allow-filesystem=" + str(temp / "absent"), "-e", 'print("ran")'],
        cwd=cwd,
        env=env,
        expected_status=1,
    )
    if (temp / "absent").exists():
        fail("a refused Filesystem grant created its root")

    probe = subprocess.run(
        [str(launcher), grant, "-e", "null"],
        cwd=cwd,
        env=env,
        text=True,
        capture_output=True,
        check=False,
    )
    if probe.returncode != 0:
        if probe.returncode == 1 and FILESYSTEM_REFUSAL in probe.stderr:
            print("NATIVE_DIST_FILESYSTEM_GRANT=REJECTED_UNSUPPORTED_HOST")
            # Only the controlled refusal ran; no confinement case was executed.
            print("NATIVE_DIST_FILESYSTEM_CONFINEMENT=NOT_EXECUTED_UNSUPPORTED_HOST")
            print("NATIVE_DIST_FILESYSTEM_BIND_MOUNT=NOT_EXECUTED_UNSUPPORTED_HOST")
            return
        fail("Filesystem grant probe failed:\n" + probe.stderr)

    run_case(
        "FILESYSTEM_GRANT_WRITE",
        [str(launcher), grant, "-e", FILESYSTEM_WRITE],
        cwd=cwd,
        env=env,
        expected_stdout="written",
    )
    run_case(
        "FILESYSTEM_GRANT_PERSISTENCE",
        [str(launcher), grant, "-e", FILESYSTEM_READ],
        cwd=cwd,
        env=env,
        expected_stdout="dist-granted",
    )
    outside = temp / "filesystem-outside"
    outside.mkdir()
    (root / "escape").symlink_to(outside)
    run_refused(
        "FILESYSTEM_OUTSIDE_ROOT",
        [
            str(launcher),
            grant,
            "-e",
            'Files: import("std:io/files")\n'
            'Files.writeAllText(filesystem, Path.relative().child("escape")'
            '.child("x"), "x", Encoding.UTF8).value()\n'
            'print("ran")\n',
        ],
        cwd=cwd,
        env=env,
        expected_status=1,
    )
    if any(outside.iterdir()):
        fail("a confined Filesystem grant wrote outside its root")
    print("NATIVE_DIST_FILESYSTEM_GRANT=PASS")
    print("NATIVE_DIST_FILESYSTEM_CONFINEMENT=EXECUTED_SYMLINK_REFUSED")
    # Bind mounts and T04/T05 are not exercised by the distribution check; the
    # JVM security test and scripts/native-test.sh report them separately.
    print("NATIVE_DIST_FILESYSTEM_BIND_MOUNT=NOT_EXECUTED_IN_DIST_CHECK")


# I170 / PLAT077-A: the generic FFM provider integrated in the installed
# Native executable. Every case below runs the extracted launcher without a
# JDK, Maven, GraalVM or an external provider JAR on the consumer path.
FFM_SQLITE_TARGET = "libsqlite3.so.0"
FFM_LIBC_TARGET = "libc.so.6"
# Printed by a guest program when a call that must be refused before native
# entry failed after entry instead; never part of a passing case.
FFM_FOREIGN_ENTRY_MARKER = "ffm-foreign-entry-failure"
FFM_UNEXPECTED_MARKERS = (
    FFM_FOREIGN_ENTRY_MARKER,
    "Internal error",
    "MissingForeignRegistrationError",
    "UnsatisfiedLinkError",
    "NoClassDefFoundError",
)
FFM_DEFAULT_OFF = (
    'Error.handle(() => import("ffi:sqlite3"), (error) => { print("ffm-default-off") })\n'
)
FFM_AUTHORIZED_NO_IMPORT = 'print("ffm-authorized-no-import")\n'
FFM_SQLITE_MEMORY = (
    'SQLite: import("std:sqlite")\n'
    "db: SQLite.openMemory()\n"
    'query: db.prepare("SELECT 40 + 2")\n'
    "query.step()\n"
    '(query.column(0) == 42).ifTrue(() => { print("ffm-sqlite=true") })\n'
    "query.close().value()\n"
    "db.close().value()\n"
)
FFM_LIBC = (
    'FFI: import("std:ffi")\n'
    'libc: import("ffi:libc")\n'
    'getpid: FFI.bind(libc, "getpid", FFI.i32, Array())\n'
    '(getpid() > 0).ifTrue(() => { print("ffm-libc") })\n'
)
# An exact but unregistered physical shape, D(D,D,D): it must be refused
# before linking, so the C function is never entered.
FFM_UNSUPPORTED_SIGNATURE = (
    'FFI: import("std:ffi")\n'
    'sqlite: import("ffi:sqlite3")\n'
    "Error.handle(\n"
    '    () => FFI.bind(sqlite, "sqlite3_libversion_number", FFI.f64,'
    " Array(FFI.f64, FFI.f64, FFI.f64)),\n"
    '    (error) => { print("ffm-unsupported-refused") })\n'
)
# I174: the fixed-width scalar fixtures and C-measured layouts run against
# compiled test-only C libraries that are never bundled into the product.
FFM_CASES = Path(__file__).resolve().parents[1] / "protos" / "tests" / "foreign-provider"
FFM_FIXTURES = (("first", "ffi_test"), ("second", "ffi_second"))
FFM_LAYOUTS_MARKER = "FFM-LAYOUTS-PASS"
FFM_SCALAR_LAYOUT_ABI_MARKER = "FFM-SCALAR-LAYOUT-ABI-PASS"
FFM_ROOT_ACTOR_MARKER = "FFM-ROOT-ACTOR-PASS"
FFM_POINTERS_MARKER = "FFM-POINTERS-PASS"
FFM_NATIVE_MEMORY_MARKER = "FFM-NATIVE-MEMORY-PASS"
# By-value struct and union calls whose physical shapes are all registered.
FFM_GROUPS_MARKER = "FFM-GROUPS-PASS"
# A valid by-value group shape that is not registered: refused with the
# ordinary Error before symbol lookup, linking or C entry.
FFM_UNREGISTERED_GROUP_MARKER = "FFM-UNREGISTERED-GROUP-NATIVE-PASS"
# f32(f32, f32), physical shape F(F,F): a valid C signature that is not
# registered for the image. It must be refused before linking with exactly an
# ordinary Error (parent Error, the convention of ffm-public-api.protos), never
# a ForeignError, so the fixture's native entry counter is unchanged.
FFM_UNREGISTERED_SCALAR = (
    'FFI: import("std:ffi")\n'
    'first: import("ffi:first")\n'
    'calls: FFI.bind(first, "ffi_test_scalar_calls_count", FFI.i32, Array())\n'
    "before: calls()\n"
    "refused: null\n"
    "Error.handle(\n"
    '    () => FFI.bind(first, "ffi_test_f32_add", FFI.f32, Array(FFI.f32, FFI.f32)),\n'
    "    (error) => { refused = error })\n"
    "((refused === null) == false).ifTrue(() => {\n"
    "    (refused.parent() === ForeignError).ifTrue(() => {\n"
    '        print("' + FFM_FOREIGN_ENTRY_MARKER + '")\n'
    "    })\n"
    "    ((refused.parent() === Error) && (calls() == before)).ifTrue(() => {\n"
    '        print("ffm-unregistered-scalar-refused")\n'
    "    })\n"
    "})\n"
)
FFM_UNAUTHORIZED = (
    "error: null\n"
    'ForeignError.handle(() => import("ffi:sqlite3"), (e) => { error = e })\n'
    "print(error.foreignCategory)\n"
)
FFM_MISSING_LIBRARY = (
    "error: null\n"
    'ForeignError.handle(() => import("ffi:missing"), (e) => { error = e })\n'
    "print(error.foreignCategory)\n"
)


def classify_ffm_case(
    returncode: int,
    stdout: str,
    stderr: str,
    *,
    expected_status: int,
    expected_stdout: str | None,
    exact_stdout: bool = False,
) -> tuple[str, str]:
    """Classify one Native-FFM case as ("PASS", "") or ("FAIL", reason).

    A pass needs the exact status, the expected marker and no uncontrolled
    host failure; anything else is a failure, never a pass. With
    exact_stdout, stdout must be exactly the marker line: a duplicated marker
    or any additional output is a failure.
    """
    for marker in FFM_UNEXPECTED_MARKERS:
        if marker in stdout or marker in stderr:
            return "FAIL", "uncontrolled host failure: " + marker
    if returncode != expected_status:
        return "FAIL", (
            "exited with " + str(returncode) + " instead of " + str(expected_status)
        )
    if expected_stdout is not None and expected_stdout not in stdout:
        return "FAIL", "output is missing " + repr(expected_stdout)
    if exact_stdout and stdout != (expected_stdout or "") + "\n":
        return "FAIL", "output is not exactly " + repr(expected_stdout)
    if expected_status != 0 and "ran" in stdout:
        return "FAIL", "refused case ran guest code"
    return "PASS", ""


def run_ffm_case(
    label: str,
    command: list[str],
    *,
    cwd: Path,
    env: dict[str, str],
    expected_status: int = 0,
    expected_stdout: str | None = None,
    exact_stdout: bool = False,
) -> None:
    result = subprocess.run(
        command, cwd=cwd, env=env, text=True, capture_output=True, check=False
    )
    verdict, reason = classify_ffm_case(
        result.returncode,
        result.stdout,
        result.stderr,
        expected_status=expected_status,
        expected_stdout=expected_stdout,
        exact_stdout=exact_stdout,
    )
    if verdict != "PASS":
        fail(
            label
            + " "
            + reason
            + "\nstdout:\n"
            + result.stdout
            + "\nstderr:\n"
            + result.stderr
        )
    print("NATIVE_DIST_" + label + "_CHECK: PASS")


def ffm_fixture_compiler() -> str | None:
    """The host C compiler used only to build the test-only FFM fixtures."""
    return next(
        (found for found in (shutil.which(name) for name in ("cc", "gcc", "clang"))
         if found is not None),
        None,
    )


def compile_ffm_fixtures(compiler: str | None, target: Path) -> dict[str, Path]:
    """Compile the FFM C fixtures into ``target``; any absence or error fails."""
    if compiler is None:
        fail("the validation host provides no C compiler for the FFM fixtures")
    libraries: dict[str, Path] = {}
    for alias, stem in FFM_FIXTURES:
        source = FFM_CASES / "native" / (stem + ".c")
        if not source.is_file():
            fail("the FFM fixture source is missing: " + str(source))
        library = target / ("lib" + stem + ".so")
        result = subprocess.run(
            [compiler, "-shared", "-fPIC", "-O2", "-std=c11", "-Wall", "-Werror",
             "-o", str(library), str(source)],
            text=True, capture_output=True, check=False,
        )
        if result.returncode != 0 or not library.is_file():
            fail("the FFM fixture " + source.name + " did not compile:\n" + result.stderr)
        libraries[alias] = library
    return libraries


def bundled_shared_objects(dist: Path, stem: str) -> list[Path]:
    """Every file of the distribution whose name starts with ``stem``."""
    return sorted(path for path in dist.rglob(stem + "*") if path.is_file())


def os_library_available(target: str, ldconfig: str | None) -> bool:
    """Whether the host dynamic loader cache lists ``target``."""
    if ldconfig is None:
        return False
    result = subprocess.run(
        [ldconfig, "-p"], text=True, capture_output=True, check=False
    )
    return result.returncode == 0 and target in result.stdout


def validate_native_ffm(
    launcher: Path,
    dist: Path,
    dt_needed: str,
    temp: Path,
    cwd: Path,
    env: dict[str, str],
    ldconfig: str | None,
) -> None:
    """I170: the installed executable integrates the generic FFM provider.

    No provider JAR is passed or bundled, no third-party C library is bundled
    or linked, authorization is default-off, loading is lazy and uses the
    OS-provided shared object, and an unregistered physical signature fails
    cleanly before native entry.
    """
    bundled = bundled_shared_objects(dist, "libsqlite3")
    if bundled:
        fail("the Native distribution bundles SQLite: " + ", ".join(map(str, bundled)))
    print("NATIVE_DIST_FFM_NO_BUNDLED_SQLITE_CHECK: PASS")
    if "sqlite" in dt_needed.lower():
        fail("the Native executable has a DT_NEEDED SQLite dependency: " + dt_needed)
    print("NATIVE_DIST_FFM_NO_SQLITE_DT_NEEDED_CHECK: PASS")

    run_ffm_case(
        "FFM_DEFAULT_OFF",
        [str(launcher), "-e", FFM_DEFAULT_OFF],
        cwd=cwd,
        env=env,
        expected_stdout="ffm-default-off",
    )
    run_ffm_case(
        "FFM_AUTHORIZED_NO_IMPORT",
        [
            str(launcher),
            "--allow-library",
            "sqlite3=" + str(temp / "absent" / FFM_SQLITE_TARGET),
            "-e",
            FFM_AUTHORIZED_NO_IMPORT,
        ],
        cwd=cwd,
        env=env,
        expected_stdout="ffm-authorized-no-import",
    )
    run_ffm_case(
        "FFM_ALLOW_LIBRARY_REQUIRES_TARGET",
        [str(launcher), "--allow-library", "sqlite3", "-e", 'print("ran")'],
        cwd=cwd,
        env=env,
        expected_status=2,
    )
    run_ffm_case(
        "FFM_MISSING_LIBRARY",
        [
            str(launcher),
            "--allow-library",
            "missing=" + str(temp / "absent" / "libnone.so"),
            "-e",
            FFM_MISSING_LIBRARY,
        ],
        cwd=cwd,
        env=env,
        expected_stdout="library-unavailable",
    )
    run_ffm_case(
        "FFM_LIBC",
        [str(launcher), "--allow-library", "libc=" + FFM_LIBC_TARGET, "-e", FFM_LIBC],
        cwd=cwd,
        env=env,
        expected_stdout="ffm-libc",
    )

    # I174: declarative layouts need no library grant.
    run_ffm_case(
        "FFM_LAYOUTS",
        [str(launcher), str(FFM_CASES / "ffm-layouts.protos")],
        cwd=cwd,
        env=env,
        expected_stdout=FFM_LAYOUTS_MARKER,
    )
    fixtures = temp / "ffm-fixtures"
    fixtures.mkdir()
    libraries = compile_ffm_fixtures(ffm_fixture_compiler(), fixtures)
    fixture_grants: list[str] = []
    for alias, library in libraries.items():
        fixture_grants += ["--allow-library", alias + "=" + str(library)]
    # Real fixed-width scalars, outputs and C-measured layouts; the same u8(u8)
    # shape is also called through the second, independently authorized library.
    run_ffm_case(
        "FFM_SCALAR_LAYOUT_ABI",
        [str(launcher), *fixture_grants, str(FFM_CASES / "ffm-scalar-layout-abi.protos")],
        cwd=cwd,
        env=env,
        expected_stdout=FFM_SCALAR_LAYOUT_ABI_MARKER,
    )
    # Native FFM authority is RootActor-only; the guest program itself verifies
    # the secondary Actor's exact ordinary Error before printing its marker.
    run_ffm_case(
        "FFM_ROOT_ACTOR",
        [str(launcher), *fixture_grants, str(FFM_CASES / "ffm-root-actor.protos")],
        cwd=cwd,
        env=env,
        expected_stdout=FFM_ROOT_ACTOR_MARKER,
    )
    # Typed native pointer capabilities through both compiled fixtures; every
    # physical shape they use is already registered for the image.
    run_ffm_case(
        "FFM_POINTERS",
        [str(launcher), *fixture_grants, str(FFM_CASES / "ffm-pointers.protos")],
        cwd=cwd,
        env=env,
        expected_stdout=FFM_POINTERS_MARKER,
    )
    # Bounded native memory with the installed executable: the program
    # allocates, slices, views and accesses native memory before it imports a
    # library; the fixture grants only make the provider available. Every C
    # shape it calls is already registered for the image.
    run_ffm_case(
        "FFM_NATIVE_MEMORY",
        [str(launcher), *fixture_grants, str(FFM_CASES / "ffm-native-memory.protos")],
        cwd=cwd,
        env=env,
        expected_stdout=FFM_NATIVE_MEMORY_MARKER,
    )
    # By-value struct and union arguments, returns and group outs; every
    # group signature it binds is in the exact image registration.
    run_ffm_case(
        "FFM_GROUPS",
        [str(launcher), *fixture_grants, str(FFM_CASES / "ffm-groups.protos")],
        cwd=cwd,
        env=env,
        expected_stdout=FFM_GROUPS_MARKER,
        exact_stdout=True,
    )
    # The unregistered reverse-layout group signature: an ordinary refusal,
    # never a MissingForeignRegistrationError, a ForeignError or a C entry.
    run_ffm_case(
        "FFM_UNREGISTERED_GROUP_SIGNATURE",
        [
            str(launcher),
            *fixture_grants,
            str(FFM_CASES / "ffm-groups-unregistered-native.protos"),
        ],
        cwd=cwd,
        env=env,
        expected_stdout=FFM_UNREGISTERED_GROUP_MARKER,
        exact_stdout=True,
    )
    run_ffm_case(
        "FFM_UNREGISTERED_SCALAR_SIGNATURE",
        [str(launcher), *fixture_grants, "-e", FFM_UNREGISTERED_SCALAR],
        cwd=cwd,
        env=env,
        expected_stdout="ffm-unregistered-scalar-refused",
    )

    if not os_library_available(FFM_SQLITE_TARGET, ldconfig):
        fail("the validation host provides no OS " + FFM_SQLITE_TARGET)
    sqlite_grant = ["--allow-library", "sqlite3=" + FFM_SQLITE_TARGET]
    run_ffm_case(
        "FFM_SQLITE_MEMORY",
        [str(launcher), *sqlite_grant, "-e", FFM_SQLITE_MEMORY],
        cwd=cwd,
        env=env,
        expected_stdout="ffm-sqlite=true",
    )
    run_ffm_case(
        "FFM_SQLITE_LIFECYCLE",
        [
            str(launcher),
            *sqlite_grant,
            str(FFM_CASES / "sqlite-lifecycle.protos"),
        ],
        cwd=cwd,
        env=env,
        expected_stdout="LIB029-LIFECYCLE-PASS",
    )
    run_ffm_case(
        "FFM_UNAUTHORIZED",
        [str(launcher), "--allow-library", "other=" + FFM_SQLITE_TARGET, "-e", FFM_UNAUTHORIZED],
        cwd=cwd,
        env=env,
        expected_stdout="unauthorized-library",
    )
    run_ffm_case(
        "FFM_UNSUPPORTED_SIGNATURE",
        [str(launcher), *sqlite_grant, "-e", FFM_UNSUPPORTED_SIGNATURE],
        cwd=cwd,
        env=env,
        expected_stdout="ffm-unsupported-refused",
    )
    print("NATIVE_DIST_FFM=PASS")


def normalized_terminal_text(data: bytes) -> str:
    data = ANSI_TERMINAL_RE.sub(b"", data)

    output = bytearray()
    for byte in data:
        if byte == 8:
            if output:
                output.pop()
        elif byte == 13:
            continue
        elif byte in (9, 10) or byte >= 32:
            output.append(byte)

    return output.decode("utf-8", errors="replace")


def wait_for_terminal(
    master: int,
    transcript: bytearray,
    predicate,
    timeout: float,
) -> bool:
    deadline = time.monotonic() + timeout

    while time.monotonic() < deadline:
        rendered = normalized_terminal_text(bytes(transcript))
        if predicate(rendered):
            return True

        ready, _, _ = select.select([master], [], [], 0.1)
        if not ready:
            continue

        try:
            chunk = os.read(master, 65536)
        except OSError:
            break

        if not chunk:
            break

        transcript.extend(chunk)

    return predicate(normalized_terminal_text(bytes(transcript)))


def validate_repl_pty(
    launcher: Path,
    *,
    cwd: Path,
    env: dict[str, str],
) -> None:
    master, slave = pty.openpty()

    try:
        fcntl.ioctl(
            slave,
            termios.TIOCSWINSZ,
            struct.pack("HHHH", 24, 120, 0, 0),
        )

        repl_env = env.copy()
        repl_env["TERM"] = "xterm-256color"

        process = subprocess.Popen(
            [str(launcher)],
            cwd=cwd,
            env=repl_env,
            stdin=slave,
            stdout=slave,
            stderr=slave,
            close_fds=True,
        )
    finally:
        os.close(slave)

    transcript = bytearray()

    try:
        banner_ok = wait_for_terminal(
            master,
            transcript,
            lambda rendered: (
                "Protos REPL" in rendered
                and "Type :help for help, :quit to exit." in rendered
            ),
            10.0,
        )
        if not banner_ok:
            process.terminate()
            process.wait(timeout=2.0)
            fail(
                "Native REPL did not present its interactive PTY banner:\n"
                + normalized_terminal_text(bytes(transcript))
            )

        os.write(master, b"1 + 2\r")

        expression_ok = wait_for_terminal(
            master,
            transcript,
            lambda rendered: (
                re.search(r"(?:^|\n)3(?:\n|$)", rendered) is not None
            ),
            10.0,
        )
        if not expression_ok:
            process.terminate()
            process.wait(timeout=2.0)
            fail(
                "Native REPL PTY did not evaluate 1 + 2 to 3:\n"
                + normalized_terminal_text(bytes(transcript))
            )

        os.write(master, b":quit\r")

        try:
            status = process.wait(timeout=10.0)
        except subprocess.TimeoutExpired:
            process.terminate()
            try:
                process.wait(timeout=2.0)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            fail("Native REPL PTY did not terminate after :quit")

        if status != 0:
            fail(
                "Native REPL PTY exited with status "
                + str(status)
                + ":\n"
                + normalized_terminal_text(bytes(transcript))
            )

        print("NATIVE_DIST_REPL_PTY_BANNER_CHECK: PASS")
        print("NATIVE_DIST_REPL_PTY_EXPRESSION_CHECK: PASS")
        print("NATIVE_DIST_REPL_PTY_EXIT_CHECK: PASS")
        print("NATIVE_DIST_REPL_PTY_ADMISSION_CHECK: PASS")
    finally:
        os.close(master)



def terminate_process(process: subprocess.Popen) -> None:
    if process.poll() is not None:
        return

    process.terminate()
    try:
        process.wait(timeout=2.0)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()


# BUG021: supervised execution of a long-running child. Both output streams are
# forwarded to the echo sink as soon as the child writes them, so progress is
# visible while the child is still running, and are retained in full for
# admission checks and failure diagnostics. The retained transcript is bounded.
SUPERVISED_TRANSCRIPT_LIMIT_BYTES = 64 * 1024 * 1024


class SupervisedResult:
    def __init__(
        self,
        *,
        pid: int,
        returncode: int,
        stdout: str,
        stderr: str,
        timed_out: bool,
        truncated: bool,
    ) -> None:
        self.pid = pid
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = stderr
        self.timed_out = timed_out
        self.truncated = truncated

    def termination(self) -> str:
        if self.timed_out:
            return "timeout"

        if self.truncated:
            return "transcript-limit"

        if self.returncode < 0:
            try:
                return "signal:" + signal.Signals(-self.returncode).name
            except ValueError:
                return "signal:" + str(-self.returncode)

        return "exit"


def run_supervised(
    command: list[str],
    *,
    cwd: Path,
    env: dict[str, str],
    echo=None,
    timeout: float | None = None,
    transcript_limit: int = SUPERVISED_TRANSCRIPT_LIMIT_BYTES,
) -> SupervisedResult:
    if echo is None:
        echo = sys.stderr.buffer

    process = subprocess.Popen(
        command,
        cwd=cwd,
        env=env,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )

    captured = {
        process.stdout: bytearray(),
        process.stderr: bytearray(),
    }
    retained = 0
    timed_out = False
    truncated = False
    deadline = None if timeout is None else time.monotonic() + timeout

    selector = selectors.DefaultSelector()

    try:
        for stream in captured:
            selector.register(stream, selectors.EVENT_READ)

        while selector.get_map():
            wait = None
            if deadline is not None:
                wait = deadline - time.monotonic()
                if wait <= 0:
                    timed_out = True
                    break

            for key, _ in selector.select(wait):
                chunk = os.read(key.fd, 65536)

                if not chunk:
                    selector.unregister(key.fileobj)
                    continue

                echo.write(chunk)
                echo.flush()

                retained += len(chunk)
                if retained > transcript_limit:
                    truncated = True
                    break

                captured[key.fileobj].extend(chunk)

            if truncated:
                break

        if timed_out or truncated:
            terminate_process(process)
        else:
            process.wait()
    finally:
        selector.close()
        terminate_process(process)
        process.stdout.close()
        process.stderr.close()

    return SupervisedResult(
        pid=process.pid,
        returncode=process.returncode,
        stdout=captured[process.stdout].decode("utf-8", errors="replace"),
        stderr=captured[process.stderr].decode("utf-8", errors="replace"),
        timed_out=timed_out,
        truncated=truncated,
    )


def write_framed_json(stream, payload: dict) -> None:
    body = json.dumps(
        payload,
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")

    stream.write(
        f"Content-Length: {len(body)}\r\n\r\n".encode("ascii")
        + body
    )
    stream.flush()


def read_stdio_json(
    process: subprocess.Popen,
    buffer: bytearray,
    timeout: float,
) -> dict | None:
    if process.stdout is None:
        fail("protocol process has no stdout pipe")

    deadline = time.monotonic() + timeout

    while time.monotonic() < deadline:
        marker = buffer.find(b"\r\n\r\n")

        if marker >= 0:
            header = bytes(buffer[:marker]).decode("ascii")
            content_length = None

            for line in header.split("\r\n"):
                name, separator, value = line.partition(":")
                if separator and name.lower() == "content-length":
                    content_length = int(value.strip())

            if content_length is None:
                fail("protocol message has no Content-Length header")

            start = marker + 4
            end = start + content_length

            if len(buffer) >= end:
                body = bytes(buffer[start:end])
                del buffer[:end]
                return json.loads(body.decode("utf-8"))

        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break

        ready, _, _ = select.select(
            [process.stdout.fileno()],
            [],
            [],
            min(0.2, remaining),
        )

        if not ready:
            if process.poll() is not None:
                break
            continue

        chunk = os.read(process.stdout.fileno(), 65536)
        if not chunk:
            break

        buffer.extend(chunk)

    return None


def await_stdio_json(
    process: subprocess.Popen,
    buffer: bytearray,
    deferred: list[dict],
    predicate,
    timeout: float = 10.0,
) -> dict | None:
    for index, message in enumerate(deferred):
        if predicate(message):
            return deferred.pop(index)

    deadline = time.monotonic() + timeout

    while time.monotonic() < deadline:
        message = read_stdio_json(
            process,
            buffer,
            min(2.0, deadline - time.monotonic()),
        )

        if message is None:
            if process.poll() is not None:
                break
            continue

        if predicate(message):
            return message

        deferred.append(message)

    return None


def validate_lsp_stdio(
    launcher: Path,
    *,
    cwd: Path,
    env: dict[str, str],
) -> None:
    source = cwd / "dist005-lsp.protos"
    source_text = "value: 42\nvalue\n"
    source.write_text(source_text, encoding="utf-8")
    uri = source.resolve().as_uri()

    process = subprocess.Popen(
        [str(launcher), "language-server"],
        cwd=cwd,
        env=env,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )

    if process.stdin is None:
        terminate_process(process)
        fail("Native LSP process has no stdin pipe")

    buffer = bytearray()
    deferred: list[dict] = []

    try:
        write_framed_json(
            process.stdin,
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "initialize",
                "params": {
                    "processId": None,
                    "rootUri": None,
                    "capabilities": {
                        "textDocument": {
                            "documentSymbol": {
                                "hierarchicalDocumentSymbolSupport": True
                            }
                        }
                    },
                },
            },
        )

        initialize = await_stdio_json(
            process,
            buffer,
            deferred,
            lambda message: message.get("id") == 1,
        )

        if initialize is None or "error" in initialize:
            fail(
                "Native LSP initialize failed; observed="
                + repr(deferred)
                + " response="
                + repr(initialize)
            )

        print("NATIVE_DIST_LSP_INITIALIZE_CHECK: PASS")

        write_framed_json(
            process.stdin,
            {
                "jsonrpc": "2.0",
                "method": "initialized",
                "params": {},
            },
        )

        write_framed_json(
            process.stdin,
            {
                "jsonrpc": "2.0",
                "method": "textDocument/didOpen",
                "params": {
                    "textDocument": {
                        "uri": uri,
                        "languageId": "protos",
                        "version": 1,
                        "text": source_text,
                    }
                },
            },
        )

        diagnostics = await_stdio_json(
            process,
            buffer,
            deferred,
            lambda message: (
                message.get("method")
                == "textDocument/publishDiagnostics"
                and message.get("params", {}).get("uri") == uri
            ),
        )

        if (
            diagnostics is None
            or diagnostics.get("params", {}).get("diagnostics") != []
        ):
            fail(
                "Native LSP didOpen diagnostics were not empty: "
                + repr(diagnostics)
            )

        print("NATIVE_DIST_LSP_DIAGNOSTICS_CHECK: PASS")

        write_framed_json(
            process.stdin,
            {
                "jsonrpc": "2.0",
                "id": 2,
                "method": "textDocument/documentSymbol",
                "params": {
                    "textDocument": {
                        "uri": uri,
                    }
                },
            },
        )

        symbols = await_stdio_json(
            process,
            buffer,
            deferred,
            lambda message: message.get("id") == 2,
        )

        if (
            symbols is None
            or "error" in symbols
            or not isinstance(symbols.get("result"), list)
            or not symbols["result"]
        ):
            fail(
                "Native LSP documentSymbol failed: "
                + repr(symbols)
            )

        print("NATIVE_DIST_LSP_DOCUMENT_SYMBOL_CHECK: PASS")

        write_framed_json(
            process.stdin,
            {
                "jsonrpc": "2.0",
                "id": 3,
                "method": "shutdown",
                "params": None,
            },
        )

        shutdown = await_stdio_json(
            process,
            buffer,
            deferred,
            lambda message: message.get("id") == 3,
        )

        if (
            shutdown is None
            or "error" in shutdown
            or shutdown.get("result") is not None
        ):
            fail(
                "Native LSP shutdown failed: "
                + repr(shutdown)
            )

        write_framed_json(
            process.stdin,
            {
                "jsonrpc": "2.0",
                "method": "exit",
                "params": None,
            },
        )

        try:
            status = process.wait(timeout=10.0)
        except subprocess.TimeoutExpired:
            terminate_process(process)
            fail("Native LSP did not terminate after exit")

        if status != 0:
            fail(
                "Native LSP exited with status "
                + str(status)
            )

        stderr = (
            process.stderr.read().decode(
                "utf-8",
                errors="replace",
            )
            if process.stderr is not None
            else ""
        )

        forbidden = (
            "MissingReflectionRegistrationError",
            "NoSuchMethodException",
            "Unsupported request method",
        )

        present = [
            marker
            for marker in forbidden
            if marker in stderr
        ]

        if present:
            fail(
                "Native LSP emitted reachability/protocol failures "
                + repr(present)
                + "\nstderr:\n"
                + stderr
            )

        print("NATIVE_DIST_LSP_SHUTDOWN_CHECK: PASS")
        print("NATIVE_DIST_LSP_STDIO_ADMISSION_CHECK: PASS")

    finally:
        terminate_process(process)


class DapClient:
    def __init__(
        self,
        host: str,
        port: int,
        timeout: float = 12.0,
    ) -> None:
        self.timeout = timeout
        self.socket = socket.create_connection(
            (host, port),
            timeout=timeout,
        )
        self.socket.settimeout(timeout)
        self.reader = self.socket.makefile("rb")
        self.writer = self.socket.makefile("wb")
        self.next_sequence = 1
        self.deferred: list[dict] = []

    def close(self) -> None:
        try:
            self.reader.close()
        finally:
            try:
                self.writer.close()
            finally:
                self.socket.close()

    def request(
        self,
        command: str,
        arguments: dict,
    ) -> int:
        sequence = self.next_sequence
        self.next_sequence += 1

        write_framed_json(
            self.writer,
            {
                "seq": sequence,
                "type": "request",
                "command": command,
                "arguments": arguments,
            },
        )

        return sequence

    def read(self) -> dict:
        content_length = None

        while True:
            raw = self.reader.readline()

            if raw == b"":
                raise EOFError(
                    "DAP socket closed while reading headers"
                )

            if raw in (b"\r\n", b"\n"):
                break

            line = raw.decode("ascii").strip()
            name, separator, value = line.partition(":")

            if not separator:
                raise RuntimeError(
                    "malformed DAP header: " + repr(line)
                )

            if name.lower() == "content-length":
                content_length = int(value.strip())

        if content_length is None:
            raise RuntimeError(
                "DAP message has no Content-Length header"
            )

        body = self.reader.read(content_length)

        if len(body) != content_length:
            raise EOFError(
                "DAP body truncated: expected "
                + str(content_length)
                + ", got "
                + str(len(body))
            )

        return json.loads(body.decode("utf-8"))

    def await_matching(self, predicate) -> dict:
        for index, message in enumerate(self.deferred):
            if predicate(message):
                return self.deferred.pop(index)

        deadline = time.monotonic() + self.timeout

        while time.monotonic() < deadline:
            message = self.read()

            if predicate(message):
                return message

            self.deferred.append(message)

        raise TimeoutError("DAP message timeout")

    def response(self, request_sequence: int) -> dict:
        return self.await_matching(
            lambda message: (
                message.get("type") == "response"
                and message.get("request_seq")
                == request_sequence
            )
        )

    def event(self, name: str) -> dict:
        return self.await_matching(
            lambda message: (
                message.get("type") == "event"
                and message.get("event") == name
            )
        )

    def output(
        self,
        category: str,
        fragment: str,
    ) -> dict:
        return self.await_matching(
            lambda message: (
                message.get("type") == "event"
                and message.get("event") == "output"
                and message.get("body", {}).get("category")
                == category
                and fragment
                in str(
                    message.get("body", {}).get(
                        "output",
                        "",
                    )
                )
            )
        )


def dap_response_ok(
    message: dict,
    request_sequence: int,
    command: str,
) -> bool:
    return (
        message.get("type") == "response"
        and message.get("request_seq")
        == request_sequence
        and message.get("command") == command
        and message.get("success") is True
    )


def validate_dap(
    launcher: Path,
    *,
    cwd: Path,
    env: dict[str, str],
) -> None:
    helper = cwd / "dist005-dap-helper.protos"
    source = cwd / "dist005-dap-main.protos"

    helper.write_text(
        'value: "application-out"\n',
        encoding="utf-8",
    )

    source.write_text(
        'Helper: import("./dist005-dap-helper.protos")\n'
        'errorWriter: TextWriter('
        'process.stderr(), process.stderrEncoding())\n'
        'print(Helper.value)\n'
        'errorWriter.writeLine('
        'process.args().at(1)).value()\n',
        encoding="utf-8",
    )

    process = subprocess.Popen(
        [
            str(launcher),
            "debug",
            str(source),
            "application-out",
            "application-err",
        ],
        cwd=cwd,
        env=env,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )

    client = None

    try:
        if process.stdout is None:
            fail("Native DAP process has no stdout pipe")

        readiness = None
        deadline = time.monotonic() + 12.0

        while time.monotonic() < deadline:
            ready, _, _ = select.select(
                [process.stdout.fileno()],
                [],
                [],
                0.2,
            )

            if ready:
                raw_line = process.stdout.readline()
                if raw_line:
                    readiness = raw_line.decode(
                        "utf-8",
                        errors="replace",
                    ).rstrip("\r\n")
                break

            if process.poll() is not None:
                break

        prefix = "PROTOS_DEBUG_READY "

        if readiness is None or not readiness.startswith(prefix):
            fail(
                "Native DAP did not publish public readiness: "
                + repr(readiness)
            )

        try:
            endpoint = json.loads(
                readiness[len(prefix):]
            )
        except json.JSONDecodeError as failure:
            fail(
                "Native DAP readiness JSON is malformed: "
                + str(failure)
            )

        host = endpoint.get("host")
        port = endpoint.get("port")

        try:
            loopback = (
                isinstance(host, str)
                and ipaddress.ip_address(host).is_loopback
            )
        except ValueError:
            loopback = False

        if not (
            endpoint.get("version") == 1
            and endpoint.get("protocol") == "dap"
            and endpoint.get("transport") == "tcp"
            and loopback
            and isinstance(port, int)
            and 0 < port <= 65535
        ):
            fail(
                "Native DAP readiness contract changed: "
                + repr(endpoint)
            )

        print("NATIVE_DIST_DAP_READINESS_CHECK: PASS")

        client = DapClient(host, port)

        initialize = client.request(
            "initialize",
            {
                "adapterID": "protos",
                "clientID": "dist005-native",
                "clientName": "DIST005 Native DAP",
                "linesStartAt1": True,
                "columnsStartAt1": True,
                "pathFormat": "path",
            },
        )

        initialize_response = client.response(initialize)

        if not dap_response_ok(
            initialize_response,
            initialize,
            "initialize",
        ):
            fail(
                "Native DAP initialize failed: "
                + repr(initialize_response)
            )

        initialized = client.event("initialized")
        if initialized.get("event") != "initialized":
            fail(
                "Native DAP initialized event changed: "
                + repr(initialized)
            )

        print("NATIVE_DIST_DAP_INITIALIZE_CHECK: PASS")

        launch = client.request("launch", {})
        launch_response = client.response(launch)

        if not dap_response_ok(
            launch_response,
            launch,
            "launch",
        ):
            fail(
                "Native DAP launch failed: "
                + repr(launch_response)
            )

        configuration = client.request(
            "configurationDone",
            {},
        )
        configuration_response = client.response(
            configuration
        )

        if not dap_response_ok(
            configuration_response,
            configuration,
            "configurationDone",
        ):
            fail(
                "Native DAP configurationDone failed: "
                + repr(configuration_response)
            )

        print("NATIVE_DIST_DAP_CONFIGURATION_CHECK: PASS")

        stdout_event = client.output(
            "stdout",
            "application-out",
        )
        stderr_event = client.output(
            "stderr",
            "application-err",
        )

        if (
            "application-out"
            not in str(
                stdout_event.get("body", {}).get(
                    "output",
                    "",
                )
            )
        ):
            fail(
                "Native DAP stdout event changed: "
                + repr(stdout_event)
            )

        if (
            "application-err"
            not in str(
                stderr_event.get("body", {}).get(
                    "output",
                    "",
                )
            )
        ):
            fail(
                "Native DAP stderr event changed: "
                + repr(stderr_event)
            )

        print("NATIVE_DIST_DAP_STREAM_ROUTING_CHECK: PASS")

        terminated = client.event("terminated")
        if terminated.get("event") != "terminated":
            fail(
                "Native DAP terminated event changed: "
                + repr(terminated)
            )

        client.close()
        client = None

        try:
            status = process.wait(timeout=12.0)
        except subprocess.TimeoutExpired:
            terminate_process(process)
            fail(
                "Native debug invocation did not terminate "
                "after DAP completion"
            )

        if status != 0:
            fail(
                "Native debug invocation exited with status "
                + str(status)
            )

        remaining_stdout = process.stdout.read().decode(
            "utf-8",
            errors="replace",
        )

        diagnostic_text = (
            process.stderr.read().decode(
                "utf-8",
                errors="replace",
            )
            if process.stderr is not None
            else ""
        )

        if remaining_stdout:
            fail(
                "Native DAP control stdout contains data "
                "after readiness:\n"
                + remaining_stdout
            )

        forbidden_diagnostics = (
            "[Graal DAP] Starting server and listening on",
            "application-out",
            "application-err",
        )

        leaked = [
            marker
            for marker in forbidden_diagnostics
            if marker in diagnostic_text
        ]

        if leaked:
            fail(
                "Native DAP diagnostic channel leaked "
                + repr(leaked)
                + ":\n"
                + diagnostic_text
            )

        print("NATIVE_DIST_DAP_TERMINATION_CHECK: PASS")
        print("NATIVE_DIST_DAP_CONTROL_CHANNEL_CHECK: PASS")
        print("NATIVE_DIST_DAP_ADMISSION_CHECK: PASS")

    finally:
        if client is not None:
            try:
                client.close()
            except Exception:
                pass

        terminate_process(process)



# BUG036: an extracted Native distribution must provide the same optimizing
# Truffle runtime as scripts/native-test.sh. Successful guest Tier-2
# compilations are mandatory for both Bytecode DSL and builtin-call roots.
FALLBACK_RUNTIME_MARKER_RE = re.compile(
    r"fallback runtime that does not support runtime compilation",
    flags=re.IGNORECASE,
)
OPT_DONE_RE = re.compile(r"\bopt done\b", flags=re.IGNORECASE)
OPT_FAILED_RE = re.compile(r"\bopt failed\b", flags=re.IGNORECASE)
TIER2_RE = re.compile(r"\bTier ?2\b")
FRAME_FAILURE_RE = re.compile(
    r"(?:"
    r"FrameWithoutBoxing.*should not be materialized"
    r"|"
    r"should not be materialized.*FrameWithoutBoxing"
    r")",
    flags=re.IGNORECASE,
)
COMPILATION_FAILURE_RE = re.compile(
    r"Compilation failed|Internal error",
    flags=re.IGNORECASE,
)
TRACE_COMPILATION_LINE_RE = re.compile(
    r"^\[engine\]\s+opt (?:queued|start|done|failed)\b.*$",
    flags=re.IGNORECASE,
)

TRUFFLE_LOG_REDIRECTION_HINT = (
    "[To redirect Truffle log output to a file use one of the following "
    "options:\n"
    "* '--log.file=<path>' if the option is passed using a guest language "
    "launcher.\n"
    "* '-Dpolyglot.log.file=<path>' if the option is passed using the host "
    "Java launcher.\n"
    "* Configure logging using the polyglot embedding API.]\n"
)

GUEST_JIT_OPTIONS = (
    "-Dpolyglot.engine.AllowExperimentalOptions=true",
    "-Dpolyglot.engine.TraceCompilation=true",
    "-Dpolyglot.engine.CompileImmediately=true",
    "-Dpolyglot.engine.BackgroundCompilation=false",
    "-Dpolyglot.engine.CompilationFailureAction=Print",
)


def guest_jit_probe_source() -> str:
    # Match the actual Native admission workload, not the retired
    # interpreter-only probe's smaller warmup.
    lines = [
        "x: 1",
        "f: () => { x }",
        "g: () => { (() => false).whileTrue(() => { null }) }",
    ]

    for _ in range(500):
        lines.append("f()")
        lines.append("g()")

    lines.append("print(f())")
    return "\n".join(lines) + "\n"


def count_guest_jit_evidence(output: str) -> dict[str, int]:
    tier2_done = [
        line
        for line in output.splitlines()
        if OPT_DONE_RE.search(line) and TIER2_RE.search(line)
    ]

    return {
        "fallback_markers": len(
            FALLBACK_RUNTIME_MARKER_RE.findall(output)
        ),
        "opt_done": len(OPT_DONE_RE.findall(output)),
        "opt_failed": len(OPT_FAILED_RE.findall(output)),
        "frame_failures": len(FRAME_FAILURE_RE.findall(output)),
        "compilation_failures": len(
            COMPILATION_FAILURE_RE.findall(output)
        ),
        "bytecode_root_tier2": sum(
            "ProtosBytecodeRootNodeGen" in line
            for line in tier2_done
        ),
        "builtin_call_root_tier2": sum(
            "ProtosBuiltinCallRootNode" in line
            for line in tier2_done
        ),
    }


def guest_jit_failure(counts: dict[str, int]) -> str | None:
    if counts["fallback_markers"] != 0:
        return "fallback runtime was selected"

    if counts["opt_failed"] != 0:
        return "reported opt failed"

    if counts["frame_failures"] != 0:
        return "reproduced FrameWithoutBoxing materialization failure"

    if counts["compilation_failures"] != 0:
        return "reported compilation failure"

    if counts["bytecode_root_tier2"] < 1:
        return "no successful Bytecode DSL root Tier-2 compilation"

    if counts["builtin_call_root_tier2"] < 1:
        return "no successful builtin-call root Tier-2 compilation"

    return None


def unexpected_guest_jit_stderr(stderr: str) -> str:
    # TraceCompilation legitimately writes compiler events to stderr.
    # The log-redirection hint is not a fallback warning. Only compiler
    # trace lines and that exact optional hint are permitted.
    remaining = stderr

    if remaining.startswith(TRUFFLE_LOG_REDIRECTION_HINT):
        remaining = remaining[len(TRUFFLE_LOG_REDIRECTION_HINT):]

    return "".join(
        line
        for line in remaining.splitlines(keepends=True)
        if not TRACE_COMPILATION_LINE_RE.fullmatch(
            line.rstrip("\r\n")
        )
    )


def validate_forced_guest_jit(
    native: Path,
    dist: Path,
    *,
    cwd: Path,
    env: dict[str, str],
) -> None:
    probe_env = env.copy()
    probe_env["PROTOS_HOME"] = str(dist)

    # The source and process working directory are outside the extracted
    # distribution. The executable is exclusively the extracted payload.
    with tempfile.TemporaryDirectory(
        prefix="dist005-native-jit-",
        dir=cwd,
    ) as raw:
        source = Path(raw) / "guest-jit.protos"
        source.write_text(
            guest_jit_probe_source(),
            encoding="utf-8",
        )

        result = subprocess.run(
            [
                str(native),
                *GUEST_JIT_OPTIONS,
                str(source),
            ],
            cwd=cwd,
            env=probe_env,
            text=True,
            capture_output=True,
            check=False,
        )

    combined = result.stdout + "\n" + result.stderr
    counts = count_guest_jit_evidence(combined)
    result_ok = result.stdout == "1\n"

    print("NATIVE_DIST_GUEST_JIT_STATUS=" + str(result.returncode))
    print("NATIVE_DIST_GUEST_JIT_RESULT_OK=" + str(int(result_ok)))
    print(
        "NATIVE_DIST_FALLBACK_RUNTIME_MARKERS="
        + str(counts["fallback_markers"])
    )
    print("NATIVE_DIST_OPT_DONE=" + str(counts["opt_done"]))
    print(
        "NATIVE_DIST_BYTECODE_ROOT_TIER2_COMPILATIONS="
        + str(counts["bytecode_root_tier2"])
    )
    print(
        "NATIVE_DIST_BUILTIN_CALL_ROOT_TIER2_COMPILATIONS="
        + str(counts["builtin_call_root_tier2"])
    )
    print("NATIVE_DIST_OPT_FAILED=" + str(counts["opt_failed"]))
    print(
        "NATIVE_DIST_FRAME_FAILURES="
        + str(counts["frame_failures"])
    )
    print(
        "NATIVE_DIST_COMPILATION_FAILURES="
        + str(counts["compilation_failures"])
    )

    if result.returncode != 0:
        fail(
            "extracted Native guest JIT probe exited with status "
            + str(result.returncode)
            + "\nstdout:\n"
            + result.stdout
            + "\nstderr:\n"
            + result.stderr
        )

    if not result_ok:
        fail(
            "extracted Native guest JIT probe returned an unexpected "
            "program result:\n"
            + repr(result.stdout)
            + "\nstderr:\n"
            + result.stderr
        )

    failure = guest_jit_failure(counts)
    if failure is not None:
        fail(
            "extracted Native guest JIT probe "
            + failure
            + ":\n"
            + combined
        )

    unexpected = unexpected_guest_jit_stderr(result.stderr)
    if unexpected:
        fail(
            "extracted Native guest JIT probe emitted unexpected stderr:\n"
            + unexpected
        )

    print("NATIVE_DIST_GUEST_JIT_CHECK: PASS")
    print("NATIVE_DIST_GUEST_JIT=TIER2_REQUIRED")


def validate_full_test_tool(
    native: Path,
    dist: Path,
    *,
    cwd: Path,
    env: dict[str, str],
    echo=None,
) -> None:
    test_env = env.copy()
    test_env["PROTOS_HOME"] = str(dist)

    if hasattr(os, "sched_getaffinity"):
        jobs = len(os.sched_getaffinity(0))
    else:
        jobs = os.cpu_count() or 1

    jobs = max(1, jobs)
    started = time.monotonic()

    # BUG021: progress streams while the complete suite runs. No timeout is
    # imposed: a slow but progressing suite is a valid admission.
    result = run_supervised(
        [
            str(native),
            "test",
            "--jobs",
            str(jobs),
        ],
        cwd=cwd,
        env=test_env,
        echo=echo,
    )

    elapsed = time.monotonic() - started
    combined = result.stdout + "\n" + result.stderr

    summaries = re.findall(
        r"(?m)^([0-9]+) passed, ([0-9]+) failed\s*$",
        combined,
    )

    teardown_failures = combined.count(
        "Polyglot runtime host cannot close while "
        "Process Contexts are active"
    )
    reflection_failures = combined.count(
        "MissingReflectionRegistrationError"
    )

    print("NATIVE_DIST_FULL_TEST_TOOL_JOBS=" + str(jobs))
    print("NATIVE_DIST_FULL_TEST_TOOL_STATUS=" + str(result.returncode))
    print("NATIVE_DIST_FULL_TEST_TOOL_TERMINATION=" + result.termination())
    print(
        "NATIVE_DIST_FULL_TEST_TOOL_SECONDS="
        + f"{elapsed:.2f}"
    )
    print(
        "NATIVE_DIST_FULL_TEST_TOOL_CONTEXT_TEARDOWN_FAILURES="
        + str(teardown_failures)
    )
    print(
        "NATIVE_DIST_FULL_TEST_TOOL_REFLECTION_FAILURES="
        + str(reflection_failures)
    )

    if summaries:
        passed, failed_count = summaries[-1]
        print("NATIVE_DIST_FULL_TEST_TOOL_PASSED=" + passed)
        print("NATIVE_DIST_FULL_TEST_TOOL_FAILED=" + failed_count)
    else:
        passed = None
        failed_count = None
        print("NATIVE_DIST_FULL_TEST_TOOL_SUMMARY=MISSING")

    bootstrap_ok = (
        "Protos test tool bootstrap" in combined
        and re.search(r"(?m)^test\s*$", combined) is not None
    )

    if not (
        result.returncode == 0
        and result.termination() == "exit"
        and passed is not None
        and failed_count is not None
        and int(passed) > 0
        and int(failed_count) == 0
        and teardown_failures == 0
        and reflection_failures == 0
        and bootstrap_ok
    ):
        fail(
            "complete extracted Native Test Tool admission failed"
            + " (termination "
            + result.termination()
            + ")"
            + "\nstdout:\n"
            + result.stdout
            + "\nstderr:\n"
            + result.stderr
        )

    print("NATIVE_DIST_FULL_TEST_TOOL_BOOTSTRAP_CHECK: PASS")
    print("DIST005_NATIVE_FULL_TEST_TOOL_ADMISSION: PASS")

def validate(archive: Path) -> None:
    if not archive.is_file():
        fail("archive is missing: " + str(archive))

    recorded_outer = read_outer_checksum(archive)
    before = sha256(archive)

    if before != recorded_outer:
        fail(
            "outer SHA-256 mismatch before validation: "
            + before
            + " != "
            + recorded_outer
        )

    checkout = Path(__file__).resolve().parents[1]

    with tempfile.TemporaryDirectory(prefix="dist005-native-validate-") as raw:
        temp = Path(raw)
        extraction = temp / "relocated-install"
        extraction.mkdir()

        unrelated_cwd = temp / "caller-cwd"
        unrelated_cwd.mkdir()

        external_source_dir = temp / "external-source"
        external_source_dir.mkdir()

        dist = extract_preserving_modes(archive, extraction)
        launcher = dist / "bin/protos"
        native = dist / "libexec/protos-native"

        require_executable(launcher, "Native distribution launcher")
        require_executable(native, "Native executable payload")

        runtime = read_key_values(
            dist / "RUNTIME.txt",
            "Native RUNTIME.txt",
        )
        elf_observation = validate_native_elf_metadata(native, runtime)

        if dist.resolve() == checkout.resolve():
            fail("extracted distribution root equals repository checkout")

        print("NATIVE_DIST_RELOCATED_ROOT_CHECK: PASS")

        # Resolved before PATH isolation: only the validator consults it.
        ldconfig = shutil.which("ldconfig") or shutil.which("ldconfig", path="/sbin:/usr/sbin")
        proof_path = isolated_path(temp)
        print("NATIVE_DIST_NO_JAVA_PATH_CHECK: PASS")
        print("NATIVE_DIST_NO_MAVEN_PATH_CHECK: PASS")

        source = external_source_dir / "unicode-resource-proof.protos"
        source.write_text(
            'Arrays: import("std:collections/array")\n'
            'print("DIST005 unicode áéí π 😀")\n',
            encoding="utf-8",
        )

        if dist.resolve() in source.resolve().parents:
            fail("external source fixture is inside the extracted distribution")

        if dist.resolve() in unrelated_cwd.resolve().parents:
            fail("caller CWD is inside the extracted distribution")

        env = os.environ.copy()
        env.pop("JAVA_HOME", None)
        env.pop("PROTOS_JAVA", None)
        env["PATH"] = proof_path
        env["PROTOS_HOME"] = "/DIST005/POISON/PROTOS_HOME"

        source_metadata = read_key_values(
            dist / "SOURCE.txt",
            "Native SOURCE.txt",
        )
        implementation_version = source_metadata.get("implementation_version")
        if not implementation_version:
            fail("Native SOURCE.txt is missing implementation_version")

        version_env = env.copy()
        version_env["PROTOS_IMPLEMENTATION_VERSION"] = "BUG019-POISON-VERSION"
        version = run_case(
            "VERSION",
            [str(launcher), "--version"],
            cwd=unrelated_cwd,
            env=version_env,
            expected_stdout="Protos ",
        )
        expected_version_stdout = "Protos " + implementation_version + "\n"
        if version.stdout != expected_version_stdout:
            fail(
                "VERSION output is not the exact distribution identity "
                + repr(expected_version_stdout)
                + "\nstdout:\n"
                + version.stdout
            )
        print("NATIVE_DIST_EXACT_VERSION_IDENTITY_CHECK: PASS")

        run_case(
            "HELP",
            [str(launcher), "--help"],
            cwd=unrelated_cwd,
            env=env,
            expected_stdout="Usage:",
        )

        run_case(
            "EVAL",
            [
                str(launcher),
                "-e",
                'print("DIST005 eval áéí π 😀")',
            ],
            cwd=unrelated_cwd,
            env=env,
            expected_stdout="DIST005 eval áéí π 😀",
        )

        run_case(
            "EXTERNAL_SOURCE",
            [str(launcher), str(source)],
            cwd=unrelated_cwd,
            env=env,
            expected_stdout="DIST005 unicode áéí π 😀",
        )

        print("NATIVE_DIST_UNRELATED_CWD_CHECK: PASS")
        print("NATIVE_DIST_EXTERNAL_SOURCE_LOCATION_CHECK: PASS")
        print("NATIVE_DIST_PROTOS_HOME_DERIVATION_CHECK: PASS")

        validate_filesystem_grant(launcher, temp, unrelated_cwd, env)
        validate_native_ffm(
            launcher,
            dist,
            elf_observation["dt_needed"],
            temp,
            unrelated_cwd,
            env,
            ldconfig,
        )

        workspace_fixture = (
            dist
            / "protos/tests/package-tool/execution-plan/cases/workspace"
        )
        if not workspace_fixture.is_dir():
            fail(
                "bundled workspace fixture is missing: "
                + str(workspace_fixture)
            )

        project = temp / "workspace-project"
        shutil.copytree(workspace_fixture, project)

        if checkout.resolve() in project.resolve().parents:
            fail("workspace proof project is inside repository checkout")
        if dist.resolve() in project.resolve().parents:
            fail("workspace proof project is inside extracted distribution")

        main_module = project / "Main"
        main_module.mkdir()
        (main_module / "module.protos").write_text(
            'writer: TextWriter('
            'process.stdout(), process.stdoutEncoding())\n'
            'writer.writeLine(process.args().at(0)).value()\n'
            '42\n',
            encoding="utf-8",
        )

        package_result = run_case(
            "PACKAGE_MANIFEST",
            [str(launcher), "package", "manifest"],
            cwd=project,
            env=env,
            expected_stdout="package.toml: valid schema v1",
        )
        if package_result.stdout != "package.toml: valid schema v1\n":
            fail(
                "Package Tool manifest output changed:\n"
                + package_result.stdout
            )
        if package_result.stderr:
            fail(
                "Package Tool manifest emitted unexpected stderr:\n"
                + package_result.stderr
            )
        print("NATIVE_DIST_PACKAGE_TOOL_ADMISSION_CHECK: PASS")

        workspace_result = run_case(
            "WORKSPACE_RUN",
            [
                str(launcher),
                "run",
                "Main",
                "application-value",
            ],
            cwd=project,
            env=env,
            expected_stdout="application-value",
        )
        if workspace_result.stdout != "application-value\n":
            fail(
                "workspace run output changed:\n"
                + workspace_result.stdout
            )
        if workspace_result.stderr:
            fail(
                "workspace run emitted unexpected stderr:\n"
                + workspace_result.stderr
            )
        print("NATIVE_DIST_WORKSPACE_RUN_ADMISSION_CHECK: PASS")

        test_file = dist / "protos/tests/library/uri/parse.protos"
        if not test_file.is_file():
            fail("bundled Test Tool focal fixture is missing: " + str(test_file))

        test_result = run_case(
            "TEST_TOOL",
            [
                str(launcher),
                "test",
                "--file",
                str(test_file),
            ],
            cwd=unrelated_cwd,
            env=env,
        )

        combined = test_result.stdout + "\n" + test_result.stderr
        expected_test_lines = (
            "[library/uri] 0/4",
            "[library/uri] 1/4",
            "[library/uri] 2/4",
            "[library/uri] 3/4",
            "[library/uri] 4/4 passed",
            "4 passed, 0 failed",
            "Protos test tool bootstrap",
        )
        missing = [
            line for line in expected_test_lines if line not in combined
        ]
        if missing:
            fail(
                "Test Tool focal output is missing markers: "
                + repr(missing)
                + "\nstdout:\n"
                + test_result.stdout
                + "\nstderr:\n"
                + test_result.stderr
            )

        teardown_diagnostic = (
            "Polyglot runtime host cannot close while "
            "Process Contexts are active"
        )
        if teardown_diagnostic in combined:
            fail(
                "Test Tool focal reproduced Native context teardown failure"
            )

        print("NATIVE_DIST_TEST_TOOL_FOCAL_ADMISSION_CHECK: PASS")

        validate_repl_pty(
            launcher,
            cwd=unrelated_cwd,
            env=env,
        )

        validate_lsp_stdio(
            launcher,
            cwd=unrelated_cwd,
            env=env,
        )

        validate_dap(
            launcher,
            cwd=unrelated_cwd,
            env=env,
        )

        validate_forced_guest_jit(
            native,
            dist,
            cwd=unrelated_cwd,
            env=env,
        )

        validate_full_test_tool(
            native,
            dist,
            cwd=unrelated_cwd,
            env=env,
        )

    after = sha256(archive)

    if after != before:
        fail(
            "archive bytes changed during validation: "
            + before
            + " -> "
            + after
        )

    if after != recorded_outer:
        fail("archive no longer matches its outer checksum")

    print("NATIVE_DIST_OUTER_CHECKSUM_CHECK: PASS")
    print("NATIVE_DIST_ARCHIVE_UNCHANGED_CHECK: PASS")
    print("DIST005_NATIVE_BASIC_ADMISSION: PASS")
    print("DIST005_NATIVE_A_G_ADMISSION: PASS")
    print("DIST005_NATIVE_EXTRACTED_GUEST_TIER2_ADMISSION: PASS")
    print("DIST005_NATIVE_COMPLETE_ADMISSION: PASS")


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Validate relocation and no-Java execution of one exact DIST005 "
            "Native distribution archive."
        )
    )

    parser.add_argument(
        "--archive",
        required=True,
        help="exact Native distribution ZIP produced by dist/build_native.py",
    )

    args = parser.parse_args()
    validate(Path(args.archive).resolve())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
