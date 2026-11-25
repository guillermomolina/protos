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
from unittest import mock

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


class GroupProgramTest(unittest.TestCase):
    MARKER = validate_native.FFM_GROUPS_MARKER

    def verdict(self, returncode: int, stdout: str, stderr: str) -> str:
        return classify_ffm_case(returncode, stdout, stderr, expected_status=0,
                                 expected_stdout=self.MARKER, exact_stdout=True)[0]

    def test_native_programs_exist_and_emit_one_unique_marker(self) -> None:
        markers = {
            "ffm-groups.protos": validate_native.FFM_GROUPS_MARKER,
            "ffm-groups-unregistered-native.protos":
                validate_native.FFM_UNREGISTERED_GROUP_MARKER,
        }
        self.assertNotEqual(*markers.values())
        for name, marker in markers.items():
            text = (validate_native.FFM_CASES / name).read_text(encoding="utf-8")
            self.assertEqual(1, text.count('print("' + marker + '")'), name)
            self.assertEqual(1, text.count("print("), name)
            self.assertIn('import("ffi:first")', text, name)

    def test_success_needs_status_and_marker(self) -> None:
        self.assertEqual("PASS", self.verdict(0, self.MARKER + "\n", ""))

    def test_missing_marker_fails(self) -> None:
        self.assertEqual("FAIL", self.verdict(0, "", ""))

    def test_wrong_marker_fails(self) -> None:
        self.assertEqual("FAIL", self.verdict(0, "FFM-GROUPS-FAIL\n", ""))
        self.assertEqual(
            "FAIL", self.verdict(0, validate_native.FFM_NATIVE_MEMORY_MARKER + "\n", ""))
        self.assertEqual(
            "FAIL", self.verdict(0, validate_native.FFM_UNREGISTERED_GROUP_MARKER + "\n", ""))

    def test_unsuccessful_exit_fails(self) -> None:
        self.assertEqual("FAIL", self.verdict(1, self.MARKER + "\n", ""))
        self.assertEqual("FAIL", self.verdict(1, "", "CheckFailure"))

    def test_duplicate_or_additional_output_fails(self) -> None:
        self.assertEqual("FAIL", self.verdict(0, self.MARKER + "\n" + self.MARKER + "\n", ""))
        self.assertEqual("FAIL", self.verdict(0, "ran\n" + self.MARKER + "\n", ""))
        self.assertEqual("FAIL", self.verdict(0, self.MARKER + "\nextra\n", ""))

    def test_missing_registration_error_fails(self) -> None:
        self.assertEqual(
            "FAIL",
            self.verdict(0, self.MARKER + "\n",
                         "MissingForeignRegistrationError: struct(I,padding(4),D)()"))

    def test_exactness_is_opt_in(self) -> None:
        # Existing cases keep their substring classification unchanged.
        self.assertEqual(
            "PASS",
            classify_ffm_case(0, "noise\n" + self.MARKER + "\n", "", expected_status=0,
                              expected_stdout=self.MARKER)[0])


class UnregisteredGroupSignatureTest(unittest.TestCase):
    MARKER = validate_native.FFM_UNREGISTERED_GROUP_MARKER

    def verdict(self, returncode: int, stdout: str, stderr: str) -> str:
        return classify_ffm_case(returncode, stdout, stderr, expected_status=0,
                                 expected_stdout=self.MARKER, exact_stdout=True)[0]

    def test_clean_ordinary_refusal_passes(self) -> None:
        self.assertEqual("PASS", self.verdict(0, self.MARKER + "\n", ""))

    def test_refusal_needs_its_own_marker(self) -> None:
        self.assertEqual("FAIL", self.verdict(0, "", ""))
        self.assertEqual("FAIL", self.verdict(0, validate_native.FFM_GROUPS_MARKER + "\n", ""))
        self.assertEqual("FAIL", self.verdict(0, "ffm-unregistered-scalar-refused\n", ""))

    def test_missing_registration_error_is_not_a_refusal(self) -> None:
        self.assertEqual(
            "FAIL",
            self.verdict(1, "", "MissingForeignRegistrationError: struct(D,I,padding(4))"))
        self.assertEqual(
            "FAIL",
            self.verdict(0, self.MARKER + "\n", "MissingForeignRegistrationError"))

    def test_foreign_entry_failure_fails(self) -> None:
        self.assertEqual(
            "FAIL",
            self.verdict(0, validate_native.FFM_FOREIGN_ENTRY_MARKER + "\n" + self.MARKER
                         + "\n", ""))

    def test_unsuccessful_exit_fails(self) -> None:
        self.assertEqual("FAIL", self.verdict(1, self.MARKER + "\n", ""))

    def test_program_requires_an_exact_ordinary_error_before_lookup(self) -> None:
        text = (validate_native.FFM_CASES / "ffm-groups-unregistered-native.protos").read_text(
            encoding="utf-8")
        self.assertIn("error.parent() === Error", text)
        self.assertIn("(error.parent() === ForeignError) == false", text)
        self.assertIn("ffi_test_group_no_such_symbol", text)
        self.assertIn("calls() == before", text)


