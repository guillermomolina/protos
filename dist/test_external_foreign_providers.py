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

"""I085-B: external foreign providers exercised from Protos programs.

Extracts the portable JVM distribution built by ``make dist``, compiles the
three external plugin fixtures under ``protos/tests/foreign-provider/plugins``
against the extracted ``lib/protos.jar`` only, keeps the plugin JARs outside
the installation, and runs the ``.protos`` programs in that directory through
the extracted ``bin/protos`` as independent OS processes. Behavioral
assertions live in the ``.protos`` files; this runner only checks exit codes,
markers, provider trace lines, and that the installation is left unmodified.

I144 extends the same run with the optional external FFM provider
(``providers/ffm``), built by ``dist/build_ffm_provider.py`` against the same
extracted ``lib/protos.jar`` and passed only with ``--foreign-provider-path``:
real shared SQLite when present, the denial cases, and the absence of implicit
authorization. Native library authorization is default-off: every case that
loads a library also passes exactly the ``--allow-library alias=target``
options it needs (``sqlite3=libsqlite3.so.0`` for SQLite), and the negative
cases prove that provider presence alone authorizes nothing. The two-library
case compiles ``native/ffi_test.c`` and ``native/ffi_second.c`` under the
runner's work directory and authorizes both by absolute path under the same
distributable JAR. I174 adds, under the same distributable JAR, the pure
declarative layout program (``ffm-layouts.protos``, no library grant) and the
fixed-width scalar and C-measured layout program
(``ffm-scalar-layout-abi.protos``), which authorizes both compiled fixtures
and calls the same registered scalar shape through each of them, and the
typed native pointer program (``ffm-pointers.protos``) under the same grants. The ``ffi-test`` ABI fixture (``ffm-abi.protos``) is run by
the Java test host ``ProtosI144FfmFixtureAbiTest``. Missing platform support or
shared SQLite is reported as UNSUPPORTED, never as PASS. The I144 tutorials
and examples (``protos/tutorials``, ``protos/examples/ffi``) run from their
real paths against the same JAR; they validate by assertion and must exit 0
with empty stdout and stderr.

Before any I144/I169 case, two minimal launcher invocations (no provider, no
library) confirm that the extracted archive implements the I169 option
contract; a stale archive is an explicit I169_DISTRIBUTION_BASELINE failure
and no native case is attempted. Launcher parsing and rejection cases need no
native support and are never UNSUPPORTED for platform reasons; they run under
a shorter timeout, and a timeout is reported as FAIL for that command.
"""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import platform
import re
import selectors
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import zipfile

import build_ffm_provider

ROOT = Path(__file__).resolve().parents[1]
CASES = ROOT / "protos" / "tests" / "foreign-provider"
# I144 learning material: assertion-validated, no marker, run from real paths.
I144_LEARNING = (
    ("I144_LIB028_PUBLIC_VERSION", ROOT / "protos" / "examples" / "ffi" / "sqlite-version-public.protos"),
    ("I144_DOC023_PUBLIC_BINDINGS", ROOT / "protos" / "tutorials" / "08-language-interactions"
     / "09-public-ffi-bindings.protos"),
    ("I144_DOC023_PUBLIC_OWNERSHIP", ROOT / "protos" / "tutorials" / "12-system-resources"
     / "05-public-ffi-owned-out.protos"),
    ("I144_DOC023_PUBLIC_COMPLETE", ROOT / "protos" / "examples" / "ffi"
     / "sqlite-complete-public.protos"),
    ("I144_DOC023_PUBLIC_NUMERIC_QUERY", ROOT / "protos" / "examples" / "ffi"
     / "sqlite-numeric-query-public.protos"),
    ("I144_TUTORIAL_BASIC", ROOT / "protos" / "tutorials" / "08-language-interactions"
     / "07-ffi-provider-basics.protos"),
    ("I144_TUTORIAL_LIFETIME", ROOT / "protos" / "tutorials" / "12-system-resources"
     / "04-ffi-ownership-and-borrows.protos"),
    ("I144_EXAMPLE_VERSION", ROOT / "protos" / "examples" / "ffi" / "sqlite-version.protos"),
    ("I144_EXAMPLE_QUERY", ROOT / "protos" / "examples" / "ffi" / "sqlite-memory-query.protos"),
)
# LIB029 assertions stay in Protos; runner keys distinguish native availability
# from behavioral acceptance and from the provider-free import/denial case.
LIB029_CASES = (
    ("LIB029_QUERY", "sqlite-query.protos", "LIB029-QUERY-PASS"),
    ("LIB029_ROWS", "sqlite-rows.protos", "LIB029-ROWS-PASS"),
    ("LIB029_LIFECYCLE", "sqlite-lifecycle.protos", "LIB029-LIFECYCLE-PASS"),
    ("LIB029_DENIED", "sqlite-denied.protos", "LIB029-DENIED-PASS"),
)
# LIB030 real-SQLite extension: TEXT/BLOB input, statement reuse and complete
# single-statement SQL admission, through the same authorized provider JAR.
LIB030_CASES = (
    ("LIB030_BIND_TEXT_BLOB", "sqlite-bind-text-blob.protos", "LIB030-BIND-TEXT-BLOB-PASS"),
    ("LIB030_STATEMENT_REUSE", "sqlite-statement-reuse.protos", "LIB030-STATEMENT-REUSE-PASS"),
    ("LIB030_SQL_ADMISSION", "sqlite-sql-admission.protos", "LIB030-SQL-ADMISSION-PASS"),
    # Exact 16 MiB / 16 MiB + 1 input policy; separate because it builds large values.
    ("LIB030_INPUT_LIMITS", "sqlite-input-limits.protos", "LIB030-INPUT-LIMITS-PASS"),
)
# Each scenario owns its own SQLite file inside Runner.work. Reopen/recovery
# phases always invoke independent Protos processes against that same file.
LIB031_CASES = (
    ("LIB031_LOCAL", "local"),
    ("LIB031_REOPEN", "reopen"),
    ("LIB031_READ_ONLY", "readOnly"),
    ("LIB031_OPEN_MODES", "missing"),
    ("LIB031_LOCKING", "locked"),
    ("LIB031_RECOVERY", "recover"),
    ("LIB031_EXAMPLE", "example"),
    ("LIB031_TUTORIAL", "tutorial"),
    ("LIB031_WAL_EXAMPLE", "walExample"),
)
LIB029_LEARNING = (
    ("LIB029_EXAMPLE", ROOT / "protos/examples/ffi/sqlite-library.protos"),
    ("LIB029_TUTORIAL", ROOT / "protos/tutorials/12-system-resources/05-sqlite-protos-first.protos"),
)
# DOC024 public std:sqlite learning programs: silent, assertion-validated,
# run from their real paths through the same authorized provider JAR.
DOC024_LEARNING = (
    ("DOC024_TUTORIAL_SQLITE_LIFECYCLE",
     ROOT / "protos/tutorials/12-system-resources/06-sqlite-query-lifecycle.protos"),
    ("DOC024_EXAMPLE_NUMERIC", ROOT / "protos/examples/ffi/sqlite-prepared-numeric.protos"),
    ("DOC024_EXAMPLE_COPIED_VALUES", ROOT / "protos/examples/ffi/sqlite-copied-values.protos"),
)
SERVICE = "META-INF/services/com.guillermomolina.protos.spi.foreign.ProtosForeignProviderPlugin"
TRACE = "I085-B-TRACE "
OPAQUE = "i085b-opaque"
LIBC = "i085b-libc"
JAVA = "i085b-java"
TIMEOUT = 120
# CLI usage errors are rejected before any program or Tool runs; they get a shorter bound.
USAGE_TIMEOUT = 30
# Exact load name the host authorizes for SQLite cases; passed unchanged to the loader.
SQLITE_GRANT = ("sqlite3=libsqlite3.so.0",)
SECOND_MARKER_TEXT = "PROTOS-FFI-SECOND-MARKER\n"

