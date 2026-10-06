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


"""TEST009-V single-root Truffle/Graal diagnostic: the standard targeted workflow.

Manual escalation after the strict gate (truffle_compilation_gate.py) is red. It
never changes product code or compiler policy; it only observes.

1. `catalog -- <protos test args>` runs the bounded Test Tool invocation once on
   the packaged JVM with only -Dprotos.diagnostic.stableRootIdentity=true (no
   compilation options) and lists every semantic root it lowered:

     ROOT selector=protos-root:<16 hex> kind=<TOP_LEVEL|CLOSURE> source=<uri> start=<n> length=<n> occurrences=<n>

   ordered by source, start, length and kind. A selector is a digest of kind,
   source URI and exact span only: no object address, run-local id or bytecode.
   A logical root lowered more than once (e.g. in several Contexts) keeps one row
   with its occurrence count; one selector reported with different metadata is a
   SELECTOR_COLLISION and fails the catalog.

2. `diagnose --selector protos-root:<16 hex> [--expansion method|node|none]
   [--dump-level 1|2] -- <protos test args including --case <ref>>` compiles only
   that root (engine.CompileOnly=<selector>, CompileImmediately, synchronous,
   CompilationFailureAction=Print, TraceCompilation), with at most one
   per-compilation expansion view (TraceMethodExpansion or TraceNodeExpansion,
   never both, never the run-aggregate expansion statistics) and
   -Djdk.graal.Dump=Truffle:<level> into a fresh directory under target/. Open the
   retained .bgv in any IGV and inspect "After TruffleTier". The result is one of:

     TARGET_COMPILATION_SUCCEEDED
     TARGET_COMPILATION_FAILED_CODE_TOO_LARGE   valid evidence, not a tool failure
     TARGET_COMPILATION_FAILED_OTHER            valid evidence, not a tool failure
     TARGET_NOT_FOUND                           the selector never appeared in the trace
     TARGET_SELECTOR_AMBIGUOUS                  more than one distinct target name matched
     TOOL_ACQUISITION_FAILED                    bad input, launch/timeout/exit failure,
                                                semantic Case failure, or missing evidence

   Several lifecycle events (tiers, retries) of one target name are one target. A
   generated continuation root is named after its source root plus
   "(resume_bci=<n>)", so a root with continuations is reported as ambiguous with
   all matching names listed. The exit status is 0 only for the three
   TARGET_COMPILATION_* results.
"""

from __future__ import print_function

import argparse
import json
import re
import shutil
import subprocess
import sys
from collections import OrderedDict
from pathlib import Path
from typing import List, Optional, Sequence

sys.path.insert(0, str(Path(__file__).resolve().parent))

from truffle_compilation_gate import COMPILER, ENGINE, corpus_summary  # noqa: E402
from truffle_jvm_launch import CheckError, cli_command, failure_records, is_failure_line, run_cli, select_java  # noqa: E402,E501

STABLE_ROOT_IDENTITY = "-Dprotos.diagnostic.stableRootIdentity=true"
CATALOG_PREFIX = "[protos-root] "
CATALOG_FIELDS = ("selector", "kind", "source", "start", "length")
SELECTOR = re.compile(r"^protos-root:[0-9a-f]{16}$")
TARGET_NAME = re.compile(r"protos-root:[0-9a-f]{16}\[[^\]\s]*\](?:\(resume_bci=[^)\s]*\))?")
ENGINE_COMPILATION_EVENT = re.compile(r"^\s*\[engine\] opt \w+")
EXPANSION_TREE = "Expansion tree for "
CODE_TOO_LARGE_MARKER = "code is too large"

EXPANSION_OPTIONS = OrderedDict([
    ("method", (COMPILER + "TraceMethodExpansion=truffleTier",)),
    ("node", (COMPILER + "TraceNodeExpansion=truffleTier",)),
    ("none", ()),
])
DUMP_LEVELS = ("1", "2")
DEFAULT_EXPANSION = "method"
DEFAULT_DUMP_LEVEL = "1"
CASE_OPTION = "--case"
DIAGNOSTIC_DIR = "root-diagnostic"

SUCCEEDED = "TARGET_COMPILATION_SUCCEEDED"
CODE_TOO_LARGE = "TARGET_COMPILATION_FAILED_CODE_TOO_LARGE"
FAILED_OTHER = "TARGET_COMPILATION_FAILED_OTHER"
NOT_FOUND = "TARGET_NOT_FOUND"
AMBIGUOUS = "TARGET_SELECTOR_AMBIGUOUS"
ACQUISITION_FAILED = "TOOL_ACQUISITION_FAILED"
EVIDENCE_RESULTS = (SUCCEEDED, CODE_TOO_LARGE, FAILED_OTHER)

