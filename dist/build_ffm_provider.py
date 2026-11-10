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

"""I144: package the optional external FFM provider (providers/ffm) as an independent JAR.

Takes an already built portable JVM distribution (its ZIP or an extracted
installation), compiles ``providers/ffm/src/main/java`` with
``javac --release 25`` against the public SPI of that distribution's
``lib/protos.jar``, and writes ``protos-ffm-provider.jar`` (classes plus the
provider's ``META-INF/services`` descriptor) into ``--output`` only. The
distribution is read, never modified, and no provider class is added to
``lib/protos.jar``. Nothing is downloaded.

The JAR carries no authorization: which libraries it may load is decided by
each host at run time (``--foreign-provider-path`` plus provider options), so
the same JAR is reusable between hosts unchanged.
"""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROVIDER = ROOT / "providers" / "ffm" / "src" / "main"
SERVICE = "META-INF/services/com.guillermomolina.protos.spi.foreign.ProtosForeignProviderPlugin"
JAR_NAME = "protos-ffm-provider.jar"
RELEASE = 25
CLASSFILE_MAJOR = RELEASE + 44


class BuildError(Exception):
    """A precondition or build step failed; the message is safe to print."""


def javac_major(javac: str) -> int | None:
    try:
        completed = subprocess.run([javac, "--version"], capture_output=True, text=True, timeout=60)
    except OSError:
        return None
    match = re.search(r"javac (\d+)", completed.stdout + completed.stderr)
    return int(match.group(1)) if match else None


def protos_jar_from(distribution: Path, scratch: Path) -> Path:
    """Answers the distribution's lib/protos.jar, extracting it from a ZIP into scratch."""
    if distribution.is_dir():
        jar = distribution / "lib" / "protos.jar"
        if not jar.is_file():
            raise BuildError(f"no lib/protos.jar in installation {distribution}")
        return jar
    if not zipfile.is_zipfile(distribution):
        raise BuildError(f"not a portable distribution ZIP or directory: {distribution}")
    with zipfile.ZipFile(distribution) as archive:
        names = [n for n in archive.namelist() if n.endswith("/lib/protos.jar")]
        if len(names) != 1:
            raise BuildError(f"expected exactly one lib/protos.jar in {distribution}")
        target = scratch / "protos.jar"
        target.write_bytes(archive.read(names[0]))
        return target


def build_provider_jar(
    javac: str,
    protos_jar: Path,
    output_jar: Path,
    scratch: Path,
    log: Path | None = None,
    extra_sources: list[Path] | None = None,
    service: Path | None = None,
) -> Path:
    """Compiles providers/ffm against protos_jar and writes exactly output_jar.

    ``extra_sources`` and ``service`` exist only for test hosts that package a
    test-only registration of the same backend; the distributable build uses
    neither.
    """
    sources = sorted(str(p) for p in (PROVIDER / "java").rglob("*.java"))
    sources += [str(p) for p in (extra_sources or [])]
    service = service or PROVIDER / "resources" / SERVICE
    if not sources or not service.is_file():
        raise BuildError(f"provider sources missing under {PROVIDER}")
    classes = scratch / "ffm-classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    command = [javac, "--release", str(RELEASE), "-proc:none",
               "-classpath", str(protos_jar), "-d", str(classes), *sources]
    completed = subprocess.run(command, capture_output=True, text=True, timeout=300)
    if log is not None:
        log.write_text(
            f"$ {' '.join(command)}\nexit={completed.returncode}\n"
            f"--- stdout\n{completed.stdout}--- stderr\n{completed.stderr}",
            encoding="utf-8",
        )
    if completed.returncode != 0:
        raise BuildError(f"javac exit {completed.returncode}\n{completed.stderr}")
    compiled = sorted(classes.rglob("*.class"))
    if not compiled:
        raise BuildError("javac produced no classfiles")
    for path in compiled:
        header = path.read_bytes()[:8]
        if header[:4] != bytes.fromhex("CAFEBABE") or int.from_bytes(header[6:8], "big") != CLASSFILE_MAJOR:
            raise BuildError(f"{path.name}: unexpected classfile version")
    output_jar.parent.mkdir(parents=True, exist_ok=True)
    partial = output_jar.with_name(output_jar.name + ".partial")
    with zipfile.ZipFile(partial, "w", zipfile.ZIP_DEFLATED) as jar:
        for path in compiled:
            jar.write(path, path.relative_to(classes).as_posix())
        jar.write(service, SERVICE)
    partial.replace(output_jar)
    return output_jar


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("distribution", help="portable JVM ZIP or extracted installation directory")
    parser.add_argument("--output", required=True, help="directory that receives the provider JAR")
    args = parser.parse_args()

    try:
        javac = shutil.which("javac") if not _java_home() else str(_java_home() / "bin" / "javac")
        if javac is None or javac_major(javac) != RELEASE:
            raise BuildError(f"JDK {RELEASE} javac required (set JAVA_HOME)")
        distribution = Path(args.distribution).resolve()
        if not distribution.exists():
            raise BuildError(f"distribution not found: {distribution}")
        output = Path(args.output).resolve()
        with tempfile.TemporaryDirectory(prefix="protos-ffm-build-") as scratch_name:
            scratch = Path(scratch_name)
            protos_jar = protos_jar_from(distribution, scratch)
            jar = build_provider_jar(javac, protos_jar, output / JAR_NAME, scratch)
    except BuildError as failure:
        print(f"error: {failure}", file=sys.stderr)
        return 1
    print(jar)
    return 0


def _java_home() -> Path | None:
    import os

    home = os.environ.get("JAVA_HOME")
    return Path(home) if home else None


if __name__ == "__main__":
    sys.exit(main())
