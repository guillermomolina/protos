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
authorization. The CLI has no provider-option channel, so the SQLite-
independent ABI fixture (``native/ffi_test.c`` through ``ffm-abi.protos``) is
run by the Java test host ``ProtosI144FfmFixtureAbiTest``, which selects the
fixture library with ``withProviderOptions``. Missing platform support or
shared SQLite is reported as UNSUPPORTED, never as PASS. The I144 tutorials
and examples (``protos/tutorials``, ``protos/examples/ffi``) run from their
real paths against the same JAR; they validate by assertion and must exit 0
with empty stdout and stderr.
"""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tempfile
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
LIB029_LEARNING = (
    ("LIB029_EXAMPLE", ROOT / "protos/examples/ffi/sqlite-library.protos"),
    ("LIB029_TUTORIAL", ROOT / "protos/tutorials/12-system-resources/05-sqlite-protos-first.protos"),
)
SERVICE = "META-INF/services/com.guillermomolina.protos.spi.foreign.ProtosForeignProviderPlugin"
TRACE = "I085-B-TRACE "
OPAQUE = "i085b-opaque"
LIBC = "i085b-libc"
JAVA = "i085b-java"
TIMEOUT = 120

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
    *(key for key, _, _ in LIB029_CASES),
    *(key for key, _ in LIB029_LEARNING),
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

    def run(self, label: str, command: list[str], cwd: Path, env: dict[str, str]):
        completed = subprocess.run(
            command, cwd=cwd, env=env, capture_output=True, text=True, timeout=TIMEOUT
        )
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
) -> None:
    """Runs one I144 program with exactly the FFM provider JAR configured."""
    check_program(runner, key, install, program, [jar], marker, [])


def check_ffm_silent(runner: Runner, key: str, install: Path, source: Path, jar: Path) -> None:
    """Runs one assertion-validated I144 program: exit 0, no stdout, no stderr."""
    command = [str(install / "bin" / "protos"), "--foreign-provider-path", str(jar), str(source)]
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


def plan_i144(runner: Runner, install: Path, protos_jar: Path, plugins: Path,
              native_supported: bool, native_reason: str, plan: list) -> None:
    plan.append(lambda: check_program(runner, "LIB029_MISSING", install, "sqlite-missing.protos",
                                     [], "LIB029-MISSING-PASS", []))
    keys = [key for key in RESULT_KEYS
            if key.startswith("I144_") or (key.startswith("LIB029_") and key != "LIB029_MISSING")]
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
                    *(key for key, _, _ in LIB029_CASES), *(key for key, _ in LIB029_LEARNING)):
            runner.record(key, UNSUPPORTED, f"no shared libsqlite3.so.0 in {SQLITE_CANDIDATES}")
    else:
        plan.append(lambda: check_ffm_program(runner, "I144_FFM_SQLITE", install, "ffm-sqlite.protos",
                      provider, "I144-FFM-SQLITE-PASS"))
        plan.append(lambda: check_ffm_program(runner, "I144_FFM_DENIED", install, "ffm-denied.protos",
                      provider, "I144-FFM-DENIED-PASS"))
        for key, source in I144_LEARNING:
            plan.append(lambda key=key, source=source: check_ffm_silent(runner, key, install, source, provider))

        for key, program, marker in LIB029_CASES:
            plan.append(lambda key=key, program=program, marker=marker: check_ffm_program(
                runner, key, install, program, provider, marker))
        for key, source in LIB029_LEARNING:
            plan.append(lambda key=key, source=source: check_ffm_silent(
                runner, key, install, source, provider))

    # The distributable provider knows only ffi:sqlite3: the fixture program
    # (ffi-test scheme, ffi_test alias) must fail without any native access.
    plan.append(lambda: check_failure(runner, "I144_ARBITRARY_ALIAS_DENIED", install,
                  ["--foreign-provider-path", str(provider), str(CASES / "ffm-abi.protos")],
                  "i144-alias-denied", "I144-FFM-ABI-PASS", []))
    # Configured but never imported: the program runs and nothing else happens.
    plan.append(lambda: check_program(runner, "I144_UNUSED_NO_SESSION", install, "unused-provider.protos",
                  [provider], "I085-B-UNUSED-PASS", []))
    # Presence on CLASSPATH or in the working directory authorizes nothing.
    shutil.copy2(provider, runner.work / provider.name)
    exposed = runner.environment({"CLASSPATH": str(provider)})
    plan.append(lambda: check_failure(runner, "I144_NO_IMPLICIT_AUTHORIZATION", install,
                  [str(CASES / "ffm-denied.protos")], "i144-zero-discovery",
                  "I144-FFM-DENIED-PASS", [], exposed))


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
) -> None:
    command = [str(install / "bin" / "protos")]
    if jars:
        command += ["--foreign-provider-path", os.pathsep.join(str(j) for j in jars)]
    command.append(str(CASES / program))
    label = f"{key.lower()}-{Path(program).stem}"
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

    plan_i144(runner, install, protos_jar, plugins, native_supported, native_reason, plan)

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
