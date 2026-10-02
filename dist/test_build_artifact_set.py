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
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

DIST = Path(__file__).resolve().parent
if str(DIST) not in sys.path:
    sys.path.insert(0, str(DIST))

from build_artifact_set import (
    CHECKSUMS_NAME,
    MANIFEST_NAME,
    ArtifactSetError,
    construction_steps,
    render_manifest,
    verify_artifact_set,
    write_envelope,
)

REVISION = "a" * 40
OTHER_REVISION = "b" * 40
VERSION = "0.3.1-SNAPSHOT"
JVM = f"protos-{VERSION}-posix-jvm.zip"
NATIVE = f"protos-{VERSION}-native-linux-x86_64.zip"
D064 = f"protos-{VERSION}-stdlib-documentation.json"
D067 = f"protos-{VERSION}-stdlib-documentation-coverage.txt"


def source_txt(revision: str) -> str:
    return (
        f"implementation_version={VERSION}\n"
        f"source_revision={revision}\n"
        "source_dirty=false\n"
        "public_release=false\n"
    )


def write_archive(path: Path, root: str, revision: str, runtime: str) -> None:
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr(f"{root}/SOURCE.txt", source_txt(revision))
        archive.writestr(f"{root}/RUNTIME.txt", runtime)


def write_d064(path: Path, revision: str) -> None:
    document = {
        "format": {"name": "protos-documentation", "major": 1, "minor": 0},
        "provenance": {
            "kind": "repositoryRevision",
            "repository": "guillermomolina/protos",
            "revision": revision,
        },
    }
    path.write_bytes((json.dumps(document) + "\n").encode("utf-8"))


