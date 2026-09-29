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

"""Deterministic ELF admission helpers for DIST005 Native artifacts."""

from __future__ import annotations

from pathlib import Path
import re
import subprocess


class NativeElfError(ValueError):
    """Raised when Native ELF evidence is missing, malformed, or incompatible."""


GLIBC_TOKEN_RE = re.compile(r"\bGLIBC_([A-Za-z0-9_.+-]+)")
GLIBC_VERSION_RE = re.compile(r"([0-9]+(?:\.[0-9]+)+)")


def numeric_version(value: str) -> tuple[int, ...]:
    parts = value.split(".")
    if len(parts) < 2 or any(not part.isdigit() for part in parts):
        raise NativeElfError("malformed numeric ABI version: " + repr(value))
    return tuple(int(part) for part in parts)


def compare_numeric_versions(left: str, right: str) -> int:
    left_parts = numeric_version(left)
    right_parts = numeric_version(right)

    width = max(len(left_parts), len(right_parts))
    left_parts += (0,) * (width - len(left_parts))
    right_parts += (0,) * (width - len(right_parts))

    return (left_parts > right_parts) - (left_parts < right_parts)


def parse_glibc_versions(version_info: str) -> tuple[tuple[str, ...], str]:
    tokens = GLIBC_TOKEN_RE.findall(version_info)
    versions: set[str] = set()

    for token in tokens:
        # Only numeric GLIBC_X.Y[.Z] identities define the public ABI
        # requirement. Namespaces such as GLIBC_PRIVATE are not ABI floors.
        if not token or not token[0].isdigit():
            continue

        match = GLIBC_VERSION_RE.fullmatch(token)
        if match is None:
            raise NativeElfError(
                "malformed GLIBC symbol-version evidence: "
                + repr("GLIBC_" + token)
            )
        versions.add(match.group(1))

    if not versions:
        raise NativeElfError(
            "ELF version-info contains no observable GLIBC symbol requirements"
        )

    ordered = tuple(
        sorted(
            versions,
            key=lambda value: numeric_version(value),
        )
    )
    observed_max = max(
        ordered,
        key=lambda value: numeric_version(value),
    )
    return ordered, observed_max


def require_glibc_within_policy(
    observed_max: str,
    policy_max: str,
) -> None:
    if compare_numeric_versions(observed_max, policy_max) > 0:
        raise NativeElfError(
            "observed GLIBC requirement exceeds selected public policy: "
            + observed_max
            + " > "
            + policy_max
        )


def parse_interpreter(program_headers: str) -> str:
    matches = re.findall(
        r"\[Requesting program interpreter:\s*([^\]]+)\]",
        program_headers,
    )

    if len(matches) != 1:
        raise NativeElfError(
            "expected exactly one ELF interpreter, found "
            + str(len(matches))
        )

    interpreter = matches[0].strip()
    if not interpreter.startswith("/"):
        raise NativeElfError(
            "ELF interpreter is not an absolute path: " + repr(interpreter)
        )

    return interpreter


def parse_dt_needed(dynamic_info: str) -> tuple[str, ...]:
    needed = tuple(
        sorted(
            set(
                re.findall(
                    r"\(NEEDED\)\s+Shared library: \[([^\]]+)\]",
                    dynamic_info,
                )
            )
        )
    )

    if not needed:
        raise NativeElfError("ELF dynamic section exposes no DT_NEEDED entries")

    for library in needed:
        if "/" in library or not library.strip():
            raise NativeElfError(
                "malformed DT_NEEDED library identity: " + repr(library)
            )

    return needed


def parse_ldd_closure(
    text: str,
    *,
    required: tuple[str, ...],
) -> tuple[str, ...]:
    resolved: set[str] = set()
    unresolved: set[str] = set()

    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("linux-vdso"):
            continue

        if "=>" in line:
            name, remainder = line.split("=>", 1)
            name = name.strip()
            target = remainder.strip().split(None, 1)[0]

            if target == "not":
                if "not found" not in remainder:
                    raise NativeElfError(
                        "malformed ldd unresolved-library evidence: " + line
                    )
                unresolved.add(name)
                continue

            if target == "not found":
                unresolved.add(name)
                continue

            if "not found" in remainder:
                unresolved.add(name)
                continue

            resolved.add(name)
            continue

        token = line.split(None, 1)[0]
        if token.startswith("/"):
            resolved.add(Path(token).name)

    if unresolved:
        raise NativeElfError(
            "unresolved Native shared libraries: "
            + ",".join(sorted(unresolved))
        )

    missing = sorted(set(required) - resolved)
    if missing:
        raise NativeElfError(
            "ldd closure does not resolve every DT_NEEDED entry: "
            + ",".join(missing)
        )

    return tuple(sorted(required))


