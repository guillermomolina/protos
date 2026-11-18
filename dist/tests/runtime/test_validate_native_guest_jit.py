# THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
# ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
# DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
# DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
# OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
# THIS LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
# OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
# THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
# FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
# https://github.com/guillermomolina/protos
#
# Software distributed under the License is distributed on an "AS IS" basis,
# WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
# the specific language governing rights and limitations under the License.

"""BUG036: extracted Native distribution requires guest Tier-2 JIT."""

from __future__ import annotations

import contextlib
import io
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

DIST = Path(__file__).resolve().parents[2]
if str(DIST) not in sys.path:
    sys.path.insert(0, str(DIST))

import validate_native
from validate_native import (
    GUEST_JIT_OPTIONS,
    TRUFFLE_LOG_REDIRECTION_HINT,
    count_guest_jit_evidence,
    guest_jit_failure,
    guest_jit_probe_source,
    unexpected_guest_jit_stderr,
    validate_forced_guest_jit,
)


BYTECODE_DONE = (
    "[engine] opt done f ProtosBytecodeRootNodeGen |Tier 2\n"
)
BUILTIN_DONE = (
    "[engine] opt done g ProtosBuiltinCallRootNode |Tier 2\n"
)
GOOD_TRACE = (
    "[engine] opt queued f |Tier 2\n"
    "[engine] opt start f |Tier 2\n"
    + BYTECODE_DONE
    + BUILTIN_DONE
)
FALLBACK_WARNING = (
    "[engine] WARNING: The polyglot engine uses a fallback runtime "
    "that does not support runtime compilation to native code.\n"
)


def evidence(**overrides: int) -> dict[str, int]:
    result = {
        "fallback_markers": 0,
        "opt_done": 2,
        "opt_failed": 0,
        "frame_failures": 0,
        "compilation_failures": 0,
        "bytecode_root_tier2": 1,
        "builtin_call_root_tier2": 1,
    }
    result.update(overrides)
    return result