PASS, FAIL, UNSUPPORTED = "PASS", "FAIL", "UNSUPPORTED"
RESULT_KEYS = (
    "PROTOS_CLASSFILE_BASELINE",
    "EXTERNAL_JAR_COMPILE",
    "PORTABLE_JVM_LAUNCH",
    "PROTOS_OPAQUE_PROVIDER",
    "PROTOS_NATIVE_FFM_PROVIDER",
    "PROTOS_MULTIPLE_PROVIDERS",
    "PROTOS_JAVA_MATH_PROVIDER",
    "PROTOS_JAVA_DATE_PROVIDER",
    "NO_PROVIDER_NEGATIVE",
    "DEFAULT_ZERO_DISCOVERY",
    "UNUSED_PROVIDER_NO_SESSION",
    "DISTRIBUTION_UNMODIFIED",
    "I144_LIB028_PUBLIC_VERSION",
    "I144_DOC023_PUBLIC_BINDINGS",
    "I144_DOC023_PUBLIC_OWNERSHIP",
    "I144_DOC023_PUBLIC_COMPLETE",
    "I144_DOC023_PUBLIC_NUMERIC_QUERY",
    "I144_FFM_PROVIDER_JAR",
    "I144_FFM_SQLITE",
    "I144_FFM_DENIED",
    "I144_NO_IMPLICIT_AUTHORIZATION",
    "I144_UNUSED_NO_SESSION",
    "I144_ARBITRARY_ALIAS_DENIED",
    "I144_TUTORIAL_BASIC",
    "I144_TUTORIAL_LIFETIME",
    "I144_EXAMPLE_VERSION",
    "I144_EXAMPLE_QUERY",
    "LIB029_MISSING",
    "I169_DISTRIBUTION_BASELINE",
    "I169_PROVIDER_ABSENT",
    "I169_ALLOWLIST_DENIED",
    "I169_UNAUTHORIZED_ALIAS",
    "I169_LIBRARY_UNAVAILABLE",
    "I169_CLI_INVALID_OPTIONS",
    "I169_TOOLS_DENIED",
    "I169_TWO_LIBRARIES",
    "I174_FFM_LAYOUTS",
    "I174_FFM_SCALAR_LAYOUT_ABI",
    "I174_FFM_ROOT_ACTOR",
    "I174_FFM_POINTERS",
    *(key for key, _, _ in LIB029_CASES),
    *(key for key, _, _ in LIB030_CASES),
    *(key for key, _ in LIB031_CASES),
    *(key for key, _ in LIB029_LEARNING),
    *(key for key, _ in DOC024_LEARNING),
)
SQLITE_CANDIDATES = (
    "/usr/lib/x86_64-linux-gnu/libsqlite3.so.0",
    "/lib/x86_64-linux-gnu/libsqlite3.so.0",
    "/usr/lib64/libsqlite3.so.0",
    "/usr/lib/libsqlite3.so.0",
)


class Runner:
    def __init__(self, work: Path, java_home: Path | None) -> None:
        self.work = work
        self.evidence = work / "evidence"
        self.evidence.mkdir()
        self.java_home = java_home
        self.results: dict[str, str] = {}
        self.notes: dict[str, list[str]] = {}

    def record(self, key: str, status: str, note: str | None = None) -> None:
        # A key checked by several runs keeps its worst outcome.
        previous = self.results.get(key)
        if previous != FAIL and not (previous == UNSUPPORTED and status == PASS):
            self.results[key] = status
        if note:
            self.notes.setdefault(key, []).append(note)

    def tool(self, name: str) -> str:
        if self.java_home is not None:
            return str(self.java_home / "bin" / name)
        found = shutil.which(name)
        if found is None:
            raise SystemExit(f"error: {name} not found on PATH; set JAVA_HOME")
        return found

    def environment(self, extra: dict[str, str] | None = None) -> dict[str, str]:
        env = {k: v for k, v in os.environ.items() if k not in ("CLASSPATH", "PROTOS_HOME")}
        if self.java_home is not None:
            env["JAVA_HOME"] = str(self.java_home)
        env.update(extra or {})
        return env

    def run(self, label: str, command: list[str], cwd: Path, env: dict[str, str],
            timeout: int = TIMEOUT):
        try:
            completed = subprocess.run(
                command, cwd=cwd, env=env, capture_output=True, text=True, timeout=timeout
            )
        except subprocess.TimeoutExpired as expired:
            (self.evidence / f"{label}.log").write_text(
                f"$ {' '.join(command)}\ntimeout after {timeout}s\n",
                encoding="utf-8",
            )
            raise expired
        log = self.evidence / f"{label}.log"
        log.write_text(
            f"$ {' '.join(command)}\nexit={completed.returncode}\n"
            f"--- stdout\n{completed.stdout}--- stderr\n{completed.stderr}",
            encoding="utf-8",
        )
        return completed


def java_major(command: list[str]) -> int | None:
    try:
        completed = subprocess.run(command, capture_output=True, text=True, timeout=60)
    except OSError:
        return None
    match = re.search(r'(?:version "|javac )(\d+)', completed.stdout + completed.stderr)
    return int(match.group(1)) if match else None


def classfile_major(bytecode: bytes) -> int:
    if len(bytecode) < 8 or bytecode[:4] != bytes.fromhex("CAFEBABE"):
        raise ValueError("invalid Java classfile header")
    return int.from_bytes(bytecode[6:8], "big")