def parse_post_link_cpu_isa(notes: str) -> str:
    values = {
        match.strip()
        for match in re.findall(
            r"x86 ISA needed:\s*([^\r\n]+)",
            notes,
        )
        if match.strip()
    }

    if not values:
        return "not-recorded"

    return ",".join(sorted(values))


def _run(
    command: list[str],
    *,
    cwd: Path,
) -> str:
    try:
        result = subprocess.run(
            command,
            cwd=cwd,
            check=False,
            text=True,
            capture_output=True,
        )
    except FileNotFoundError as exc:
        raise NativeElfError(
            "required ELF inspection tool is unavailable: " + command[0]
        ) from exc

    if result.returncode != 0:
        raise NativeElfError(
            "ELF inspection command failed: "
            + " ".join(command)
            + "\nstderr:\n"
            + result.stderr.strip()
        )

    return result.stdout


def inspect_native_elf(
    binary: Path,
    *,
    policy_glibc_max: str,
) -> dict[str, str]:
    binary = binary.resolve()
    if not binary.is_file():
        raise NativeElfError("Native executable is missing: " + str(binary))

    cwd = binary.parent

    file_description = " ".join(
        _run(["file", "-b", str(binary)], cwd=cwd).strip().split()
    )

    if "ELF" not in file_description:
        raise NativeElfError(
            "Native executable is not ELF: " + file_description
        )
    if "x86-64" not in file_description:
        raise NativeElfError(
            "Native ELF is not x86-64: " + file_description
        )
    if "dynamically linked" not in file_description:
        raise NativeElfError(
            "Native ELF is not dynamically linked: " + file_description
        )

    header = _run(
        ["readelf", "--file-header", "--wide", str(binary)],
        cwd=cwd,
    )
    machine_match = re.search(
        r"^\s*Machine:\s*(.+?)\s*$",
        header,
        flags=re.MULTILINE,
    )
    if (
        machine_match is None
        or machine_match.group(1) != "Advanced Micro Devices X86-64"
    ):
        raise NativeElfError(
            "ELF machine is not x86_64: "
            + (
                "<missing>"
                if machine_match is None
                else machine_match.group(1)
            )
        )

    program_headers = _run(
        ["readelf", "--program-headers", "--wide", str(binary)],
        cwd=cwd,
    )
    interpreter = parse_interpreter(program_headers)

    dynamic_info = _run(
        ["readelf", "--dynamic", "--wide", str(binary)],
        cwd=cwd,
    )
    dt_needed = parse_dt_needed(dynamic_info)

    if "libc.so.6" not in dt_needed:
        raise NativeElfError(
            "Native ELF DT_NEEDED does not identify glibc libc.so.6"
        )

    version_info = _run(
        ["readelf", "--version-info", "--wide", str(binary)],
        cwd=cwd,
    )
    glibc_versions, glibc_max = parse_glibc_versions(version_info)
    require_glibc_within_policy(glibc_max, policy_glibc_max)

    ldd_output = _run(["ldd", str(binary)], cwd=cwd)
    closure = parse_ldd_closure(
        ldd_output,
        required=dt_needed,
    )

    notes = _run(
        ["readelf", "--notes", "--wide", str(binary)],
        cwd=cwd,
    )
    cpu_isa_evidence = parse_post_link_cpu_isa(notes)

    if not Path(interpreter).is_file():
        raise NativeElfError(
            "ELF interpreter is not resolved on the validation host: "
            + interpreter
        )

    return {
        "target_os": "linux",
        "target_arch": "x86_64",
        "linkage": "dynamic",
        "libc_family": "glibc",
        "elf_interpreter": interpreter,
        "dt_needed": ",".join(dt_needed),
        "shared_library_closure": ",".join(closure),
        "glibc_symbol_versions": ",".join(glibc_versions),
        "libc_abi_observed_max": glibc_max,
        "post_link_cpu_isa_evidence": cpu_isa_evidence,
        "binary_file_description": file_description,
    }