class GuestJitEvidenceTest(unittest.TestCase):
    def test_positive_two_root_families(self) -> None:
        observed = count_guest_jit_evidence(GOOD_TRACE)
        self.assertEqual(evidence(), observed)
        self.assertIsNone(guest_jit_failure(observed))

    def test_tier_one_does_not_count(self) -> None:
        observed = count_guest_jit_evidence(
            "[engine] opt done ProtosBytecodeRootNodeGen |Tier 1\n"
            "[engine] opt done ProtosBuiltinCallRootNode |Tier 1\n"
        )
        self.assertEqual(0, observed["bytecode_root_tier2"])
        self.assertEqual(0, observed["builtin_call_root_tier2"])
        self.assertIsNotNone(guest_jit_failure(observed))

    def test_only_bytecode_root_fails(self) -> None:
        self.assertIn(
            "builtin-call",
            guest_jit_failure(evidence(builtin_call_root_tier2=0)),
        )

    def test_only_builtin_root_fails(self) -> None:
        self.assertIn(
            "Bytecode DSL",
            guest_jit_failure(evidence(bytecode_root_tier2=0)),
        )

    def test_multiple_compilations_of_one_family_are_insufficient(self) -> None:
        observed = count_guest_jit_evidence(BYTECODE_DONE * 3)
        self.assertEqual(3, observed["bytecode_root_tier2"])
        self.assertEqual(0, observed["builtin_call_root_tier2"])
        self.assertIsNotNone(guest_jit_failure(observed))

    def test_zero_successful_compilations_fail(self) -> None:
        self.assertIsNotNone(
            guest_jit_failure(evidence(
                opt_done=0,
                bytecode_root_tier2=0,
                builtin_call_root_tier2=0,
            ))
        )

    def test_fallback_runtime_fails(self) -> None:
        observed = count_guest_jit_evidence(
            GOOD_TRACE + FALLBACK_WARNING
        )
        self.assertEqual(1, observed["fallback_markers"])
        self.assertIn("fallback", guest_jit_failure(observed))

    def test_opt_failed_fails(self) -> None:
        observed = count_guest_jit_evidence(
            GOOD_TRACE + "[engine] opt failed f |Tier 2\n"
        )
        self.assertEqual(1, observed["opt_failed"])
        self.assertIsNotNone(guest_jit_failure(observed))

    def test_compilation_failure_fails(self) -> None:
        observed = count_guest_jit_evidence(
            GOOD_TRACE + "Compilation failed\nInternal error\n"
        )
        self.assertEqual(2, observed["compilation_failures"])
        self.assertIsNotNone(guest_jit_failure(observed))

    def test_frame_without_boxing_failure_fails(self) -> None:
        observed = count_guest_jit_evidence(
            GOOD_TRACE
            + "FrameWithoutBoxing should not be materialized\n"
        )
        self.assertEqual(1, observed["frame_failures"])
        self.assertIsNotNone(guest_jit_failure(observed))

    def test_reversed_frame_failure_is_detected(self) -> None:
        observed = count_guest_jit_evidence(
            GOOD_TRACE
            + "should not be materialized: FrameWithoutBoxing\n"
        )
        self.assertEqual(1, observed["frame_failures"])

    def test_compiler_trace_is_expected_stderr(self) -> None:
        self.assertEqual(
            "",
            unexpected_guest_jit_stderr(GOOD_TRACE),
        )

    def test_exact_log_hint_is_expected(self) -> None:
        self.assertEqual(
            "",
            unexpected_guest_jit_stderr(
                TRUFFLE_LOG_REDIRECTION_HINT + GOOD_TRACE
            ),
        )

    def test_unexpected_stderr_is_rejected(self) -> None:
        self.assertEqual(
            "unexpected diagnostic\n",
            unexpected_guest_jit_stderr(
                GOOD_TRACE + "unexpected diagnostic\n"
            ),
        )

    def test_fallback_warning_is_not_suppressed(self) -> None:
        self.assertEqual(
            FALLBACK_WARNING,
            unexpected_guest_jit_stderr(
                TRUFFLE_LOG_REDIRECTION_HINT + FALLBACK_WARNING
            ),
        )


    def test_unknown_compiler_event_is_unexpected_stderr(self) -> None:
        diagnostic = "[engine] opt unexpected diagnostic\\n"
        self.assertEqual(
            diagnostic,
            unexpected_guest_jit_stderr(GOOD_TRACE + diagnostic),
        )

    def test_exact_probe_workload(self) -> None:
        source = guest_jit_probe_source()
        self.assertEqual(500, source.splitlines().count("f()"))
        self.assertEqual(500, source.splitlines().count("g()"))
        self.assertTrue(source.endswith("print(f())\n"))


