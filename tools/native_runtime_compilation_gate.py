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


"""Native Image global runtime-compilation check (blocklist + neverPartOfCompilation).

`check` runs at most two sequential Native Image analysis builds, each with
its own timeout, streaming the combined stdout/stderr live to the terminal
while keeping complete copies in the main log and a per-phase log:

  Phase A, strict inventory (-Pnative,native-analysis):
      -TruffleCheckBlockListMethods + PrintRuntimeCompileMethods make
      TruffleFeature.checkBlockList print every blocklist violation without
      aborting; TruffleCheckNeverPartOfCompilation stays enabled, so every
      neverPartOfCompilation violation is recorded and then rejected.
  Phase B, diagnostic graph (-Pnative,native-analysis,native-analysis-graph):
      run only when Phase A was stopped by that rejection before printing the
      runtime compile methods and call tree. It disables only
      TruffleCheckNeverPartOfCompilation. Phase B supplies graph evidence only;
      it never replaces Phase A's violation results, and its blocklist
      inventory is compared with Phase A's as diagnostic evidence only.

GraalVM computes reachability and virtual-call targets; this tool never
reconstructs them. It reports four independent properties separately:

  PARSER_SUCCESS             the detector processed the evidence it received;
  GRAALVM_ANALYSIS_SUCCESS   GraalVM really finished the required analysis;
  GLOBAL_INVENTORY_COMPLETE  the official runtime compile method list and call
                             tree were printed completely by a finished run;
  POLICY_PASS                all of the above plus exactly zero violations of
                             both categories, proven by the strict phase.

The observed reachability graph contains only the edges explicitly present in
GraalVM traces and never substitutes the official call tree. Unknown facts stay
null/UNKNOWN, never zero. A GraalVM internal failure is a valid, classified
diagnostic result but never a completed global analysis. There are no
baselines, allowlists or thresholds.
"""

from __future__ import print_function

import argparse
import datetime
import hashlib
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import threading
import time
import traceback
from collections import Counter, OrderedDict
from pathlib import Path

SCHEMA = "protos.native-runtime-compilation/v1"
DEFAULT_ARTIFACT_ROOT = Path("target") / "native-analysis"
DEFAULT_TIMEOUT = 1800

HEADER_RE = re.compile(
    r"===\s*Found\s+(\d{1,3}(?:[,.'_\u00a0\u202f ]\d{3})+|\d+)\s+"
    r"compilation\s+blocklist\s+violations?\s*===")
BLOCK_RE = re.compile(r"^\s*Blocklisted method\b[:\s]*(.*)$")
TARGET_RE = re.compile(r"^(.*?)\s+\(target:\s*(.*)\)$")
NPOC_HEADER_RE = re.compile(
    r"CompilerAsserts\.neverPartOfCompilation reachable for runtime compilation from\s+"
    r"(\d{1,3}(?:[,.'_\u00a0\u202f ]\d{3})+|\d+)\s+places?")
NPOC_CALLED_FROM = "called from:"
NPOC_RUNTIME_TRACE = "runtime trace:"
NPOC_MARKER = "neverPartOfCompilation"
NPOC_SINK = "CompilerAsserts.neverPartOfCompilation"
METHODS_START = "Start Print Runtime Compile Methods"
METHODS_END = "End Print Runtime Compile Methods"
TREE_START = "Start Print Runtime Compile Call Tree"
TREE_END = "End Print Runtime Compile Call Tree"
PROTOS_ROOT_MARKER = "ProtosBytecodeRootNodeGen"
PROTOS_PACKAGE = "com.guillermomolina.protos"
ANALYSIS_MARKER_RE = re.compile(r"Performing analysis")
NATIVE_IMAGE_MARKER_RE = re.compile(r"GraalVM Native Image|native-image|native-maven-plugin")
# Evidence that the Native Image builder itself started (not merely Maven).
NATIVE_IMAGE_STARTED_RE = re.compile(r"GraalVM Native Image:|^\s*\[\d+/\d+\] Initializing",
                                     re.MULTILINE)
SEALED_MARKER = "Sealed problem"
SEAL_TRACE_START = "stack trace:"
SEAL_TRACE_END = "end trace:"
SEAL_TRACE_UNKNOWN = "stack trace is unknown"
HOSTED_CONSTANT_INVOKE = 'Cannot invoke "jdk.vm.ci.meta.JavaConstant.getJavaKind()"'
HOSTED_CONSTANT_NULL = '"hostedConstant" is null'
HOSTED_CONSTANT_CHAIN = (
    "SubstrateGraalUtils.hostedToRuntime",
    "RuntimeCompilationGraphEncoder.replaceObjectForEncoding",
    "GraphEncoder.prepare",
    "RuntimeCompilationParsingSupport.validateGraph",
)
PARSING_CONTEXT_RE = re.compile(r"parsing context", re.IGNORECASE)
PARSING_FAILURE_MARKER = "Parsing failed on a special method version"
REACHABILITY_TRACE_MARKER = "Method reachability trace"
ENCOUNTERED_MARKER = "Error encountered while parsing"
CAUSED_BY = "Caused by:"
# GraalVM's builder reports a fatal error as this heading followed by '> <exception>'.
UNEXPECTED_ERROR_HEADING_RE = re.compile(r"^The build process encountered an unexpected error:?\s*$")
QUOTED_HEAD_PREFIX = "> "
PARSING_CONTEXT_PATH_RE = re.compile(r"parsing method (.+?) reachable via the parsing context")
POINTS_TO_PACKAGE = "com.oracle.graal.pointsto."
BLOCKLIST_ABORT_MARKER = "Blocklisted methods are reachable for runtime compilation"
POINTS_TO_RE = re.compile(
    r"Unsupported features in \d+ methods?|com\.oracle\.graal\.pointsto\.\S*(?:Error|Exception)\b")
FAILURE_SEALED = "sealed-problem"
FAILURE_HOSTED_CONSTANT = "hosted-constant-null"
MAVEN_SUCCESS = "BUILD SUCCESS"
MAVEN_FAILURE = "BUILD FAILURE"
ANALYZER_ERROR_RE = re.compile(
    r"^(?:Error:|Fatal error|com\.oracle\.(?:svm|graal)\.\S*(?:Error|Exception)\b)")
# Generic consequences of any failed image build; never a cause on their own.
CONSEQUENTIAL_ERROR_RE = re.compile(
    r"^Error: (?:Image build request\b|Use -H:\+ReportExceptionStackTraces\b)")
# Lines that end a failure block (Maven/build-stage output or another report).
BLOCK_BOUNDARY_RE = re.compile(
    r"^(?:\[(?:INFO|ERROR|WARNING|WARN|DEBUG)\]|Error: Image build request\b|"
    r"Error: Use -H:\+ReportExceptionStackTraces\b|===|-{5,}|\[\d+/\d+\])")
ERROR_REPORT_GLOB = "svm_err_b_*.md"
ERROR_REPORT_RE = re.compile(r"([^\s'\"`<>()]*svm_err_b_[^\s'\"`<>()]*\.md)")
DIAGNOSTIC_PROFILE = "native-analysis-graph"
NPOC_DISABLE_OPTION = "-H:-TruffleCheckNeverPartOfCompilation"

# GraalVM failure classification (native_image_failures[].kind).
KIND_BLOCKLIST_ABORT = "BLOCKLIST_CHECK_ABORT"
KIND_NPOC_ABORT = "NEVER_PART_OF_COMPILATION_ABORT"
KIND_SEALED = "SEALED_PROBLEM"
KIND_PARSING = "RUNTIME_COMPILATION_PARSING_FAILURE"
KIND_HOSTED = "HOSTED_CONSTANT_ENCODING_FAILURE"
KIND_POINTS_TO = "POINTS_TO_ANALYSIS_FAILURE"
KIND_START = "NATIVE_IMAGE_START_FAILURE"
KIND_TIMEOUT = "NATIVE_IMAGE_TIMEOUT"
KIND_UNEXPECTED = "NATIVE_IMAGE_UNEXPECTED_FAILURE"
KIND_MAVEN = "MAVEN_FAILURE"
KIND_INCOMPLETE = "INCOMPLETE_OUTPUT"
FAILURE_KINDS = (KIND_BLOCKLIST_ABORT, KIND_NPOC_ABORT, KIND_SEALED, KIND_PARSING, KIND_HOSTED,
                 KIND_POINTS_TO, KIND_START, KIND_TIMEOUT, KIND_UNEXPECTED, KIND_MAVEN,
                 KIND_INCOMPLETE)

UNKNOWN = "UNKNOWN"
TREE_COMPLETE = "COMPLETE"
TREE_INCOMPLETE = "INCOMPLETE"
TREE_NOT_PRODUCED = "NOT_PRODUCED"


