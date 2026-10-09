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

"""Focused BUG021 (#859) checks for supervised Native Test Tool execution."""

from __future__ import annotations

import contextlib
import io
import os
from pathlib import Path
import sys
import tempfile
import textwrap
import unittest

DIST = Path(__file__).resolve().parent
if str(DIST) not in sys.path:
    sys.path.insert(0, str(DIST))

from validate_native import run_supervised, validate_full_test_tool


# Upper bound only for a broken implementation; passing runs never wait on it.
SAFETY_TIMEOUT_SECONDS = 30.0


class ReleasingEcho:
    """Echo sink that releases the child once the progress line has arrived."""

    def __init__(self, fifo: Path) -> None:
        self.fifo = fifo
        self.data = bytearray()
        self.released = False

    def write(self, chunk: bytes) -> int:
        self.data.extend(chunk)
        if not self.released and b"PROGRESS 0/1\n" in self.data:
            self.released = True
            with open(self.fifo, "wb") as release:
                release.write(b"go")
        return len(chunk)

    def flush(self) -> None:
        pass


class SupervisedRunTest(unittest.TestCase):
    def setUp(self) -> None:
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)

    def tearDown(self) -> None:
        self.directory.cleanup()

    def script(self, source: str) -> list[str]:
        path = self.root / "child.py"
        path.write_text(textwrap.dedent(source), encoding="utf-8")
        return [sys.executable, str(path)]

    def run_child(self, source: str, **options):
        return run_supervised(
            self.script(source),
            cwd=self.root,
            env=os.environ.copy(),
            **options,
        )

    def assert_reaped(self, pid: int) -> None:
        with self.assertRaises(ProcessLookupError):
            os.kill(pid, 0)

    def test_progress_reaches_parent_before_child_terminates(self) -> None:
        fifo = self.root / "release"
        os.mkfifo(fifo)
        echo = ReleasingEcho(fifo)

        # The child cannot finish until the parent has received its progress.
        result = self.run_child(
            f"""
            import sys
            sys.stderr.write("PROGRESS 0/1\\n")
            sys.stderr.flush()
            with open({str(fifo)!r}, "rb") as release:
                release.read()
            sys.stderr.write("PROGRESS 1/1\\n")
            sys.stdout.write("final-without-newline")
            """,
            echo=echo,
            timeout=SAFETY_TIMEOUT_SECONDS,
        )

        self.assertTrue(echo.released)
        self.assertEqual("exit", result.termination())
        self.assertEqual(0, result.returncode)
        self.assertEqual("final-without-newline", result.stdout)
        self.assertEqual("PROGRESS 0/1\nPROGRESS 1/1\n", result.stderr)
        self.assertIn(b"final-without-newline", bytes(echo.data))
        self.assert_reaped(result.pid)

    def test_failing_exit_preserves_transcript(self) -> None:
        result = self.run_child(
            """
            import sys
            sys.stdout.write("partial\\n")
            sys.stderr.write("boom")
            sys.exit(3)
            """,
            echo=io.BytesIO(),
            timeout=SAFETY_TIMEOUT_SECONDS,
        )

        self.assertEqual("exit", result.termination())
        self.assertEqual(3, result.returncode)
        self.assertEqual("partial\n", result.stdout)
        self.assertEqual("boom", result.stderr)

    def test_signal_termination_is_distinguished(self) -> None:
        result = self.run_child(
            """
            import os, signal
            os.kill(os.getpid(), signal.SIGTERM)
            """,
            echo=io.BytesIO(),
            timeout=SAFETY_TIMEOUT_SECONDS,
        )

        self.assertEqual("signal:SIGTERM", result.termination())
        self.assert_reaped(result.pid)

    def test_timeout_terminates_and_reaps_child(self) -> None:
        result = self.run_child(
            """
            import signal
            signal.pause()
            """,
            echo=io.BytesIO(),
            timeout=0.5,
        )

        self.assertTrue(result.timed_out)
        self.assertEqual("timeout", result.termination())
        self.assertNotEqual(0, result.returncode)
        self.assert_reaped(result.pid)

    def test_transcript_limit_terminates_child(self) -> None:
        result = self.run_child(
            """
            import signal, sys
            sys.stdout.write("x" * 4096)
            sys.stdout.flush()
            signal.pause()
            """,
            echo=io.BytesIO(),
            timeout=SAFETY_TIMEOUT_SECONDS,
            transcript_limit=1024,
        )

        self.assertTrue(result.truncated)
        self.assertEqual("transcript-limit", result.termination())
        self.assert_reaped(result.pid)


class FullTestToolAdmissionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)

    def tearDown(self) -> None:
        self.directory.cleanup()

    def native(self, body: str) -> Path:
        path = self.root / "protos"
        path.write_text(
            "#!" + sys.executable + "\n" + textwrap.dedent(body),
            encoding="utf-8",
        )
        path.chmod(0o755)
        return path

    def admit(self, body: str) -> tuple[str, bytes]:
        markers = io.StringIO()
        echo = io.BytesIO()
        with contextlib.redirect_stdout(markers):
            validate_full_test_tool(
                self.native(body),
                self.root,
                cwd=self.root,
                env=os.environ.copy(),
                echo=echo,
            )
        return markers.getvalue(), echo.getvalue()

    def test_successful_run_is_admitted(self) -> None:
        markers, echo = self.admit(
            """
            import sys
            sys.stderr.write("[library/uri] 0/1\\n[library/uri] 1/1 passed\\n")
            sys.stderr.write("1 passed, 0 failed\\n")
            sys.stdout.write("Protos test tool bootstrap\\n" + sys.argv[1] + "\\n")
            """
        )

        self.assertIn("NATIVE_DIST_FULL_TEST_TOOL_STATUS=0\n", markers)
        self.assertIn("NATIVE_DIST_FULL_TEST_TOOL_TERMINATION=exit\n", markers)
        self.assertIn("NATIVE_DIST_FULL_TEST_TOOL_SECONDS=", markers)
        self.assertIn("NATIVE_DIST_FULL_TEST_TOOL_PASSED=1\n", markers)
        self.assertIn("NATIVE_DIST_FULL_TEST_TOOL_FAILED=0\n", markers)
        self.assertIn("DIST005_NATIVE_FULL_TEST_TOOL_ADMISSION: PASS\n", markers)
        self.assertIn(b"[library/uri] 1/1 passed\n", echo)

    def test_discovery_failure_exit_is_rejected_with_diagnostic(self) -> None:
        markers = io.StringIO()
        with contextlib.redirect_stdout(markers):
            with self.assertRaises(SystemExit) as raised:
                validate_full_test_tool(
                    self.native(
                        """
                        import sys
                        sys.stderr.write(
                            "Test tool discovery error: corpus:suite.protos: cause\\n"
                        )
                        sys.exit(1)
                        """
                    ),
                    self.root,
                    cwd=self.root,
                    env=os.environ.copy(),
                    echo=io.BytesIO(),
                )

        self.assertIn("NATIVE_DIST_FULL_TEST_TOOL_STATUS=1\n", markers.getvalue())
        self.assertIn(
            "Test tool discovery error: corpus:suite.protos: cause",
            str(raised.exception),
        )

    def test_missing_summary_is_rejected(self) -> None:
        markers = io.StringIO()
        with contextlib.redirect_stdout(markers):
            with self.assertRaises(SystemExit):
                validate_full_test_tool(
                    self.native(
                        """
                        import sys
                        sys.stdout.write("Protos test tool bootstrap\\ntest\\n")
                        """
                    ),
                    self.root,
                    cwd=self.root,
                    env=os.environ.copy(),
                    echo=io.BytesIO(),
                )

        self.assertIn(
            "NATIVE_DIST_FULL_TEST_TOOL_SUMMARY=MISSING\n",
            markers.getvalue(),
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