def populate(directory: Path, *, revision: str = REVISION,
             native_revision: str | None = None,
             d064_revision: str | None = None) -> None:
    write_archive(
        directory / JVM,
        f"protos-{VERSION}",
        revision,
        "distribution_format=protos-portable-posix-jvm-v1\n"
        "graalvm_release=25.4.4.1.1\n"
        "java_version=25.0.4.1.1\n"
        "truffle_runtime_version=25.4.4.1.1\n",
    )
    write_archive(
        directory / NATIVE,
        NATIVE.removesuffix(".zip"),
        native_revision or revision,
        "distribution_format=protos-native-image-posix-v1\n"
        "graalvm_release=25.4.4.1.1\n"
        "native_build_container=example/native:1\n"
        "target_os=linux\ntarget_arch=x86_64\nlinkage=dynamic\n"
        "libc_family=glibc\nlibc_abi_min=2.39\n"
        "cpu_isa_assumption=compatibility\n",
    )
    write_d064(directory / D064, d064_revision or revision)
    (directory / D067).write_text("modules.total=1\n", encoding="utf-8")


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class ArtifactSetTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self._tmp.name)

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def built(self) -> dict[str, object]:
        populate(self.dir)
        write_envelope(self.dir, revision=REVISION, version=VERSION)
        return verify_artifact_set(self.dir, expect_revision=REVISION)

    def test_manifest_binds_every_member_to_exact_revision_and_digest(self) -> None:
        manifest = self.built()
        self.assertEqual(manifest["revision"], REVISION)
        self.assertEqual(manifest["version"], VERSION)
        self.assertEqual(manifest["repository"], "guillermomolina/protos")
        self.assertIs(manifest["public_release"], False)
        by_kind = {entry["kind"]: entry for entry in manifest["artifacts"]}
        self.assertEqual(
            set(by_kind),
            {
                "jvm-distribution",
                "native-distribution",
                "stdlib-documentation",
                "stdlib-documentation-coverage",
            },
        )
        for kind, name in [
            ("jvm-distribution", JVM),
            ("native-distribution", NATIVE),
            ("stdlib-documentation", D064),
        ]:
            self.assertEqual(by_kind[kind]["path"], name)
            self.assertEqual(by_kind[kind]["sha256"], digest(self.dir / name))
        self.assertEqual(
            by_kind["stdlib-documentation"]["identity"]["provenance_revision"],
            REVISION,
        )
        self.assertEqual(
            by_kind["native-distribution"]["identity"]["target_arch"], "x86_64"
        )
        sums = (self.dir / CHECKSUMS_NAME).read_text(encoding="utf-8")
        self.assertIn(f"{digest(self.dir / NATIVE)}  {NATIVE}\n", sums)

    def test_manifest_serialization_is_deterministic(self) -> None:
        self.built()
        first = (self.dir / MANIFEST_NAME).read_bytes()
        write_envelope(self.dir, revision=REVISION, version=VERSION)
        self.assertEqual((self.dir / MANIFEST_NAME).read_bytes(), first)
        manifest = json.loads(first)
        self.assertEqual(
            render_manifest(
                revision=REVISION,
                version=VERSION,
                artifacts=list(reversed(manifest["artifacts"])),
            ),
            first,
        )
        self.assertTrue(first.endswith(b"}\n"))

    def test_d064_provenance_must_match_set_revision(self) -> None:
        populate(self.dir, d064_revision=OTHER_REVISION)
        with self.assertRaisesRegex(ArtifactSetError, "provenance"):
            write_envelope(self.dir, revision=REVISION, version=VERSION)

    def test_mixed_revision_native_archive_fails(self) -> None:
        populate(self.dir, native_revision=OTHER_REVISION)
        with self.assertRaisesRegex(ArtifactSetError, "source_revision"):
            write_envelope(self.dir, revision=REVISION, version=VERSION)

    def test_missing_required_artifact_fails(self) -> None:
        for name in (JVM, NATIVE, D064, D067):
            with self.subTest(name=name):
                populate(self.dir)
                (self.dir / name).unlink()
                with self.assertRaises(ArtifactSetError):
                    write_envelope(self.dir, revision=REVISION, version=VERSION)

    def test_partial_set_without_manifest_is_not_a_set(self) -> None:
        populate(self.dir)
        with self.assertRaisesRegex(ArtifactSetError, MANIFEST_NAME):
            verify_artifact_set(self.dir)

    def test_member_removed_after_envelope_fails(self) -> None:
        self.built()
        (self.dir / NATIVE).unlink()
        with self.assertRaises(ArtifactSetError):
            verify_artifact_set(self.dir)

    def test_expected_revision_mismatch_fails(self) -> None:
        self.built()
        with self.assertRaisesRegex(ArtifactSetError, "not expected"):
            verify_artifact_set(self.dir, expect_revision=OTHER_REVISION)

    def test_stale_member_from_other_revision_fails(self) -> None:
        self.built()
        write_archive(
            self.dir / JVM,
            f"protos-{VERSION}",
            OTHER_REVISION,
            "distribution_format=x\ngraalvm_release=x\n"
            "java_version=x\ntruffle_runtime_version=x\n",
        )
        with self.assertRaisesRegex(ArtifactSetError, "source_revision"):
            verify_artifact_set(self.dir)

    def test_changed_bytes_invalidate_recorded_digest(self) -> None:
        self.built()
        with (self.dir / D067).open("a", encoding="utf-8") as handle:
            handle.write("symbols.total=2\n")
        with self.assertRaisesRegex(ArtifactSetError, "manifest record"):
            verify_artifact_set(self.dir)

    def test_tampered_checksums_fail(self) -> None:
        self.built()
        (self.dir / CHECKSUMS_NAME).write_text("0  x\n", encoding="utf-8")
        with self.assertRaisesRegex(ArtifactSetError, CHECKSUMS_NAME):
            verify_artifact_set(self.dir)

    def test_unrecorded_extra_entry_fails(self) -> None:
        self.built()
        (self.dir / "protos-old-posix-jvm.zip").write_bytes(b"stale")
        with self.assertRaisesRegex(ArtifactSetError, "unrecorded"):
            verify_artifact_set(self.dir)

    def test_second_native_archive_is_ambiguous(self) -> None:
        populate(self.dir)
        (self.dir / f"protos-{VERSION}-native-linux-aarch64.zip").write_bytes(b"")
        with self.assertRaisesRegex(ArtifactSetError, "exactly one Native"):
            write_envelope(self.dir, revision=REVISION, version=VERSION)

    def test_construction_never_invokes_release_machinery(self) -> None:
        release_markers = (
            "--public-prerelease",
            "--release-baseline",
            "prepare_release",
            "publish_release",
            "materialize_release_candidate",
            "release_asset_envelope",
            "prepare_release_metadata",
            "commit_release_candidate",
            "transition_release_candidate_version",
            "tag",
            "gh",
            "push",
        )
        steps = construction_steps()
        self.assertEqual(
            [label for label, _ in steps],
            [
                "clean",
                "native-image-build",
                "native-archive-build",
                "portable-jvm-build",
            ],
        )
        for label, command in steps:
            for part in command:
                for marker in release_markers:
                    self.assertNotIn(marker, part.split("/")[-1], label)


if __name__ == "__main__":
    unittest.main()