class IntegratedMarkersTest(unittest.TestCase):
    def test_new_markers_require_clean_exact_success(self) -> None:
        for marker in (validate_native.FFM_NATIVE_MEMORY_NO_LIBRARY_MARKER,
                       validate_native.FFM_R1_PUBLIC_API_MARKER,
                       validate_native.FFM_TWO_LIBRARIES_MARKER):
            for status, stdout, stderr, expected in (
                (0, marker + "\n", "", "PASS"),
                (1, marker + "\n", "", "FAIL"),
                (0, "", "", "FAIL"),
                (0, marker + "\n" + marker + "\n", "", "FAIL"),
                (0, marker + "\nextra\n", "", "FAIL"),
                (0, marker + "\n", "MissingForeignRegistrationError", "FAIL"),
                (0, marker + "\n", "UnsatisfiedLinkError", "FAIL"),
                (0, marker + "\n", "Internal error", "FAIL"),
            ):
                with self.subTest(marker=marker, status=status, stdout=stdout, stderr=stderr):
                    self.assertEqual(expected, classify_ffm_case(
                        status, stdout, stderr, expected_status=0,
                        expected_stdout=marker, exact_stdout=True)[0])


class R1PreparationTest(unittest.TestCase):
    def test_complete_fixture_is_preserved_except_import_and_final_marker(self) -> None:
        source = validate_native.FFM_CASES / "ffm-public-api.protos"
        original = source.read_bytes()
        with tempfile.TemporaryDirectory() as raw:
            target = Path(raw) / "r1.protos"
            self.assertEqual(target, validate_native.prepare_r1_public_api(source, target))
            adapted = target.read_text(encoding="utf-8")
            expected = original.decode("utf-8").replace(
                'native: import("ffi-test:ffi_test")', 'native: import("ffi:first")', 1)
            self.assertEqual(expected + '\nprint("' + validate_native.FFM_R1_PUBLIC_API_MARKER
                             + '")\n', adapted)
            self.assertEqual(1, adapted.count(validate_native.FFM_R1_PUBLIC_API_MARKER))
            self.assertIn("retained: succeeded[1]", adapted)
            self.assertIn("increment: increment", adapted)
        self.assertEqual(original, source.read_bytes())

    def test_missing_or_ambiguous_import_is_rejected_without_output(self) -> None:
        declaration = 'native: import("ffi-test:ffi_test")\n'
        with tempfile.TemporaryDirectory() as raw:
            source, target = Path(raw) / "source.protos", Path(raw) / "target.protos"
            for text in ("null\n", declaration * 2):
                source.write_text(text, encoding="utf-8")
                with self.assertRaises(ValueError):
                    validate_native.prepare_r1_public_api(source, target)
                self.assertFalse(target.exists())
                self.assertEqual(text, source.read_text(encoding="utf-8"))

    def test_source_cannot_be_overwritten(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            source = Path(raw) / "source.protos"
            original = 'native: import("ffi-test:ffi_test")\n'
            source.write_text(original, encoding="utf-8")
            with self.assertRaises(ValueError):
                validate_native.prepare_r1_public_api(source, source)
            self.assertEqual(original, source.read_text(encoding="utf-8"))


class InstalledRunnerPlanTest(unittest.TestCase):
    def test_sqlite_fixtures_and_markers_exist(self) -> None:
        for name, marker in (("sqlite-query.protos", "LIB029-QUERY-PASS"),
                             ("sqlite-bind-text-blob.protos", "LIB030-BIND-TEXT-BLOB-PASS"),
                             ("sqlite-durable.protos", "LIB031-DURABLE-PASS"),
                             ("ffm-native-memory-no-library.protos",
                              validate_native.FFM_NATIVE_MEMORY_NO_LIBRARY_MARKER)):
            self.assertIn('print("' + marker + '")',
                          (validate_native.FFM_CASES / name).read_text(encoding="utf-8"))

    def test_installed_invocations_and_native_effect_check(self) -> None:
        # This checks runner wiring only. No launcher, C compiler or SQLite executes.
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            launcher, cwd = root / "installed" / "bin" / "protos", root / "unrelated"
            env = {"PATH": "/restricted"}

            def case(label, command, **kwargs):
                if label == "FFM_TWO_LIBRARIES":
                    Path(command[-1]).write_bytes(validate_native.SECOND_MARKER_TEXT.encode("utf-8"))

            with mock.patch.object(validate_native, "run_ffm_case", side_effect=case) as run, \
                    mock.patch.object(validate_native, "ffm_fixture_compiler", return_value="cc"), \
                    mock.patch.object(validate_native, "compile_ffm_fixtures", return_value={
                        "first": root / "libffi_test.so", "second": root / "libffi_second.so"}), \
                    mock.patch.object(validate_native, "os_library_available", return_value=True), \
                    contextlib.redirect_stdout(io.StringIO()):
                validate_native.validate_native_ffm(launcher, root / "dist", "libc.so.6",
                                                    root, cwd, env, "ldconfig")
            cases = {call.args[0]: call for call in run.call_args_list}
            for call in run.call_args_list:
                self.assertEqual(str(launcher), call.args[1][0])
                self.assertEqual(cwd, call.kwargs["cwd"])
                self.assertEqual(env, call.kwargs["env"])
                self.assertNotIn("--foreign-provider-path", call.args[1])
            no_library = cases["FFM_NATIVE_MEMORY_NO_LIBRARY"]
            self.assertEqual([str(launcher), "--allow-library",
                              "unused=" + str(root / "absent" / "libunused.so"),
                              str(validate_native.FFM_CASES / "ffm-native-memory-no-library.protos")],
                             no_library.args[1])
            self.assertTrue(no_library.kwargs["exact_stdout"])
            r1 = cases["FFM_R1_PUBLIC_API"]
            self.assertEqual(1, r1.args[1].count("--allow-library"))
            self.assertEqual("first=" + str(root / "libffi_test.so"), r1.args[1][2])
            self.assertTrue(r1.kwargs["exact_stdout"])
            self.assertTrue(cases["FFM_TWO_LIBRARIES"].kwargs["exact_stdout"])
            local = cases["FFM_SQLITE_LOCAL_TRANSACTIONS"].args[1]
            seed = cases["FFM_SQLITE_SEED"].args[1]
            verify = cases["FFM_SQLITE_REOPEN"].args[1]
            self.assertEqual(("local", "seed", "verify"), (local[-2], seed[-2], verify[-2]))
            self.assertEqual(seed[-1], verify[-1])
            self.assertNotEqual(local[-1], seed[-1])
            for label in ("FFM_SQLITE_QUERY", "FFM_SQLITE_TEXT_BLOB",
                          "FFM_SQLITE_LOCAL_TRANSACTIONS", "FFM_SQLITE_SEED", "FFM_SQLITE_REOPEN"):
                self.assertIn("sqlite3=libsqlite3.so.0", cases[label].args[1])
                self.assertTrue(cases[label].kwargs["exact_stdout"])


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

    def test_fixture_libraries_are_detected(self) -> None:
        for stem in ("libffi_test.so", "libffi_second.so"):
            with self.subTest(stem=stem), tempfile.TemporaryDirectory() as raw:
                root = Path(raw)
                self.assertEqual([], bundled_shared_objects(root, stem))
                (root / "lib").mkdir()
                (root / "lib" / stem).write_bytes(b"\x7fELF")
                self.assertEqual([root / "lib" / stem], bundled_shared_objects(root, stem))
                with mock.patch.object(validate_native, "run_ffm_case") as run, \
                        contextlib.redirect_stderr(io.StringIO()):
                    with self.assertRaises(SystemExit):
                        validate_native.validate_native_ffm(
                            root / "absent-launcher", root, "libc.so.6", root, root, {}, None)
                    run.assert_not_called()

    def test_fixture_dt_needed_fails_before_launcher_execution(self) -> None:
        for stem in ("libffi_test.so", "libffi_second.so"):
            with self.subTest(stem=stem), tempfile.TemporaryDirectory() as raw:
                root = Path(raw)
                with mock.patch.object(validate_native, "run_ffm_case") as run, \
                        contextlib.redirect_stdout(io.StringIO()), \
                        contextlib.redirect_stderr(io.StringIO()):
                    with self.assertRaises(SystemExit):
                        validate_native.validate_native_ffm(
                            root / "absent-launcher", root, "libc.so.6," + stem,
                            root, root, {}, None)
                    run.assert_not_called()

    def test_missing_os_sqlite_is_never_reported_as_pass(self) -> None:
        self.assertFalse(validate_native.os_library_available("libsqlite3.so.0", None))


if __name__ == "__main__":
    unittest.main()
