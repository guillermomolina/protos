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

"""Focused I170 checks for the installed Native-FFM validator gate."""

from __future__ import annotations

from pathlib import Path
import contextlib
import io
import sys
import tempfile
import unittest

DIST = Path(__file__).resolve().parents[2]
if str(DIST) not in sys.path:
    sys.path.insert(0, str(DIST))

import validate_native
from validate_native import bundled_shared_objects, classify_ffm_case


class ClassifyFfmCaseTest(unittest.TestCase):
    def test_success_needs_status_and_marker(self) -> None:
        self.assertEqual(
            ("PASS", ""),
            classify_ffm_case(0, "ffm-sqlite=true\n", "", expected_status=0,
                              expected_stdout="ffm-sqlite=true"),
        )

    def test_missing_marker_is_not_a_false_pass(self) -> None:
        verdict, reason = classify_ffm_case(
            0, "", "", expected_status=0, expected_stdout="ffm-sqlite=true")
        self.assertEqual("FAIL", verdict)
        self.assertIn("missing", reason)

    def test_refusal_classification(self) -> None:
        self.assertEqual(
            "PASS",
            classify_ffm_case(2, "", "usage", expected_status=2, expected_stdout=None)[0],
        )
        self.assertEqual(
            "FAIL",
            classify_ffm_case(2, "ran\n", "", expected_status=2, expected_stdout=None)[0],
        )
        self.assertEqual(
            "FAIL",
            classify_ffm_case(0, "ran\n", "", expected_status=2, expected_stdout=None)[0],
        )

    def test_missing_library_category(self) -> None:
        self.assertEqual(
            "PASS",
            classify_ffm_case(0, "library-unavailable\n", "", expected_status=0,
                              expected_stdout="library-unavailable")[0],
        )
        self.assertEqual(
            "FAIL",
            classify_ffm_case(1, "", "ForeignError", expected_status=0,
                              expected_stdout="library-unavailable")[0],
        )

    def test_absent_provider_is_a_failure(self) -> None:
        # An executable without the integrated provider cannot route ffi:.
        verdict, _ = classify_ffm_case(
            1, "", "Error: no provider for scheme ffi", expected_status=0,
            expected_stdout="ffm-libc")
        self.assertEqual("FAIL", verdict)

    def test_unsupported_signature_must_fail_cleanly(self) -> None:
        self.assertEqual(
            "PASS",
            classify_ffm_case(0, "ffm-unsupported-refused\n", "", expected_status=0,
                              expected_stdout="ffm-unsupported-refused")[0],
        )
        for marker in ("MissingForeignRegistrationError", "Internal error",
                       "UnsatisfiedLinkError"):
            verdict, reason = classify_ffm_case(
                0, "ffm-unsupported-refused\n", "x " + marker, expected_status=0,
                expected_stdout="ffm-unsupported-refused")
            self.assertEqual("FAIL", verdict, marker)
            self.assertIn(marker, reason)


class FixedWidthFixtureTest(unittest.TestCase):
    def test_missing_compiler_is_a_failure_not_a_pass(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            with contextlib.redirect_stdout(io.StringIO()), \
                    contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit):
                    validate_native.compile_ffm_fixtures(None, Path(raw))

    def test_fixture_sources_and_programs_exist(self) -> None:
        for _, stem in validate_native.FFM_FIXTURES:
            self.assertTrue((validate_native.FFM_CASES / "native" / (stem + ".c")).is_file())
        for program, marker in (
                ("ffm-layouts.protos", validate_native.FFM_LAYOUTS_MARKER),
                ("ffm-scalar-layout-abi.protos",
                 validate_native.FFM_SCALAR_LAYOUT_ABI_MARKER),
                ("ffm-root-actor.protos", validate_native.FFM_ROOT_ACTOR_MARKER),
                ("ffm-pointers.protos", validate_native.FFM_POINTERS_MARKER),
                ("ffm-native-memory.protos", validate_native.FFM_NATIVE_MEMORY_MARKER)):
            text = (validate_native.FFM_CASES / program).read_text(encoding="utf-8")
            self.assertIn('print("' + marker + '")', text)
        self.assertNotIn("ffi:sqlite3", validate_native.FFM_UNREGISTERED_SCALAR)

    def test_missing_scalar_markers_are_failures(self) -> None:
        for marker in (validate_native.FFM_LAYOUTS_MARKER,
                       validate_native.FFM_SCALAR_LAYOUT_ABI_MARKER,
                       "ffm-unregistered-scalar-refused"):
            self.assertEqual(
                "FAIL",
                classify_ffm_case(0, "", "", expected_status=0, expected_stdout=marker)[0],
            )
            self.assertEqual(
                "FAIL",
                classify_ffm_case(0, marker + "\n", "MissingForeignRegistrationError",
                                  expected_status=0, expected_stdout=marker)[0],
            )