def locate_archive(explicit: str | None) -> Path:
    if explicit:
        archive = Path(explicit).resolve()
    else:
        # The packaged project version selects the archive, as bin/protos does in checkout mode.
        properties = ROOT / "target" / "maven-archiver" / "pom.properties"
        versions = [
            line.split("=", 1)[1].strip()
            for line in (properties.read_text(encoding="utf-8").splitlines()
                         if properties.is_file() else [])
            if line.startswith("version=")
        ]
        if not versions:
            raise SystemExit(f"error: no packaged version in {properties}; run 'mvn package'")
        archive = ROOT / "target" / "distributions" / f"protos-{versions[0]}-posix-jvm.zip"
    if not archive.is_file():
        raise SystemExit(f"error: portable archive not found: {archive}")
    return archive


def extract(archive: Path, target: Path) -> Path:
    with zipfile.ZipFile(archive) as zf:
        for info in zf.infolist():
            path = Path(zf.extract(info, target))
            mode = (info.external_attr >> 16) & 0o777
            if mode and not info.is_dir():
                path.chmod(mode)
    roots = [p for p in target.iterdir() if p.is_dir()]
    if len(roots) != 1 or not (roots[0] / "bin" / "protos").is_file():
        raise SystemExit(f"error: unexpected portable archive layout in {archive}")
    return roots[0]


def snapshot(tree: Path) -> dict[str, str]:
    return {
        str(path.relative_to(tree)): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(tree.rglob("*"))
        if path.is_file()
    }


def build_plugin(runner: Runner, name: str, release: int, protos_jar: Path, out: Path) -> Path | None:
    source_root = CASES / "plugins" / name
    classes = runner.work / "classes" / name
    classes.mkdir(parents=True)
    sources = sorted(str(p) for p in (source_root / "src").rglob("*.java"))
    completed = runner.run(
        f"compile-{name}",
        [runner.tool("javac"), "--release", str(release), "-proc:none",
         "-classpath", str(protos_jar), "-d", str(classes), *sources],
        runner.work,
        runner.environment(),
    )
    if completed.returncode != 0:
        runner.record("EXTERNAL_JAR_COMPILE", FAIL, f"{name}: javac exit {completed.returncode}")
        return None
    compiled_classes = sorted(classes.rglob("*.class"))
    if not compiled_classes:
        runner.record("EXTERNAL_JAR_COMPILE", FAIL, f"{name}: no classfiles")
        return None

    expected_major = release + 44
    for compiled in compiled_classes:
        try:
            actual_major = classfile_major(compiled.read_bytes())
        except ValueError as failure:
            runner.record(
                "EXTERNAL_JAR_COMPILE",
                FAIL,
                f"{name}: {compiled.name}: {failure}",
            )
            return None
        if actual_major != expected_major:
            runner.record(
                "EXTERNAL_JAR_COMPILE",
                FAIL,
                f"{name}: {compiled.name}: expected classfile "
                f"{expected_major}, found {actual_major}",
            )
            return None

    jar = out / f"{name}-provider.jar"
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as zf:
        for path in compiled_classes:
            zf.write(path, path.relative_to(classes).as_posix())
        zf.write(source_root / SERVICE, SERVICE)
    runner.record("EXTERNAL_JAR_COMPILE", PASS)
    return jar


def traces(stderr: str) -> list[str]:
    return [line[len(TRACE):] for line in stderr.splitlines() if line.startswith(TRACE)]


def other_stderr(stderr: str) -> list[str]:
    return [line for line in stderr.splitlines() if line and not line.startswith(TRACE)]


def check_ffm_program(
    runner: Runner,
    key: str,
    install: Path,
    program: str,
    jar: Path,
    marker: str,
    libraries: tuple[str, ...] = (),
    arguments: tuple[str, ...] = (),
    report_progress: bool = True,
    evidence_suffix: str = "",
) -> None:
    """Runs one program with exactly the FFM provider JAR and the given library grants."""
    check_program(runner, key, install, program, [jar], marker, [],
                  library_options(libraries), list(arguments), report_progress, evidence_suffix)


def library_options(libraries: tuple[str, ...]) -> list[str]:
    """Explicit ``--allow-library`` options, one per ``alias=target`` grant."""
    options: list[str] = []
    for grant in libraries:
        options += ["--allow-library", grant]
    return options


def terminate_sqlite_writer(runner: Runner, key: str, install: Path,
                            provider: Path, database: Path) -> None:
    """Wait for an uncommitted-write acknowledgment, then kill and reap the JVM.

    This verifies process-termination recovery, not physical power-loss safety.
    Readiness is pipe-driven and bounded; no timing sleep stands in for a signal.
    """
    command = [str(install / "bin" / "protos"), "--foreign-provider-path", str(provider),
               *library_options(SQLITE_GRANT), str(CASES / "sqlite-durable.protos"),
               "crashWriter", str(database)]
    log = runner.evidence / f"{key.lower()}-crash-writer.log"
    stderr_log = runner.evidence / f"{key.lower()}-crash-writer.stderr"
    output = bytearray()
    child = None
    primary_failure = None
    cleanup_failures = []
    try:
        with stderr_log.open("wb") as stderr:
            child = subprocess.Popen(command, cwd=runner.work, env=runner.environment(),
                                     stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=stderr, start_new_session=True)
            try:
                deadline = time.monotonic() + TIMEOUT
                with selectors.DefaultSelector() as ready:
                    ready.register(child.stdout, selectors.EVENT_READ)
                    while b"\n" not in output:
                        remaining = deadline - time.monotonic()
                        if remaining <= 0 or not ready.select(remaining):
                            raise TimeoutError("uncommitted SQLite writer did not acknowledge readiness")
                        chunk = os.read(child.stdout.fileno(), 4096)
                        if not chunk:
                            raise RuntimeError("SQLite writer exited before readiness")
                        output.extend(chunk)
                        if len(output) > 4096:
                            raise RuntimeError("unexpected SQLite writer output")
                if bytes(output) != b"LIB031-UNCOMMITTED\n" or child.poll() is not None:
                    raise RuntimeError("SQLite writer did not remain alive after its uncommitted write")
                # Kill the launcher's entire process group, including a JVM if
                # a launcher ever stops using exec. Always reap the direct child.
                os.killpg(child.pid, signal.SIGKILL)
                remainder, _ = child.communicate(timeout=USAGE_TIMEOUT)
                output.extend(remainder)
                if child.returncode != -signal.SIGKILL:
                    raise RuntimeError(f"unexpected writer termination: {child.returncode}")
                if bytes(output) != b"LIB031-UNCOMMITTED\n":
                    raise RuntimeError("unexpected output after uncommitted-write acknowledgment")
            except BaseException as failure:
                primary_failure = failure
                raise
            finally:
                # Attempt every cleanup step without hiding the readiness or
                # termination failure that made this phase fail.
                for cleanup in (
                    lambda: os.killpg(child.pid, signal.SIGKILL),
                    lambda: child.wait(timeout=USAGE_TIMEOUT),
                    child.stdin.close,
                    child.stdout.close,
                ):
                    try:
                        cleanup()
                    except ProcessLookupError:
                        pass
                    except Exception as failure:
                        cleanup_failures.append(failure)
                if cleanup_failures:
                    if primary_failure is None:
                        primary_failure = cleanup_failures[0]
                        raise primary_failure
        if stderr_log.read_bytes():
            raise RuntimeError("unexpected SQLite crash writer stderr")
    except BaseException as failure:
        primary_failure = failure
        raise
    finally:
        try:
            log.write_text(f"$ {' '.join(command)}\nexit={child.returncode if child else 'not started'}\n"
                           f"--- stdout\n{output.decode('utf-8', errors='replace')}\n"
                           f"--- failure\n{primary_failure!r}\n"
                           f"--- cleanup failures\n{cleanup_failures!r}\n", encoding="utf-8")
        except Exception:
            if primary_failure is None:
                raise