def parse_count(text):
    return int(re.sub(r"[^0-9]", "", text))


def _delimiter_re(marker):
    """A real section delimiter: the marker alone on its line, optionally framed by '*'."""
    return re.compile(r"^\s*\**\s*" + re.escape(marker) + r"\s*\**\s*$")


DELIMITER_RES = {marker: _delimiter_re(marker)
                 for marker in (METHODS_START, METHODS_END, TREE_START, TREE_END)}


def _is_section_delimiter(line):
    return any(pattern.match(line) for pattern in DELIMITER_RES.values())


def _section_info(lines, start, end):
    """Facts of the first real start..end section; quoted markers never count."""
    start_re, end_re = DELIMITER_RES[start], DELIMITER_RES[end]
    starts = [i for i, line in enumerate(lines) if start_re.match(line)]
    ends = [i for i, line in enumerate(lines) if end_re.match(line)]
    info = {"start_found": bool(starts), "end_found": False, "complete": False,
            "repeated": len(starts) > 1 or len(ends) > 1, "nested_start": False,
            "start_line": None, "end_line": None, "body": []}
    if not starts:
        info["end_found"] = bool(ends)
        return info
    first = starts[0]
    info["start_line"] = first + 1
    closing = next((i for i in ends if i > first), None)
    if closing is None:
        # Truncated: keep what GraalVM printed, up to the next build-stage line.
        body = []
        for line in lines[first + 1:]:
            if BLOCK_BOUNDARY_RE.match(line.strip()) or _is_section_delimiter(line):
                break
            body.append(line.rstrip())
        info["body"] = body
        return info
    info["end_found"] = True
    info["end_line"] = closing + 1
    info["body"] = [line.rstrip() for line in lines[first + 1:closing]]
    info["nested_start"] = any(first < i < closing for i in starts)
    info["complete"] = not info["nested_start"]
    return info


def _section(lines, start, end):
    """Return (complete, body_lines) for the first real start..end section."""
    info = _section_info(lines, start, end)
    return info["complete"], (info["body"] if info["complete"] else [])


def _indented_frames(lines, start):
    frames = []
    for line in lines[start:]:
        if not line.strip() or not line[:1].isspace():
            break
        frames.append(line.strip())
    return frames


def _trace_complete(frames):
    return bool(frames) and not any(frame.strip().startswith("...") for frame in frames)


def _blocklist_entries(lines, phase="strict"):
    entries = []
    for index, line in enumerate(lines):
        match = BLOCK_RE.match(line)
        if not match:
            continue
        name = match.group(1).strip()
        cursor = index + 1
        if not name:
            while cursor < len(lines) and not lines[cursor].strip():
                cursor += 1
            if cursor < len(lines):
                name = lines[cursor].strip()
                cursor += 1
        trace, trace_header = [], False
        if cursor < len(lines) and lines[cursor].strip() == "trace:":
            trace_header = True
            trace = _indented_frames(lines, cursor + 1)
        target_match = TARGET_RE.match(name) if name else None
        entries.append({
            "method": name or "<unnamed>",
            "method_name": target_match.group(1) if target_match else (name or None),
            "target": target_match.group(2) if target_match else None,
            "trace": trace,
            "trace_header": trace_header,
            "trace_complete": _trace_complete(trace),
            "block_complete": bool(name) and trace_header and bool(trace),
            "index": len(entries),
            "block_line": index + 1,
            "phase": phase,
        })
    return entries


def _npoc_entries(lines, header_index, phase="strict"):
    """Blocks after the neverPartOfCompilation header: (complete, incomplete)."""
    entries, incomplete = [], []
    index = header_index + 1
    order = 0
    while index < len(lines):
        stripped = lines[index].strip()
        if stripped.startswith(NPOC_CALLED_FROM):
            called_from = stripped[len(NPOC_CALLED_FROM):].strip()
            block_line = index + 1
            trace, complete = [], False
            if index + 1 < len(lines) and lines[index + 1].strip() == NPOC_RUNTIME_TRACE:
                trace = _indented_frames(lines, index + 2)
                complete = bool(called_from) and bool(trace)
                index += 1 + len(trace)
            entry = {"called_from": called_from, "runtime_trace": trace, "index": order,
                     "block_line": block_line, "phase": phase,
                     "trace_complete": complete and _trace_complete(trace)}
            order += 1
            (entries if complete else incomplete).append(entry)
        index += 1
    return entries, incomplete


def _sealed_problem(lines):
    """GraalVM 'Should not reach here: Sealed problem' report: (failed, trace, available).

    The trace is kept verbatim between 'stack trace:' and 'end trace:'; it is
    available only when that block is closed, non-empty and not reported unknown.
    """
    for index, line in enumerate(lines):
        if SEALED_MARKER not in line:
            continue
        rest = lines[index:]
        if any(SEAL_TRACE_UNKNOWN in candidate for candidate in rest):
            return True, None, False
        for offset, candidate in enumerate(rest):
            if candidate.strip() == SEAL_TRACE_START:
                trace = []
                for traced in rest[offset + 1:]:
                    if traced.strip() == SEAL_TRACE_END:
                        return True, trace, bool(trace)
                    trace.append(traced)
                return True, trace or None, False
        return True, None, False
    return False, None, False


def _java_stack_trace(lines, start):
    """Verbatim Java frames ('at ...', 'Caused by', '... N more') after start."""
    trace = []
    for line in lines[start:]:
        stripped = line.strip()
        if stripped.startswith(("at ", "Caused by", "Suppressed:", "... ")):
            trace.append(line)
        elif trace:
            break
    return trace


def _hosted_constant_failure(lines):
    """Encoder failure on a null hosted constant: (cause, stack_trace, index) or Nones."""
    for index, line in enumerate(lines):
        if HOSTED_CONSTANT_INVOKE not in line:
            continue
        cause = line.strip()
        start = index + 1
        if HOSTED_CONSTANT_NULL not in line:
            if start < len(lines) and HOSTED_CONSTANT_NULL in lines[start]:
                cause = cause + " " + lines[start].strip()
                start += 1
            else:
                continue
        return cause, _java_stack_trace(lines, start), index
    return None, None, None


def _parsing_contexts(lines):
    """Every parsing-context entry GraalVM printed, in order (context only, not cause)."""
    contexts = []
    for index, line in enumerate(lines):
        if PARSING_CONTEXT_RE.search(line):
            for following in lines[index + 1:]:
                if not following.strip() or not following[:1].isspace():
                    break
                contexts.append(following.strip())
    return contexts


def _is_frame_line(stripped):
    return stripped.startswith(("at ", CAUSED_BY, "Suppressed:", "..."))


def _block_range(lines, start):
    """(start, end, reached_eof) of the verbatim report block starting at start."""
    end = start + 1
    blanks = 0
    while end < len(lines):
        stripped = lines[end].strip()
        if BLOCK_BOUNDARY_RE.match(stripped) or _is_section_delimiter(lines[end]):
            break
        if not stripped:
            blanks += 1
            if blanks >= 2:
                break
        else:
            blanks = 0
        end += 1
    reached_eof = end >= len(lines)
    while end > start + 1 and not lines[end - 1].strip():
        end -= 1
    return start, end, reached_eof


def _exception_details(block):
    """Split a verbatim report block into its GraalVM sections; nothing is dropped."""
    details = {"head": block[0].strip() if block else None, "frames": [],
               "method_reachability_trace": None, "error_encountered_while_parsing": None,
               "parsing_context": None, "causes": []}
    current = details["frames"]
    for line in block[1:]:
        stripped = line.strip()
        if not stripped:
            continue
        path = PARSING_CONTEXT_PATH_RE.search(stripped)
        if path and not stripped.startswith("at "):
            # 'parsing method X reachable via the parsing context' + 'at ...' frames:
            # an explicit GraalVM path whose innermost frame is X.
            details["method_reachability_trace"] = {"heading": stripped,
                                                    "frames": [path.group(1).strip()]}
            current = details["method_reachability_trace"]["frames"]
        elif REACHABILITY_TRACE_MARKER in stripped and not stripped.startswith("at "):
            details["method_reachability_trace"] = {"heading": stripped, "frames": []}
            current = details["method_reachability_trace"]["frames"]
        elif stripped.startswith(ENCOUNTERED_MARKER):
            details["error_encountered_while_parsing"] = {"heading": stripped, "lines": []}
            current = details["error_encountered_while_parsing"]["lines"]
        elif stripped.lower().startswith("parsing context"):
            details["parsing_context"] = {"heading": stripped, "entries": []}
            current = details["parsing_context"]["entries"]
        elif stripped.startswith(CAUSED_BY):
            cause = {"exception": stripped[len(CAUSED_BY):].strip(), "frames": []}
            details["causes"].append(cause)
            current = cause["frames"]
        else:
            current.append(stripped)
    return details