class UnregisteredScalarSignatureTest(unittest.TestCase):
    MARKER = "ffm-unregistered-scalar-refused"

    def verdict(self, returncode: int, stdout: str, stderr: str) -> str:
        return classify_ffm_case(returncode, stdout, stderr, expected_status=0,
                                 expected_stdout=self.MARKER)[0]

    def test_clean_ordinary_refusal_passes(self) -> None:
        self.assertEqual("PASS", self.verdict(0, self.MARKER + "\n", ""))

    def test_unsuccessful_exit_fails(self) -> None:
        self.assertEqual("FAIL", self.verdict(1, self.MARKER + "\n", ""))

    def test_missing_marker_fails(self) -> None:
        self.assertEqual("FAIL", self.verdict(0, "", ""))

    def test_missing_registration_error_fails(self) -> None:
        self.assertEqual(
            "FAIL",
            self.verdict(0, self.MARKER + "\n", "MissingForeignRegistrationError: F(F,F)"))

    def test_foreign_entry_failure_fails(self) -> None:
        self.assertEqual(
            "FAIL",
            self.verdict(0, validate_native.FFM_FOREIGN_ENTRY_MARKER + "\n", ""))
        self.assertEqual(
            "FAIL",
            self.verdict(0, validate_native.FFM_FOREIGN_ENTRY_MARKER + "\n" + self.MARKER + "\n",
                         ""))

    def test_program_requires_an_exact_ordinary_error(self) -> None:
        program = validate_native.FFM_UNREGISTERED_SCALAR
        self.assertIn("refused.parent() === Error", program)
        self.assertIn("calls() == before", program)
        self.assertIn(validate_native.FFM_FOREIGN_ENTRY_MARKER, program)
        self.assertNotIn("MissingForeignRegistrationError", program)


class RootActorProgramTest(unittest.TestCase):
    MARKER = validate_native.FFM_ROOT_ACTOR_MARKER

    def verdict(self, returncode: int, stdout: str, stderr: str) -> str:
        return classify_ffm_case(returncode, stdout, stderr, expected_status=0,
                                 expected_stdout=self.MARKER)[0]

    def test_worker_module_exists(self) -> None:
        self.assertTrue((validate_native.FFM_CASES / "ffm-root-actor-worker.protos").is_file())

    def test_success_needs_status_and_marker(self) -> None:
        self.assertEqual("PASS", self.verdict(0, self.MARKER + "\n", ""))
        self.assertEqual("FAIL", self.verdict(1, self.MARKER + "\n", ""))
        self.assertEqual("FAIL", self.verdict(0, "", ""))

    def test_sqlite_lifecycle_programs_exist(self) -> None:
        for name in ("sqlite-lifecycle.protos", "sqlite-lifecycle-worker.protos"):
            self.assertTrue((validate_native.FFM_CASES / name).is_file(), name)
        text = (validate_native.FFM_CASES / "sqlite-lifecycle.protos").read_text(encoding="utf-8")
        self.assertIn('print("LIB029-LIFECYCLE-PASS")', text)

    def test_sqlite_lifecycle_classification(self) -> None:
        marker = "LIB029-LIFECYCLE-PASS"

        def verdict(returncode: int, stdout: str, stderr: str) -> str:
            return classify_ffm_case(returncode, stdout, stderr, expected_status=0,
                                     expected_stdout=marker)[0]

        self.assertEqual("PASS", verdict(0, marker + "\n", ""))
        self.assertEqual("FAIL", verdict(0, "", ""))
        self.assertEqual("FAIL", verdict(1, marker + "\n", ""))
        self.assertEqual("FAIL", verdict(0, marker + "\n", "MissingForeignRegistrationError"))
        self.assertEqual(
            "FAIL",
            verdict(0, validate_native.FFM_FOREIGN_ENTRY_MARKER + "\n" + marker + "\n", ""))

    def test_uncontrolled_failures_fail(self) -> None:
        self.assertEqual(
            "FAIL",
            self.verdict(0, validate_native.FFM_FOREIGN_ENTRY_MARKER + "\n" + self.MARKER, ""))
        self.assertEqual(
            "FAIL",
            self.verdict(0, self.MARKER + "\n", "MissingForeignRegistrationError"))


