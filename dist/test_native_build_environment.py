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

from build_native import (
    parse_os_release,
    require_native_build_os,
)


class NativeBuildEnvironmentTest(unittest.TestCase):
    def test_parse_os_release(self) -> None:
        values = parse_os_release(
            'NAME="Oracle Linux Server"\n'
            'ID="ol"\n'
            'VERSION_ID="10.2"\n'
        )

        self.assertEqual(values["ID"], "ol")
        self.assertEqual(values["VERSION_ID"], "10.2")

    def test_oracle_linux_10_is_accepted(self) -> None:
        require_native_build_os("ol", "10")
        require_native_build_os("ol", "10.2")

    def test_oracle_linux_9_is_rejected(self) -> None:
        with self.assertRaisesRegex(
            ValueError,
            "not Oracle Linux 10",
        ):
            require_native_build_os("ol", "9.6")

    def test_non_oracle_linux_is_rejected(self) -> None:
        with self.assertRaisesRegex(
            ValueError,
            "not Oracle Linux",
        ):
            require_native_build_os("ubuntu", "24.04")


if __name__ == "__main__":
    unittest.main(verbosity=2)
