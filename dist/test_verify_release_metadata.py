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
import shutil
import tempfile
import unittest
import zipfile

from prepare_release_metadata import prepare
from verify_release_metadata import verify_envelope


class Args:
    def __init__(self, archive: Path, output_dir: Path) -> None:
        self.archive = str(archive)
        self.output_dir = str(output_dir)
        self.spec_revision = "0.1.382"
        self.capability = ["Portable execution is validated outside the checkout."]
        self.limitation = ["GraalVM Community JDK 22 remains an external requirement."]


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def write_candidate_archive(path: Path) -> None:
    version = "0.2.230"
    root = f"protos-{version}"
    source = "\n".join(
        [
            f"implementation_version={version}",
            "source_revision=" + "b" * 40,
            "source_dirty=false",
            "source_repository=https://github.com/guillermomolina/protos",
            "source_path=/tree/" + "b" * 40,
            "artifact_kind=public-prerelease",
            "public_release=true",
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
            "java_vendor_contains=GraalVM",
            "java_distribution=GraalVM Community Edition for JDK 22",
            "truffle_runtime_version=24.0.0",
            "optimizing_runtime=HotSpotTruffleRuntime",
        ]
    ) + "\n"
    entries = {
        "SOURCE.txt": source.encode(),
        "RUNTIME.txt": runtime.encode(),
        "LICENSE.TXT": b"fixture\n",
    }
    entries["SHA256SUMS"] = (
        "\n".join(f"{sha256(data)}  {name}" for name, data in sorted(entries.items()))
        + "\n"
    ).encode()
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in sorted(entries.items()):
            z.writestr(f"{root}/{name}", data)


class VerifyReleaseMetadataTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="protos-e3c2-")
        self.root = Path(self.temp.name)
        self.archive = self.root / "protos-0.2.230-posix-jvm.zip"
        self.envelope = self.root / "envelope"
        write_candidate_archive(self.archive)
        prepare(Args(self.archive, self.envelope))

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_accepts_exact_e3b_envelope(self) -> None:
        verify_envelope(self.archive, self.envelope)

    def test_rejects_corrupt_archive_bytes(self) -> None:
        data = bytearray(self.archive.read_bytes())
        data[-1] ^= 1
        self.archive.write_bytes(data)
        with self.assertRaises(SystemExit):
            verify_envelope(self.archive, self.envelope)

    def test_rejects_modified_checksum(self) -> None:
        checksum = self.envelope / (self.archive.name + ".sha256")
        checksum.write_text("0" * 64 + "  " + self.archive.name + "\n", encoding="utf-8")
        with self.assertRaises(SystemExit):
            verify_envelope(self.archive, self.envelope)

    def test_rejects_modified_notes(self) -> None:
        notes = self.envelope / "RELEASE_NOTES.md"
        notes.write_text(notes.read_text(encoding="utf-8") + "tampered\n", encoding="utf-8")
        with self.assertRaises(SystemExit):
            verify_envelope(self.archive, self.envelope)

    def test_rejects_modified_manifest_identity(self) -> None:
        manifest = self.envelope / "RELEASE_MANIFEST.txt"
        text = manifest.read_text(encoding="utf-8").replace(
            "release_tag=v0.2.230",
            "release_tag=v0.2.999",
        )
        manifest.write_text(text, encoding="utf-8")
        with self.assertRaises(SystemExit):
            verify_envelope(self.archive, self.envelope)

    def test_rejects_extra_envelope_file(self) -> None:
        (self.envelope / "unexpected.txt").write_text("unexpected\n", encoding="utf-8")
        with self.assertRaises(SystemExit):
            verify_envelope(self.archive, self.envelope)


if __name__ == "__main__":
    unittest.main(verbosity=2)