class PointerProgramTest(unittest.TestCase):
    MARKER = validate_native.FFM_POINTERS_MARKER

    def verdict(self, returncode: int, stdout: str, stderr: str) -> str:
        return classify_ffm_case(returncode, stdout, stderr, expected_status=0,
                                 expected_stdout=self.MARKER)[0]

    def test_program_uses_both_fixture_aliases(self) -> None:
        text = (validate_native.FFM_CASES / "ffm-pointers.protos").read_text(encoding="utf-8")
        self.assertIn('import("ffi:first")', text)
        self.assertIn('import("ffi:second")', text)
        self.assertIn('print("' + self.MARKER + '")', text)

    def test_success_needs_status_and_marker(self) -> None:
        self.assertEqual("PASS", self.verdict(0, self.MARKER + "\n", ""))

    def test_missing_marker_fails(self) -> None:
        self.assertEqual("FAIL", self.verdict(0, "", ""))

    def test_unsuccessful_exit_fails(self) -> None:
        self.assertEqual("FAIL", self.verdict(1, self.MARKER + "\n", ""))

    def test_foreign_entry_failure_fails(self) -> None:
        self.assertEqual(
            "FAIL",
            self.verdict(0, validate_native.FFM_FOREIGN_ENTRY_MARKER + "\n" + self.MARKER + "\n",
                         ""))

    def test_missing_registration_error_fails(self) -> None:
        self.assertEqual(
            "FAIL",
            self.verdict(0, self.MARKER + "\n", "MissingForeignRegistrationError: P(I)"))


class NativeMemoryProgramTest(unittest.TestCase):
    MARKER = validate_native.FFM_NATIVE_MEMORY_MARKER

    def verdict(self, returncode: int, stdout: str, stderr: str) -> str:
        return classify_ffm_case(returncode, stdout, stderr, expected_status=0,
                                 expected_stdout=self.MARKER)[0]

    def test_program_allocates_before_importing_a_library(self) -> None:
        text = (validate_native.FFM_CASES / "ffm-native-memory.protos").read_text(
            encoding="utf-8")
        allocation = text.index("FFI.allocate(")
        self.assertLess(allocation, text.index('import("ffi:first")'))
        self.assertIn('print("' + self.MARKER + '")', text)
        self.assertEqual(1, text.count('print("' + self.MARKER + '")'))

    def test_success_needs_status_and_marker(self) -> None:
        self.assertEqual("PASS", self.verdict(0, self.MARKER + "\n", ""))

    def test_missing_marker_fails(self) -> None:
        self.assertEqual("FAIL", self.verdict(0, "", ""))

    def test_incorrect_marker_fails(self) -> None:
        self.assertEqual("FAIL", self.verdict(0, "FFM-NATIVE-MEMORY-FAIL\n", ""))
        self.assertEqual("FAIL", self.verdict(0, validate_native.FFM_POINTERS_MARKER + "\n", ""))

    def test_unsuccessful_exit_fails(self) -> None:
        self.assertEqual("FAIL", self.verdict(1, self.MARKER + "\n", ""))

    def test_incomplete_run_fails(self) -> None:
        self.assertEqual("FAIL", self.verdict(1, "", "CheckFailure"))
        self.assertEqual(
            "FAIL",
            self.verdict(0, validate_native.FFM_FOREIGN_ENTRY_MARKER + "\n" + self.MARKER + "\n",
                         ""))

    def test_missing_registration_error_fails(self) -> None:
        self.assertEqual(
            "FAIL",
            self.verdict(0, self.MARKER + "\n", "MissingForeignRegistrationError: L(P,I)"))


class DistributionContentTest(unittest.TestCase):
    def test_bundled_sqlite_is_detected(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            (root / "lib").mkdir()
            self.assertEqual([], bundled_shared_objects(root, "libsqlite3"))
            (root / "lib" / "libsqlite3.so.0").write_bytes(b"\x7fELF")
            self.assertEqual(1, len(bundled_shared_objects(root, "libsqlite3")))

    def test_sqlite_dt_needed_fails_before_any_execution(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            with contextlib.redirect_stdout(io.StringIO()), \
                    contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit):
                    validate_native.validate_native_ffm(
                        root / "absent-launcher", root,
                        "libc.so.6,libsqlite3.so.0", root, root, {}, None)

    def test_missing_os_sqlite_is_never_reported_as_pass(self) -> None:
        self.assertFalse(validate_native.os_library_available("libsqlite3.so.0", None))


if __name__ == "__main__":
    unittest.main()
