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


"""TEST009-D dynamic check: generated cached-dispatch BCI under real compilation.

The static guard (tools/java_generated_bytecode_bci_pe_guard.py) proves every
generated cached-dispatch BCI transition except EXCEPTION_HANDLER_TABLE
structurally only: the generated handleException selects its handler with a
non-exploded loop, so whether partial evaluation folds the resulting bci to
a constant is not statically demonstrable. This check discharges that premise
by running the packaged Protos CLI on one small guest program with real,
synchronous Truffle compilation:

  -Dpolyglot.engine.AllowExperimentalOptions=true
  -Dpolyglot.engine.CompileImmediately=true
  -Dpolyglot.engine.BackgroundCompilation=false
  -Dpolyglot.engine.CompilationFailureAction=Print
  -Dpolyglot.engine.TraceCompilation=true   (evidence that compilation ran)

The program loops (BRANCH_BACKWARD), branches, signals an Error caught by
Error.handle (ProtosBytecodeRootNode TryCatch handler-table re-entry), runs
ensure cleanup (TryFinally), and performs a non-local return
(control-flow-exception path) inside ordinary closures (ProtosSemantic
BytecodeRootNode). It must produce the exact expected output, at least one
compilation must complete, and no permanent compilation failure may carry the
partialEvaluationConstant assertion. Other permanent failures are reported
but belong to the general dynamic-compilation family, not to TEST009-D.

On the JVM the generated roots use CachedBytecodeNode; the
CachedBytecodeNodeTailCall tier is selected only under TruffleOptions.AOT
(native image) and is covered by the static guard's identical structural
proof of its handleException.
"""

from __future__ import print_function

import argparse
import json
import os
import re
import subprocess
import sys
import time
from collections import OrderedDict
from pathlib import Path
from typing import List, Optional

ENGINE_OPTIONS = (
    "-Dpolyglot.engine.AllowExperimentalOptions=true",
    "-Dpolyglot.engine.CompileImmediately=true",
    "-Dpolyglot.engine.BackgroundCompilation=false",
    "-Dpolyglot.engine.CompilationFailureAction=Print",
    "-Dpolyglot.engine.TraceCompilation=true",
)
JVM_OPTIONS = ("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")
MAIN_CLASS = "com.guillermomolina.protos.cli.ProtosCli"

PROGRAM = """\
count: 0
work: (i) => {
    caught: Error.handle(
        () => {
            (i < 100).ifTrue() { Error().signal() }
            i
        },
        (error) => { 0 - 1 }
    )
    acc: 0
    (() => {
        acc = acc + caught
    }).ensure(() => {
        count = count + 1
    })
    k: 0
    (() => { k < 3 }).whileTrue(() => {
        k = k + 1
    })
    firstK: () => {
        (() => { true }).whileTrue(() => {
            ^k
        })
        0
    }
    acc + firstK()
}
total: 0
n: 0
(() => { n < 200 }).whileTrue(() => {
    total = total + work(n)
    n = n + 1
})
print(total)
print(count)
"""
# ^k is a non-local return from the home Closure work, so each call yields k = 3 after
# Error.handle and ensure ran (200 x 3); ensure cleanup runs once per call. The output is
# identical to the interpreter-only run of the same program.
EXPECTED_OUTPUT = ("600", "200")

PE_CONSTANT_MARKERS = ("partialevaluationconstant", "reduce value to a constant")
GENERATED_DISPATCH_MARKERS = ("BytecodeRootNodeGen",)
GENERATED_ROOTS = ("ProtosBytecodeRootNodeGen", "ProtosSemanticBytecodeRootNodeGen")

GENERATED_BCI = "GENERATED_BCI_PE_CONSTANT"
OTHER_PE_CONSTANT = "OTHER_PE_CONSTANT"
OTHER_PERMANENT = "OTHER_PERMANENT_FAILURE"


class CheckError(Exception):
    pass


def classify_failure(record: str) -> str:
    lowered = record.lower()
    if any(marker in lowered for marker in PE_CONSTANT_MARKERS):
        if any(marker in record for marker in GENERATED_DISPATCH_MARKERS) and "continueAt" in record:
            return GENERATED_BCI
        return OTHER_PE_CONSTANT
    return OTHER_PERMANENT


def failure_records(output: str) -> List[str]:
    """Each permanent failure printed by CompilationFailureAction=Print, with its trace."""
    lines = output.splitlines()
    records, current = [], None
    for line in lines:
        if "opt failed" in line:
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