def check_lib031(runner: Runner, key: str, scenario: str, install: Path, provider: Path) -> None:
    """Compose existing FFM invocations; any behavioral/harness failure is FAIL."""
    database = runner.work / f"{key.lower()}.sqlite"

    def run(mode: str) -> bool:
        check_ffm_program(runner, key, install, "sqlite-durable.protos", provider,
                          "LIB031-DURABLE-PASS", SQLITE_GRANT, (mode, str(database)),
                          report_progress=False, evidence_suffix=f"-{mode}")
        return runner.results.get(key) == PASS

    programs = {
        "example": "protos/examples/ffi/sqlite-durable.protos",
        "tutorial": "protos/tutorials/12-system-resources/07-sqlite-durable-transactions.protos",
        "walExample": "protos/examples/ffi/sqlite-wal.protos",
    }
    try:
        if scenario in ("local", "missing"):
            run(scenario)
        elif scenario in programs:
            command = [str(install / "bin" / "protos"), "--foreign-provider-path", str(provider),
                       *library_options(SQLITE_GRANT),
                       str(ROOT / programs[scenario]), str(database)]
            for index in range(2):
                completed = runner.run(f"{key.lower()}-{index}", command,
                                       runner.work, runner.environment())
                if completed.returncode or completed.stdout or completed.stderr:
                    raise RuntimeError(f"{scenario} run {index}: exit={completed.returncode}, "
                                       f"stdout={completed.stdout!r}, stderr={completed.stderr!r}")
            runner.record(key, PASS)
        elif run("seed"):
            if scenario == "reopen":
                run("verify")
            elif scenario == "readOnly":
                run("readOnly")
            elif scenario == "locked":
                # External instrumentation only: all round trips and recovery
                # assertions still execute in independent Protos processes.
                import sqlite3

                lock = sqlite3.connect(database, timeout=0, isolation_level=None)
                try:
                    lock.execute("BEGIN IMMEDIATE")
                    lock.execute("UPDATE samples SET n=n")
                    if not run("locked"):
                        return
                finally:
                    lock.close()
                run("verify")
            elif scenario == "recover":
                terminate_sqlite_writer(runner, key, install, provider, database)
                run("recover")
            else:
                raise ValueError(f"unknown SQLite scenario {scenario}")
    except Exception as failure:
        runner.record(key, FAIL, f"{type(failure).__name__}: {failure}")
    finally:
        # The plan contains one logical case even when several OS processes
        # contribute evidence. Preserve one progress event and every phase log.
        problems = [] if runner.results.get(key) == PASS else ["SQLite scenario failed"]
        report(f"sqlite-{scenario}", [provider], problems)


def check_ffm_silent(runner: Runner, key: str, install: Path, source: Path, jar: Path,
                     libraries: tuple[str, ...] = ()) -> None:
    """Runs one assertion-validated program: exit 0, no stdout, no stderr."""
    command = [str(install / "bin" / "protos"), "--foreign-provider-path", str(jar),
               *library_options(libraries), str(source)]
    label = f"{key.lower()}-{source.stem}"
    completed = runner.run(label, command, runner.work, runner.environment())
    problems = []
    if completed.returncode != 0:
        problems.append(f"exit {completed.returncode}")
    if completed.stdout:
        problems.append(f"stdout {completed.stdout!r}")
    if traces(completed.stderr):
        problems.append(f"traces {traces(completed.stderr)} != []")
    if other_stderr(completed.stderr):
        problems.append("unexpected stderr")
    report(source.name, [jar], problems)
    runner.record(key, FAIL if problems else PASS, f"{label}: {'; '.join(problems)}" if problems else None)


def shared_sqlite() -> str | None:
    for candidate in SQLITE_CANDIDATES:
        if Path(candidate).exists():
            return candidate
    return None


# I169 cases that only exercise launcher parsing and rejection: no provider, library,
# Linux, JDK 25 or C compiler is needed, so they never become UNSUPPORTED for those reasons.
I169_PARSE_KEYS = ("I169_DISTRIBUTION_BASELINE", "I169_CLI_INVALID_OPTIONS", "I169_TOOLS_DENIED")


def native_keys() -> list[str]:
    """Result keys that need the native FFM provider (Linux x86_64, JDK 25)."""
    return [key for key in RESULT_KEYS
            if (key.startswith(("I144_", "I169_", "I174_", "DOC024_", "LIB030_", "LIB031_"))
                or (key.startswith("LIB029_") and key != "LIB029_MISSING"))
            and key not in I169_PARSE_KEYS]


