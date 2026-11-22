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

"""Focused I170 checks for the FFM provider builder's SDK API class path."""

from __future__ import annotations

import json
import os
from pathlib import Path
import stat
import sys
import tempfile
import textwrap
import unittest
import zipfile

DIST = Path(__file__).resolve().parents[2]
if str(DIST) not in sys.path:
    sys.path.insert(0, str(DIST))

import build_ffm_provider
from build_ffm_provider import BuildError, IMAGE_INFO, SERVICE

SDK_API = "org.graalvm.sdk.nativeimage-25.5.5.0.3.jar"
PROVIDER_CLASS = "com/guillermomolina/protos/ffm/FfmForeignProvider.class"


def write_jar(path: Path, entries: dict[str, bytes]) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as jar:
        for name, data in entries.items():
            jar.writestr(name, data)
    return path


def fake_javac(root: Path) -> tuple[str, Path]:
    """A recording javac that writes one Java 25 classfile into -d."""
    record = root / "javac-argv.json"
    script = root / "javac"
    script.write_text(textwrap.dedent(f"""\
        #!{sys.executable}
        import json, pathlib, sys
        argv = sys.argv[1:]
        pathlib.Path({str(record)!r}).write_text(json.dumps(argv))
        out = pathlib.Path(argv[argv.index("-d") + 1]) / {PROVIDER_CLASS!r}
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(bytes.fromhex("CAFEBABE00000045"))
        """), encoding="utf-8")
    script.chmod(script.stat().st_mode | stat.S_IXUSR)
    return str(script), record


def installation(root: Path, sdk_jars: dict[str, bool]) -> Path:
    install = root / "protos-portable"
    write_jar(install / "lib" / "protos.jar", {"com/guillermomolina/protos/spi/X.class": b"x"})
    write_jar(install / "lib" / "runtime" / "org.graalvm.sdk.graal-sdk-25.5.5.0.3.jar",
              {"org/graalvm/other/Other.class": b"x"})
    for name, holds in sdk_jars.items():
        write_jar(install / "lib" / "runtime" / name,
                  {IMAGE_INFO if holds else "org/graalvm/word/Word.class": b"x"})
    return install


def zipped(install: Path, archive: Path) -> Path:
    with zipfile.ZipFile(archive, "w") as out:
        for path in sorted(install.rglob("*")):
            if path.is_file():
                out.write(path, Path(install.name, path.relative_to(install)).as_posix())
    return archive


class SdkApiResolutionTest(unittest.TestCase):
    def test_directory_and_zip_select_the_same_exact_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            install = installation(root, {SDK_API: True})
            scratch = root / "scratch"
            scratch.mkdir()
            self.assertEqual(SDK_API, build_ffm_provider.sdk_api_jar_from(install, scratch).name)
            archive = zipped(install, root / "protos.zip")
            extracted = build_ffm_provider.sdk_api_jar_from(archive, scratch)
            self.assertEqual(SDK_API, extracted.name)
            self.assertEqual(scratch, extracted.parent)
            with zipfile.ZipFile(extracted) as jar:
                self.assertIn(IMAGE_INFO, jar.namelist())

    def test_missing_artifact_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            install = installation(root, {SDK_API: False})
            scratch = root / "scratch"
            scratch.mkdir()
            for distribution in (install, zipped(install, root / "protos.zip")):
                with self.assertRaisesRegex(BuildError, "no Native Image SDK API JAR"):
                    build_ffm_provider.sdk_api_jar_from(distribution, scratch)

    def test_ambiguous_artifacts_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            install = installation(
                root, {SDK_API: True, "org.graalvm.sdk.nativeimage-copy.jar": True})
            scratch = root / "scratch"
            scratch.mkdir()
            for distribution in (install, zipped(install, root / "protos.zip")):
                with self.assertRaisesRegex(BuildError, "ambiguous"):
                    build_ffm_provider.sdk_api_jar_from(distribution, scratch)


class ProviderBuildTest(unittest.TestCase):
    def build(self, root: Path, from_zip: bool) -> tuple[Path, list[str], Path, Path]:
        install = installation(root, {SDK_API: True})
        distribution = zipped(install, root / "protos.zip") if from_zip else install
        javac, record = fake_javac(root)
        scratch = root / "scratch"
        scratch.mkdir()
        protos_jar = build_ffm_provider.protos_jar_from(distribution, scratch)
        sdk_jar = build_ffm_provider.sdk_api_jar_from(distribution, scratch)
        output = build_ffm_provider.build_provider_jar(
            javac, protos_jar, root / "out" / build_ffm_provider.JAR_NAME, scratch,
            sdk_jar=sdk_jar)
        return output, json.loads(record.read_text()), protos_jar, sdk_jar

    def check(self, from_zip: bool) -> None:
        with tempfile.TemporaryDirectory() as raw:
            output, argv, protos_jar, sdk_jar = self.build(Path(raw), from_zip)
            classpath = argv[argv.index("-classpath") + 1].split(os.pathsep)
            self.assertEqual([str(protos_jar), str(sdk_jar)], classpath)
            with zipfile.ZipFile(output) as jar:
                names = jar.namelist()
                self.assertFalse([n for n in names if n.startswith("org/graalvm/")])
                self.assertEqual(sorted([PROVIDER_CLASS, SERVICE]), sorted(names))
                service = (build_ffm_provider.PROVIDER / "resources" / SERVICE).read_bytes()
                self.assertEqual(service, jar.read(SERVICE))

    def test_build_from_installation(self) -> None:
        self.check(from_zip=False)

    def test_build_from_zip(self) -> None:
        self.check(from_zip=True)

    def test_installation_default_resolves_the_runtime_sdk_api(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            install = installation(root, {SDK_API: True})
            javac, record = fake_javac(root)
            scratch = root / "scratch"
            scratch.mkdir()
            build_ffm_provider.build_provider_jar(
                javac, install / "lib" / "protos.jar", root / "out" / "p.jar", scratch)
            argv = json.loads(record.read_text())
            self.assertEqual(
                [str(install / "lib" / "protos.jar"), str(install / "lib" / "runtime" / SDK_API)],
                argv[argv.index("-classpath") + 1].split(os.pathsep))


if __name__ == "__main__":
    unittest.main()