def _subject_after(lines, index, marker):
    """Text GraalVM printed after marker (or on the next line); context, not a cause."""
    rest = lines[index].split(marker, 1)[1].lstrip(" :").strip()
    if rest:
        return rest
    for following in lines[index + 1:]:
        if following.strip():
            return following.strip()
    return None


def _blocking(failures):
    """Failures that prevent a finished analysis (not violations, not post-analysis Maven)."""
    return [f for f in failures
            if f["kind"] != KIND_NPOC_ABORT
            and not (f["kind"] == KIND_MAVEN and not f.get("before_native_image"))]


def _classify_failures(phase, lines, output, exit_code, timed_out, start_error,
                       native_started, analysis_ran, npoc_header_index, sections_complete,
                       maven_success, sealed, hosted):
    failures = []
    claimed = []

    def inside(index):
        return any(start <= index < end for start, end in claimed)

    def add(kind, index, message=None, keep_block=True, **extra):
        entry = {"kind": kind, "phase": phase, "line": None, "message": message,
                 "block": [], "block_line_range": None, "truncated": False, "exception": None}
        if index is not None:
            start, end, eof = _block_range(lines, index)
            claimed.append((start, end))
            block = [line.rstrip() for line in lines[start:end]]
            entry.update({"line": index + 1, "message": message or lines[index].strip(),
                          "block_line_range": [start + 1, end], "truncated": eof,
                          "block": block if keep_block else [],
                          "exception": _exception_details(block) if keep_block else None})
        entry.update(extra)
        failures.append(entry)
        return entry

    for index, line in enumerate(lines):
        stripped = line.strip()
        if PARSING_FAILURE_MARKER in line and not _is_frame_line(stripped) and not inside(index):
            add(KIND_PARSING, index,
                subject=_subject_after(lines, index, PARSING_FAILURE_MARKER),
                subject_is_proven_cause=False,
                parsing_context_is_proven_cause=False)
    if sealed[0]:
        index = next(i for i, line in enumerate(lines) if SEALED_MARKER in line)
        add(KIND_SEALED, index, nested=inside(index), seal_stack_trace=sealed[1],
            seal_trace_available=sealed[2])
    if hosted[0] is not None:
        index = hosted[2]
        nested = inside(index)
        entry = add(KIND_HOSTED, index, nested=nested, cause=hosted[0],
                    stack_trace=hosted[1], parsing_contexts=_parsing_contexts(lines),
                    parsing_context_is_proven_cause=False)
        chain_text = "\n".join(entry["block"] + (hosted[1] or []))
        entry["call_chain"] = {frame: frame in chain_text for frame in HOSTED_CONSTANT_CHAIN}
    for index, line in enumerate(lines):
        if not UNEXPECTED_ERROR_HEADING_RE.match(line.strip()):
            continue
        head = next((i for i in range(index + 1, len(lines)) if lines[i].strip()), None)
        if head is None or inside(head):
            continue
        exception = lines[head].strip()
        if exception.startswith(QUOTED_HEAD_PREFIX):
            exception = exception[len(QUOTED_HEAD_PREFIX):].strip()
        if PARSING_FAILURE_MARKER in exception:
            kind = KIND_PARSING
            extra = {"subject": _subject_after(lines, head, PARSING_FAILURE_MARKER),
                     "subject_is_proven_cause": False,
                     "parsing_context_is_proven_cause": False}
        elif exception.startswith(POINTS_TO_PACKAGE) or POINTS_TO_RE.search(exception):
            kind, extra = KIND_POINTS_TO, {}
        else:
            kind, extra = KIND_UNEXPECTED, {}
        add(kind, head, message=exception, heading=line.strip(), **extra)
    for index, line in enumerate(lines):
        stripped = line.strip()
        if (BLOCKLIST_ABORT_MARKER in line and ANALYZER_ERROR_RE.match(stripped)
                and not inside(index)):
            add(KIND_BLOCKLIST_ABORT, index)
    if (npoc_header_index is not None and start_error is None and not timed_out
            and exit_code not in (None, 0)):
        add(KIND_NPOC_ABORT, npoc_header_index, keep_block=False)
    for index, line in enumerate(lines):
        stripped = line.strip()
        if (POINTS_TO_RE.search(stripped) and ANALYZER_ERROR_RE.match(stripped)
                and PARSING_FAILURE_MARKER not in stripped and "ParsingError" not in stripped
                and not inside(index)):
            add(KIND_POINTS_TO, index)
    for index, line in enumerate(lines):
        stripped = line.strip()
        if (ANALYZER_ERROR_RE.match(stripped) and NPOC_MARKER not in stripped
                and not CONSEQUENTIAL_ERROR_RE.match(stripped) and not inside(index)):
            add(KIND_UNEXPECTED, index)

    if start_error is not None:
        add(KIND_START, None, message="analysis process could not be started: %s" % start_error)
        return failures
    if timed_out:
        add(KIND_TIMEOUT, None, message="phase exceeded the timeout and was terminated",
            native_image_started=native_started)
        return failures
    if exit_code not in (None, 0) and not native_started:
        add(KIND_MAVEN, None, before_native_image=True,
            message="Maven exited with code %s before Native Image started" % exit_code)
    elif native_started and not analysis_ran and not failures:
        add(KIND_START, None, message="Native Image started but the analysis did not run")
    elif exit_code not in (None, 0) and not failures:
        add(KIND_MAVEN, None, before_native_image=False,
            message="Maven exited with code %s after Native Image ran" % exit_code)
    if exit_code == 0 and not native_started:
        add(KIND_INCOMPLETE, None, message="exit 0 without Native Image builder output")
    elif native_started and not sections_complete and not _blocking(failures) and not any(
            f["kind"] == KIND_NPOC_ABORT for f in failures):
        add(KIND_INCOMPLETE, None,
            message="Native Image output ended without complete runtime compilation sections")
    elif exit_code == 0 and not maven_success:
        add(KIND_INCOMPLETE, None, message="exit 0 without %s (truncated output?)" % MAVEN_SUCCESS)
    return failures


def analyze_phase(output, exit_code, timed_out=False, start_error=None, phase="strict"):
    """Facts of one phase. Never a verdict and never an inferred zero."""
    lines = output.splitlines()
    methods_info = _section_info(lines, METHODS_START, METHODS_END)
    tree_info = _section_info(lines, TREE_START, TREE_END)
    methods_complete = methods_info["complete"]
    tree_complete = tree_info["complete"]
    methods_body = methods_info["body"] if methods_complete else []
    tree_body = tree_info["body"] if tree_complete else []
    candidates = [line for line in methods_body if line.strip()]
    tree_entries = [line for line in tree_body if line.strip()]
    # Everything printed inside a (possibly truncated) section is kept as evidence;
    # only a complete section counts towards the legacy counts and the verdict.
    methods_printed = [line for line in methods_info["body"] if line.strip()]
    tree_printed = [line for line in tree_info["body"] if line.strip()]
    tree_roots = [line.strip() for line in tree_printed if not line[:1].isspace()]
    sections_text = "\n".join(methods_body + tree_body)

    npoc_headers, npoc_entries, npoc_incomplete, npoc_header_index = [], [], [], None
    for index, line in enumerate(lines):
        match = NPOC_HEADER_RE.search(line)
        if match:
            npoc_headers.append(parse_count(match.group(1)))
            if len(npoc_headers) == 1:
                npoc_header_index = index
                npoc_entries, npoc_incomplete = _npoc_entries(lines, index, phase)

    errors, unexpected = [], []
    for line in lines:
        stripped = line.strip()
        if ANALYZER_ERROR_RE.match(stripped):
            errors.append(stripped)
            if NPOC_MARKER not in stripped and not CONSEQUENTIAL_ERROR_RE.match(stripped):
                unexpected.append(stripped)

    analysis_ran = bool(ANALYSIS_MARKER_RE.search(output)) and bool(
        NATIVE_IMAGE_MARKER_RE.search(output))
    native_started = bool(NATIVE_IMAGE_STARTED_RE.search(output))
    sealed = _sealed_problem(lines)
    hosted = _hosted_constant_failure(lines)
    hosted_cause, hosted_trace = hosted[0], hosted[1]
    if sealed[0]:
        failure_kind, failure_cause, failure_trace = (
            FAILURE_SEALED, "Should not reach here: " + SEALED_MARKER, sealed[1])
    elif hosted_cause is not None:
        failure_kind, failure_cause, failure_trace = (
            FAILURE_HOSTED_CONSTANT, hosted_cause, hosted_trace)
    else:
        failure_kind, failure_cause, failure_trace = None, None, None
    trace_text = "\n".join(failure_trace or [])
    graph_complete = (methods_complete and tree_complete and bool(candidates)
                      and bool(tree_entries) and failure_kind is None)
    npoc_rejection = (start_error is None and not timed_out and exit_code not in (None, 0)
                      and analysis_ran and bool(npoc_headers) and not unexpected)
    native_image_failures = _classify_failures(
        phase, lines, output, exit_code, timed_out, start_error, native_started, analysis_ran,
        npoc_header_index, methods_complete and tree_complete, MAVEN_SUCCESS in output,
        sealed, hosted)
    return {
        "phase": phase,
        "exit_code": exit_code,
        "timed_out": timed_out,
        "start_error": start_error,
        "analysis_ran": analysis_ran,
        "native_image_started": native_started,
        "blocklist_headers": [parse_count(m.group(1)) for m in HEADER_RE.finditer(output)],
        "blocklist_entries": _blocklist_entries(lines, phase),
        "npoc_headers": npoc_headers,
        "npoc_entries": npoc_entries,
        "npoc_incomplete_blocks": len(npoc_incomplete),
        "npoc_incomplete_entries": npoc_incomplete,
        "methods_complete": methods_complete,
        "methods_section": dict(methods_info, body=None, entries=methods_printed),
        "candidates": len(candidates),
        "tree_complete": tree_complete,
        "tree_section": dict(tree_info, body=None, entries=tree_printed, roots=tree_roots),
        "tree_entries": len(tree_entries),
        "graph_complete": graph_complete,
        "protos_roots": PROTOS_ROOT_MARKER in sections_text and PROTOS_PACKAGE in sections_text,
        "maven_success": MAVEN_SUCCESS in output,
        "maven_failure": MAVEN_FAILURE in output,
        "errors": errors,
        "unexpected_errors": unexpected,
        "never_part_of_compilation_rejection": npoc_rejection,
        "sealed_failure": sealed[0],
        "seal_stack_trace": sealed[1],
        "seal_trace_available": sealed[2],
        "failure_kind": failure_kind,
        "failure_cause": failure_cause,
        "failure_stack_trace": failure_trace,
        "failure_call_chain": ({frame: frame in trace_text for frame in HOSTED_CONSTANT_CHAIN}
                               if failure_kind == FAILURE_HOSTED_CONSTANT else None),
        "failure_parsing_contexts": _parsing_contexts(lines) if failure_kind else [],
        "native_image_failures": native_image_failures,
    }


