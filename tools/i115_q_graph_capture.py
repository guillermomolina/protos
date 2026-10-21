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
"""I115-Q: isolated JVM capture of run DSL FULL and Graal compiler graphs."""

import argparse
import csv
import hashlib
import json
import os
import re
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path

CASES = (
    "primitive-return-literal",
    "primitive-if-true",
    "primitive-if-false",
    "primitive-while-zero",
)
TIERS = (1, 2)
TEST = "ProtosI115QCompilerGraphTest"

MARKER = re.compile(r"I115_Q_GRAPH_([A-Z_]+)=(.*)$")
ROOT = re.compile(r"protos-root:[0-9a-f]{16}\[CLOSURE\|[^\]\s]+\]")

REQUIRED = {
    "CASE", "REQUESTED_TIER", "SELECTED_ROOT", "ROOT_COUNT",
    "MODULE_DSL_FULL", "RUN_DSL_FULL", "TOTAL_DSL_FULL",
    "BYTECODE", "WARMUP_CALLS", "COMPILE_RETURN", "COMPILED_TIER",
    "BGV_COUNT", "RESULT", "EPILOG_PROJECTS",
}

EXPORT = (
    "--add-exports=org.graalvm.truffle.compiler/"
    "com.oracle.truffle.compiler=ALL-UNNAMED"
)


def options(case, tier, dump):
    return [
        EXPORT,
        "-Dpolyglot.engine.AllowExperimentalOptions=true",
        "-Dpolyglot.engine.BackgroundCompilation=false",
        "-Dpolyglot.engine.TraceCompilation=true",
        "-Dpolyglot.engine.TraceCompilationDetails=true",
        "-Dpolyglot.engine.CompilationFailureAction=Throw",
        "-Dprotos.diagnostic.stableRootIdentity=true",
        "-Dprotos.i115q.graphCapture=true",
        "-Dprotos.i115q.graphCase=" + case,
        "-Dprotos.i115q.graphTier=" + str(tier),
        "-Djdk.graal.Dump=Truffle:1",
        "-Djdk.graal.DumpPath=" + str(dump),
    ]


def markers(log):
    found = {}
    bgv = []

    for line in log.splitlines():
        match = MARKER.search(line)
        if match is None:
            continue

        key, value = match.groups()
        value = value.strip()

        if key == "BGV":
            bgv.append(value)
        elif key in found:
            raise ValueError("DUPLICATE_MARKER=" + key)
        else:
            found[key] = value

    if set(found) != REQUIRED:
        raise ValueError(
            "MARKER_SET_MISMATCH="
            + str(sorted(set(found) ^ REQUIRED))
        )

    return found, bgv


def compilation(log, selector, tier):
    events = [
        line.strip()
        for line in log.splitlines()
        if line.lstrip().startswith("[engine] opt done")
        and selector in line
    ]

    matching = [
        line for line in events
        if re.search(r"\bTier\s+" + str(tier) + r"\b", line)
    ]

    if len(matching) != 1:
        raise ValueError(
            "SELECTED_OPT_DONE_COUNT=" + str(len(matching))
            + " ALL=" + str(len(events))
        )

    event = matching[0]

    ir = re.search(r"\bIR\s+(\d+)\s*/\s*(\d+)", event)
    size = re.search(r"\bCodeSize\s+(\d+)", event)

    if ir is None or size is None:
        raise ValueError(
            "OPT_DONE_MISSING_IR_OR_CODESIZE: " + event
        )

    return {
        "after_truffle_tier_ir": int(ir.group(1)),
        "final_ir": int(ir.group(2)),
        "code_size": int(size.group(1)),
        "opt_done": event,
        "selected_root_compilations": events,
    }


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def product_fingerprint(root):
    diff = subprocess.run(
        [
            "git", "diff", "--binary", "HEAD", "--",
            "pom.xml", "src/main/java", "protos/lib/core",
        ],
        cwd=root,
        capture_output=True,
        check=True,
    )
    return hashlib.sha256(diff.stdout).hexdigest()


def git_head(root):
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=root,
        capture_output=True,
        text=True,
        check=True,
    )
    return result.stdout.strip()


