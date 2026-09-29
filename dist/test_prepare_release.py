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
from unittest import mock
import tempfile
import unittest

import sys

DIST = Path(__file__).resolve().parent
if str(DIST) not in sys.path:
    sys.path.insert(0, str(DIST))

import prepare_release


class PrepareReleaseTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(
            prefix="protos-dist005-d5-"
        )
        self.top = Path(self.temp.name)
        self.selection = self.top / "selection.txt"
        self.candidate = (
            self.top
            / "cache"
            / "protos"
            / "release-candidates"
            / "protos-1.2.3-candidate"
        )

        values = {
            "selection_format":
                "protos-dist001-e4-selection-v1",
            "selection_authorized": "true",
            "selection_authorization_basis":
                "explicit-user-decision",
            "release_publication_authorized": "false",
            "release_baseline_revision": "a" * 40,
            "release_baseline_version":
                "1.2.3-SNAPSHOT",
            "release_version": "1.2.3",
            "release_tag": "v1.2.3",
            "specification_revision": "0.1.434",
            "i023_status": "CLOSED",
            "b007_status": "CLOSED",
            "candidate_source_revision": "UNMATERIALIZED",
            "git_tag_created": "false",
            "github_release_created": "false",
            "release_assets_published": "false",
        }

        self.selection.write_text(
            "\n".join(
                f"{key}={value}"
                for key, value in values.items()
            )
            + "\n",
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_default_candidate_uses_xdg_cache(self) -> None:
        with mock.patch.dict(
            "os.environ",
            {"XDG_CACHE_HOME": str(self.top / "cache")},
            clear=False,
        ):
            self.assertEqual(
                prepare_release.candidate_path("1.2.3"),
                self.candidate.resolve(),
            )

    def test_resets_only_owned_generated_envelope(self) -> None:
        candidate = self.top / "candidate"
        envelope = (
            candidate
            / "target"
            / "release-envelope-1.2.3"
        )
        envelope.mkdir(parents=True)
        (envelope / "stale.txt").write_text(
            "stale\n",
            encoding="utf-8",
        )

        prepare_release.reset_generated_envelope(
            candidate,
            envelope,
        )

        self.assertFalse(envelope.exists())

        outside = self.top / "outside-envelope"
        outside.mkdir()

        with self.assertRaisesRegex(
            SystemExit,
            "outside the owned candidate target",
        ):
            prepare_release.reset_generated_envelope(
                candidate,
                outside,
            )

        self.assertTrue(outside.is_dir())

    def test_requires_explicit_claims(self) -> None:
        with self.assertRaisesRegex(
            SystemExit,
            "--capability",
        ):
            prepare_release.prepare(
                self.selection,
                [],
                ["limitation"],
            )

        with self.assertRaisesRegex(
            SystemExit,
            "--limitation",
        ):
            prepare_release.prepare(
                self.selection,
                ["capability"],
                [],
            )

    def test_composes_selected_multi_asset_candidate(self) -> None:
        candidate_sha = "b" * 40

        def git_result(
            _cwd: Path,
            *args: str,
        ) -> str:
            if args == ("rev-parse", "HEAD"):
                return candidate_sha

            if args == (
                "status",
                "--porcelain=v1",
                "--untracked-files=all",
            ):
                return ""

            self.fail(
                "unexpected git call: "
                + repr(args)
            )

        with (
            mock.patch.object(
                prepare_release,
                "root",
                return_value=self.top,
            ),
            mock.patch.object(
                prepare_release,
                "current_specification_revision",
                return_value="0.1.434",
            ),
            mock.patch.object(
                prepare_release,
                "require_tag_available",
            ) as tag_guard,
            mock.patch.object(
                prepare_release,
                "resume_or_materialize",
                return_value=candidate_sha,
            ),
            mock.patch.object(
                prepare_release,
                "candidate_path",
                return_value=self.candidate,
            ),
            mock.patch.object(
                prepare_release,
                "git",
                side_effect=git_result,
            ),
            mock.patch.object(
                prepare_release,
                "step",
            ) as step,
        ):
            prepare_release.prepare(
                self.selection,
                ["capability"],
                ["limitation"],
            )

        self.assertEqual(
            tag_guard.call_count,
            2,
        )

        labels = [
            call.args[0]
            for call in step.call_args_list
        ]

        self.assertEqual(
            labels,
            [
                "refresh-origin-main",
                "native-image-build",
                "native-archive-build",
                "native-complete-admission",
                "portable-jvm-build",
                "portable-jvm-complete-admission",
                "multi-asset-envelope-prepare",
                "multi-asset-envelope-verify",
            ],
        )

        prepare_command = (
            step.call_args_list[6].args[1]
        )

        self.assertIn(
            prepare_release.NATIVE_ROLE,
            prepare_command,
        )
        self.assertIn(
            prepare_release.PORTABLE_ROLE,
            prepare_command,
        )
        self.assertIn(
            "capability",
            prepare_command,
        )
        self.assertIn(
            "limitation",
            prepare_command,
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