def phase_status(facts):
    if facts is None:
        return "not-run"
    if facts["start_error"] is not None:
        return "start-failed"
    if facts["timed_out"]:
        return "timeout"
    if facts["never_part_of_compilation_rejection"]:
        return "never-part-of-compilation-rejection"
    if facts["exit_code"] == 0:
        return "completed"
    return "failed"


def graph_phase_required(strict):
    """Phase B only when Phase A was stopped by the neverPartOfCompilation rejection."""
    return strict["never_part_of_compilation_rejection"] and not strict["graph_complete"]


def _common_phase_failures(name, facts, errors):
    """Process-level failures shared by both phases (kind 'error')."""
    if facts["start_error"] is not None:
        errors.append("%s phase could not be started: %s" % (name, facts["start_error"]))
        return
    if facts["timed_out"]:
        errors.append("%s phase exceeded the timeout and was terminated" % name)
        return
    if not facts["analysis_ran"]:
        errors.append("%s phase: Native Image analysis did not run" % name)
    for line in facts["unexpected_errors"][:1]:
        errors.append("%s phase: analyzer/build error reported: %s" % (name, line))
    code = facts["exit_code"]
    if code == 0:
        if not facts["maven_success"]:
            errors.append("%s phase: Maven exited 0 without reporting %s (truncated output?)"
                          % (name, MAVEN_SUCCESS))
        if facts["maven_failure"]:
            errors.append("%s phase: contradiction: Maven exited 0 but reported %s"
                          % (name, MAVEN_FAILURE))
    else:
        if facts["maven_success"] and not facts["maven_failure"]:
            errors.append("%s phase: contradiction: Maven reported %s but exited %s"
                          % (name, MAVEN_SUCCESS, code))
        if not facts["never_part_of_compilation_rejection"] or name != "strict":
            errors.append("%s phase: Maven exited with code %s" % (name, code))


def _complete_blocks(entries):
    return [e for e in entries if e.get("block_complete", True)]


def _phase_finished(facts):
    """GraalVM really finished this phase's analysis: exit 0, sections, no blocking failure."""
    return bool(facts is not None and facts["start_error"] is None and not facts["timed_out"]
                and facts["exit_code"] == 0 and facts["analysis_ran"]
                and facts["graph_complete"] and facts["protos_roots"]
                and not _blocking(facts.get("native_image_failures", [])))


def _is_diagnostic_command(command):
    for arg in command or []:
        if arg == NPOC_DISABLE_OPTION:
            return True
        if arg.startswith("-P") and DIAGNOSTIC_PROFILE in arg[2:].split(","):
            return True
    return False


def _blocklist_inventory(role, facts, accepted=None, exhaustive=False):
    if facts is None:
        return {"phase": role, "executed": False, "header_present": None, "headers": [],
                "reported_count": None, "parsed_count": None, "incomplete_blocks": None,
                "report_consistent": None, "count": None, "count_state": "NOT_RUN",
                "count_basis": None, "exhaustive": False, "entries": []}
    headers = facts["blocklist_headers"]
    entries = facts["blocklist_entries"]
    complete = _complete_blocks(entries)
    reported = headers[0] if len(set(headers)) == 1 else None
    if not headers and not entries:
        consistent = None
    else:
        consistent = (len(set(headers)) == 1 and reported == len(complete)
                      and len(complete) == len(entries))
    count = accepted
    if role != "strict":
        count = reported if consistent else None
        if count is None and not headers and not entries and _phase_finished(facts):
            count = 0
    if count is None:
        basis = None
    elif headers:
        basis = "header and %d complete blocks" % len(complete)
    else:
        basis = "finished analysis without a violation header"
    return {"phase": role, "executed": True, "header_present": bool(headers), "headers": headers,
            "reported_count": reported, "parsed_count": len(complete),
            "incomplete_blocks": len(entries) - len(complete),
            "report_consistent": consistent, "count": count,
            "count_state": "KNOWN" if count is not None else UNKNOWN, "count_basis": basis,
            "exhaustive": bool(exhaustive and count is not None), "entries": entries}


def _npoc_inventory(role, facts, accepted=None, exhaustive=False):
    if facts is None:
        return {"phase": role, "executed": False, "check_state": "NOT_RUN",
                "header_present": None, "headers": [], "reported_count": None,
                "parsed_count": None, "incomplete_blocks": None, "report_consistent": None,
                "count": None, "count_state": "NOT_RUN", "exhaustive": False, "entries": [],
                "incomplete_entries": []}
    disabled = role != "strict" or _is_diagnostic_command((facts.get("run") or {}).get("command"))
    headers = facts["npoc_headers"]
    entries = facts["npoc_entries"]
    reported = headers[0] if len(set(headers)) == 1 else None
    if not headers and not entries and not facts["npoc_incomplete_blocks"]:
        consistent = None
    else:
        consistent = (len(set(headers)) == 1 and reported == len(entries)
                      and not facts["npoc_incomplete_blocks"])
    count = None if disabled else accepted
    if disabled:
        state = "DISABLED"
    else:
        state = "KNOWN" if count is not None else UNKNOWN
    return {"phase": role, "executed": True,
            "check_state": "DISABLED_FOR_DIAGNOSIS" if disabled else "ENABLED",
            "header_present": bool(headers), "headers": headers, "reported_count": reported,
            "parsed_count": len(entries), "incomplete_blocks": facts["npoc_incomplete_blocks"],
            "report_consistent": consistent, "count": count, "count_state": state,
            "exhaustive": bool(exhaustive and count is not None and not disabled),
            "entries": [dict(e, declared_count=reported) for e in entries],
            "incomplete_entries": facts["npoc_incomplete_entries"]}


def _phase_comparison(strict, graph, strict_count, graph_count):
    if graph is None:
        return {"available": False, "reason": "graph phase not run", "common_methods": [],
                "strict_only_methods": [], "graph_only_methods": [],
                "interpretation": "DIAGNOSTIC_ONLY", "equivalence_claimed": False,
                "merged_inventory": None}
    strict_methods = Counter(e["method"] for e in strict["blocklist_entries"])
    graph_methods = Counter(e["method"] for e in graph["blocklist_entries"])
    return {
        "available": True,
        "strict_reported_count": strict_count,
        "graph_reported_count": graph_count,
        "strict_parsed_count": sum(strict_methods.values()),
        "graph_parsed_count": sum(graph_methods.values()),
        "common_methods": sorted((strict_methods & graph_methods).elements()),
        "strict_only_methods": sorted((strict_methods - graph_methods).elements()),
        "graph_only_methods": sorted((graph_methods - strict_methods).elements()),
        "interpretation": "DIAGNOSTIC_ONLY",
        "note": "phases use different Native Image configurations; a difference is neither "
                "a parser inconsistency nor a Protos regression, and the union is not a "
                "complete global inventory",
        "equivalence_claimed": False,
        "merged_inventory": None,
    }