def validate(log, rc, case, tier, dump, root, lane):
    result = {
        "case": case,
        "tier": tier,
        "maven_rc": rc,
        "valid": False,
    }

    errors = []

    if rc != 0:
        errors.append("MAVEN_RC=" + str(rc))

    try:
        mark, bgv = markers(log)
        result["markers"] = mark

        if (
            mark["CASE"] != case
            or mark["REQUESTED_TIER"] != str(tier)
        ):
            errors.append("CASE_OR_REQUESTED_TIER_MISMATCH")

        if (
            mark["RESULT"] != "PASS"
            or mark["COMPILE_RETURN"] != "true"
        ):
            errors.append("RESULT_OR_COMPILE_FAILURE")

        if (
            mark["COMPILED_TIER"] != str(tier)
            or mark["ROOT_COUNT"] != "2"
        ):
            errors.append("WRONG_TIER_OR_ROOT_COUNT")

        full = [
            int(mark[key])
            for key in (
                "MODULE_DSL_FULL",
                "RUN_DSL_FULL",
                "TOTAL_DSL_FULL",
            )
        ]

        if min(full) < 1 or full[0] + full[1] != full[2]:
            errors.append("INVALID_DSL_FULL_CENSUS")

        result["dsl"] = dict(
            zip(("module", "run", "total"), full)
        )

        selected = mark["SELECTED_ROOT"]

        if not ROOT.fullmatch(selected):
            errors.append("INVALID_SELECTED_ROOT")
        else:
            result["root"] = selected
            result["graal"] = compilation(
                log,
                selected.split("[", 1)[0],
                tier,
            )

        signature = (root / mark["BYTECODE"]).resolve()

        expected_signature = (
            root / "target" / "i115-q-graphs"
            / case / ("tier" + str(tier)) / "dsl-full.txt"
        ).resolve()

        if (
            signature != expected_signature
            or not signature.is_file()
        ):
            errors.append("BYTECODE_FILE_MISMATCH_OR_MISSING")
        else:
            shutil.copyfile(signature, lane / "dsl-full.txt")
            result["dsl_full_sha256"] = sha256(
                lane / "dsl-full.txt"
            )

        source_file = (
            root / "target" / "i115-q-graphs"
            / case / ("tier" + str(tier)) / "source.protos"
        )

        if not source_file.is_file():
            errors.append("SOURCE_MISSING")
        else:
            shutil.copyfile(
                source_file,
                lane / "source.protos",
            )
            result["source_sha256"] = sha256(
                lane / "source.protos"
            )

        if int(mark["BGV_COUNT"]) != len(bgv) or not bgv:
            errors.append("BGV_COUNT_MISMATCH")

        safe_dump = dump.resolve()
        graph_files = []

        for value in bgv:
            graph = Path(value).resolve()

            if (
                not graph.is_relative_to(safe_dump)
                or not graph.is_file()
                or graph.stat().st_size == 0
            ):
                errors.append("INVALID_BGV=" + value)
            else:
                graph_files.append({
                    "path": str(graph),
                    "sha256": sha256(graph),
                    "bytes": graph.stat().st_size,
                })

        result["bgv"] = graph_files

    except (ValueError, OSError) as error:
        errors.append(str(error))

    result["errors"] = errors
    result["valid"] = not errors

    return result