USAGE = ("usage: make diagnose-truffle-root TRUFFLE_ROOT_SELECTOR=protos-root:<16 hex> "
         "TRUFFLE_ROOT_TEST_ARGS='--case <ref>' [TRUFFLE_ROOT_EXPANSION=method|node|none] "
         "[TRUFFLE_ROOT_DUMP_LEVEL=1|2]; list selectors first with "
         "make truffle-root-catalog TRUFFLE_ROOT_TEST_ARGS='--case <ref>'")


def target_directory(root: Path, artifacts: Path) -> Path:
    """The artifact directory, required to stay inside <root>/target."""
    resolved = artifacts.resolve()
    try:
        resolved.relative_to((root / "target").resolve())
    except ValueError:
        raise CheckError("artifact directory %s is not under %s" % (resolved, root / "target"))
    return resolved


# ---------------------------------------------------------------------------- catalog

def parse_catalog(output: str) -> "OrderedDict[str, object]":
    """Deterministic root catalog from the [protos-root] lines; collisions are explicit problems."""
    roots, problems = OrderedDict(), []
    for line in output.splitlines():
        if not line.startswith(CATALOG_PREFIX):
            continue
        fields = OrderedDict()
        for token in line[len(CATALOG_PREFIX):].split():
            key, separator, value = token.partition("=")
            if separator:
                fields[key] = value
        if tuple(fields) != CATALOG_FIELDS or not SELECTOR.match(fields["selector"]) \
                or not fields["start"].isdigit() or not fields["length"].isdigit():
            problems.append("MALFORMED_CATALOG_LINE %s" % line.strip())
            continue
        metadata = OrderedDict([("selector", fields["selector"]), ("kind", fields["kind"]),
                                ("source", fields["source"]), ("start", int(fields["start"])),
                                ("length", int(fields["length"]))])
        known = roots.get(metadata["selector"])
        if known is None:
            metadata["occurrences"] = 1
            roots[metadata["selector"]] = metadata
        elif all(known[key] == metadata[key] for key in metadata):
            known["occurrences"] += 1
        else:
            problems.append("SELECTOR_COLLISION %s" % metadata["selector"])
    ordered = sorted(roots.values(), key=lambda entry: (entry["source"], entry["start"], entry["length"],
                                                        entry["kind"], entry["selector"]))
    return OrderedDict([("roots", ordered), ("problems", problems)])


def catalog(root: Path, java: str, test_arguments: Sequence[str], timeout: int, artifacts: Path,
            out=sys.stdout) -> int:
    print("truffle-root-catalog: stable semantic-root selectors (no compilation requested)", file=out)
    entry = OrderedDict([("options", [STABLE_ROOT_IDENTITY]), ("test_arguments", list(test_arguments))])
    output, failures, directory = "", [], None
    try:
        directory = target_directory(root, artifacts) / DIAGNOSTIC_DIR
        command = cli_command(root, java, (STABLE_ROOT_IDENTITY,), ["test"] + list(test_arguments))
        exit_code, output, _ = run_cli(root, command, timeout)
        if exit_code != 0:
            failures.append("EXIT_CODE %d" % exit_code)
    except CheckError as error:
        failures.append("ARTIFACT %s" % error)
    except subprocess.TimeoutExpired as expired:
        output = (expired.output or b"").decode("utf-8", errors="replace")
        failures.append("TIMEOUT after %d s" % timeout)
    except OSError as error:
        failures.append("LAUNCH %s" % error)
    parsed = parse_catalog(output)
    failures.extend(parsed["problems"])
    if not parsed["roots"] and not failures:
        failures.append("NO_SEMANTIC_ROOT no %sline in the output" % CATALOG_PREFIX)
    entry["roots"], entry["failures"] = parsed["roots"], failures
    entry["status"] = "FAIL" if failures else "PASS"
    if directory is not None:
        directory.mkdir(parents=True, exist_ok=True)
        (directory / "catalog.log").write_text(output, encoding="utf-8")
        report = directory / "catalog.json"
        report.write_text(json.dumps(entry, indent=2) + "\n", encoding="utf-8")
        print("TRUFFLE_ROOT_CATALOG_REPORT=%s" % report, file=out)
    for found in parsed["roots"]:
        print("ROOT " + " ".join("%s=%s" % (key, value) for key, value in found.items()), file=out)
    for failure in failures:
        print("  " + failure, file=out)
    print("TRUFFLE_ROOT_CATALOG_ROOTS=%d" % len(parsed["roots"]), file=out)
    print("TRUFFLE_ROOT_CATALOG=%s" % entry["status"], file=out)
    return 0 if entry["status"] == "PASS" else 1


# ---------------------------------------------------------------------------- single-root diagnose