def _frame_id(frame):
    stripped = frame.strip()
    return stripped[3:].strip() if stripped.startswith("at ") else stripped


def observed_reachability_graph(phases):
    """Diagnostic graph of the edges explicitly present in GraalVM traces only.

    GraalVM prints traces innermost frame first; each consecutive pair (outer,
    inner) is an observed caller->callee edge. Nothing is inferred from names,
    packages or hierarchies, and this graph never substitutes the official tree.
    """
    nodes = OrderedDict()
    edges = OrderedDict()
    roots = OrderedDict()
    paths = []

    def node(name):
        nodes.setdefault(name, None)

    def edge(caller, callee, ref):
        node(caller)
        node(callee)
        edges.setdefault((caller, callee), []).append(ref)

    def chain(frames, ref, complete):
        for frame in frames:
            node(frame)
        for index in range(len(frames) - 1):
            edge(frames[index + 1], frames[index], ref)
        if complete and frames:
            roots.setdefault(frames[-1], []).append(ref)

    for role, facts in phases:
        if facts is None:
            continue
        for entry in facts["blocklist_entries"]:
            ref = "%s:blocklist#%d" % (role, entry["index"])
            frames = [_frame_id(f) for f in entry["trace"]]
            sink = entry["method_name"] or entry["method"]
            complete = bool(entry.get("block_complete")) and entry.get("trace_complete", False)
            chain([sink] + frames, ref, complete)
            paths.append({"ref": ref, "phase": role, "category": "blocklist",
                          "block_index": entry["index"], "block_line": entry["block_line"],
                          "sink": sink, "path_root_to_sink": list(reversed([sink] + frames)),
                          "complete": complete})
        for entry in facts["npoc_entries"] + facts.get("npoc_incomplete_entries", []):
            ref = "%s:never_part_of_compilation#%d" % (role, entry["index"])
            frames = [_frame_id(f) for f in entry["runtime_trace"]]
            called_from = entry["called_from"]
            complete = entry.get("trace_complete", False)
            if called_from and (not frames or frames[0] == called_from):
                full = [NPOC_SINK, called_from] + frames[1:]
                chain(full, ref, complete)
                path = list(reversed(full))
            else:
                if called_from:
                    edge(called_from, NPOC_SINK, ref)
                chain(frames, ref, False)
                complete = False
                path = None
            paths.append({"ref": ref, "phase": role, "category": "never_part_of_compilation",
                          "block_index": entry["index"], "block_line": entry["block_line"],
                          "sink": NPOC_SINK, "called_from": called_from,
                          "path_root_to_sink": path, "complete": complete})
        for number, failure in enumerate(facts.get("native_image_failures", [])):
            reach = (failure.get("exception") or {}).get("method_reachability_trace")
            if not reach:
                continue
            ref = "%s:failure#%d:%s" % (role, number, failure["kind"])
            frames = [_frame_id(f) for f in reach["frames"]]
            complete = _trace_complete(frames) and not failure["truncated"]
            chain(frames, ref, complete)
            paths.append({"ref": ref, "phase": role, "category": "method_reachability_trace",
                          "failure_kind": failure["kind"], "block_line": failure["line"],
                          "sink": frames[0] if frames else None,
                          "path_root_to_sink": list(reversed(frames)), "complete": complete})
    if not paths:
        status = "EMPTY"
    elif all(p["complete"] for p in paths):
        status = "TRACES_COMPLETE"
    else:
        status = "PARTIAL"
    return {
        "status": status,
        "partial": status != "TRACES_COMPLETE",
        "scope": "edges explicitly present in GraalVM traces only; no inferred calls",
        "trace_order": "innermost frame first, as printed by GraalVM",
        "global_inventory": False,
        "substitutes_runtime_compilation_call_tree": False,
        "nodes": list(nodes),
        "edges": [{"caller": caller, "callee": callee, "evidence": refs}
                  for (caller, callee), refs in edges.items()],
        "roots": [{"method": method, "basis": "outermost frame of a complete GraalVM trace",
                   "evidence": refs} for method, refs in roots.items()],
        "paths": paths,
    }


def _call_tree(source_role, source, analysis_complete, legacy_tree_complete):
    if source is None or not (source["tree_section"]["start_found"]
                              or source["methods_section"]["start_found"]):
        status = TREE_NOT_PRODUCED
    elif (analysis_complete and legacy_tree_complete and source["tree_section"]["roots"]
          and source["protos_roots"] and not source["tree_section"]["repeated"]
          and not source["methods_section"]["repeated"]):
        status = TREE_COMPLETE
    else:
        status = TREE_INCOMPLETE
    tree = source["tree_section"] if source else {}
    methods = source["methods_section"] if source else {}

    def section(info):
        return {key: info.get(key) for key in ("start_found", "end_found", "complete",
                                               "repeated", "nested_start", "start_line",
                                               "end_line")}
    return {
        "status": status,
        "phase": source_role if source is not None else None,
        "official_source": "GraalVM -H:+PrintRuntimeCompileMethods / "
                           "-H:+PrintRuntimeCompilationCallTree sections",
        "runtime_compile_methods": dict(section(methods),
                                        status=(TREE_COMPLETE if status == TREE_COMPLETE
                                                else TREE_INCOMPLETE if methods.get("start_found")
                                                else TREE_NOT_PRODUCED),
                                        entries=methods.get("entries", [])),
        "call_tree": dict(section(tree), entries=tree.get("entries", []),
                          roots=tree.get("roots", [])),
        "protos_roots_detected": bool(source and source["protos_roots"]),
    }


def _phase_state(role, facts, skipped_reason=None):
    if facts is None:
        return {"role": role, "executed": False, "status": "not-run",
                "skipped_reason": skipped_reason, "parser": None,
                "analysis_finished": False, "failure_kinds": [], "error_reports": []}
    run = facts.get("run") or {}
    return {
        "role": role,
        "executed": True,
        "status": phase_status(facts),
        "parser": "PARSER_SUCCESS",
        "command": run.get("command"),
        "diagnostic_configuration": _is_diagnostic_command(run.get("command")),
        "started_at": run.get("started_at"),
        "finished_at": run.get("finished_at"),
        "duration_seconds": run.get("duration_seconds"),
        "termination": run.get("termination", _termination(facts["exit_code"],
                                                            facts["timed_out"],
                                                            facts["start_error"])),
        "exit_code": facts["exit_code"],
        "timed_out": facts["timed_out"],
        "start_error": facts["start_error"],
        "log": run.get("log"),
        "native_image_started": facts["native_image_started"],
        "analysis_ran": facts["analysis_ran"],
        "analysis_finished": _phase_finished(facts),
        "failure_kinds": [f["kind"] for f in facts["native_image_failures"]],
        "unexpected_errors": facts["unexpected_errors"],
        "error_reports": run.get("error_reports", []),
        "rejected_error_reports": run.get("rejected_error_reports", []),
    }