def capture(root, timeout):
    test_path = (
        root / "src/test/java/com/guillermomolina/protos/"
        "execution/compiler/optimizations/"
        "ProtosI115QCompilerGraphTest.java"
    )

    if not test_path.is_file():
        raise RuntimeError(
            "Missing focal test: " + str(test_path)
        )

    stamp = datetime.now(timezone.utc).strftime(
        "%Y%m%dT%H%M%S%fZ"
    )

    output = (
        root / "target" / "i115-q-graphs" / "captures"
        / (stamp + "-" + str(os.getpid()))
    )

    output.mkdir(parents=True, exist_ok=False)

    recorded_head = git_head(root)
    recorded_test = sha256(test_path)
    recorded_product = product_fingerprint(root)
    records = []

    print("I115_Q_CAPTURE_OUTPUT=" + str(output), flush=True)

    for case in CASES:
        for tier in TIERS:
            lane = output / case / ("tier" + str(tier))
            dump = lane / "bgv"

            dump.mkdir(parents=True, exist_ok=False)

            if (
                git_head(root) != recorded_head
                or sha256(test_path) != recorded_test
                or product_fingerprint(root) != recorded_product
            ):
                raise RuntimeError(
                    "Source or HEAD changed during capture"
                )

            env = os.environ.copy()
            inherited = env.get("JAVA_TOOL_OPTIONS", "")

            if any(
                flag in inherited
                for flag in (
                    "CompileImmediately",
                    "i115q.graph",
                    "jdk.graal.DumpPath",
                )
            ):
                raise RuntimeError(
                    "Conflicting inherited JAVA_TOOL_OPTIONS"
                )

            env["JAVA_TOOL_OPTIONS"] = " ".join(
                filter(
                    None,
                    [
                        inherited.strip(),
                        *options(case, tier, dump),
                    ],
                )
            )

            cmd = [
                "mvn", "-q",
                "-Dtest=" + TEST,
                "test",
            ]

            log_path = lane / "maven.log"

            print(
                "I115_Q_CAPTURE_LANE="
                + case + "/tier" + str(tier),
                flush=True,
            )

            try:
                with log_path.open(
                    "w", encoding="utf-8"
                ) as logfile:
                    proc = subprocess.run(
                        cmd,
                        cwd=root,
                        env=env,
                        stdout=logfile,
                        stderr=subprocess.STDOUT,
                        timeout=timeout,
                        check=False,
                    )
                rc = proc.returncode

            except (OSError, subprocess.TimeoutExpired) as error:
                rc = -1
                with log_path.open(
                    "a", encoding="utf-8"
                ) as logfile:
                    logfile.write(
                        "\nCAPTURE_LAUNCH_ERROR="
                        + str(error) + "\n"
                    )

            log = log_path.read_text(
                encoding="utf-8",
                errors="replace",
            )

            result = validate(
                log, rc, case, tier, dump, root, lane
            )

            result["maven_log"] = str(log_path)
            result["command"] = cmd
            result["jvm_options"] = options(
                case, tier, dump
            )

            records.append(result)

            print(
                "I115_Q_CAPTURE_STATUS="
                + ("PASS" if result["valid"] else "FAIL"),
                flush=True,
            )

            (output / "report.json").write_text(
                json.dumps(
                    {
                        "head": recorded_head,
                        "test_sha256": recorded_test,
                        "product_fingerprint": recorded_product,
                        "records": records,
                    },
                    indent=2,
                ) + "\n",
                encoding="utf-8",
            )

            if not result["valid"]:
                print(
                    "I115_Q_CAPTURE_ERRORS="
                    + repr(result["errors"]),
                    flush=True,
                )
                raise RuntimeError(
                    "Capture invalid; see " + str(log_path)
                )

    for case in CASES:
        entries = [
            record
            for record in records
            if record["case"] == case
        ]

        if (
            entries[0]["dsl"] != entries[1]["dsl"]
            or entries[0]["source_sha256"]
                != entries[1]["source_sha256"]
        ):
            raise RuntimeError(
                "Tier sources or DSL censuses differ: " + case
            )

    if (
        git_head(root) != recorded_head
        or sha256(test_path) != recorded_test
        or product_fingerprint(root) != recorded_product
    ):
        raise RuntimeError(
            "Source or HEAD changed during capture"
        )

    summary = output / "summary.tsv"

    with summary.open(
        "w", encoding="utf-8", newline=""
    ) as handle:
        writer = csv.writer(handle, delimiter="\t")

        writer.writerow((
            "case",
            "module_dsl_full",
            "run_dsl_full",
            "total_dsl_full",
            "tier1_ir_after_truffle",
            "tier1_ir_final",
            "tier1_code_size",
            "tier2_ir_after_truffle",
            "tier2_ir_final",
            "tier2_code_size",
        ))

        for case in CASES:
            a, b = [
                record
                for record in records
                if record["case"] == case
            ]

            writer.writerow((
                case,
                a["dsl"]["module"],
                a["dsl"]["run"],
                a["dsl"]["total"],
                a["graal"]["after_truffle_tier_ir"],
                a["graal"]["final_ir"],
                a["graal"]["code_size"],
                b["graal"]["after_truffle_tier_ir"],
                b["graal"]["final_ir"],
                b["graal"]["code_size"],
            ))

    print("I115_Q_SUMMARY=" + str(summary), flush=True)
    print(
        summary.read_text(encoding="utf-8"),
        flush=True,
    )
    print(
        "I115_Q_BGV_NOTE=Raw graphs retained; "
        "StructuredGraph phase selection not yet validated",
        flush=True,
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)

    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=600,
    )

    args = parser.parse_args()
    capture(args.root.resolve(), args.timeout)


if __name__ == "__main__":
    main()
