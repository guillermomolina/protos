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

sys.path.insert(0, str(Path(__file__).resolve().parent))

from release_identity import (
    build_source_metadata,
    public_version_from_snapshot,
    require_public_version,
    require_snapshot_version,
)

POM = """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.guillermomolina</groupId>
  <artifactId>protos</artifactId>
  <version>{version}</version>
</project>
"""


def git(root: Path, *args: str) -> str:
    return subprocess.run(
        ["git", *args],
        cwd=root,
        check=True,
        text=True,
        capture_output=True,
    ).stdout.strip()


class ReleaseIdentityTest(unittest.TestCase):
    def test_strict_version_mapping(self) -> None:
        self.assertEqual(public_version_from_snapshot("0.2.230-SNAPSHOT"), "0.2.230")
        self.assertEqual(require_public_version("0.2.230"), "0.2.230")
        self.assertEqual(
            require_snapshot_version("0.2.230-SNAPSHOT"),
            "0.2.230-SNAPSHOT",
        )
        for invalid in [
            "0.2-SNAPSHOT",
            "0.2.03-SNAPSHOT",
            "v0.2.3-SNAPSHOT",
            "0.2.3-rc.1",
        ]:
            with self.assertRaises(ValueError):
                require_snapshot_version(invalid)
        for invalid in ["0.2", "0.02.3", "v0.2.3", "0.2.3-SNAPSHOT", "0.2.3-rc.1"]:
            with self.assertRaises(ValueError):
                require_public_version(invalid)

    def test_development_metadata_is_unchanged(self) -> None:
        metadata = build_source_metadata(
            Path("."),
            version="0.2.230-SNAPSHOT",
            source_revision="a" * 40,
            source_dirty=False,
            public_prerelease=False,
            release_baseline=None,
        )
        self.assertEqual(metadata["artifact_kind"], "development-distribution")
        self.assertEqual(metadata["public_release"], "false")
        self.assertNotIn("release_baseline_revision", metadata)
        self.assertNotIn("release_tag", metadata)

    def test_development_mode_rejects_public_version_and_release_baseline(self) -> None:
        with self.assertRaises(ValueError):
            build_source_metadata(
                Path("."),
                version="0.2.230",
                source_revision="a" * 40,
                source_dirty=False,
                public_prerelease=False,
                release_baseline=None,
            )
        with self.assertRaises(ValueError):
            build_source_metadata(
                Path("."),
                version="0.2.230-SNAPSHOT",
                source_revision="a" * 40,
                source_dirty=False,
                public_prerelease=False,
                release_baseline="b" * 40,
            )

    def test_public_prerelease_metadata_binds_baseline_and_candidate(self) -> None:
        with tempfile.TemporaryDirectory(prefix="protos-release-identity-") as td:
            root = Path(td)
            git(root, "init", "-q")
            git(root, "config", "user.email", "fixture@example.invalid")
            git(root, "config", "user.name", "Fixture")

            (root / "pom.xml").write_text(
                POM.format(version="0.2.230-SNAPSHOT"),
                encoding="utf-8",
            )
            git(root, "add", "pom.xml")
            git(root, "commit", "-q", "-m", "baseline")
            baseline = git(root, "rev-parse", "HEAD")

            (root / "pom.xml").write_text(POM.format(version="0.2.230"), encoding="utf-8")
            git(root, "add", "pom.xml")
            git(root, "commit", "-q", "-m", "candidate")
            candidate = git(root, "rev-parse", "HEAD")

            metadata = build_source_metadata(
                root,
                version="0.2.230",
                source_revision=candidate,
                source_dirty=False,
                public_prerelease=True,
                release_baseline=baseline,
            )

            self.assertEqual(metadata["artifact_kind"], "public-prerelease")
            self.assertEqual(metadata["public_release"], "true")
            self.assertEqual(metadata["implementation_version"], "0.2.230")
            self.assertEqual(metadata["source_revision"], candidate)
            self.assertEqual(metadata["release_baseline_revision"], baseline)
            self.assertEqual(metadata["release_baseline_version"], "0.2.230-SNAPSHOT")
            self.assertEqual(metadata["release_version"], "0.2.230")
            self.assertEqual(metadata["release_tag"], "v0.2.230")

    def test_public_prerelease_rejects_dirty_candidate(self) -> None:
        with self.assertRaisesRegex(ValueError, "clean candidate"):
            build_source_metadata(
                Path("."),
                version="0.2.230",
                source_revision="a" * 40,
                source_dirty=True,
                public_prerelease=True,
                release_baseline="b" * 40,
            )

    def test_public_prerelease_rejects_baseline_version_mismatch(self) -> None:
        with tempfile.TemporaryDirectory(prefix="protos-release-mismatch-") as td:
            root = Path(td)
            git(root, "init", "-q")
            git(root, "config", "user.email", "fixture@example.invalid")
            git(root, "config", "user.name", "Fixture")

            (root / "pom.xml").write_text(
                POM.format(version="0.2.229-SNAPSHOT"),
                encoding="utf-8",
            )
            git(root, "add", "pom.xml")
            git(root, "commit", "-q", "-m", "baseline")
            baseline = git(root, "rev-parse", "HEAD")

            (root / "pom.xml").write_text(POM.format(version="0.2.230"), encoding="utf-8")
            git(root, "add", "pom.xml")
            git(root, "commit", "-q", "-m", "candidate")
            candidate = git(root, "rev-parse", "HEAD")

            with self.assertRaisesRegex(ValueError, "baseline project version mismatch"):
                build_source_metadata(
                    root,
                    version="0.2.230",
                    source_revision=candidate,
                    source_dirty=False,
                    public_prerelease=True,
                    release_baseline=baseline,
                )


if __name__ == "__main__":
    unittest.main(verbosity=2)