def evaluate_phases(strict, graph=None, graph_skipped_reason=None):
    """Joint verdict. Phase A owns violation results; Phase B only the graph."""
    errors, violations = [], []
    _common_phase_failures("strict", strict, errors)

    headers = strict["blocklist_headers"]
    entries = strict["blocklist_entries"]
    complete_entries = _complete_blocks(entries)
    blocklist = None
    if len(set(headers)) > 1:
        errors.append("contradictory blocklist headers: %s" % sorted(set(headers)))
    elif headers:
        blocklist = headers[0]
        if len(complete_entries) != blocklist or len(complete_entries) != len(entries):
            errors.append("inconsistent diagnostic: header declares %d blocklist violations "
                          "but %d complete 'Blocklisted method' blocks were recovered "
                          "(%d incomplete)" % (blocklist, len(complete_entries),
                                               len(entries) - len(complete_entries)))
    elif entries:
        errors.append("%d 'Blocklisted method' blocks without a violation header" % len(entries))

    npoc_headers = strict["npoc_headers"]
    npoc_entries = strict["npoc_entries"]
    npoc = None
    if len(set(npoc_headers)) > 1:
        errors.append("contradictory neverPartOfCompilation headers: %s" % sorted(set(npoc_headers)))
    elif npoc_headers:
        npoc = npoc_headers[0]
        if len(npoc_entries) != npoc or strict["npoc_incomplete_blocks"]:
            errors.append("inconsistent diagnostic: header declares %d neverPartOfCompilation "
                          "violations but %d complete blocks were recovered (%d incomplete)"
                          % (npoc, len(npoc_entries), strict["npoc_incomplete_blocks"]))
        if strict["exit_code"] == 0:
            errors.append("contradiction: neverPartOfCompilation violations reported with exit 0")
    elif strict["npoc_incomplete_blocks"] or npoc_entries:
        errors.append("neverPartOfCompilation blocks without a header")

    consistent = None
    comparison = None
    graph_count = None
    if strict["failure_kind"] is not None:
        errors.append("strict phase: GraalVM failed (%s): %s"
                      % (strict["failure_kind"], strict["failure_cause"]))
    if graph is not None:
        _common_phase_failures("graph", graph, errors)
        if graph["npoc_headers"]:
            errors.append("graph phase: contradiction: neverPartOfCompilation reported while "
                          "its check is disabled")
        graph_headers = graph["blocklist_headers"]
        graph_count = graph_headers[0] if len(set(graph_headers)) == 1 else None
        graph_complete_entries = _complete_blocks(graph["blocklist_entries"])
        if len(set(graph_headers)) > 1:
            errors.append("graph phase: contradictory blocklist headers: %s"
                          % sorted(set(graph_headers)))
        elif graph_count is not None and (
                len(graph_complete_entries) != graph_count
                or len(graph_complete_entries) != len(graph["blocklist_entries"])):
            errors.append("graph phase: inconsistent diagnostic: header declares %d blocklist "
                          "violations but %d complete blocks were recovered"
                          % (graph_count, len(graph_complete_entries)))
        # Diagnostic evidence only: the phases run different configurations, so
        # neither identity nor a Protos cause is implied by this comparison.
        strict_methods = sorted(e["method"] for e in entries)
        graph_methods = sorted(e["method"] for e in graph["blocklist_entries"])
        consistent = graph_count == blocklist and graph_methods == strict_methods
        comparison = {
            "strict_count": blocklist,
            "graph_count": graph_count,
            "equal": consistent,
            "only_in_strict": sorted(set(strict_methods) - set(graph_methods)),
            "only_in_graph": sorted(set(graph_methods) - set(strict_methods)),
            "note": "phases use different Native Image configurations; "
                    "diagnostic evidence, not an acceptance condition",
        }
        if graph["sealed_failure"]:
            errors.append("graph phase: GraalVM failed with 'Should not reach here: %s' "
                          "(seal stack trace %s)" % (
                              SEALED_MARKER,
                              "recorded" if graph["seal_trace_available"] else "unavailable"))
        elif graph["failure_kind"] is not None:
            errors.append("graph phase: GraalVM runtime-compilation graph encoding failed "
                          "(%s): %s" % (graph["failure_kind"], graph["failure_cause"]))
    elif graph_skipped_reason is not None:
        errors.append(graph_skipped_reason)

    source = graph if graph is not None else strict
    source_role = "graph" if graph is not None else "strict"
    usable = source["start_error"] is None and not source["timed_out"]
    analysis_complete = (usable and source["analysis_ran"] and source["graph_complete"]
                         and strict["failure_kind"] is None
                         and not (graph is not None and graph["failure_kind"] is not None))
    if usable and source["analysis_ran"]:
        if not source["methods_complete"]:
            errors.append("runtime compile methods section incomplete ('%s' ... '%s')"
                          % (METHODS_START, METHODS_END))
        elif not source["candidates"]:
            errors.append("no runtime compilation candidates were printed")
        if not source["tree_complete"]:
            errors.append("runtime compilation call tree section incomplete ('%s' ... '%s')"
                          % (TREE_START, TREE_END))
        elif not source["tree_entries"]:
            errors.append("runtime compilation call tree section is empty")
        if (source["methods_complete"] or source["tree_complete"]) and not source["protos_roots"]:
            errors.append("no Protos compilable roots (%s) in the analysis" % PROTOS_ROOT_MARKER)
    if not analysis_complete and not errors:
        errors.append("analysis incomplete")

    # Zero is inferred only from complete, error-free evidence.
    graph_reports = bool(graph and (graph["blocklist_headers"] or graph["blocklist_entries"]))
    if (blocklist is None and not headers and not entries and not errors and analysis_complete
            and not graph_reports):
        blocklist = 0
    strict_diagnostic = _is_diagnostic_command((strict.get("run") or {}).get("command"))
    # A disabled check is never evidence of zero violations.
    if (npoc is None and not npoc_headers and not errors and analysis_complete
            and graph is None and strict["exit_code"] == 0 and not strict_diagnostic):
        npoc = 0

    if blocklist:
        violations.append("GraalVM reported %d compilation blocklist violations" % blocklist)
    if npoc:
        violations.append("GraalVM reported %d CompilerAsserts.neverPartOfCompilation "
                          "violations" % npoc)
    if blocklist is None:
        errors.append("blocklist violation count could not be determined")
    if npoc is None:
        errors.append("neverPartOfCompilation violation count could not be determined")

    # ---- Independent properties (detector / GraalVM / inventory / policy) ----
    all_failures = list(strict["native_image_failures"]) + (
        list(graph["native_image_failures"]) if graph is not None else [])
    blocking = _blocking(all_failures)
    graalvm_analysis_complete = bool(analysis_complete and not _blocking(
        source["native_image_failures"]) and not _blocking(strict["native_image_failures"]))
    legacy_tree_complete = bool(analysis_complete and source["tree_complete"])
    call_tree = _call_tree(source_role, source, graalvm_analysis_complete, legacy_tree_complete)
    strict_evidence_valid = (phase_status(strict) in ("completed",
                                                      "never-part-of-compilation-rejection")
                             and not strict_diagnostic)
    global_inventory_complete = bool(
        graalvm_analysis_complete and call_tree["status"] == TREE_COMPLETE
        and strict_evidence_valid and not errors)
    strict_finished = _phase_finished(strict) and not strict_diagnostic

    blocklist_section = {
        "accepted_count": blocklist,
        "accepted_count_state": "KNOWN" if blocklist is not None else UNKNOWN,
        "accepted_from": "strict",
        "exhaustive": bool(strict_finished and blocklist is not None),
        "strict": _blocklist_inventory("strict", strict, blocklist, strict_finished),
        "graph": _blocklist_inventory("graph", graph),
    }
    npoc_section = {
        "accepted_count": npoc,
        "accepted_count_state": "KNOWN" if npoc is not None else UNKNOWN,
        "accepted_from": "strict",
        "exhaustive": bool(strict_finished and npoc is not None),
        "strict": _npoc_inventory("strict", strict, npoc, strict_finished),
        "graph": _npoc_inventory("graph", graph),
    }

    extra = []
    if strict_diagnostic:
        extra.append("strict phase ran with the diagnostic configuration (%s); it cannot "
                     "provide neverPartOfCompilation evidence" % DIAGNOSTIC_PROFILE)
    for failure in all_failures:
        if failure["kind"] != KIND_NPOC_ABORT:
            extra.append("%s phase: %s: %s" % (failure["phase"], failure["kind"],
                                               failure["message"]))
    if not graalvm_analysis_complete:
        extra.append("GraalVM did not complete the required runtime-compilation analysis")
    if not global_inventory_complete:
        extra.append("global runtime-compilation inventory incomplete (official call tree %s)"
                     % call_tree["status"])
    if not blocklist_section["exhaustive"]:
        extra.append("strict phase did not prove an exhaustive blocklist inventory")
    if not npoc_section["exhaustive"]:
        extra.append("strict phase did not prove an exhaustive neverPartOfCompilation inventory")
    for role, facts in (("strict", strict), ("graph", graph)):
        if facts is None:
            continue
        for name, key in (("runtime compile methods", "methods_section"),
                          ("runtime compilation call tree", "tree_section")):
            if facts[key]["repeated"] or facts[key]["nested_start"]:
                extra.append("%s phase: contradiction: %s section delimiters repeated"
                              % (role, name))

    failures = violations + errors
    policy_pass = (not failures and not extra and graalvm_analysis_complete
                   and global_inventory_complete and blocklist == 0 and npoc == 0)
    if not failures and not policy_pass:
        # Keep 'failures' non-empty whenever the verdict is FAIL.
        errors.extend(extra or ["policy conditions not met"])
        failures = violations + errors
    failure_reasons = list(OrderedDict.fromkeys(failures + extra))
    status = "PASS" if policy_pass else "FAIL"
    return {
        "status": status,
        "result": status,
        "exit_code": strict["exit_code"],
        "timed_out": strict["timed_out"] or bool(graph and graph["timed_out"]),
        "analysis_ran": strict["analysis_ran"],
        "analysis_complete": analysis_complete,
        "violations": blocklist,
        "blocklist_violations": blocklist,
        "blocklisted_method_count": len(entries),
        "blocklisted_methods": [e["method"] for e in entries],
        "blocklist_entries": entries,
        "never_part_of_compilation_violations": npoc,
        "never_part_of_compilation_entry_count": len(npoc_entries),
        "never_part_of_compilation_entries": npoc_entries,
        "runtime_compile_methods_complete": source["methods_complete"],
        "runtime_compile_candidates": source["candidates"],
        "call_tree_complete": legacy_tree_complete,
        "protos_roots_detected": source["protos_roots"],
        "graph_phase_ran": graph is not None,
        "blocklist_phases_consistent": consistent,
        "blocklist_phase_comparison": comparison,
        "graph_sealed_failure": bool(graph and graph["sealed_failure"]),
        "graph_failure_kind": graph["failure_kind"] if graph else None,
        "graph_failure_cause": graph["failure_cause"] if graph else None,
        "graph_failure_stack_trace": graph["failure_stack_trace"] if graph else None,
        "graph_failure_call_chain": graph["failure_call_chain"] if graph else None,
        "graph_failure_parsing_contexts": graph["failure_parsing_contexts"] if graph else [],
        "strict_failure_kind": strict["failure_kind"],
        "graph_seal_stack_trace": graph["seal_stack_trace"] if graph else None,
        "graph_seal_trace_available": bool(graph and graph["seal_trace_available"]),
        "phases": {
            "strict": {"status": phase_status(strict), "exit_code": strict["exit_code"],
                       "timed_out": strict["timed_out"],
                       "unexpected_errors": strict["unexpected_errors"]},
            "graph": {"status": phase_status(graph),
                      "exit_code": graph["exit_code"] if graph else None,
                      "timed_out": graph["timed_out"] if graph else False,
                      "unexpected_errors": graph["unexpected_errors"] if graph else []},
        },
        "errors": errors,
        "failures": failures,
        # Independent properties, reported separately.
        "detector_operational": True,
        "detector_error": None,
        "strict_phase": _phase_state("strict", strict),
        "graph_phase": _phase_state("graph", graph, graph_skipped_reason),
        "graalvm_analysis_complete": graalvm_analysis_complete,
        "global_inventory_complete": global_inventory_complete,
        "blocklist": blocklist_section,
        "never_part_of_compilation": npoc_section,
        "observed_reachability_graph": observed_reachability_graph(
            [("strict", strict), ("graph", graph)]),
        "runtime_compilation_call_tree": call_tree,
        "phase_comparison": _phase_comparison(strict, graph, blocklist, graph_count),
        "native_image_failures": all_failures,
        "blocking_failure_kinds": sorted(set(f["kind"] for f in blocking)),
        "failure_reasons": failure_reasons,
        "checks": {
            "PARSER_SUCCESS": True,
            "GRAALVM_ANALYSIS_SUCCESS": graalvm_analysis_complete,
            "GLOBAL_INVENTORY_COMPLETE": global_inventory_complete,
            "POLICY_PASS": policy_pass,
        },
    }