def relative_to_root(root: Path, path: Path) -> str:
    return path.resolve().relative_to(root.resolve()).as_posix()


def diagnostic_options(selector: str, expansion: str, dump_level: str, dump_path: str) -> List[str]:
    """The exact single-root JVM options; no compiler limit, inlining or splitting option is touched."""
    return [
        STABLE_ROOT_IDENTITY,
        ENGINE + "AllowExperimentalOptions=true",
        ENGINE + "CompileOnly=" + selector,
        ENGINE + "CompileImmediately=true",
        ENGINE + "BackgroundCompilation=false",
        ENGINE + "CompilationFailureAction=Print",
        ENGINE + "TraceCompilation=true",
        # Observational only: never TreatPerformanceWarningsAsErrors here.
        COMPILER + "TracePerformanceWarnings=all",
    ] + list(EXPANSION_OPTIONS[expansion]) + [
        "-Djdk.graal.Dump=Truffle:" + dump_level,
        "-Djdk.graal.DumpPath=" + dump_path,
    ]


def input_problems(selector: str, expansion: str, dump_level: str, test_arguments: Sequence[str]) -> List[str]:
    problems = []
    if not SELECTOR.match(selector or ""):
        problems.append("INVALID_SELECTOR %r is not protos-root:<16 lowercase hex>" % selector)
    if expansion not in EXPANSION_OPTIONS:
        problems.append("INVALID_EXPANSION %r is not one of %s" % (expansion, "|".join(EXPANSION_OPTIONS)))
    if dump_level not in DUMP_LEVELS:
        problems.append("INVALID_DUMP_LEVEL %r is not one of %s" % (dump_level, "|".join(DUMP_LEVELS)))
    cases = [argument for argument in test_arguments
             if argument == CASE_OPTION or argument.startswith(CASE_OPTION + "=")]
    if len(cases) != 1:
        problems.append("CASE_SELECTION the Test Tool arguments must select exactly one %s <ref>" % CASE_OPTION)
    return problems


def bgv_files(dump_directory: Path) -> List[str]:
    return sorted(str(path) for path in dump_directory.rglob("*.bgv")) if dump_directory.is_dir() else []


def classify(output: str, exit_code: Optional[int], selector: str, expansion: str,
             bgv: Sequence[str]) -> "OrderedDict[str, object]":
    """Fail-closed classification of one single-root diagnostic run from its retained evidence."""
    names = []
    for line in output.splitlines():
        if ENGINE_COMPILATION_EVENT.match(line):
            for name in TARGET_NAME.findall(line):
                if selector in name and name not in names:
                    names.append(name)
    records = [record for record in failure_records(output, is_failure_line)
               if selector in record.splitlines()[0]]
    done = any(line.lstrip().startswith("[engine] opt done") and selector in line for line in output.splitlines())
    expansion_trees = sum(1 for line in output.splitlines() if EXPANSION_TREE in line and selector in line)
    summary = corpus_summary(output)
    problems = []
    if exit_code != 0:
        problems.append("EXIT_CODE %s" % exit_code)
    if summary is None or summary[1] != 0 or summary[0] == 0:
        problems.append("SEMANTIC_CORPUS %s" % ("no Test Tool summary" if summary is None
                                                 else "%d passed, %d failed" % summary))
    if problems:
        result = ACQUISITION_FAILED
    elif not names:
        result = NOT_FOUND
    elif len(names) > 1:
        result = AMBIGUOUS
    elif any(CODE_TOO_LARGE_MARKER in record.lower() for record in records):
        result = CODE_TOO_LARGE
    elif records:
        result = FAILED_OTHER
    elif done:
        result = SUCCEEDED
    else:
        result = ACQUISITION_FAILED
        problems.append("NO_TERMINAL_COMPILATION_EVENT for %s" % names[0])
    if result in EVIDENCE_RESULTS:
        if not bgv:
            problems.append("MISSING_BGV no .bgv graph dump was produced")
        # A failure before the Truffle tier completes legitimately prints no expansion tree.
        if expansion != "none" and result != FAILED_OTHER and expansion_trees == 0:
            problems.append("MISSING_EXPANSION_TREE no '%s<target> after truffleTier' trace" % EXPANSION_TREE)
        if problems:
            result = ACQUISITION_FAILED
    evidence = OrderedDict()
    evidence["result"] = result
    evidence["target_names"] = names
    evidence["failure_records"] = records
    evidence["expansion_trees"] = expansion_trees
    evidence["bgv_files"] = list(bgv)
    evidence["problems"] = problems
    return evidence


