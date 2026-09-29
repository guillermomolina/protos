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
import subprocess
import sys
import tempfile
import unittest

DIST = Path(__file__).resolve().parent
if str(DIST) not in sys.path:
    sys.path.insert(0, str(DIST))

import build_native


POM = """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.guillermomolina</groupId>
  <artifactId>protos</artifactId>
  <version>{version}</version>
</project>
"""


def run(
    args: list[str],
    *,
    cwd: Path,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=cwd,
        check=True,
        text=True,
        capture_output=True,
    )


def git(root: Path, *args: str) -> str:
    return run(["git", *args], cwd=root).stdout.strip()


class NativePublicReleaseModeTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(
            prefix="protos-dist005-d4-native-release-"
        )
        self.repo = Path(self.temp.name) / "repo"
        self.repo.mkdir()

        git(self.repo, "init", "-q")
        git(self.repo, "config", "user.email", "fixture@example.invalid")
        git(self.repo, "config", "user.name", "Fixture")

        self.write_version("1.2.2-SNAPSHOT")
        git(self.repo, "add", "pom.xml")
        git(self.repo, "commit", "-q", "-m", "mismatched baseline")
        self.mismatched_baseline = git(self.repo, "rev-parse", "HEAD")

        self.write_version("1.2.3-SNAPSHOT")
        git(self.repo, "add", "pom.xml")
        git(self.repo, "commit", "-q", "-m", "release baseline")
        self.baseline = git(self.repo, "rev-parse", "HEAD")

        self.write_version("1.2.3")
        git(self.repo, "add", "pom.xml")
        git(self.repo, "commit", "-q", "-m", "proof candidate")
        self.candidate = git(self.repo, "rev-parse", "HEAD")

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write_version(self, version: str) -> None:
        (self.repo / "pom.xml").write_text(
            POM.format(version=version),
            encoding="utf-8",
        )

    def metadata(
        self,
        *,
        version: str = "1.2.3",
        source_dirty: bool = False,
        public_prerelease: bool = True,
        release_baseline: str | None = None,
    ) -> dict[str, str]:
        if release_baseline is None and public_prerelease:
            release_baseline = self.baseline

        return build_native.native_source_metadata(
            self.repo,
            version=version,
            source_revision=self.candidate,
            source_dirty=source_dirty,
            public_prerelease=public_prerelease,
            release_baseline=release_baseline,
        )

    def test_public_native_accepts_exact_candidate_lineage(self) -> None:
        metadata = self.metadata()

        self.assertEqual(metadata["artifact_kind"], "public-prerelease")
        self.assertEqual(metadata["public_release"], "true")
        self.assertEqual(metadata["implementation_version"], "1.2.3")
        self.assertEqual(metadata["source_revision"], self.candidate)
        self.assertEqual(metadata["source_dirty"], "false")
        self.assertEqual(
            metadata["release_baseline_revision"],
            self.baseline,
        )
        self.assertEqual(
            metadata["release_baseline_version"],
            "1.2.3-SNAPSHOT",
        )
        self.assertEqual(metadata["release_version"], "1.2.3")
        self.assertEqual(metadata["release_tag"], "v1.2.3")

    def test_public_native_rejects_dirty_candidate(self) -> None:
        with self.assertRaisesRegex(
            ValueError,
            "requires a clean candidate source tree",
        ):
            self.metadata(source_dirty=True)

    def test_public_native_rejects_snapshot_candidate_version(self) -> None:
        with self.assertRaisesRegex(
            ValueError,
            "public prerelease project version must be canonical",
        ):
            self.metadata(version="1.2.3-SNAPSHOT")

    def test_public_native_requires_release_baseline(self) -> None:
        with self.assertRaisesRegex(
            ValueError,
            "--public-prerelease requires --release-baseline",
        ):
            build_native.native_source_metadata(
                self.repo,
                version="1.2.3",
                source_revision=self.candidate,
                source_dirty=False,
                public_prerelease=True,
                release_baseline=None,
            )

    def test_public_native_rejects_non_sha_release_baseline(self) -> None:
        with self.assertRaisesRegex(
            ValueError,
            "release baseline revision must be an exact 40-hex commit SHA",
        ):
            self.metadata(release_baseline="not-a-sha")

    def test_public_native_rejects_baseline_version_mismatch(self) -> None:
        with self.assertRaisesRegex(
            ValueError,
            "release baseline project version mismatch",
        ):
            self.metadata(release_baseline=self.mismatched_baseline)

    def test_development_native_retains_distinct_identity(self) -> None:
        metadata = self.metadata(
            version="1.2.3-SNAPSHOT",
            public_prerelease=False,
            release_baseline=None,
        )

        self.assertEqual(
            metadata["artifact_kind"],
            build_native.ARTIFACT_KIND,
        )
        self.assertEqual(
            metadata["artifact_kind"],
            "development-native-distribution",
        )
        self.assertEqual(metadata["public_release"], "false")
        self.assertEqual(
            metadata["implementation_version"],
            "1.2.3-SNAPSHOT",
        )
        self.assertNotIn("release_version", metadata)
        self.assertNotIn("release_tag", metadata)

    def test_development_native_cannot_claim_release_baseline(self) -> None:
        with self.assertRaisesRegex(
            ValueError,
            "--release-baseline is valid only with --public-prerelease",
        ):
            self.metadata(
                version="1.2.3-SNAPSHOT",
                public_prerelease=False,
                release_baseline=self.baseline,
            )


if __name__ == "__main__":
    unittest.main(verbosity=2)