def check_i169_baseline(runner: Runner, install: Path) -> bool:
    """Fail closed unless the extracted launcher implements the I169 option contract.

    Two minimal invocations load no provider and no library: a library grant before -e
    is accepted and the program runs, and a grant after 'run' is parsed as a run option
    (so the missing entry is reported) rather than rejected as an unknown option. A
    launcher built before I169 fails one of them; no version heuristic is used.
    """
    protos = str(install / "bin" / "protos")
    problems = []
    try:
        accepted = runner.run(
            "i169-baseline-file", [protos, "--allow-library", "probe=libprotos_i169_probe.so",
                                   "-e", 'print("I169-BASELINE")'],
            runner.work, runner.environment(), USAGE_TIMEOUT)
        if accepted.returncode != 0 or accepted.stdout != "I169-BASELINE\n":
            problems.append(f"--allow-library before -e: exit {accepted.returncode}, "
                            f"stdout {accepted.stdout!r}")
        run_option = runner.run(
            "i169-baseline-run", [protos, "run", "--allow-library", "probe=libprotos_i169_probe.so"],
            runner.work, runner.environment(), USAGE_TIMEOUT)
        if (run_option.returncode != 2
                or "run requires a root-package logical entry" not in run_option.stderr):
            problems.append(f"--allow-library after run: exit {run_option.returncode}, "
                            f"stderr {run_option.stderr.strip()!r}")
    except subprocess.TimeoutExpired as expired:
        problems.append(f"timeout after {expired.timeout}s: {' '.join(expired.cmd)}")
    report("I169 distribution baseline", [], problems)
    runner.record("I169_DISTRIBUTION_BASELINE", FAIL if problems else PASS,
                  "archive does not contain the I169 launcher: " + "; ".join(problems)
                  if problems else None)
    return not problems


def plan_i144(runner: Runner, install: Path, protos_jar: Path, plugins: Path,
              native_supported: bool, native_reason: str, plan: list) -> None:
    keys = native_keys()
    if not native_supported:
        for key in keys:
            runner.record(key, UNSUPPORTED, native_reason)
        return
    scratch = runner.work / "i144-build"
    scratch.mkdir()
    try:
        provider = build_ffm_provider.build_provider_jar(
            runner.tool("javac"), protos_jar, plugins / "ffm-provider.jar", scratch,
            log=runner.evidence / "i144-compile-ffm.log")
    except build_ffm_provider.BuildError as failure:
        for key in keys:
            runner.record(key, FAIL, f"provider JAR not built: {str(failure).splitlines()[0]}")
        return
    runner.record("I144_FFM_PROVIDER_JAR", PASS)

    sqlite = shared_sqlite()
    if sqlite is None:
        for key in ("I144_FFM_SQLITE", "I144_FFM_DENIED", *(key for key, _ in I144_LEARNING),
                    *(key for key, _, _ in LIB029_CASES), *(key for key, _, _ in LIB030_CASES),
                    *(key for key, _ in LIB031_CASES),
                    *(key for key, _ in LIB029_LEARNING),
                    *(key for key, _ in DOC024_LEARNING)):
            runner.record(key, UNSUPPORTED, f"no shared libsqlite3.so.0 in {SQLITE_CANDIDATES}")
    else:
        plan.append(lambda: check_ffm_program(runner, "I144_FFM_SQLITE", install, "ffm-sqlite.protos",
                      provider, "I144-FFM-SQLITE-PASS", SQLITE_GRANT))
        plan.append(lambda: check_ffm_program(runner, "I144_FFM_DENIED", install, "ffm-denied.protos",
                      provider, "I144-FFM-DENIED-PASS", SQLITE_GRANT))
        for key, source in I144_LEARNING:
            plan.append(lambda key=key, source=source: check_ffm_silent(
                runner, key, install, source, provider, SQLITE_GRANT))

        for key, program, marker in LIB029_CASES:
            plan.append(lambda key=key, program=program, marker=marker: check_ffm_program(
                runner, key, install, program, provider, marker, SQLITE_GRANT))
        for key, program, marker in LIB030_CASES:
            plan.append(lambda key=key, program=program, marker=marker: check_ffm_program(
                runner, key, install, program, provider, marker, SQLITE_GRANT))
        for key, scenario in LIB031_CASES:
            plan.append(lambda key=key, scenario=scenario: check_lib031(
                runner, key, scenario, install, provider))
        for key, source in LIB029_LEARNING:
            plan.append(lambda key=key, source=source: check_ffm_silent(
                runner, key, install, source, provider, SQLITE_GRANT))
        for key, source in DOC024_LEARNING:
            plan.append(lambda key=key, source=source: check_ffm_silent(
                runner, key, install, source, provider, SQLITE_GRANT))

    # The distributable provider never registers the ffi-test scheme: the fixture
    # program must fail without any native access.
    plan.append(lambda: check_failure(runner, "I144_ARBITRARY_ALIAS_DENIED", install,
                  ["--foreign-provider-path", str(provider), str(CASES / "ffm-abi.protos")],
                  "i144-alias-denied", "I144-FFM-ABI-PASS", []))
    # Configured but never imported: the program runs and nothing else happens.
    plan.append(lambda: check_program(runner, "I144_UNUSED_NO_SESSION", install, "unused-provider.protos",
                  [provider], "I085-B-UNUSED-PASS", []))
    # Presence on CLASSPATH or in the working directory authorizes nothing, not even
    # together with a library grant: the provider itself was never loaded.
    shutil.copy2(provider, runner.work / provider.name)
    exposed = runner.environment({"CLASSPATH": str(provider)})
    plan.append(lambda: check_failure(runner, "I144_NO_IMPLICIT_AUTHORIZATION", install,
                  [*library_options(SQLITE_GRANT), str(CASES / "ffm-denied.protos")],
                  "i144-zero-discovery", "I144-FFM-DENIED-PASS", [], exposed))

    plan_i169(runner, install, provider, plan)