def diagnose(root: Path, java: str, selector: str, expansion: str, dump_level: str,
             test_arguments: Sequence[str], timeout: int, artifacts: Path, out=sys.stdout) -> int:
    print("truffle-root-diagnostic: compile one selected semantic root (observation only)", file=out)
    report = OrderedDict([("selector", selector), ("expansion", expansion), ("dump_level", dump_level),
                          ("test_arguments", list(test_arguments))])
    problems = input_problems(selector, expansion, dump_level, test_arguments)
    directory = None
    if not problems:
        try:
            directory = target_directory(root, artifacts) / DIAGNOSTIC_DIR / selector.split(":", 1)[1]
        except CheckError as error:
            problems.append("ARTIFACT %s" % error)
    if problems:
        report.update(classify("", None, selector or "", expansion, []))
        report["result"], report["problems"] = ACQUISITION_FAILED, problems
        return finish(report, None, out, usage=True)
    dumps = directory / "graal_dumps"
    if directory.exists():
        shutil.rmtree(str(directory))
    directory.mkdir(parents=True)
    options = diagnostic_options(selector, expansion, dump_level, relative_to_root(root, dumps))
    report["options"] = options
    output, exit_code = "", None
    try:
        command = cli_command(root, java, options, ["test"] + list(test_arguments))
        exit_code, output, elapsed = run_cli(root, command, timeout)
        report["elapsed_seconds"] = round(elapsed, 1)
        launch_problem = None
    except CheckError as error:
        launch_problem = "ARTIFACT %s" % error
    except subprocess.TimeoutExpired as expired:
        output = (expired.output or b"").decode("utf-8", errors="replace")
        launch_problem = "TIMEOUT after %d s" % timeout
    except OSError as error:
        launch_problem = "LAUNCH %s" % error
    log = directory / "diagnostic.log"
    log.write_text(output, encoding="utf-8")
    report["log"], report["dump_path"] = str(log), str(dumps)
    report.update(classify(output, exit_code, selector, expansion, bgv_files(dumps)))
    if launch_problem is not None:
        report["result"] = ACQUISITION_FAILED
        report["problems"] = [launch_problem] + report["problems"]
    return finish(report, directory, out)


def finish(report, directory: Optional[Path], out, usage: bool = False) -> int:
    if directory is not None:
        path = directory / "report.json"
        path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print("TRUFFLE_ROOT_DIAGNOSTIC_REPORT=%s" % path, file=out)
    print("TRUFFLE_ROOT_SELECTOR=%s" % report["selector"], file=out)
    for name in report.get("target_names", []):
        print("TRUFFLE_ROOT_TARGET=%s" % name, file=out)
    for record in report.get("failure_records", []):
        print("  " + record.splitlines()[0].strip(), file=out)
    if "log" in report:
        print("TRUFFLE_ROOT_DIAGNOSTIC_LOG=%s" % report["log"], file=out)
    for bgv in report.get("bgv_files", []):
        print("TRUFFLE_ROOT_BGV=%s" % bgv, file=out)
    for problem in report["problems"]:
        print("  " + problem, file=out)
    if usage:
        print(USAGE, file=out)
    if report["result"] in EVIDENCE_RESULTS:
        print("Open the BGV in IGV and inspect the 'After TruffleTier' graph.", file=out)
    print("TRUFFLE_ROOT_DIAGNOSTIC=%s" % report["result"], file=out)
    return 0 if report["result"] in EVIDENCE_RESULTS else 1


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=("catalog", "diagnose"))
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--java", default=None)
    parser.add_argument("--timeout", type=int, default=3600, help="JVM timeout in seconds")
    parser.add_argument("--artifacts", type=Path, default=Path("target/truffle-compilation"),
                        help="must be under <root>/target")
    parser.add_argument("--selector", default="", help="diagnose only: protos-root:<16 hex> from the catalog")
    parser.add_argument("--expansion", default=DEFAULT_EXPANSION,
                        help="diagnose only: method | node | none (default: %(default)s)")
    parser.add_argument("--dump-level", default=DEFAULT_DUMP_LEVEL,
                        help="diagnose only: Graal Dump=Truffle:<level>, 1 or 2 (default: %(default)s)")
    parser.epilog = "Arguments after -- are passed to `protos test` (e.g. -- --case <ref>)."
    argv = list(sys.argv[1:] if argv is None else argv)
    split = argv.index("--") if "--" in argv else len(argv)
    args = parser.parse_args(argv[:split])
    test_arguments = argv[split + 1:]
    root = args.root.resolve()
    java = select_java(args.java)
    artifacts = args.artifacts if args.artifacts.is_absolute() else root / args.artifacts
    if args.command == "catalog":
        return catalog(root, java, test_arguments, args.timeout, artifacts)
    return diagnose(root, java, args.selector, args.expansion, args.dump_level, test_arguments,
                    args.timeout, artifacts)


if __name__ == "__main__":
    sys.exit(main())
