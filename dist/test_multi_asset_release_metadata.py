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

from release_asset_envelope import (
    FALLBACK_ROLE,
    NATIVE_KIND,
    PORTABLE_KIND,
    RECOMMENDED_ROLE,
    prepare_multi,
    verify_multi,
)


VERSION = "0.2.230"
BASELINE = "a" * 40
CANDIDATE = "b" * 40


class Args:
    def __init__(
        self,
        *,
        archives: list[Path],
        output_dir: Path,
        roles: list[str],
    ) -> None:
        self.archive = [str(path) for path in archives]
        self.output_dir = str(output_dir)
        self.spec_revision = "0.1.382"
        self.capability = ["Synthetic multi-asset release-envelope fixture."]
        self.limitation = ["Synthetic Native platform values are test-only."]
        self.asset_role = roles


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def source_text(candidate: str) -> str:
    return "\n".join(
        [
            f"implementation_version={VERSION}",
            f"source_revision={candidate}",
            "source_dirty=false",
            "source_repository=https://github.com/guillermomolina/protos",
            f"source_path=/tree/{candidate}",
            "artifact_kind=public-prerelease",
            "public_release=true",
            f"release_baseline_revision={BASELINE}",
            f"release_baseline_version={VERSION}-SNAPSHOT",
            f"release_version={VERSION}",
            f"release_tag=v{VERSION}",
        ]
    ) + "\n"


def write_asset(
    directory: Path,
    *,
    kind: str,
    candidate: str = CANDIDATE,
    cpu_isa: str = "synthetic-test-isa",
    omit_native_key: str | None = None,
    distribution_format: str | None = None,
) -> Path:
    if kind == PORTABLE_KIND:
        root = f"protos-{VERSION}"
        archive = directory / f"protos-{VERSION}-posix-jvm.zip"
        runtime_values = [
            "distribution_format="
            + (distribution_format or "protos-portable-posix-jvm-v1"),
            "java_feature=25",
            "java_distribution=GraalVM Community Edition for JDK 25",
            "truffle_runtime_version=25.4.4.1.1",
            "optimizing_runtime=HotSpotTruffleRuntime",
        ]
    else:
        root = f"protos-{VERSION}-native-linux-x86_64"
        archive = directory / (root + ".zip")
        runtime = {
            "distribution_format": distribution_format
            or "protos-native-image-posix-v1",
            "native_runtime_kind": "graalvm-native-image-truffle",
            "external_java_required": "false",
            "graalvm_release": "25.4.4.1.1",
            "native_image_version": "25.4.4.1.1",
            "jdk_version": "25.0.4.1.1",
            "target_os": "linux",
            "target_arch": "x86_64",
            "linkage": "dynamic",
            "libc_family": "glibc",
            "cpu_isa_assumption": cpu_isa,
        }
        if omit_native_key is not None:
            runtime.pop(omit_native_key)
        runtime_values = [f"{key}={value}" for key, value in runtime.items()]

    entries: dict[str, bytes] = {
        "SOURCE.txt": source_text(candidate).encode("utf-8"),
        "RUNTIME.txt": ("\n".join(runtime_values) + "\n").encode("utf-8"),
        "LICENSE.TXT": b"synthetic license fixture\n",
        "DEPENDENCIES.txt": b"synthetic dependency notice fixture\n",
    }
    entries["SHA256SUMS"] = (
        "\n".join(f"{sha256(data)}  {name}" for name, data in sorted(entries.items()))
        + "\n"
    ).encode("utf-8")

    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in sorted(entries.items()):
            z.writestr(f"{root}/{name}", data)
    return archive


def native_key(cpu_isa: str = "synthetic-test-isa") -> str:
    return f"native-linux-x86_64-glibc-dynamic-{cpu_isa}"


def model_roles(cpu_isa: str = "synthetic-test-isa") -> list[str]:
    return [
        f"{PORTABLE_KIND}={FALLBACK_ROLE}",
        f"{native_key(cpu_isa)}={RECOMMENDED_ROLE}",
    ]