def detector_failure_result(error_text, strict_run=None, graph_run=None):
    """The detector itself failed: nothing is known; never PASS, never zero."""
    reason = "detector internal failure: %s" % error_text.strip().splitlines()[-1]
    unknown_tree = {"status": TREE_INCOMPLETE, "phase": None, "runtime_compile_methods": None,
                    "call_tree": None, "protos_roots_detected": False}
    return {
        "status": "FAIL", "result": "FAIL", "exit_code": (strict_run or {}).get("exit_code"),
        "timed_out": bool((strict_run or {}).get("timed_out")), "analysis_ran": False,
        "analysis_complete": False, "violations": None, "blocklist_violations": None,
        "blocklisted_method_count": None, "blocklisted_methods": [], "blocklist_entries": [],
        "never_part_of_compilation_violations": None,
        "never_part_of_compilation_entry_count": None, "never_part_of_compilation_entries": [],
        "call_tree_complete": False, "graph_phase_ran": graph_run is not None,
        "errors": [reason], "failures": [reason],
        "detector_operational": False, "detector_error": error_text,
        "strict_phase": dict(strict_run or {}, parser="PARSER_FAILURE"),
        "graph_phase": dict(graph_run or {}, parser="PARSER_FAILURE") if graph_run else None,
        "graalvm_analysis_complete": False, "global_inventory_complete": False,
        "blocklist": None, "never_part_of_compilation": None,
        "observed_reachability_graph": None, "runtime_compilation_call_tree": unknown_tree,
        "phase_comparison": None, "native_image_failures": [], "failure_reasons": [reason],
        "checks": {"PARSER_SUCCESS": False, "GRAALVM_ANALYSIS_SUCCESS": False,
                   "GLOBAL_INVENTORY_COMPLETE": False, "POLICY_PASS": False},
    }


def evaluate(output, exit_code, timed_out=False, start_error=None):
    """Single-phase convenience: evaluate one strict run with no graph phase."""
    strict = analyze_phase(output, exit_code, timed_out, start_error)
    reason = ("graph phase required but not run" if graph_phase_required(strict) else None)
    return evaluate_phases(strict, None, reason)


def _inside(path, root):
    try:
        Path(os.path.abspath(path)).relative_to(os.path.abspath(root))
        return True
    except ValueError:
        return False


def run_streaming(command, logs, timeout, terminal=None, popen=subprocess.Popen):
    """Run once; stream every combined line live to the terminal and every log.

    Returns (output, exit_code, timed_out, start_error).
    """
    terminal = terminal if terminal is not None else sys.stdout
    chunks = []

    def emit(text):
        terminal.write(text)
        terminal.flush()
        for log in logs:
            log.write(text)
            log.flush()

    try:
        process = popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                        universal_newlines=True, encoding="utf-8", errors="replace",
                        bufsize=1)
    except (OSError, ValueError) as error:
        message = "failed to start %s: %s\n" % (command, error)
        emit(message)
        return message, None, False, str(error)

    def pump():
        for line in process.stdout:
            chunks.append(line)
            emit(line)

    reader = threading.Thread(target=pump, daemon=True)
    reader.start()
    timed_out = False
    try:
        exit_code = process.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        timed_out = True
        process.terminate()
        try:
            process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
        exit_code = process.returncode
    reader.join(timeout=60)
    if timed_out:
        message = "native runtime compilation check: timeout after %ss\n" % timeout
        chunks.append(message)
        emit(message)
    return "".join(chunks), exit_code, timed_out, None


def _bool(value):
    return "true" if value else "false"


def summary_lines(result):
    def count(value):
        return "unknown" if value is None else str(value)
    tree = (result.get("runtime_compilation_call_tree") or {}).get("status")
    if tree is None:
        tree = TREE_COMPLETE if result.get("call_tree_complete") else TREE_INCOMPLETE
    return [
        "NATIVE_RUNTIME_DETECTOR_OPERATIONAL=%s" % _bool(result.get("detector_operational", True)),
        "NATIVE_RUNTIME_ANALYSIS_COMPLETE=%s"
        % _bool(result.get("graalvm_analysis_complete", result.get("analysis_complete"))),
        "NATIVE_RUNTIME_GLOBAL_INVENTORY_COMPLETE=%s"
        % _bool(result.get("global_inventory_complete", False)),
        "NATIVE_RUNTIME_COMPILATION_BLOCKLIST=%s" % count(result["blocklist_violations"]),
        "NATIVE_RUNTIME_NEVER_PART_OF_COMPILATION=%s"
        % count(result["never_part_of_compilation_violations"]),
        "NATIVE_RUNTIME_COMPILATION_CALL_TREE=%s"
        % (TREE_COMPLETE if tree == TREE_COMPLETE else TREE_INCOMPLETE),
        "NATIVE_RUNTIME_COMPILATION_CHECK=%s" % result["status"],
    ]


def _iso(timestamp):
    return datetime.datetime.fromtimestamp(timestamp, datetime.timezone.utc).isoformat()


def _termination(exit_code, timed_out, start_error):
    if start_error is not None:
        return "start-failed"
    if timed_out:
        return "timeout-terminated"
    if exit_code is None:
        return UNKNOWN
    if exit_code < 0:
        return "signal-%d" % -exit_code
    return "exited-success" if exit_code == 0 else "exited-failure"


def _snapshot_reports(directories):
    snapshot = {}
    for directory in directories:
        if not os.path.isdir(str(directory)):
            continue
        for path in Path(directory).glob(ERROR_REPORT_GLOB):
            try:
                stat = path.stat()
            except OSError:
                continue
            snapshot[os.path.realpath(str(path))] = (stat.st_mtime_ns, stat.st_size)
    return snapshot