def plan_i169(runner: Runner, install: Path, provider: Path, plan: list) -> None:
    """Default-off native library allowlist: denial, CLI validation, and two libraries."""
    sqlite_program = str(CASES / "ffm-sqlite.protos")
    # No provider: a library grant alone discovers and enables nothing.
    plan.append(lambda: check_failure(runner, "I169_PROVIDER_ABSENT", install,
                  [*library_options(SQLITE_GRANT), sqlite_program],
                  "i169-provider-absent", "I144-FFM-SQLITE-PASS", []))
    # Provider present, empty allowlist: nothing is authorized, sqlite3 included.
    plan.append(lambda: check_ffm_program(runner, "I169_ALLOWLIST_DENIED", install,
                  "ffm-allowlist-denied.protos", provider, "FFM-ALLOWLIST-DENIED-PASS"))
    plan.append(lambda: check_failure(runner, "I169_ALLOWLIST_DENIED", install,
                  ["--foreign-provider-path", str(provider), sqlite_program],
                  "i169-empty-allowlist", "I144-FFM-SQLITE-PASS", []))
    # A different valid alias does not authorize sqlite3.
    plan.append(lambda: check_failure(runner, "I169_UNAUTHORIZED_ALIAS", install,
                  ["--foreign-provider-path", str(provider),
                   *library_options(("other=libsqlite3.so.0",)), sqlite_program],
                  "i169-unauthorized-alias", "I144-FFM-SQLITE-PASS", []))
    # An authorized target that does not exist is unavailable, never PASS.
    missing = runner.work / "absent" / "libsqlite3.so.0"
    plan.append(lambda: check_failure(runner, "I169_LIBRARY_UNAVAILABLE", install,
                  ["--foreign-provider-path", str(provider),
                   *library_options((f"sqlite3={missing}",)), sqlite_program],
                  "i169-library-unavailable", "I144-FFM-SQLITE-PASS", []))

    # Declarative layouts need no library grant at all.
    plan.append(lambda: check_ffm_program(runner, "I174_FFM_LAYOUTS", install,
                  "ffm-layouts.protos", provider, "FFM-LAYOUTS-PASS"))

    compiler = next((found for found in (shutil.which(name) for name in ("cc", "gcc", "clang"))
                     if found is not None), None)
    if compiler is None:
        for key in ("I169_TWO_LIBRARIES", "I174_FFM_SCALAR_LAYOUT_ABI", "I174_FFM_ROOT_ACTOR",
                    "I174_FFM_POINTERS"):
            runner.record(key, UNSUPPORTED, "no C compiler (cc, gcc, or clang) on PATH")
        return
    native = runner.work / "i169-native"
    native.mkdir()
    libraries = {}
    for alias, source in (("first", "ffi_test.c"), ("second", "ffi_second.c")):
        target = native / f"lib{Path(source).stem}.so"
        completed = runner.run(
            f"i169-cc-{alias}",
            [compiler, "-shared", "-fPIC", "-O2", "-std=c11", "-Wall", "-Werror",
             "-o", str(target), str(CASES / "native" / source)],
            runner.work, runner.environment())
        if completed.returncode != 0:
            for key in ("I169_TWO_LIBRARIES", "I174_FFM_SCALAR_LAYOUT_ABI", "I174_FFM_ROOT_ACTOR",
                        "I174_FFM_POINTERS"):
                runner.record(key, FAIL, f"{source} did not compile")
            return
        libraries[alias] = target
    marker_file = native / "second-marker.txt"
    grants = tuple(f"{alias}={path}" for alias, path in libraries.items())

    def two_libraries() -> None:
        check_ffm_program(runner, "I169_TWO_LIBRARIES", install, "ffm-library-allowlist.protos",
                          provider, "FFM-LIBRARY-ALLOWLIST-PASS", grants, (str(marker_file),))
        # The native write happened under OS process authority only; verified by the host.
        try:
            written = marker_file.read_text(encoding="utf-8")
        except OSError:
            written = None
        if written != SECOND_MARKER_TEXT:
            runner.record("I169_TWO_LIBRARIES", FAIL, f"native marker file content {written!r}")

    plan.append(two_libraries)
    # Fixed-width scalars and C-measured layouts; the u8(u8) shape is also called
    # through the second, independently authorized library.
    plan.append(lambda: check_ffm_program(runner, "I174_FFM_SCALAR_LAYOUT_ABI", install,
                  "ffm-scalar-layout-abi.protos", provider, "FFM-SCALAR-LAYOUT-ABI-PASS", grants))
    # Native FFM authority is RootActor-only; a real secondary Actor is refused.
    plan.append(lambda: check_ffm_program(runner, "I174_FFM_ROOT_ACTOR", install,
                  "ffm-root-actor.protos", provider, "FFM-ROOT-ACTOR-PASS", grants))
    # Typed native pointer capabilities, reusing the same compiled fixtures.
    plan.append(lambda: check_ffm_program(runner, "I174_FFM_POINTERS", install,
                  "ffm-pointers.protos", provider, "FFM-POINTERS-PASS", grants))


def plan_i169_cli(runner: Runner, install: Path, plan: list) -> None:
    """I169 launcher parsing and early rejection; no provider is loaded and no library opened."""
    sqlite_program = str(CASES / "ffm-sqlite.protos")
    # The parser only checks that a provider path exists; it is never loaded here.
    placeholder = runner.work / "i169-placeholder-provider.jar"
    placeholder.write_bytes(b"")
    provider = str(placeholder)
    # Malformed, relative, invalid-alias and duplicate grants are usage errors (exit 2),
    # for file execution and for run/debug in both option positions.
    for index, grants in enumerate((
        ("--allow-library",),
        ("--allow-library", "sqlite3"),
        ("--allow-library", "=libsqlite3.so.0"),
        ("--allow-library", "sqlite3="),
        ("--allow-library", "libsqlite3.so.0=libsqlite3.so.0"),
        ("--allow-library", "lib/sqlite3=libsqlite3.so.0"),
        ("--allow-library", "sqlite3=lib/libsqlite3.so.0"),
        ("--allow-library", "sqlite3=libsqlite3.so.0", "--allow-library", "sqlite3=libsqlite3.so.0"),
    )):
        plan.append(lambda index=index, grants=grants: check_usage(
            runner, "I169_CLI_INVALID_OPTIONS", install,
            ["--foreign-provider-path", provider, *grants, sqlite_program],
            f"i169-cli-invalid-{index}"))
        if grants != ("--allow-library",):
            plan.append(lambda index=index, grants=grants: check_usage(
                runner, "I169_CLI_INVALID_OPTIONS", install,
                ["run", "--foreign-provider-path", provider, *grants, "Main"],
                f"i169-cli-invalid-run-{index}"))
            plan.append(lambda index=index, grants=grants: check_usage(
                runner, "I169_CLI_INVALID_OPTIONS", install,
                ["--foreign-provider-path", provider, *grants, "debug", sqlite_program],
                f"i169-cli-invalid-debug-{index}"))
    # A duplicate alias split across the two run/debug option positions.
    for index, subcommand, entry in ((0, "run", "Main"), (1, "debug", sqlite_program)):
        plan.append(lambda index=index, subcommand=subcommand, entry=entry: check_usage(
            runner, "I169_CLI_INVALID_OPTIONS", install,
            ["--allow-library", "sqlite3=libsqlite3.so.0", subcommand,
             "--allow-library", "sqlite3=/usr/lib/libsqlite3.so.0", entry],
            f"i169-cli-invalid-split-{index}"))
    # A nonexistent provider path is rejected before any host exists.
    plan.append(lambda: check_usage(
        runner, "I169_CLI_INVALID_OPTIONS", install,
        ["run", "--foreign-provider-path", str(runner.work / "absent-provider.jar"), "Main"],
        "i169-cli-invalid-provider-path"))
    # Bundled Tools never receive native library authority: an authorization before the
    # subcommand is rejected by the outer launcher parser, before any Tool starts.
    for tool in ("test", "package", "format", "lint", "language-server"):
        plan.append(lambda tool=tool: check_usage(
            runner, "I169_TOOLS_DENIED", install,
            ["--allow-library", "sqlite3=libsqlite3.so.0", tool], f"i169-tools-denied-{tool}"))