class ExtractedGuestJitExecutionTest(unittest.TestCase):
    def run_probe(
        self,
        *,
        returncode: int = 0,
        stdout: str = "1\n",
        stderr: str = GOOD_TRACE,
    ) -> str:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            dist = root / "relocated"
            native = dist / "libexec/protos-native"
            cwd = root / "caller"
            cwd.mkdir()

            output = io.StringIO()

            def execute(command, **kwargs):
                self.assertEqual(str(native), command[0])
                self.assertEqual(
                    list(GUEST_JIT_OPTIONS),
                    command[1:-1],
                )
                source = Path(command[-1])
                self.assertTrue(source.is_file())
                self.assertTrue(source.is_relative_to(cwd))
                self.assertEqual(
                    guest_jit_probe_source(),
                    source.read_text(encoding="utf-8"),
                )
                self.assertEqual(cwd, kwargs["cwd"])
                self.assertEqual(str(dist), kwargs["env"]["PROTOS_HOME"])
                self.assertTrue(kwargs["capture_output"])
                self.assertTrue(kwargs["text"])
                self.assertFalse(kwargs["check"])
                return subprocess.CompletedProcess(
                    command, returncode, stdout, stderr
                )

            with mock.patch.object(
                validate_native.subprocess,
                "run",
                side_effect=execute,
            ) as invoked:
                with contextlib.redirect_stdout(output):
                    validate_forced_guest_jit(
                        native,
                        dist,
                        cwd=cwd,
                        env={"PROTOS_HOME": "wrong-home"},
                    )
                invoked.assert_called_once()

            return output.getvalue()

    def test_positive_extracted_native_admission(self) -> None:
        output = self.run_probe()
        for marker in (
            "NATIVE_DIST_GUEST_JIT_STATUS=0",
            "NATIVE_DIST_GUEST_JIT_RESULT_OK=1",
            "NATIVE_DIST_FALLBACK_RUNTIME_MARKERS=0",
            "NATIVE_DIST_OPT_DONE=2",
            "NATIVE_DIST_BYTECODE_ROOT_TIER2_COMPILATIONS=1",
            "NATIVE_DIST_BUILTIN_CALL_ROOT_TIER2_COMPILATIONS=1",
            "NATIVE_DIST_GUEST_JIT_CHECK: PASS",
            "NATIVE_DIST_GUEST_JIT=TIER2_REQUIRED",
        ):
            self.assertIn(marker, output)

    def test_nonzero_status_fails(self) -> None:
        with self.assertRaisesRegex(SystemExit, "status 7"):
            self.run_probe(returncode=7)

    def test_wrong_result_fails(self) -> None:
        with self.assertRaisesRegex(SystemExit, "program result"):
            self.run_probe(stdout="2\n")

    def test_missing_result_fails(self) -> None:
        with self.assertRaisesRegex(SystemExit, "program result"):
            self.run_probe(stdout="")

    def test_extra_stdout_fails(self) -> None:
        with self.assertRaisesRegex(SystemExit, "program result"):
            self.run_probe(stdout="1\nunexpected\n")

    def test_missing_bytecode_tier2_fails(self) -> None:
        with self.assertRaisesRegex(SystemExit, "Bytecode DSL"):
            self.run_probe(stderr=BUILTIN_DONE)

    def test_missing_builtin_tier2_fails(self) -> None:
        with self.assertRaisesRegex(SystemExit, "builtin-call"):
            self.run_probe(stderr=BYTECODE_DONE)

    def test_fallback_runtime_fails(self) -> None:
        with self.assertRaisesRegex(SystemExit, "fallback runtime"):
            self.run_probe(stderr=GOOD_TRACE + FALLBACK_WARNING)

    def test_compilation_failure_fails(self) -> None:
        with self.assertRaisesRegex(SystemExit, "compilation failure"):
            self.run_probe(
                stderr=GOOD_TRACE + "Compilation failed\n"
            )

    def test_opt_failure_fails(self) -> None:
        with self.assertRaisesRegex(SystemExit, "opt failed"):
            self.run_probe(
                stderr=GOOD_TRACE + "[engine] opt failed f |Tier 2\n"
            )

    def test_frame_failure_fails(self) -> None:
        with self.assertRaisesRegex(SystemExit, "FrameWithoutBoxing"):
            self.run_probe(
                stderr=GOOD_TRACE
                + "FrameWithoutBoxing should not be materialized\n"
            )

    def test_unexpected_stderr_fails(self) -> None:
        with self.assertRaisesRegex(SystemExit, "unexpected stderr"):
            self.run_probe(stderr=GOOD_TRACE + "unexpected warning\n")


    def test_unknown_compiler_event_fails_execution(self) -> None:
        with self.assertRaisesRegex(SystemExit, "unexpected stderr"):
            self.run_probe(
                stderr=GOOD_TRACE
                + "[engine] opt unexpected diagnostic\\n"
            )

    def test_log_redirection_hint_is_accepted(self) -> None:
        output = self.run_probe(
            stderr=TRUFFLE_LOG_REDIRECTION_HINT + GOOD_TRACE
        )
        self.assertIn("NATIVE_DIST_GUEST_JIT_CHECK: PASS", output)


if __name__ == "__main__":
    unittest.main()
