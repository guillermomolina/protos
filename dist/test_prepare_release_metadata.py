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

import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))

from prepare_release_metadata import prepare  # noqa: E402


class Args:
    def __init__(
        self,
        *,
        archive: str,
        output_dir: str,
        spec_revision: str = "0.1.382",
        capability: list[str] | None = None,
        limitation: list[str] | None = None,
    ) -> None:
        self.archive = archive
        self.output_dir = output_dir
        self.spec_revision = spec_revision
        self.capability = capability or ["Portable distribution works outside the checkout."]
        self.limitation = limitation or ["GraalVM Community JDK 22 is externally required."]


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def candidate_archive(root: Path, *, public_release: str = "true", dirty: str = "false") -> Path:
    version = "0.2.230"
    archive = root / f"protos-{version}-posix-jvm.zip"
    source = "\n".join(
        [
            f"implementation_version={version}",
            "source_revision=" + "b" * 40,
            f"source_dirty={dirty}",
            "source_repository=https://github.com/guillermomolina/protos",
            "source_path=/tree/" + "b" * 40,
            "artifact_kind=public-prerelease",
            f"public_release={public_release}",
            "release_baseline_revision=" + "a" * 40,
            "release_baseline_version=0.2.230-SNAPSHOT",
            f"release_version={version}",
            f"release_tag=v{version}",
        ]
    ) + "\n"
    runtime = "\n".join(
        [
            "distribution_format=protos-portable-posix-jvm-v1",
            "java_feature=22",
            "java_distribution=GraalVM Community Edition for JDK 22",
            "truffle_runtime_version=24.0.0",
            "optimizing_runtime=HotSpotTruffleRuntime",
        ]
    ) + "\n"

    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr(f"protos-{version}/SOURCE.txt", source)
        z.writestr(f"protos-{version}/RUNTIME.txt", runtime)
        z.writestr(f"protos-{version}/LICENSE.TXT", "fixture\n")
    return archive


class PrepareReleaseMetadataTest(unittest.TestCase):
    def test_deterministic_envelope_and_portable_checksum(self) -> None:
        with tempfile.TemporaryDirectory(prefix="protos-e3b-") as td:
            root = Path(td)
            archive = candidate_archive(root)
            out1 = root / "out1"
            out2 = root / "out2"

            prepare(Args(archive=str(archive), output_dir=str(out1)))
            prepare(Args(archive=str(archive), output_dir=str(out2)))

            for name in [
                archive.name + ".sha256",
                "RELEASE_NOTES.md",
                "RELEASE_MANIFEST.txt",
            ]:
                self.assertEqual((out1 / name).read_bytes(), (out2 / name).read_bytes())

            checksum = (out1 / (archive.name + ".sha256")).read_text(encoding="utf-8")
            expected_archive_sha = sha256(archive.read_bytes())
            self.assertEqual(checksum, f"{expected_archive_sha}  {archive.name}\n")
            self.assertNotIn("/", checksum.split("  ", 1)[1])

            copied = out1 / archive.name
            copied.write_bytes(archive.read_bytes())
            subprocess.run(
                ["sha256sum", "-c", archive.name + ".sha256"],
                cwd=out1,
                check=True,
                stdout=subprocess.DEVNULL,
            )

            notes = (out1 / "RELEASE_NOTES.md").read_text(encoding="utf-8")
            for needle in [
                "# Protos 0.2.230",
                "Candidate source revision: `" + "b" * 40 + "`",
                "Development baseline revision: `" + "a" * 40 + "`",
                "Core specification revision: `0.1.382`",
                "GraalVM Community Edition for JDK 22",
                "Portable distribution works outside the checkout.",
                "GraalVM Community JDK 22 is externally required.",
                archive.name,
                expected_archive_sha,
            ]:
                self.assertIn(needle, notes)

            manifest = (out1 / "RELEASE_MANIFEST.txt").read_text(encoding="utf-8")
            for needle in [
                "release_envelope_format=protos-public-prerelease-envelope-v1",
                "release_version=0.2.230",
                "release_tag=v0.2.230",
                "prerelease=true",
                "source_revision=" + "b" * 40,
                "release_baseline_revision=" + "a" * 40,
                "specification_revision=0.1.382",
                "portable_archive=" + archive.name,
                "portable_archive_sha256=" + expected_archive_sha,
                "release_notes=RELEASE_NOTES.md",
            ]:
                self.assertIn(needle, manifest)

    def test_rejects_development_archive(self) -> None:
        with tempfile.TemporaryDirectory(prefix="protos-e3b-dev-") as td:
            root = Path(td)
            archive = candidate_archive(root, public_release="false")
            with self.assertRaises(SystemExit):
                prepare(Args(archive=str(archive), output_dir=str(root / "out")))

    def test_rejects_dirty_candidate(self) -> None:
        with tempfile.TemporaryDirectory(prefix="protos-e3b-dirty-") as td:
            root = Path(td)
            archive = candidate_archive(root, dirty="true")
            with self.assertRaises(SystemExit):
                prepare(Args(archive=str(archive), output_dir=str(root / "out")))

    def test_requires_explicit_capability_and_limitation(self) -> None:
        with tempfile.TemporaryDirectory(prefix="protos-e3b-claims-") as td:
            root = Path(td)
            archive = candidate_archive(root)
            args = Args(archive=str(archive), output_dir=str(root / "out"))
            args.capability = []
            with self.assertRaises(SystemExit):
                prepare(args)

            args = Args(archive=str(archive), output_dir=str(root / "out2"))
            args.limitation = []
            with self.assertRaises(SystemExit):
                prepare(args)

    def test_rejects_noncanonical_spec_revision(self) -> None:
        with tempfile.TemporaryDirectory(prefix="protos-e3b-spec-") as td:
            root = Path(td)
            archive = candidate_archive(root)
            with self.assertRaises(SystemExit):
                prepare(
                    Args(
                        archive=str(archive),
                        output_dir=str(root / "out"),
                        spec_revision="draft",
                    )
                )


if __name__ == "__main__":
    unittest.main(verbosity=2)
