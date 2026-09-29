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

from __future__ import annotations

from pathlib import Path
import sys
import unittest

DIST = Path(__file__).resolve().parent
if str(DIST) not in sys.path:
    sys.path.insert(0, str(DIST))

from native_elf import (
    NativeElfError,
    compare_numeric_versions,
    parse_dt_needed,
    parse_glibc_versions,
    parse_interpreter,
    parse_ldd_closure,
    parse_post_link_cpu_isa,
    require_glibc_within_policy,
)


class NativeElfTest(unittest.TestCase):
    def test_glibc_versions_are_compared_numerically(self) -> None:
        versions, observed_max = parse_glibc_versions(
            """
            Name: GLIBC_2.2.5
            Name: GLIBC_2.9
            Name: GLIBC_2.34
            Name: GLIBC_2.39
            """
        )

        self.assertEqual(
            versions,
            ("2.2.5", "2.9", "2.34", "2.39"),
        )
        self.assertEqual(observed_max, "2.39")
        self.assertGreater(
            compare_numeric_versions("2.39", "2.9"),
            0,
        )

    def test_missing_glibc_observation_fails_closed(self) -> None:
        with self.assertRaisesRegex(
            NativeElfError,
            "no observable GLIBC symbol requirements",
        ):
            parse_glibc_versions("Version symbols section contains 0 entries")

    def test_malformed_glibc_observation_fails_closed(self) -> None:
        with self.assertRaisesRegex(
            NativeElfError,
            "malformed GLIBC symbol-version evidence",
        ):
            parse_glibc_versions("Name: GLIBC_2.bad")

    def test_observed_glibc_above_policy_fails_closed(self) -> None:
        with self.assertRaisesRegex(
            NativeElfError,
            "exceeds selected public policy",
        ):
            require_glibc_within_policy("2.40", "2.39")

        require_glibc_within_policy("2.39", "2.39")
        require_glibc_within_policy("2.34", "2.39")

    def test_interpreter_requires_one_absolute_loader(self) -> None:
        self.assertEqual(
            parse_interpreter(
                "[Requesting program interpreter: "
                "/lib64/ld-linux-x86-64.so.2]"
            ),
            "/lib64/ld-linux-x86-64.so.2",
        )

        with self.assertRaisesRegex(
            NativeElfError,
            "expected exactly one ELF interpreter",
        ):
            parse_interpreter("no interpreter")

    def test_dt_needed_is_deterministic(self) -> None:
        needed = parse_dt_needed(
            """
            0x1 (NEEDED) Shared library: [libz.so.1]
            0x1 (NEEDED) Shared library: [libc.so.6]
            0x1 (NEEDED) Shared library: [libz.so.1]
            """
        )

        self.assertEqual(
            needed,
            ("libc.so.6", "libz.so.1"),
        )

    def test_ldd_closure_rejects_unresolved_dependency(self) -> None:
        with self.assertRaisesRegex(
            NativeElfError,
            "unresolved Native shared libraries",
        ):
            parse_ldd_closure(
                """
                libc.so.6 => /lib64/libc.so.6 (0x1)
                libz.so.1 => not found
                """,
                required=("libc.so.6", "libz.so.1"),
            )

    def test_ldd_closure_requires_every_dt_needed_entry(self) -> None:
        with self.assertRaisesRegex(
            NativeElfError,
            "does not resolve every DT_NEEDED entry",
        ):
            parse_ldd_closure(
                "libc.so.6 => /lib64/libc.so.6 (0x1)",
                required=("libc.so.6", "libz.so.1"),
            )

    def test_cpu_isa_notes_are_observed_without_invention(self) -> None:
        self.assertEqual(
            parse_post_link_cpu_isa("GNU property notes without ISA requirement"),
            "not-recorded",
        )

        self.assertEqual(
            parse_post_link_cpu_isa(
                "Properties: x86 ISA needed: x86-64-baseline\n"
            ),
            "x86-64-baseline",
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