def check_usage(runner: Runner, key: str, install: Path, command_tail: list[str], label: str) -> None:
    """A CLI usage error: exit 2, nothing on stdout, a diagnostic on stderr.

    A timeout is a FAIL naming the command, never a PASS and never an aborted campaign.
    """
    command = [str(install / "bin" / "protos"), *command_tail]
    try:
        completed = runner.run(label, command, runner.work, runner.environment(), USAGE_TIMEOUT)
    except subprocess.TimeoutExpired:
        problem = f"timeout after {USAGE_TIMEOUT}s: {' '.join(command_tail)}"
        report(label, [], [problem])
        runner.record(key, FAIL, f"{label}: {problem}")
        return
    problems = []
    if completed.returncode != 2:
        problems.append(f"exit {completed.returncode}, expected 2")
    if completed.stdout:
        problems.append(f"stdout {completed.stdout!r}")
    if not completed.stderr.strip():
        problems.append("no diagnostic on stderr")
    report(label, [], problems)
    runner.record(key, FAIL if problems else PASS, f"{label}: {'; '.join(problems)}" if problems else None)


class Progress:
    """Emits the Test Tool progress lines (protos/tools/test/Progress.protos) for one phase."""

    def __init__(self, phase: str, total: int) -> None:
        self.phase = phase
        self.total = total
        self.completed = 0
        self.failed = 0
        self.step = max(1, -(-total // 10))
        self.next = self.step
        self.emit(f"0/{total}")

    def emit(self, text: str) -> None:
        print(f"[{self.phase}] {text}", flush=True)

    def complete(self, reference: str, failed: bool) -> None:
        self.completed += 1
        if failed:
            self.failed += 1
            self.emit(f"FAIL {reference}")
        if self.next <= self.completed < self.total:
            self.emit(f"{self.completed}/{self.total}")
            while self.next <= self.completed:
                self.next += self.step

    def finish(self) -> None:
        outcome = "passed" if self.failed == 0 else f"failed={self.failed}"
        self.emit(f"{self.completed}/{self.total} {outcome}")


PROGRESS: Progress | None = None


def report(program: str, jars: list[Path], problems: list[str]) -> None:
    providers = ",".join(jar.stem.removesuffix("-provider") for jar in jars) or "none"
    if PROGRESS is not None:
        PROGRESS.complete(f"foreign-provider/{program} providers={providers}", bool(problems))


def check_program(
    runner: Runner,
    key: str,
    install: Path,
    program: str,
    jars: list[Path],
    marker: str,
    expected_traces: list[str],
    options: list[str] | None = None,
    arguments: list[str] | None = None,
    report_progress: bool = True,
    evidence_suffix: str = "",
) -> None:
    command = [str(install / "bin" / "protos")]
    if jars:
        command += ["--foreign-provider-path", os.pathsep.join(str(j) for j in jars)]
    command += options or []
    command.append(str(CASES / program))
    command += arguments or []
    label = f"{key.lower()}-{Path(program).stem}{evidence_suffix}"
    completed = runner.run(label, command, runner.work, runner.environment())
    problems = []
    if completed.returncode != 0:
        problems.append(f"exit {completed.returncode}")
    if completed.stdout != marker + "\n":
        problems.append(f"stdout {completed.stdout!r}")
    if sorted(traces(completed.stderr)) != sorted(expected_traces):
        problems.append(f"traces {traces(completed.stderr)} != {expected_traces}")
    if other_stderr(completed.stderr):
        problems.append("unexpected stderr")
    if report_progress:
        report(program, jars, problems)
    runner.record(key, FAIL if problems else PASS, f"{label}: {'; '.join(problems)}" if problems else None)


def check_failure(
    runner: Runner,
    key: str,
    install: Path,
    command_tail: list[str],
    label: str,
    forbidden_stdout: str,
    expected_traces: list[str],
    env: dict[str, str] | None = None,
) -> None:
    command = [str(install / "bin" / "protos"), *command_tail]
    completed = runner.run(label, command, runner.work, env or runner.environment())
    problems = []
    if completed.returncode == 0:
        problems.append("exit 0 for a failing program")
    if forbidden_stdout in completed.stdout:
        problems.append("success marker printed")
    if traces(completed.stderr) != expected_traces:
        problems.append(f"traces {traces(completed.stderr)} != {expected_traces}")
    if not completed.stderr.strip():
        problems.append("no diagnostic on stderr")
    report(Path(command_tail[-1]).name, [], problems)
    runner.record(key, FAIL if problems else PASS, f"{label}: {'; '.join(problems)}" if problems else None)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--archive", help="portable ZIP (default: the one matching the packaged project version)")
    parser.add_argument("--keep", action="store_true", help="keep the work directory even on success")
    args = parser.parse_args()

    archive = locate_archive(args.archive)
    java_home = Path(os.environ["JAVA_HOME"]) if os.environ.get("JAVA_HOME") else None
    work = Path(tempfile.mkdtemp(prefix="protos-i085b-"))
    runner = Runner(work, java_home)

    install = extract(archive, work / "install")
    protos_jar = install / "lib" / "protos.jar"
    before = snapshot(install)
    plugins = work / "plugins"
    plugins.mkdir()

    try:
        with zipfile.ZipFile(protos_jar) as packaged:
            product_major = classfile_major(
                packaged.read(
                    "com/guillermomolina/protos/cli/ProtosCli.class"
                )
            )
        runner.record(
            "PROTOS_CLASSFILE_BASELINE",
            PASS if product_major == 69 else FAIL,
            f"Protos CLI classfile major={product_major}, expected=69",
        )
    except (OSError, KeyError, ValueError, zipfile.BadZipFile) as failure:
        runner.record(
            "PROTOS_CLASSFILE_BASELINE",
            FAIL,
            f"Protos classfile inspection failed: {failure}",
        )

    javac = java_major([runner.tool("javac"), "--version"])
    java = java_major([runner.tool("java"), "-version"])
    native_supported = (
        platform.system() == "Linux"
        and platform.machine() in ("x86_64", "amd64")
        and javac == 25
        and java == 25
    )
    native_reason = (
        f"requires Linux x86_64 with JDK 25; found {platform.system()} "
        f"{platform.machine()} javac={javac} java={java}"
    )

    launch = runner.run(
        "portable-jvm-launch",
        [str(install / "bin" / "protos"), "-e", 'print("I085-B-LAUNCH")'],
        work,
        runner.environment(),
    )
    runner.record(
        "PORTABLE_JVM_LAUNCH",
        PASS if launch.returncode == 0 and launch.stdout == "I085-B-LAUNCH\n" else FAIL,
    )

    plan: list = []
    opaque = build_plugin(runner, "opaque", 21, protos_jar, plugins)
    libc = build_plugin(runner, "libc", 25, protos_jar, plugins) if native_supported else None
    java_jar = build_plugin(runner, "java", 21, protos_jar, plugins)

    if opaque is not None:
        plan.append(lambda: check_program(runner, "PROTOS_OPAQUE_PROVIDER", install, "opaque-provider.protos",
                      [opaque], "I085-B-OPAQUE-PASS",
                      [f"provider-loaded {OPAQUE}", f"session-open {OPAQUE}"]))
    else:
        runner.record("PROTOS_OPAQUE_PROVIDER", FAIL, "plugin JAR not built")

    if not native_supported:
        runner.record("PROTOS_NATIVE_FFM_PROVIDER", UNSUPPORTED, native_reason)
        runner.record("PROTOS_MULTIPLE_PROVIDERS", UNSUPPORTED, native_reason)
    elif libc is None or opaque is None:
        runner.record("PROTOS_NATIVE_FFM_PROVIDER", FAIL, "plugin JAR not built")
        runner.record("PROTOS_MULTIPLE_PROVIDERS", FAIL, "plugin JAR not built")
    else:
        plan.append(lambda: check_program(runner, "PROTOS_NATIVE_FFM_PROVIDER", install, "native-ffm-provider.protos",
                      [libc], "I085-B-FFM-PASS",
                      [f"provider-loaded {LIBC}", f"session-open {LIBC}"]))
        plan.append(lambda: check_program(runner, "PROTOS_MULTIPLE_PROVIDERS", install, "multiple-providers.protos",
                      [opaque, libc], "I085-B-MULTIPLE-PASS",
                      [f"provider-loaded {OPAQUE}", f"provider-loaded {LIBC}",
                       f"session-open {OPAQUE}", f"session-open {LIBC}"]))

    java_session = [f"provider-loaded {JAVA}", f"session-open {JAVA}",
                    "jdk-origin java.base java.base"]
    for key, program, marker in (
        ("PROTOS_JAVA_MATH_PROVIDER", "java-math.protos", "I085-B-JAVA-MATH-PASS"),
        ("PROTOS_JAVA_DATE_PROVIDER", "java-date.protos", "I085-B-JAVA-DATE-PASS"),
    ):
        if java_jar is None:
            runner.record(key, FAIL, "plugin JAR not built")
        else:
            plan.append(lambda key=key, program=program, marker=marker: check_program(
                runner, key, install, program, [java_jar], marker, java_session))

    ids = {opaque: OPAQUE, libc: LIBC, java_jar: JAVA}
    configured = [jar for jar in (opaque, libc, java_jar) if jar is not None]
    loaded = [f"provider-loaded {ids[jar]}" for jar in configured]

    # The handled form: the Error is observed inside Protos.
    plan.append(lambda: check_program(runner, "NO_PROVIDER_NEGATIVE", install, "missing-provider.protos",
                  [], "I085-B-MISSING-PASS", []))
    if configured:
        plan.append(lambda: check_program(runner, "NO_PROVIDER_NEGATIVE", install, "missing-provider.protos",
                      configured, "I085-B-MISSING-PASS", loaded))
    # The unhandled form must fail the process, never report false success.
    plan.append(lambda: check_failure(runner, "NO_PROVIDER_NEGATIVE", install,
                  [str(CASES / "missing-provider-direct.protos")], "no-provider-direct",
                  "I085-B-MISSING-DIRECT-UNREACHABLE", []))

    # Without --foreign-provider-path nothing is discovered, even when the plugin
    # JARs sit on CLASSPATH and in the working directory.
    if configured:
        for jar in configured:
            shutil.copy2(jar, work / jar.name)
        exposed = runner.environment({"CLASSPATH": os.pathsep.join(str(j) for j in configured)})
        plan.append(lambda: check_failure(runner, "DEFAULT_ZERO_DISCOVERY", install,
                      [str(CASES / "opaque-provider.protos")], "zero-discovery-opaque",
                      "I085-B-OPAQUE-PASS", [], exposed))
        if libc is not None:
            plan.append(lambda: check_failure(runner, "DEFAULT_ZERO_DISCOVERY", install,
                          [str(CASES / "native-ffm-provider.protos")], "zero-discovery-libc",
                          "I085-B-FFM-PASS", [], exposed))
        if java_jar is not None:
            for program in ("java-math", "java-date"):
                plan.append(lambda program=program: check_failure(runner, "DEFAULT_ZERO_DISCOVERY", install,
                              [str(CASES / f"{program}.protos")], f"zero-discovery-{program}",
                              "-PASS", [], exposed))
        plan.append(lambda: check_program(runner, "UNUSED_PROVIDER_NO_SESSION", install, "unused-provider.protos",
                      configured, "I085-B-UNUSED-PASS", loaded))
    else:
        runner.record("DEFAULT_ZERO_DISCOVERY", FAIL, "no plugin JAR built")
        runner.record("UNUSED_PROVIDER_NO_SESSION", FAIL, "no plugin JAR built")

    # LIB029_MISSING loads no provider or library and does not depend on the I169 launcher.
    plan.append(lambda: check_program(runner, "LIB029_MISSING", install, "sqlite-missing.protos",
                                     [], "LIB029-MISSING-PASS", []))
    if check_i169_baseline(runner, install):
        plan_i169_cli(runner, install, plan)
        plan_i144(runner, install, protos_jar, plugins, native_supported, native_reason, plan)
    else:
        # A stale archive cannot validate the current launcher: nothing native is attempted.
        for key in (*native_keys(), "I169_CLI_INVALID_OPTIONS", "I169_TOOLS_DENIED"):
            runner.record(key, FAIL, "not run: I169 distribution baseline failed")

    global PROGRESS
    PROGRESS = Progress("foreign-provider", len(plan))
    for run in plan:
        run()
    PROGRESS.finish()

    after = snapshot(install)
    changed = sorted(set(before.items()) ^ set(after.items()))
    runner.record("DISTRIBUTION_UNMODIFIED", FAIL if changed else PASS,
                  f"changed: {sorted({name for name, _ in changed})}" if changed else None)

    print(f"archive={archive}")
    for key in RESULT_KEYS:
        status = runner.results.get(key, FAIL)
        print(f"{key}={status}")
        for note in runner.notes.get(key, []):
            print(f"  {note}")
    failed = any(runner.results.get(key, FAIL) == FAIL for key in RESULT_KEYS)
    if failed or args.keep:
        print(f"evidence={runner.evidence}")
    else:
        shutil.rmtree(work)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