class MultiAssetReleaseEnvelopeTest(unittest.TestCase):
    def test_deterministic_generation_and_verification(self) -> None:
        with tempfile.TemporaryDirectory(prefix="protos-dist005-d1-") as td:
            root = Path(td)
            jvm = write_asset(root, kind=PORTABLE_KIND)
            native = write_asset(root, kind=NATIVE_KIND)
            out1 = root / "out1"
            out2 = root / "out2"

            prepare_multi(
                Args(
                    archives=[jvm, native],
                    output_dir=out1,
                    roles=model_roles(),
                )
            )
            prepare_multi(
                Args(
                    archives=[native, jvm],
                    output_dir=out2,
                    roles=list(reversed(model_roles())),
                )
            )

            names1 = sorted(p.name for p in out1.iterdir())
            names2 = sorted(p.name for p in out2.iterdir())
            self.assertEqual(names1, names2)
            for name in names1:
                self.assertEqual((out1 / name).read_bytes(), (out2 / name).read_bytes())

            verify_multi([jvm, native], out1)

            manifest = (out1 / "RELEASE_MANIFEST.txt").read_text(encoding="utf-8")
            for needle in [
                "release_envelope_format=protos-public-prerelease-envelope-v2",
                "distribution_model=JVM_PLUS_NATIVE",
                "asset_count=2",
                "asset.0.kind=native",
                "asset.0.role=recommended-first-run",
                "asset.1.kind=portable-jvm",
                "asset.1.role=compatibility-fallback",
                "asset.0.cpu_isa_assumption=synthetic-test-isa",
                "asset.0.external_java_required=false",
                "asset.1.external_java_required=true",
                "license_sha256=",
                "notice_sha256=",
            ]:
                self.assertIn(needle, manifest)

    def test_rejects_mixed_candidate_identity(self) -> None:
        with tempfile.TemporaryDirectory(prefix="protos-dist005-mixed-") as td:
            root = Path(td)
            jvm = write_asset(root, kind=PORTABLE_KIND)
            native_dir = root / "native"
            native_dir.mkdir()
            native = write_asset(native_dir, kind=NATIVE_KIND, candidate="c" * 40)
            with self.assertRaises(SystemExit):
                prepare_multi(
                    Args(
                        archives=[jvm, native],
                        output_dir=root / "out",
                        roles=model_roles(),
                    )
                )

    def test_rejects_duplicate_artifact_identity(self) -> None:
        with tempfile.TemporaryDirectory(prefix="protos-dist005-dup-") as td:
            root = Path(td)
            a = root / "a"
            b = root / "b"
            a.mkdir()
            b.mkdir()
            first = write_asset(a, kind=PORTABLE_KIND)
            second = b / first.name
            shutil.copy2(first, second)
            with self.assertRaises(SystemExit):
                prepare_multi(
                    Args(
                        archives=[first, second],
                        output_dir=root / "out",
                        roles=[f"{PORTABLE_KIND}={FALLBACK_ROLE}"],
                    )
                )

    def test_rejects_missing_native_platform_identity(self) -> None:
        with tempfile.TemporaryDirectory(prefix="protos-dist005-missing-") as td:
            root = Path(td)
            jvm = write_asset(root, kind=PORTABLE_KIND)
            native_dir = root / "native"
            native_dir.mkdir()
            native = write_asset(
                native_dir,
                kind=NATIVE_KIND,
                omit_native_key="cpu_isa_assumption",
            )
            with self.assertRaises(SystemExit):
                prepare_multi(
                    Args(
                        archives=[jvm, native],
                        output_dir=root / "out",
                        roles=model_roles(),
                    )
                )

    def test_rejects_unresolved_public_native_isa(self) -> None:
        with tempfile.TemporaryDirectory(prefix="protos-dist005-isa-") as td:
            root = Path(td)
            jvm = write_asset(root, kind=PORTABLE_KIND)
            native_dir = root / "native"
            native_dir.mkdir()
            native = write_asset(native_dir, kind=NATIVE_KIND, cpu_isa="unresolved")
            with self.assertRaises(SystemExit):
                prepare_multi(
                    Args(
                        archives=[jvm, native],
                        output_dir=root / "out",
                        roles=model_roles("unresolved"),
                    )
                )

    def test_rejects_ambiguous_recommended_first_run(self) -> None:
        with tempfile.TemporaryDirectory(prefix="protos-dist005-role-") as td:
            root = Path(td)
            jvm = write_asset(root, kind=PORTABLE_KIND)
            native = write_asset(root, kind=NATIVE_KIND)
            with self.assertRaises(SystemExit):
                prepare_multi(
                    Args(
                        archives=[jvm, native],
                        output_dir=root / "out",
                        roles=[
                            f"{PORTABLE_KIND}={RECOMMENDED_ROLE}",
                            f"{native_key()}={RECOMMENDED_ROLE}",
                        ],
                    )
                )

    def test_rejects_independent_checksum_tamper(self) -> None:
        with tempfile.TemporaryDirectory(prefix="protos-dist005-sum-") as td:
            root = Path(td)
            jvm = write_asset(root, kind=PORTABLE_KIND)
            native = write_asset(root, kind=NATIVE_KIND)
            out = root / "out"
            prepare_multi(
                Args(
                    archives=[jvm, native],
                    output_dir=out,
                    roles=model_roles(),
                )
            )
            checksum = out / (native.name + ".sha256")
            checksum.write_text(
                "0" * 64 + "  " + native.name + "\n",
                encoding="utf-8",
            )
            with self.assertRaises(SystemExit):
                verify_multi([jvm, native], out)

    def test_rejects_unknown_public_artifact_kind(self) -> None:
        with tempfile.TemporaryDirectory(prefix="protos-dist005-kind-") as td:
            root = Path(td)
            jvm = write_asset(
                root,
                kind=PORTABLE_KIND,
                distribution_format="synthetic-unknown-public-format",
            )
            native = write_asset(root, kind=NATIVE_KIND)
            with self.assertRaises(SystemExit):
                prepare_multi(
                    Args(
                        archives=[jvm, native],
                        output_dir=root / "out",
                        roles=model_roles(),
                    )
                )


if __name__ == "__main__":
    unittest.main(verbosity=2)