def evaluate(output: str, exit_code: int) -> "OrderedDict[str, object]":
    records = failure_records(output)
    kinds = [classify_failure(record) for record in records]
    compilations = sum(1 for line in output.splitlines() if "opt done" in line)
    printed = [line.strip() for line in output.splitlines() if line.strip() in EXPECTED_OUTPUT]
    failures = []
    if exit_code != 0:
        failures.append("PROGRAM_EXIT_CODE %d" % exit_code)
    if printed != list(EXPECTED_OUTPUT):
        failures.append("SEMANTIC_RESULT expected output lines %s, found %s" % (list(EXPECTED_OUTPUT), printed))
    if compilations == 0:
        failures.append("NO_COMPILATION no 'opt done' trace: real compilation did not run")
    compiled_roots = OrderedDict((root, sum(1 for line in output.splitlines()
                                            if "opt done" in line and (" %s@" % root) in line))
                                 for root in GENERATED_ROOTS)
    for root, count in compiled_roots.items():
        if count == 0:
            failures.append("ROOT_NOT_COMPILED no successful compilation of a %s root" % root)
    for kind, record in zip(kinds, records):
        if kind in (GENERATED_BCI, OTHER_PE_CONSTANT):
            failures.append("%s %s" % (kind, record.splitlines()[0].strip()))
    result = OrderedDict()
    result["COMPILATIONS_DONE"] = compilations
    for root, count in compiled_roots.items():
        result["COMPILATIONS_DONE_" + root] = count
    result["PERMANENT_COMPILATION_FAILURES"] = len(records)
    result["GENERATED_BCI_COMPILATION_FAILURES"] = kinds.count(GENERATED_BCI)
    result["OTHER_PE_CONSTANT_FAILURES"] = kinds.count(OTHER_PE_CONSTANT)
    result["OTHER_PERMANENT_FAILURES"] = kinds.count(OTHER_PERMANENT)
    result["failures"] = failures
    result["records"] = records
    return result


def launcher_command(root: Path, java: str) -> List[str]:
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
    return [java] + list(JVM_OPTIONS) + list(ENGINE_OPTIONS) + [
        "-cp", "%s%s%s" % (jar, os.pathsep, runtime / "*"), MAIN_CLASS, "-e", PROGRAM]


def run(root: Path, java: str, timeout: int, report: Optional[Path], out=sys.stdout) -> int:
    try:
        command = launcher_command(root, java)
    except CheckError as error:
        print("generated-bytecode-bci-compilation-check: ERROR: %s" % error, file=out)
        return 1
    environment = dict(os.environ)
    environment["PROTOS_HOME"] = str(root)
    started = time.monotonic()
    try:
        completed = subprocess.run(command, cwd=str(root), env=environment, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, timeout=timeout, check=False)
    except subprocess.TimeoutExpired:
        print("generated-bytecode-bci-compilation-check: FAIL: timed out after %d s" % timeout, file=out)
        return 1
    elapsed = time.monotonic() - started
    output = completed.stdout.decode("utf-8", errors="replace")
    result = evaluate(output, completed.returncode)
    if report is not None:
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(OrderedDict([("elapsed_seconds", round(elapsed, 1)),
                                                  ("result", result), ("output", output)]), indent=2) + "\n",
                          encoding="utf-8")
    print("generated-bytecode-bci-compilation-check: real synchronous Truffle compilation of generated dispatch",
          file=out)
    print("RUN_SECONDS=%.1f" % elapsed, file=out)
    for key, value in result.items():
        if key not in ("failures", "records"):
            print("%s=%s" % (key, value), file=out)
    for record, kind in ((record, classify_failure(record)) for record in result["records"]):
        if kind == OTHER_PERMANENT:
            print("  reported (not TEST009-D): %s" % record.splitlines()[0].strip(), file=out)
    if result["failures"]:
        print("generated-bytecode-bci-compilation-check: FAIL", file=out)
        for failure in result["failures"]:
            print("  " + failure, file=out)
        for record, kind in ((record, classify_failure(record)) for record in result["records"]):
            if kind != OTHER_PERMANENT:
                print(record, file=out)
        if completed.returncode != 0 or not result["records"]:
            print("--- program output (tail) ---", file=out)
            print("\n".join(output.splitlines()[-60:]), file=out)
        return 1
    print("generated-bytecode-bci-compilation-check: PASS", file=out)
    return 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--java", default=None)
    parser.add_argument("--timeout", type=int, default=900)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args(argv)
    java = args.java or os.environ.get("PROTOS_JAVA") or (
        str(Path(os.environ["JAVA_HOME"]) / "bin" / "java") if os.environ.get("JAVA_HOME") else "java")
    return run(args.root.resolve(), java, args.timeout, args.report)


if __name__ == "__main__":
    sys.exit(main())