def preserve_error_reports(phase, output, before, started, directories, errors_dir):
    """Copy the svm_err_b_*.md reports produced by this phase run into errors_dir.

    A report is accepted only when it is new or changed since the phase began;
    reports of previous runs (even when mentioned) are rejected and recorded.
    Originals are never deleted.
    """
    candidates = OrderedDict()
    for mention in ERROR_REPORT_RE.findall(output):
        path = Path(mention)
        if not path.is_absolute():
            path = Path.cwd() / path
        candidates[os.path.realpath(str(path))] = True
    for directory in directories:
        if os.path.isdir(str(directory)):
            for path in sorted(Path(directory).glob(ERROR_REPORT_GLOB)):
                candidates.setdefault(os.path.realpath(str(path)), False)
    preserved, rejected = [], []
    for source, mentioned in candidates.items():
        if not os.path.isfile(source):
            if mentioned:
                rejected.append({"phase": phase, "source": source,
                                 "reason": "mentioned in output but not found"})
            continue
        stat = os.stat(source)
        fresh = (before.get(source) != (stat.st_mtime_ns, stat.st_size)
                 and stat.st_mtime >= started - 1.0)
        if not fresh:
            if mentioned:
                rejected.append({"phase": phase, "source": source,
                                 "reason": "predates this phase run (stale report)"})
            continue
        Path(errors_dir).mkdir(parents=True, exist_ok=True)
        destination = Path(errors_dir) / ("%s-%s" % (phase, os.path.basename(source)))
        suffix = 1
        while destination.exists():
            destination = Path(errors_dir) / ("%s-%d-%s" % (phase, suffix,
                                                            os.path.basename(source)))
            suffix += 1
        shutil.copyfile(source, str(destination))
        data = destination.read_bytes()
        preserved.append({"phase": phase, "source": source, "preserved_path": str(destination),
                          "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                          "mentioned_in_output": mentioned})
    return preserved, rejected


def _run_phase(name, command, main_log, phase_log, timeout, terminal, popen,
               report_dirs=(), errors_dir=None):
    banner = "\n===== native runtime compilation: %s phase: %s =====\n" % (
        name, " ".join(command))
    terminal.write(banner)
    main_log.write(banner)
    before = _snapshot_reports(report_dirs)
    started = time.time()
    with open(phase_log, "w", encoding="utf-8") as log:
        output, code, timed_out, start_error = run_streaming(
            command, [main_log, log], timeout, terminal=terminal, popen=popen)
    finished = time.time()
    preserved, rejected = ([], []) if errors_dir is None else preserve_error_reports(
        name, output, before, started, report_dirs, errors_dir)
    trailer = "===== %s phase finished: exit=%s timed_out=%s =====\n" % (name, code, timed_out)
    terminal.write(trailer)
    main_log.write(trailer)
    run = {"command": list(command), "log": str(phase_log), "started_at": _iso(started),
           "finished_at": _iso(finished), "duration_seconds": round(finished - started, 3),
           "exit_code": code, "timed_out": timed_out, "start_error": start_error,
           "termination": _termination(code, timed_out, start_error),
           "error_reports": preserved, "rejected_error_reports": rejected}
    return output, run


def _analyze_run(name, output, run):
    facts = analyze_phase(output, run["exit_code"], run["timed_out"], run["start_error"],
                          phase=name)
    facts["run"] = run
    return facts


def default_report_dirs(artifact_root):
    """Where Native Image may write svm_err_b_*.md: working directory and image dirs."""
    root = Path(artifact_root)
    return [Path.cwd(), root / "image", root / "graph-image"]


def check(report, log, timeout, command, graph_command=None,
          artifact_root=DEFAULT_ARTIFACT_ROOT, strict_log=None, graph_log=None,
          terminal=None, popen=subprocess.Popen, report_dirs=None):
    terminal = terminal if terminal is not None else sys.stdout
    if not command:
        raise SystemExit("check: missing strict-phase Maven command")
    if _is_diagnostic_command(command):
        raise SystemExit("check: the strict-phase command must not use the diagnostic "
                         "configuration (%s / %s)" % (DIAGNOSTIC_PROFILE, NPOC_DISABLE_OPTION))
    strict_log = strict_log or str(Path(log).parent / "strict-analysis.log")
    graph_log = graph_log or str(Path(log).parent / "graph-analysis.log")
    errors_dir = Path(artifact_root) / "errors"
    for path in (report, log, strict_log, graph_log):
        if not _inside(path, artifact_root):
            raise SystemExit("check: %s must be inside %s" % (path, artifact_root))
    Path(artifact_root).mkdir(parents=True, exist_ok=True)
    # Never let a previous diagnostic of either phase survive a new run.
    for stale in (report, log, strict_log, graph_log):
        if Path(stale).exists():
            Path(stale).unlink()
    if errors_dir.exists():
        shutil.rmtree(str(errors_dir))
    report_dirs = default_report_dirs(artifact_root) if report_dirs is None else report_dirs

    graph = None
    skipped = None
    strict_run = graph_run = None
    try:
        with open(log, "w", encoding="utf-8") as main_log:
            output, strict_run = _run_phase("strict", list(command), main_log, strict_log,
                                            timeout, terminal, popen, report_dirs, errors_dir)
            strict = _analyze_run("strict", output, strict_run)
            if graph_phase_required(strict):
                if graph_command:
                    output, graph_run = _run_phase("graph", list(graph_command), main_log,
                                                   graph_log, timeout, terminal, popen,
                                                   report_dirs, errors_dir)
                    graph = _analyze_run("graph", output, graph_run)
                else:
                    skipped = "graph phase required but no graph command was provided"
        result = evaluate_phases(strict, graph, skipped)
    except Exception:  # noqa: BLE001 - a detector defect must never become PASS
        result = detector_failure_result(traceback.format_exc(), strict_run, graph_run)

    document = dict(result)
    document["schema"] = SCHEMA
    document["log"] = str(log)
    document["logs"] = {"main": str(log), "strict": str(strict_log),
                        "graph": str(graph_log) if graph_run is not None else None}
    document["command"] = list(command)
    document["commands"] = {"strict": list(command),
                            "graph": list(graph_command) if graph_command else None}
    document["timeout_seconds"] = timeout
    document["timeout_scope"] = "per phase"
    document["diagnostic_artifacts"] = {
        "logs": document["logs"],
        "errors_dir": str(errors_dir),
        "searched_report_dirs": [str(d) for d in report_dirs],
        "error_reports": [r for run in (strict_run, graph_run) if run
                          for r in run["error_reports"]],
        "rejected_error_reports": [r for run in (strict_run, graph_run) if run
                                   for r in run["rejected_error_reports"]],
    }
    with open(report, "w", encoding="utf-8") as handle:
        json.dump(document, handle, indent=2, sort_keys=True)
        handle.write("\n")

    terminal.write("\n")
    for failure in result["failures"]:
        terminal.write("native runtime compilation check: %s\n" % failure)
    for entry in result["blocklist_entries"]:
        terminal.write("blocklisted: %s\n" % entry["method"])
    for entry in result["never_part_of_compilation_entries"]:
        terminal.write("neverPartOfCompilation called from: %s\n" % entry["called_from"])
    for failure in result.get("native_image_failures") or []:
        terminal.write("native image failure [%s] %s: %s\n"
                       % (failure["phase"], failure["kind"], failure["message"]))
    for preserved in document["diagnostic_artifacts"]["error_reports"]:
        terminal.write("native image error report [%s]: %s\n"
                       % (preserved["phase"], preserved["preserved_path"]))
    terminal.write("full diagnostic: %s\nreport: %s\n" % (log, report))
    for line in summary_lines(result):
        terminal.write(line + "\n")
    terminal.flush()
    return 0 if result["status"] == "PASS" else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="mode")
    run = sub.add_parser("check", help="run the strict (and if needed graph) analysis")
    run.add_argument("--report", default=str(DEFAULT_ARTIFACT_ROOT / "report.json"))
    run.add_argument("--log", default=str(DEFAULT_ARTIFACT_ROOT / "analysis.log"))
    run.add_argument("--strict-log", default=str(DEFAULT_ARTIFACT_ROOT / "strict-analysis.log"))
    run.add_argument("--graph-log", default=str(DEFAULT_ARTIFACT_ROOT / "graph-analysis.log"))
    run.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT,
                     help="seconds allowed for each phase")
    run.add_argument("--artifact-root", default=str(DEFAULT_ARTIFACT_ROOT))
    run.add_argument("--strict-command", required=False,
                     help="Phase A Maven command (split without a shell)")
    run.add_argument("--graph-command", required=False,
                     help="Phase B Maven command (split without a shell)")
    args = parser.parse_args(argv)
    if args.mode != "check":
        parser.print_help()
        return 2
    return check(args.report, args.log, args.timeout,
                 shlex.split(args.strict_command or ""),
                 graph_command=shlex.split(args.graph_command) if args.graph_command else None,
                 artifact_root=args.artifact_root, strict_log=args.strict_log,
                 graph_log=args.graph_log)


if __name__ == "__main__":
    sys.exit(main())
