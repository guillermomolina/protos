#!/usr/bin/env python3
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


"""Shared packaged-JVM launch and compilation-trace parsing for Truffle checks.

Used by the TEST009-D generated-dispatch BCI compilation check and the
TEST009-F strict Truffle compilation gate. It resolves the packaged checkout
JAR and runtime plane exactly as bin/protos does in checkout mode, runs the
Protos CLI on the JVM (never the Native binary) so that polyglot system
properties govern the real runtime, and parses the engine compilation trace.
"""

from __future__ import print_function

import os
import subprocess
import time
from pathlib import Path
from typing import Callable, List, Optional, Sequence, Tuple

JVM_OPTIONS = ("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")
MAIN_CLASS = "com.guillermomolina.protos.cli.ProtosCli"


class CheckError(Exception):
    pass


def packaged_classpath(root: Path) -> str:
    """The checkout-mode classpath; fail-closed when the package or runtime plane is incomplete."""
    properties = root / "target" / "maven-archiver" / "pom.properties"
    if not properties.is_file():
        raise CheckError("Maven build metadata %s not found (run mvn package)" % properties)
    values = dict(line.split("=", 1) for line in properties.read_text(encoding="utf-8").splitlines()
                  if "=" in line and not line.startswith("#"))
    jar = root / "target" / ("%s-%s.jar" % (values.get("artifactId", ""), values.get("version", "")))
    if not jar.is_file():
        raise CheckError("packaged jar %s not found (run mvn package)" % jar)
    runtime = root / "target" / "runtime"
    if not list(runtime.glob("*truffle-runtime-*.jar")) or not list(runtime.glob("*truffle-compiler-*.jar")):
        raise CheckError("runtime plane %s lacks the Truffle runtime/compiler jars (run mvn package)" % runtime)
    return "%s%s%s" % (jar, os.pathsep, runtime / "*")


def cli_command(root: Path, java: str, engine_options: Sequence[str], cli_arguments: Sequence[str]) -> List[str]:
    return [java] + list(JVM_OPTIONS) + list(engine_options) + [
        "-cp", packaged_classpath(root), MAIN_CLASS] + list(cli_arguments)


def select_java(explicit: Optional[str]) -> str:
    return explicit or os.environ.get("PROTOS_JAVA") or (
        str(Path(os.environ["JAVA_HOME"]) / "bin" / "java") if os.environ.get("JAVA_HOME") else "java")


def run_cli(root: Path, command: Sequence[str], timeout: int) -> Tuple[int, str, float]:
    """Runs with PROTOS_HOME=root, stderr merged; subprocess.TimeoutExpired propagates."""
    environment = dict(os.environ)
    environment["PROTOS_HOME"] = str(root)
    started = time.monotonic()
    completed = subprocess.run(list(command), cwd=str(root), env=environment, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, timeout=timeout, check=False)
    elapsed = time.monotonic() - started
    return completed.returncode, completed.stdout.decode("utf-8", errors="replace"), elapsed


def is_failure_line(line: str) -> bool:
    """An engine-trace permanent failure line, not arbitrary text mentioning the words."""
    return line.lstrip().startswith("[engine] opt failed")


def failure_records(output: str, starts_record: Callable[[str], bool] = lambda line: "opt failed" in line
                    ) -> List[str]:
    """Each permanent failure printed by CompilationFailureAction=Print/ExitVM, with its trace."""
    lines = output.splitlines()
    records, current = [], None
    for line in lines:
        if starts_record(line):
            if current is not None:
                records.append("\n".join(current))
            current = [line]
        elif current is not None:
            if line.startswith("[engine]") or len(current) >= 400:
                records.append("\n".join(current))
                current = None
            else:
                current.append(line)
    if current is not None:
        records.append("\n".join(current))
    return records


def is_done_line(line: str) -> bool:
    return line.lstrip().startswith("[engine] opt done")


def compilations_done(output: str, is_done: Callable[[str], bool] = lambda line: "opt done" in line) -> int:
    return sum(1 for line in output.splitlines() if is_done(line))
