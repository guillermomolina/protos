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
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]


class SmokeTestToolJobsTest(unittest.TestCase):
    def test_distribution_smoke_matches_repository_parallel_default(
        self,
    ) -> None:
        makefile = (ROOT / "Makefile").read_text(
            encoding="utf-8",
        )
        smoke = (
            ROOT / "dist/smoke_test_tool.sh"
        ).read_text(
            encoding="utf-8",
        )

        make_match = re.search(
            r"^PROTOS_TEST_JOBS \?= ([1-9][0-9]*)$",
            makefile,
            flags=re.MULTILINE,
        )
        self.assertIsNotNone(make_match)

        smoke_match = re.search(
            r"^jobs=\$\{PROTOS_TEST_JOBS:-([1-9][0-9]*)\}$",
            smoke,
            flags=re.MULTILINE,
        )
        self.assertIsNotNone(smoke_match)

        self.assertEqual(
            smoke_match.group(1),
            make_match.group(1),
        )

        self.assertIn(
            '"$launcher" test --jobs "$jobs"',
            smoke,
        )

    def test_distribution_smoke_rejects_invalid_jobs(
        self,
    ) -> None:
        smoke = (
            ROOT / "dist/smoke_test_tool.sh"
        ).read_text(
            encoding="utf-8",
        )

        self.assertIn(
            'fail "PROTOS_TEST_JOBS must be a positive integer"',
            smoke,
        )
        self.assertIn(
            '[ "$jobs" -gt 0 ]',
            smoke,
        )

    def test_distribution_smoke_reports_selected_jobs(
        self,
    ) -> None:
        smoke = (
            ROOT / "dist/smoke_test_tool.sh"
        ).read_text(
            encoding="utf-8",
        )

        self.assertIn(
            'echo "DIST_B4A_TEST_TOOL_JOBS=$jobs"',
            smoke,
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
