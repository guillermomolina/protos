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

"""Focused PLAT045 (#772) checks for the extracted Native distribution gate."""

from __future__ import annotations

from pathlib import Path
import sys
import unittest

DIST = Path(__file__).resolve().parent
if str(DIST) not in sys.path:
    sys.path.insert(0, str(DIST))

import validate_native
from validate_native import (
    count_interpreter_only_evidence,
    interpreter_only_failure,
    interpreter_only_probe_source,
    unexpected_stderr,
    FALLBACK_RUNTIME_WARNING_STDERR,
)


FALLBACK_WARNING = (
    "[engine] WARNING: The polyglot engine uses a fallback runtime that does "
    "not support runtime compilation to native code.\n"
)


def counts(**overrides: int) -> dict[str, int]:
    values = {
        "fallback_markers": 1,
        "opt_done": 0,
        "opt_failed": 0,
        "frame_failures": 0,
        "compilation_failures": 0,
    }
    values.update(overrides)
    return values


class InterpreterOnlyClassificationTest(unittest.TestCase):
    def test_accepts_fallback_without_compilation(self) -> None:
        self.assertIsNone(interpreter_only_failure(counts()))

    def test_rejects_missing_fallback_marker(self) -> None:
        self.assertIsNotNone(
            interpreter_only_failure(counts(fallback_markers=0))
        )

    def test_rejects_unexpected_opt_done(self) -> None:
        self.assertIsNotNone(interpreter_only_failure(counts(opt_done=1)))

    def test_rejects_opt_failed(self) -> None:
        self.assertIsNotNone(interpreter_only_failure(counts(opt_failed=1)))

    def test_rejects_frame_without_boxing_failure(self) -> None:
        self.assertIsNotNone(
            interpreter_only_failure(counts(frame_failures=1))
        )

    def test_rejects_compilation_failure(self) -> None:
        self.assertIsNotNone(
            interpreter_only_failure(counts(compilation_failures=1))
        )


class InterpreterOnlyEvidenceParsingTest(unittest.TestCase):
    def test_clean_fallback_output_is_accepted(self) -> None:
        observed = count_interpreter_only_evidence(FALLBACK_WARNING + "1\n")
        self.assertEqual(counts(), observed)
        self.assertIsNone(interpreter_only_failure(observed))

    def test_output_without_warning_has_no_fallback_marker(self) -> None:
        observed = count_interpreter_only_evidence("1\n")
        self.assertEqual(0, observed["fallback_markers"])
        self.assertIsNotNone(interpreter_only_failure(observed))

    def test_counts_compiler_failure_lines(self) -> None:
        observed = count_interpreter_only_evidence(
            FALLBACK_WARNING
            + "[engine] opt done   f |Tier 2\n"
            + "[engine] opt failed g |Tier 2\n"
            + "FrameWithoutBoxing should not be materialized\n"
            + "Compilation failed\n"
            + "Internal error\n"
        )
        self.assertEqual(
            counts(
                opt_done=1,
                opt_failed=1,
                frame_failures=1,
                compilation_failures=2,
            ),
            observed,
        )


class FallbackWarningStderrTest(unittest.TestCase):
    def test_exact_fallback_warning_is_expected(self) -> None:
        self.assertEqual("", unexpected_stderr(FALLBACK_RUNTIME_WARNING_STDERR))

    def test_warning_counts_as_fallback_marker(self) -> None:
        observed = count_interpreter_only_evidence(
            FALLBACK_RUNTIME_WARNING_STDERR
        )
        self.assertEqual(1, observed["fallback_markers"])

    def test_empty_stderr_is_accepted(self) -> None:
        self.assertEqual("", unexpected_stderr(""))

    def test_other_stderr_after_warning_is_rejected(self) -> None:
        self.assertEqual(
            "boom\n",
            unexpected_stderr(FALLBACK_RUNTIME_WARNING_STDERR + "boom\n"),
        )

    def test_other_stderr_without_warning_is_rejected(self) -> None:
        self.assertEqual("boom\n", unexpected_stderr("boom\n"))

    def test_altered_warning_is_rejected(self) -> None:
        altered = FALLBACK_RUNTIME_WARNING_STDERR.replace(
            "explicitly selected", "selected"
        )
        self.assertNotEqual("", unexpected_stderr(altered))


class StaleForcedTier2GateAbsenceTest(unittest.TestCase):
    def test_forced_guest_jit_gate_is_absent(self) -> None:
        self.assertFalse(hasattr(validate_native, "validate_forced_guest_jit"))

    def test_validator_has_no_tier2_requirement_or_marker(self) -> None:
        text = Path(validate_native.__file__).read_text(encoding="utf-8")
        for stale in (
            "CompileImmediately",
            "TraceCompilation",
            "NATIVE_DIST_FORCED_",
            "HELPER_BYTECODE_ROOT_TIER2",
            "SEMANTIC_BYTECODE_ROOT_TIER2",
            "Tier 2",
        ):
            self.assertNotIn(stale, text)

    def test_probe_exercises_both_bytecode_root_roles(self) -> None:
        source = interpreter_only_probe_source()
        self.assertEqual(32, source.splitlines().count("f()"))
        self.assertEqual(32, source.splitlines().count("g()"))


if __name__ == "__main__":
    unittest.main()
