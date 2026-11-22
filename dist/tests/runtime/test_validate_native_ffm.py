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
