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

import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[3]
INSTALLER = ROOT / "dist" / "install.sh"


class InstallReleaseSelectionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="protos-install-release-")
        self.work = Path(self.temp.name)
        self.bin_dir = self.work / "bin"
        self.bin_dir.mkdir()

        fake_curl = self.bin_dir / "curl"
        fake_curl.write_text(
            '#!/usr/bin/env sh\n'
            'curl_output=\nwhile [ "$#" -gt 0 ]; do\n    if [ "$1" = "-o" ]; then\n        shift\n        curl_output=$1\n    fi\n    shift\ndone\nif [ -n "$curl_output" ]; then\n    cp "$PROTOS_TEST_RESPONSE" "$curl_output"\nelse\n    printf \'%s\\n\' \'fake curl: missing -o destination\' >&2\n    false\nfi\n',
            encoding="utf-8",
        )
        fake_curl.chmod(0o755)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def run_installer(self, payload: str) -> subprocess.CompletedProcess[str]:
        response = self.work / "releases.json"
        response.write_text(payload, encoding="utf-8")

        env = os.environ.copy()
        env["PATH"] = str(self.bin_dir) + os.pathsep + env.get("PATH", "")
        env["PROTOS_TEST_RESPONSE"] = str(response)

        return subprocess.run(
            ["sh", str(INSTALLER), "--list-releases"],
            cwd=self.work,
            env=env,
            capture_output=True,
            text=True,
            check=False,
        )

    def test_published_releases_ignore_nested_tags_and_drafts(self) -> None:
        releases = [
            {
                "tag_name": "v0.3.398",
                "draft": False,
                "prerelease": True,
                "body": 'Example text: "tag_name": "v9.9.9"',
                "assets": [{"name": "native.zip", "tag_name": "v8.8.8"}],
            },
            {
                "tag_name": "v0.3.312",
                "draft": False,
                "prerelease": True,
                "assets": [],
            },
            {
                "tag_name": "v0.3.999",
                "draft": True,
                "prerelease": True,
                "assets": [],
            },
        ]

        result = self.run_installer(json.dumps(releases))

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            ["v0.3.398", "v0.3.312"],
            result.stdout.splitlines(),
        )

    def test_truncated_json_fails_closed(self) -> None:
        result = self.run_installer(
            '[{"tag_name":"v0.3.398","draft":false'
        )

        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", result.stdout)

    def test_non_release_json_fails_closed(self) -> None:
        result = self.run_installer('{"message":"API rate limit exceeded"}')

        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", result.stdout)

    def test_invalid_version_tags_are_ignored(self) -> None:
        releases = [
            {"tag_name": "v0.3.398-SNAPSHOT", "draft": False},
            {"tag_name": "v0.3.398;malicious", "draft": False},
            {"tag_name": "v0.3.312", "draft": False},
        ]

        result = self.run_installer(json.dumps(releases))

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("v0.3.312\n", result.stdout)


if __name__ == "__main__":
    unittest.main()


import json as i131_json
import os as i131_os
import subprocess as i131_subprocess
import tempfile as i131_tempfile
import unittest as i131_unittest
from pathlib import Path as I131Path


class InstallNativeManifestInspectionTest(i131_unittest.TestCase):
    INSTALLER = I131Path(__file__).resolve().parents[2] / "install.sh"

    def _manifest(self, tag="v0.3.398"):
        version = tag[1:]
        archive = f"protos-{version}-native-linux-x86_64.zip"

        fields = [
            "release_envelope_format=protos-public-prerelease-envelope-v2",
            "distribution_model=JVM_PLUS_NATIVE",
            f"release_version={version}",
            f"release_tag={tag}",
            "prerelease=true",
            "source_repository=https://github.com/guillermomolina/protos",
            "source_revision=" + "1" * 40,
            "asset_count=2",
            "asset.0.kind=portable-jvm",
            "asset.0.role=compatibility-fallback",
            "asset.1.kind=native",
            "asset.1.role=recommended-first-run",
            "asset.1.distribution_format=protos-native-image-posix-v1",
            "asset.1.external_java_required=false",
            "asset.1.native_runtime_kind=graalvm-native-image-truffle",
            "asset.1.target_os=linux",
            "asset.1.target_arch=x86_64",
            "asset.1.libc_family=glibc",
            "asset.1.libc_abi_min=2.39",
            "asset.1.libc_abi_observed_max=2.39",
            "asset.1.glibc_symbol_versions=GLIBC_2.39",
            "asset.1.elf_interpreter=/lib64/ld-linux-x86-64.so.2",
            "asset.1.dt_needed=libc.so.6",
            "asset.1.shared_library_closure=libc.so.6",
            "asset.1.linkage=dynamic",
            "asset.1.cpu_isa_assumption=compatibility",
            "asset.1.platform_identity=linux/x86_64/glibc/2.39/dynamic/compatibility",
            f"asset.1.archive={archive}",
            "asset.1.archive_sha256=" + "a" * 64,
            f"asset.1.checksum={archive}.sha256",
            "asset.1.checksum_sha256=" + "b" * 64,
        ]

        return "\n".join(fields) + "\n"

    def _run_inspection(
        self,
        *,
        manifest=None,
        releases=None,
        glibc="glibc 2.40",
        arch="x86_64",
        arguments=None,
    ):
        if manifest is None:
            manifest = self._manifest()

        if releases is None:
            releases = [
                {"tag_name": "v0.3.397", "draft": False},
                {"tag_name": "v0.3.398", "draft": False},
            ]

        if arguments is None:
            arguments = ["--inspect"]

        with i131_tempfile.TemporaryDirectory() as workspace:
            root = I131Path(workspace)
            commands = root / "commands"
            commands.mkdir()

            release_file = root / "releases.json"
            manifest_file = root / "manifest.txt"

            release_file.write_text(
                i131_json.dumps(releases),
                encoding="utf-8",
            )
            manifest_file.write_text(manifest, encoding="utf-8")

            fake_curl = commands / "curl"
            fake_curl.write_text(
                """#!/bin/sh
destination=
request_url=
while [ "$#" -gt 0 ]; do
    if [ "$1" = "-o" ]; then
        shift
        destination=$1
    else
        case "$1" in
            https://*) request_url=$1 ;;
        esac
    fi
    shift
done

case "$request_url" in
    *'/releases?per_page=100')
        cp "$PROTOS_TEST_RELEASES" "$destination"
        ;;
    *'/RELEASE_MANIFEST.txt')
        cp "$PROTOS_TEST_MANIFEST" "$destination"
        ;;
    *)
        printf '%s\\n' 'unexpected fake curl URL' >&2
        false
        ;;
esac
""",
                encoding="utf-8",
            )
            fake_curl.chmod(0o755)

            fake_getconf = commands / "getconf"
            fake_getconf.write_text(
                "#!/bin/sh\n"
                "printf '%s\\n' \"$PROTOS_TEST_GLIBC\"\n",
                encoding="utf-8",
            )
            fake_getconf.chmod(0o755)

            fake_uname = commands / "uname"
            fake_uname.write_text(
                "#!/bin/sh\n"
                "if [ \"$1\" = \"-s\" ]; then\n"
                "    printf 'Linux\\n'\n"
                "else\n"
                "    printf '%s\\n' \"$PROTOS_TEST_ARCH\"\n"
                "fi\n",
                encoding="utf-8",
            )
            fake_uname.chmod(0o755)

            environment = i131_os.environ.copy()
            environment["PATH"] = (
                str(commands) + i131_os.pathsep +
                environment.get("PATH", "")
            )
            environment["PROTOS_TEST_RELEASES"] = str(release_file)
            environment["PROTOS_TEST_MANIFEST"] = str(manifest_file)
            environment["PROTOS_TEST_GLIBC"] = glibc
            environment["PROTOS_TEST_ARCH"] = arch

            return i131_subprocess.run(
                ["sh", str(self.INSTALLER), *arguments],
                cwd=root,
                env=environment,
                text=True,
                capture_output=True,
                check=False,
                timeout=15,
            )

    def test_newest_published_version_is_selected_numerically(self):
        result = self._run_inspection()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("RELEASE_TAG=v0.3.398", result.stdout)
        self.assertIn("NATIVE_ARCHIVE=protos-0.3.398-native-linux-x86_64.zip",
                      result.stdout)
        self.assertIn("MIN_GLIBC=2.39", result.stdout)
        self.assertIn("HOST_GLIBC=2.40", result.stdout)
        self.assertIn("INSPECTION=PASS", result.stdout)

    def test_explicit_published_tag_is_supported(self):
        result = self._run_inspection(
            manifest=self._manifest("v0.3.397"),
            arguments=["--inspect", "--tag", "v0.3.397"],
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("RELEASE_TAG=v0.3.397", result.stdout)

    def test_unpublished_tag_fails_closed(self):
        result = self._run_inspection(
            arguments=["--inspect", "--tag", "v0.3.999"],
        )
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", result.stdout)

    def test_insufficient_glibc_fails_closed(self):
        result = self._run_inspection(glibc="glibc 2.38")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", result.stdout)
        self.assertIn("glibc", result.stderr.lower())

    def test_different_architecture_fails_closed(self):
        result = self._run_inspection(arch="aarch64")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", result.stdout)

    def test_missing_checksum_identity_fails_closed(self):
        manifest = self._manifest().replace(
            "asset.1.checksum_sha256=" + "b" * 64 + "\n",
            "",
        )
        result = self._run_inspection(manifest=manifest)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", result.stdout)

    def test_wrong_native_runtime_policy_fails_closed(self):
        manifest = self._manifest().replace(
            "asset.1.cpu_isa_assumption=compatibility",
            "asset.1.cpu_isa_assumption=x86-64-v4",
        )
        result = self._run_inspection(manifest=manifest)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", result.stdout)

    def test_duplicate_manifest_key_fails_closed(self):
        manifest = self._manifest() + "release_tag=v0.3.398\n"
        result = self._run_inspection(manifest=manifest)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", result.stdout)


import hashlib as i131_hashlib
import stat as i131_stat
import zipfile as i131_zipfile
import shutil as i131_shutil


class InstallNativeDownloadVerificationTest(i131_unittest.TestCase):
    INSTALLER = I131Path(__file__).resolve().parents[2] / "install.sh"
    TAG = "v0.3.398"
    VERSION = "0.3.398"
    ROOT = "protos-0.3.398-native-linux-x86_64"
    ARCHIVE = ROOT + ".zip"

    def _files(self):
        root = self.ROOT
        return {
            f"{root}/bin/protos": b"#!/bin/sh\nprintf '0.3.398\\n'\n",
            f"{root}/libexec/protos-native": b"synthetic-native-image",
            f"{root}/LICENSE.TXT": b"synthetic license\n",
            f"{root}/README.md": b"synthetic readme\n",
            f"{root}/DEPENDENCIES.txt": b"synthetic dependencies\n",
            f"{root}/SHA256SUMS": b"synthetic internal checksums\n",
            f"{root}/SOURCE.txt": (
                "artifact_kind=public-prerelease\n"
                "public_release=true\n"
                "source_dirty=false\n"
                "source_repository=https://github.com/guillermomolina/protos\n"
                "source_revision=" + "1" * 40 + "\n"
                "release_version=0.3.398\n"
                "release_tag=v0.3.398\n"
            ).encode("utf-8"),
            f"{root}/RUNTIME.txt": (
                "distribution_format=protos-native-image-posix-v1\n"
                "native_runtime_kind=graalvm-native-image-truffle\n"
                "external_java_required=false\n"
                "target_os=linux\n"
                "target_arch=x86_64\n"
                "libc_family=glibc\n"
                "libc_abi_min=2.39\n"
                "linkage=dynamic\n"
                "cpu_isa_assumption=compatibility\n"
            ).encode("utf-8"),
            f"{root}/protos/lib/example.pr": b"lib\n",
            f"{root}/protos/tools/example.pr": b"tool\n",
            f"{root}/protos/tests/example.pr": b"test\n",
        }

    def _run_download(
        self,
        *,
        mutation=None,
        corrupt_zip=False,
        corrupt_checksum=False,
    ):
        with i131_tempfile.TemporaryDirectory() as workspace:
            root = I131Path(workspace)
            commands = root / "commands"
            commands.mkdir()

            zip_path = root / self.ARCHIVE
            files = self._files()

            if mutation is not None:
                mutation(files)

            with i131_zipfile.ZipFile(
                zip_path,
                "w",
                compression=i131_zipfile.ZIP_DEFLATED,
            ) as archive:
                for name, content in files.items():
                    info = i131_zipfile.ZipInfo(name)
                    info.create_system = 3
                    permissions = 0o755 if name.endswith(
                        ("/bin/protos", "/libexec/protos-native")
                    ) else 0o644
                    info.external_attr = (
                        (i131_stat.S_IFREG | permissions) << 16
                    )
                    info.compress_type = i131_zipfile.ZIP_DEFLATED
                    archive.writestr(info, content)

            archive_digest = i131_hashlib.sha256(
                zip_path.read_bytes()
            ).hexdigest()

            checksum_path = root / (self.ARCHIVE + ".sha256")
            checksum_bytes = (
                f"{archive_digest}  {self.ARCHIVE}\n"
            ).encode("utf-8")

            if corrupt_checksum:
                checksum_bytes += b"unexpected extra checksum line\n"

            checksum_path.write_bytes(checksum_bytes)

            checksum_digest = i131_hashlib.sha256(
                checksum_bytes
            ).hexdigest()

            if corrupt_zip:
                zip_path.write_bytes(zip_path.read_bytes() + b"tampering")

            manifest = (
                InstallNativeManifestInspectionTest()
                ._manifest()
                .replace(
                    "asset.1.archive_sha256=" + "a" * 64,
                    "asset.1.archive_sha256=" + archive_digest,
                )
                .replace(
                    "asset.1.checksum_sha256=" + "b" * 64,
                    "asset.1.checksum_sha256=" + checksum_digest,
                )
            )

            manifest_path = root / "manifest.txt"
            manifest_path.write_text(manifest, encoding="utf-8")

            releases_path = root / "releases.json"
            releases_path.write_text(
                i131_json.dumps([
                    {"tag_name": self.TAG, "draft": False},
                ]),
                encoding="utf-8",
            )

            fake_curl = commands / "curl"
            fake_curl.write_text(
                """#!/bin/sh
destination=
request_url=

while [ "$#" -gt 0 ]; do
    if [ "$1" = "-o" ]; then
        shift
        destination=$1
    else
        case "$1" in
            https://*) request_url=$1 ;;
        esac
    fi
    shift
done

case "$request_url" in
    *'/releases?per_page=100')
        cp "$PROTOS_TEST_RELEASES" "$destination"
        ;;
    *'/RELEASE_MANIFEST.txt')
        cp "$PROTOS_TEST_MANIFEST" "$destination"
        ;;
    *'.zip.sha256')
        cp "$PROTOS_TEST_CHECKSUM" "$destination"
        ;;
    *'.zip')
        cp "$PROTOS_TEST_ZIP" "$destination"
        ;;
    *)
        printf '%s\\n' 'unexpected fake curl URL' >&2
        false
        ;;
esac
""",
                encoding="utf-8",
            )
            fake_curl.chmod(0o755)

            fake_getconf = commands / "getconf"
            fake_getconf.write_text(
                "#!/bin/sh\nprintf 'glibc 2.40\\n'\n",
                encoding="utf-8",
            )
            fake_getconf.chmod(0o755)

            fake_uname = commands / "uname"
            fake_uname.write_text(
                "#!/bin/sh\n"
                "if [ \"$1\" = \"-s\" ]; then\n"
                "    printf 'Linux\\n'\n"
                "else\n"
                "    printf 'x86_64\\n'\n"
                "fi\n",
                encoding="utf-8",
            )
            fake_uname.chmod(0o755)

            environment = i131_os.environ.copy()
            environment["PATH"] = (
                str(commands) + i131_os.pathsep +
                environment.get("PATH", "")
            )
            environment["PROTOS_TEST_RELEASES"] = str(releases_path)
            environment["PROTOS_TEST_MANIFEST"] = str(manifest_path)
            environment["PROTOS_TEST_CHECKSUM"] = str(checksum_path)
            environment["PROTOS_TEST_ZIP"] = str(zip_path)

            return i131_subprocess.run(
                ["sh", str(self.INSTALLER), "--verify-download"],
                cwd=root,
                env=environment,
                text=True,
                capture_output=True,
                check=False,
                timeout=20,
            )

    def test_verified_native_download_succeeds(self):
        result = self._run_download()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("NATIVE_CHECKSUM_VERIFIED=PASS", result.stdout)
        self.assertIn("NATIVE_ARCHIVE_VERIFIED=PASS", result.stdout)
        self.assertIn("NATIVE_LAYOUT_VERIFIED=PASS", result.stdout)

    def test_tampered_archive_is_rejected(self):
        result = self._run_download(corrupt_zip=True)
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("NATIVE_ARCHIVE_VERIFIED=PASS", result.stdout)

    def test_tampered_checksum_is_rejected(self):
        result = self._run_download(corrupt_checksum=True)
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("NATIVE_ARCHIVE_VERIFIED=PASS", result.stdout)

    def test_archive_traversal_is_rejected_even_with_valid_hash(self):
        def mutation(files):
            files[self.ROOT + "/../escape.txt"] = b"unexpected\n"

        result = self._run_download(mutation=mutation)
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("NATIVE_LAYOUT_VERIFIED=PASS", result.stdout)

    def test_missing_native_executable_is_rejected(self):
        def mutation(files):
            files.pop(self.ROOT + "/libexec/protos-native")

        result = self._run_download(mutation=mutation)
        self.assertNotEqual(0, result.returncode)

    def test_archive_with_jar_is_rejected(self):
        def mutation(files):
            files[self.ROOT + "/lib/unexpected.jar"] = b"not allowed\n"

        result = self._run_download(mutation=mutation)
        self.assertNotEqual(0, result.returncode)

    def test_wrong_source_revision_is_rejected(self):
        def mutation(files):
            key = self.ROOT + "/SOURCE.txt"
            files[key] = files[key].replace(b"1" * 40, b"2" * 40)

        result = self._run_download(mutation=mutation)
        self.assertNotEqual(0, result.returncode)

    def test_wrong_runtime_identity_is_rejected(self):
        def mutation(files):
            key = self.ROOT + "/RUNTIME.txt"
            files[key] = files[key].replace(
                b"external_java_required=false",
                b"external_java_required=true",
            )

        result = self._run_download(mutation=mutation)
        self.assertNotEqual(0, result.returncode)


class InstallNativeInstallationTest(i131_unittest.TestCase):
    INSTALLER = I131Path(__file__).resolve().parents[2] / "install.sh"
    RELEASE = InstallNativeDownloadVerificationTest

    def _run_case(
        self,
        *,
        existing_wrapper=None,
        failing_smoke=False,
        repeat=False,
        post_install=None,
    ):
        fixture = self.RELEASE()

        with i131_tempfile.TemporaryDirectory() as workspace:
            root = I131Path(workspace)
            home = root / "isolated user home"
            home.mkdir()

            unrelated_cwd = root / "unrelated project"
            unrelated_cwd.mkdir()
            (unrelated_cwd / "relative.protos").write_text(
                'print("relative file")\n',
                encoding="utf-8",
            )

            commands = root / "commands"
            commands.mkdir()

            files = fixture._files()

            launcher = r'''#!/bin/sh
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)

if [ ! -f "$root/protos/lib/example.pr" ]; then
    printf 'missing distribution resource tree\n' >&2
    false
elif [ "$1" = "--version" ]; then
    printf 'Protos 0.3.398\n'
elif [ "$1" = "-e" ]; then
    SMOKE_BODY
elif [ "$1" = "relative.protos" ]; then
    if [ -f relative.protos ]; then
        printf 'RELATIVE_FILE=PASS\n'
    else
        printf 'relative file was not resolved from caller CWD\n' >&2
        false
    fi
else
    printf 'unexpected test argument\n' >&2
    false
fi
'''

            smoke_body = (
                "printf 'synthetic smoke failure\\n' >&2\n"
                "    false"
                if failing_smoke
                else "printf 'SMOKE=PASS\\n'"
            )

            launcher = launcher.replace("SMOKE_BODY", smoke_body)

            files[
                fixture.ROOT + "/bin/protos"
            ] = launcher.encode("utf-8")

            archive_path = root / fixture.ARCHIVE

            with i131_zipfile.ZipFile(
                archive_path,
                "w",
                compression=i131_zipfile.ZIP_DEFLATED,
            ) as archive:
                for name, content in files.items():
                    info = i131_zipfile.ZipInfo(name)
                    info.create_system = 3
                    permissions = (
                        0o755
                        if name.endswith((
                            "/bin/protos",
                            "/libexec/protos-native",
                        ))
                        else 0o644
                    )
                    info.external_attr = (
                        (i131_stat.S_IFREG | permissions) << 16
                    )
                    info.compress_type = i131_zipfile.ZIP_DEFLATED
                    archive.writestr(info, content)

            archive_digest = i131_hashlib.sha256(
                archive_path.read_bytes()
            ).hexdigest()

            checksum_file = root / (fixture.ARCHIVE + ".sha256")
            checksum_bytes = (
                f"{archive_digest}  {fixture.ARCHIVE}\n"
            ).encode("utf-8")
            checksum_file.write_bytes(checksum_bytes)

            checksum_digest = i131_hashlib.sha256(
                checksum_bytes
            ).hexdigest()

            manifest = (
                InstallNativeManifestInspectionTest()
                ._manifest()
                .replace(
                    "asset.1.archive_sha256=" + "a" * 64,
                    "asset.1.archive_sha256=" + archive_digest,
                )
                .replace(
                    "asset.1.checksum_sha256=" + "b" * 64,
                    "asset.1.checksum_sha256=" + checksum_digest,
                )
            )

            manifest_file = root / "manifest.txt"
            manifest_file.write_text(manifest, encoding="utf-8")

            releases_file = root / "releases.json"
            releases_file.write_text(
                i131_json.dumps([
                    {"tag_name": fixture.TAG, "draft": False},
                ]),
                encoding="utf-8",
            )

            fake_curl = commands / "curl"
            fake_curl.write_text(
                """#!/bin/sh
destination=
request_url=

while [ "$#" -gt 0 ]; do
    if [ "$1" = "-o" ]; then
        shift
        destination=$1
    else
        case "$1" in
            https://*) request_url=$1 ;;
        esac
    fi
    shift
done

case "$request_url" in
    *'/releases?per_page=100')
        cp "$PROTOS_TEST_RELEASES" "$destination"
        ;;
    *'/RELEASE_MANIFEST.txt')
        cp "$PROTOS_TEST_MANIFEST" "$destination"
        ;;
    *'.zip.sha256')
        cp "$PROTOS_TEST_CHECKSUM" "$destination"
        ;;
    *'.zip')
        cp "$PROTOS_TEST_ZIP" "$destination"
        ;;
    *)
        printf '%s\\n' 'unexpected fake curl URL' >&2
        false
        ;;
esac
""",
                encoding="utf-8",
            )
            fake_curl.chmod(0o755)

            fake_getconf = commands / "getconf"
            fake_getconf.write_text(
                "#!/bin/sh\nprintf 'glibc 2.40\\n'\n",
                encoding="utf-8",
            )
            fake_getconf.chmod(0o755)

            fake_uname = commands / "uname"
            fake_uname.write_text(
                "#!/bin/sh\n"
                "if [ \"$1\" = \"-s\" ]; then\n"
                "    printf 'Linux\\n'\n"
                "else\n"
                "    printf 'x86_64\\n'\n"
                "fi\n",
                encoding="utf-8",
            )
            fake_uname.chmod(0o755)

            bin_dir = home / ".local" / "bin"
            wrapper = bin_dir / "protos"

            if existing_wrapper is not None:
                bin_dir.mkdir(parents=True)
                wrapper.write_text(
                    existing_wrapper,
                    encoding="utf-8",
                )
                wrapper.chmod(0o755)

            environment = i131_os.environ.copy()
            environment["HOME"] = str(home)
            environment["PATH"] = (
                str(commands) + i131_os.pathsep +
                environment.get("PATH", "")
            )
            environment["PROTOS_TEST_RELEASES"] = str(releases_file)
            environment["PROTOS_TEST_MANIFEST"] = str(manifest_file)
            environment["PROTOS_TEST_CHECKSUM"] = str(checksum_file)
            environment["PROTOS_TEST_ZIP"] = str(archive_path)

            def invoke(*args):
                return i131_subprocess.run(
                    ["sh", str(self.INSTALLER), *args],
                    cwd=unrelated_cwd,
                    env=environment,
                    capture_output=True,
                    text=True,
                    check=False,
                    timeout=20,
                )

            first = invoke()
            second = invoke() if repeat and first.returncode == 0 else None

            wrapper_text = (
                wrapper.read_text(encoding="utf-8")
                if wrapper.is_file()
                else None
            )

            version_directory = (
                home / ".local" / "share" / "protos" /
                "versions" / fixture.TAG
            )

            launched_version = None
            launched_relative = None

            if first.returncode == 0 and wrapper.is_file():
                launched_version = i131_subprocess.run(
                    [str(wrapper), "--version"],
                    cwd=unrelated_cwd,
                    env=environment,
                    text=True,
                    capture_output=True,
                    check=False,
                    timeout=10,
                )
                launched_relative = i131_subprocess.run(
                    [str(wrapper), "relative.protos"],
                    cwd=unrelated_cwd,
                    env=environment,
                    text=True,
                    capture_output=True,
                    check=False,
                    timeout=10,
                )

            lifecycle = None

            if post_install is not None and first.returncode == 0:
                lifecycle = post_install(
                    wrapper=wrapper,
                    version_directory=version_directory,
                    commands=commands,
                    environment=environment,
                    invoke=invoke,
                    unrelated_cwd=unrelated_cwd,
                )

            return {
                "lifecycle": lifecycle,
                "first": first,
                "second": second,
                "wrapper": wrapper_text,
                "version_dir": version_directory.is_dir(),
                "launcher": (
                    version_directory / "bin" / "protos"
                ).is_file(),
                "version": launched_version,
                "relative": launched_relative,
            }

    @staticmethod
    def _deny_network(commands):
        fake_curl = commands / "curl"
        fake_curl.write_text(
            "#!/bin/sh\n"
            "printf 'UNEXPECTED_NETWORK_REQUEST\n' >&2\n"
            "false\n",
            encoding="utf-8",
        )
        fake_curl.chmod(0o755)

    @staticmethod
    def _create_older_installed_version(version_directory):
        older = version_directory.parent / "v0.3.397"
        i131_shutil.copytree(version_directory, older)

        source = older / "SOURCE.txt"
        source_text = source.read_text(encoding="utf-8")

        if source_text.count("release_tag=v0.3.398\n") != 1:
            raise AssertionError("Unexpected SOURCE.txt release tag")

        if source_text.count("release_version=0.3.398\n") != 1:
            raise AssertionError("Unexpected SOURCE.txt release version")

        source_text = source_text.replace(
            "release_tag=v0.3.398\n",
            "release_tag=v0.3.397\n",
        )
        source_text = source_text.replace(
            "release_version=0.3.398\n",
            "release_version=0.3.397\n",
        )

        source.write_text(source_text, encoding="utf-8")

        launcher = older / "bin" / "protos"
        launcher_text = launcher.read_text(encoding="utf-8")

        if launcher_text.count("Protos 0.3.398") != 1:
            raise AssertionError("Unexpected synthetic Native launcher")

        launcher.write_text(
            launcher_text.replace(
                "Protos 0.3.398",
                "Protos 0.3.397",
            ),
            encoding="utf-8",
        )

        launcher.chmod(0o755)
        return older

    @staticmethod
    def _invoke_managed(wrapper, environment, unrelated_cwd):
        return i131_subprocess.run(
            [str(wrapper), "--version"],
            cwd=unrelated_cwd,
            env=environment,
            text=True,
            capture_output=True,
            check=False,
            timeout=10,
        )

    def test_offline_missing_rollback_preserves_active(self):
        def operation(*, wrapper, commands, invoke, **_):
            self._deny_network(commands)

            before = wrapper.read_bytes()
            rejected = invoke("--rollback", "--tag", "v0.3.397")

            return {
                "rejected": rejected,
                "wrapper_unchanged": wrapper.read_bytes() == before,
            }

        result = self._run_case(post_install=operation)

        self.assertEqual(0, result["first"].returncode,
                         result["first"].stderr)

        lifecycle = result["lifecycle"]
        rejected = lifecycle["rejected"]

        self.assertNotEqual(0, rejected.returncode)
        self.assertTrue(lifecycle["wrapper_unchanged"])
        self.assertIn("not installed", rejected.stderr.lower())
        self.assertNotIn("UNEXPECTED_NETWORK_REQUEST", rejected.stderr)

    def test_offline_uninstall_is_idempotent_and_retains_versions(self):
        def operation(
            *, wrapper, version_directory, commands, invoke, **_
        ):
            self._deny_network(commands)

            first = invoke("--uninstall")
            wrapper_removed = (
                not wrapper.exists() and not wrapper.is_symlink()
            )
            retained = version_directory.is_dir()
            second = invoke("--uninstall")

            return {
                "first": first,
                "second": second,
                "wrapper_removed": wrapper_removed,
                "retained": retained,
            }

        result = self._run_case(post_install=operation)

        self.assertEqual(0, result["first"].returncode,
                         result["first"].stderr)

        lifecycle = result["lifecycle"]
        self.assertEqual(0, lifecycle["first"].returncode,
                         lifecycle["first"].stderr)
        self.assertEqual(0, lifecycle["second"].returncode,
                         lifecycle["second"].stderr)
        self.assertTrue(lifecycle["wrapper_removed"])
        self.assertTrue(lifecycle["retained"])
        self.assertIn(
            "UNINSTALL_STATUS=DEACTIVATED",
            lifecycle["first"].stdout,
        )
        self.assertNotIn(
            "UNEXPECTED_NETWORK_REQUEST",
            lifecycle["first"].stderr + lifecycle["second"].stderr,
        )

    def test_offline_rollback_recovers_after_uninstall(self):
        def operation(
            *,
            wrapper,
            version_directory,
            commands,
            environment,
            invoke,
            unrelated_cwd,
        ):
            self._deny_network(commands)

            removed = invoke("--uninstall")
            absent = (
                not wrapper.exists() and not wrapper.is_symlink()
            )
            retained = version_directory.is_dir()

            restored = invoke(
                "--rollback",
                "--tag",
                "v0.3.398",
            )

            launched = self._invoke_managed(
                wrapper,
                environment,
                unrelated_cwd,
            )

            return {
                "removed": removed,
                "absent": absent,
                "retained": retained,
                "restored": restored,
                "launched": launched,
            }

        result = self._run_case(post_install=operation)

        self.assertEqual(0, result["first"].returncode,
                         result["first"].stderr)

        lifecycle = result["lifecycle"]

        self.assertEqual(0, lifecycle["removed"].returncode,
                         lifecycle["removed"].stderr)
        self.assertTrue(lifecycle["absent"])
        self.assertTrue(lifecycle["retained"])

        self.assertEqual(0, lifecycle["restored"].returncode,
                         lifecycle["restored"].stderr)
        self.assertIn(
            "ROLLBACK_STATUS=ACTIVATED",
            lifecycle["restored"].stdout,
        )

        self.assertEqual(0, lifecycle["launched"].returncode,
                         lifecycle["launched"].stderr)
        self.assertIn(
            "Protos 0.3.398",
            lifecycle["launched"].stdout,
        )

        self.assertNotIn(
            "UNEXPECTED_NETWORK_REQUEST",
            lifecycle["restored"].stderr,
        )

    def test_offline_rollback_switches_between_retained_versions(self):
        def operation(
            *,
            wrapper,
            version_directory,
            commands,
            environment,
            invoke,
            unrelated_cwd,
        ):
            self._create_older_installed_version(version_directory)
            self._deny_network(commands)

            older = invoke(
                "--rollback",
                "--tag",
                "v0.3.397",
            )

            older_version = self._invoke_managed(
                wrapper,
                environment,
                unrelated_cwd,
            )

            restored = invoke(
                "--rollback",
                "--tag",
                "v0.3.398",
            )

            restored_version = self._invoke_managed(
                wrapper,
                environment,
                unrelated_cwd,
            )

            return {
                "older": older,
                "older_version": older_version,
                "restored": restored,
                "restored_version": restored_version,
            }

        result = self._run_case(post_install=operation)

        self.assertEqual(0, result["first"].returncode,
                         result["first"].stderr)

        lifecycle = result["lifecycle"]

        self.assertEqual(0, lifecycle["older"].returncode,
                         lifecycle["older"].stderr)
        self.assertEqual(0, lifecycle["older_version"].returncode,
                         lifecycle["older_version"].stderr)
        self.assertIn(
            "Protos 0.3.397",
            lifecycle["older_version"].stdout,
        )

        self.assertEqual(0, lifecycle["restored"].returncode,
                         lifecycle["restored"].stderr)
        self.assertEqual(0, lifecycle["restored_version"].returncode,
                         lifecycle["restored_version"].stderr)
        self.assertIn(
            "Protos 0.3.398",
            lifecycle["restored_version"].stdout,
        )

        self.assertNotIn(
            "UNEXPECTED_NETWORK_REQUEST",
            lifecycle["older"].stderr +
            lifecycle["restored"].stderr,
        )

    def test_offline_operations_preserve_foreign_command(self):
        foreign = "#!/bin/sh\nprintf 'FOREIGN_COMMAND\n'\n"

        def operation(*, wrapper, commands, invoke, **_):
            wrapper.write_text(foreign, encoding="utf-8")
            wrapper.chmod(0o755)

            self._deny_network(commands)

            uninstall = invoke("--uninstall")
            rollback = invoke(
                "--rollback",
                "--tag",
                "v0.3.398",
            )

            return {
                "uninstall": uninstall,
                "rollback": rollback,
                "unchanged": wrapper.read_text(
                    encoding="utf-8"
                ) == foreign,
            }

        result = self._run_case(post_install=operation)

        self.assertEqual(0, result["first"].returncode,
                         result["first"].stderr)

        lifecycle = result["lifecycle"]

        self.assertNotEqual(
            0,
            lifecycle["uninstall"].returncode,
        )
        self.assertNotEqual(
            0,
            lifecycle["rollback"].returncode,
        )
        self.assertTrue(lifecycle["unchanged"])

        self.assertNotIn(
            "UNEXPECTED_NETWORK_REQUEST",
            lifecycle["uninstall"].stderr +
            lifecycle["rollback"].stderr,
        )

    def test_failed_offline_rollback_keeps_active_version(self):
        def operation(
            *,
            wrapper,
            version_directory,
            commands,
            invoke,
            **_,
        ):
            older = self._create_older_installed_version(
                version_directory
            )

            broken_launcher = older / "bin" / "protos"
            broken_launcher.write_text(
                "#!/bin/sh\n"
                "printf 'synthetic broken launcher\n' >&2\n"
                "false\n",
                encoding="utf-8",
            )
            broken_launcher.chmod(0o755)

            self._deny_network(commands)

            before = wrapper.read_bytes()
            rejected = invoke(
                "--rollback",
                "--tag",
                "v0.3.397",
            )

            return {
                "rejected": rejected,
                "unchanged": wrapper.read_bytes() == before,
            }

        result = self._run_case(post_install=operation)

        self.assertEqual(0, result["first"].returncode,
                         result["first"].stderr)

        lifecycle = result["lifecycle"]

        self.assertNotEqual(
            0,
            lifecycle["rejected"].returncode,
        )
        self.assertTrue(lifecycle["unchanged"])
        self.assertNotIn(
            "UNEXPECTED_NETWORK_REQUEST",
            lifecycle["rejected"].stderr,
        )

    def test_upgrade_from_retained_previous_version(self):
        def operation(
            *,
            wrapper,
            version_directory,
            environment,
            invoke,
            unrelated_cwd,
            **_,
        ):
            self._create_older_installed_version(
                version_directory
            )

            previous = invoke(
                "--rollback",
                "--tag",
                "v0.3.397",
            )

            before = self._invoke_managed(
                wrapper,
                environment,
                unrelated_cwd,
            )

            upgraded = invoke()

            after = self._invoke_managed(
                wrapper,
                environment,
                unrelated_cwd,
            )

            return {
                "previous": previous,
                "before": before,
                "upgraded": upgraded,
                "after": after,
            }

        result = self._run_case(post_install=operation)

        self.assertEqual(
            0,
            result["first"].returncode,
            result["first"].stderr,
        )

        lifecycle = result["lifecycle"]

        self.assertEqual(
            0,
            lifecycle["previous"].returncode,
            lifecycle["previous"].stderr,
        )
        self.assertIn(
            "Protos 0.3.397",
            lifecycle["before"].stdout,
        )

        self.assertEqual(
            0,
            lifecycle["upgraded"].returncode,
            lifecycle["upgraded"].stderr,
        )
        self.assertIn(
            "Protos 0.3.398",
            lifecycle["after"].stdout,
        )

    def test_failed_update_preserves_previous_version(self):
        def operation(
            *,
            wrapper,
            version_directory,
            environment,
            invoke,
            unrelated_cwd,
            **_,
        ):
            self._create_older_installed_version(
                version_directory
            )

            previous = invoke(
                "--rollback",
                "--tag",
                "v0.3.397",
            )

            if previous.returncode != 0:
                raise AssertionError(previous.stderr)

            before = wrapper.read_bytes()

            # Remove only the synthetic candidate installed in this
            # temporary test HOME, forcing a fresh candidate download.
            i131_shutil.rmtree(version_directory)

            archive = I131Path(
                environment["PROTOS_TEST_ZIP"]
            )
            archive.write_bytes(
                archive.read_bytes() +
                b"\nintentionally corrupted test archive\n"
            )

            rejected = invoke()

            after = self._invoke_managed(
                wrapper,
                environment,
                unrelated_cwd,
            )

            return {
                "rejected": rejected,
                "wrapper_unchanged": wrapper.read_bytes() == before,
                "after": after,
            }

        result = self._run_case(post_install=operation)

        self.assertEqual(
            0,
            result["first"].returncode,
            result["first"].stderr,
        )

        lifecycle = result["lifecycle"]

        self.assertNotEqual(
            0,
            lifecycle["rejected"].returncode,
        )
        self.assertTrue(
            lifecycle["wrapper_unchanged"]
        )
        self.assertEqual(
            0,
            lifecycle["after"].returncode,
            lifecycle["after"].stderr,
        )
        self.assertIn(
            "Protos 0.3.397",
            lifecycle["after"].stdout,
        )

    def test_foreign_symlink_wrapper_is_preserved(self):
        def operation(
            *,
            wrapper,
            commands,
            invoke,
            **_,
        ):
            foreign = commands.parent / "foreign-protos-command"

            foreign.write_text(
                "#!/bin/sh\n"
                "printf 'FOREIGN_COMMAND\n'\n",
                encoding="utf-8",
            )
            foreign.chmod(0o755)

            original_foreign = foreign.read_bytes()

            # This is the isolated test HOME, not the user's HOME.
            wrapper.unlink()
            wrapper.symlink_to(foreign)

            self._deny_network(commands)

            uninstall = invoke("--uninstall")
            rollback = invoke(
                "--rollback",
                "--tag",
                "v0.3.398",
            )

            return {
                "uninstall": uninstall,
                "rollback": rollback,
                "symlink_preserved": wrapper.is_symlink(),
                "target_preserved": (
                    foreign.read_bytes() == original_foreign
                ),
            }

        result = self._run_case(post_install=operation)

        self.assertEqual(
            0,
            result["first"].returncode,
            result["first"].stderr,
        )

        lifecycle = result["lifecycle"]

        self.assertNotEqual(
            0,
            lifecycle["uninstall"].returncode,
        )
        self.assertNotEqual(
            0,
            lifecycle["rollback"].returncode,
        )
        self.assertTrue(
            lifecycle["symlink_preserved"]
        )
        self.assertTrue(
            lifecycle["target_preserved"]
        )

    def test_symlinked_installer_lock_is_rejected(self):
        def operation(
            *,
            wrapper,
            version_directory,
            commands,
            invoke,
            **_,
        ):
            install_root = version_directory.parent.parent

            locks = list(
                install_root.rglob(".installer.lock")
            )

            if len(locks) != 1:
                raise AssertionError(
                    f"Expected one installer lock, found {len(locks)}"
                )

            lock = locks[0]

            sentinel = commands.parent / "foreign-lock-target"
            sentinel.write_text(
                "FOREIGN_FILE_MUST_NOT_CHANGE\n",
                encoding="utf-8",
            )

            original_sentinel = sentinel.read_bytes()
            original_wrapper = wrapper.read_bytes()

            # Replace only the lock in the isolated test HOME.
            lock.unlink()
            lock.symlink_to(sentinel)

            self._deny_network(commands)

            rejected_uninstall = invoke("--uninstall")

            rejected_rollback = invoke(
                "--rollback",
                "--tag",
                "v0.3.398",
            )

            return {
                "uninstall": rejected_uninstall,
                "rollback": rejected_rollback,
                "lock_still_symlink": lock.is_symlink(),
                "sentinel_unchanged": (
                    sentinel.read_bytes() == original_sentinel
                ),
                "wrapper_unchanged": (
                    wrapper.read_bytes() == original_wrapper
                ),
            }

        result = self._run_case(post_install=operation)

        self.assertEqual(
            0,
            result["first"].returncode,
            result["first"].stderr,
        )

        lifecycle = result["lifecycle"]

        self.assertNotEqual(
            0,
            lifecycle["uninstall"].returncode,
        )
        self.assertNotEqual(
            0,
            lifecycle["rollback"].returncode,
        )
        self.assertTrue(
            lifecycle["lock_still_symlink"]
        )
        self.assertTrue(
            lifecycle["sentinel_unchanged"]
        )
        self.assertTrue(
            lifecycle["wrapper_unchanged"]
        )

    def test_native_install_and_unrelated_cwd(self):
        result = self._run_case()
        self.assertEqual(
            0,
            result["first"].returncode,
            result["first"].stderr,
        )
        self.assertTrue(result["version_dir"])
        self.assertTrue(result["launcher"])
        self.assertIn(
            "INSTALL_STATUS=INSTALLED",
            result["first"].stdout,
        )
        self.assertIn(
            "ACTIVE_VERSION=v0.3.398",
            result["first"].stdout,
        )
        self.assertIn(
            "# PROTOS_NATIVE_INSTALLER_MANAGED_V1",
            result["wrapper"],
        )
        self.assertEqual(
            0,
            result["version"].returncode,
            result["version"].stderr,
        )
        self.assertIn("0.3.398", result["version"].stdout)
        self.assertEqual(
            0,
            result["relative"].returncode,
            result["relative"].stderr,
        )
        self.assertIn(
            "RELATIVE_FILE=PASS",
            result["relative"].stdout,
        )

    def test_reinstall_of_active_release_is_idempotent(self):
        result = self._run_case(repeat=True)
        self.assertEqual(
            0,
            result["first"].returncode,
            result["first"].stderr,
        )
        self.assertIsNotNone(result["second"])
        self.assertEqual(
            0,
            result["second"].returncode,
            result["second"].stderr,
        )
        self.assertIn(
            "INSTALL_STATUS=ALREADY_ACTIVE",
            result["second"].stdout,
        )

    def test_foreign_protos_command_is_preserved(self):
        foreign = "#!/bin/sh\nprintf 'foreign command\\n'\n"
        result = self._run_case(existing_wrapper=foreign)
        self.assertNotEqual(0, result["first"].returncode)
        self.assertEqual(foreign, result["wrapper"])
        self.assertFalse(result["version_dir"])

    def test_implicit_downgrade_is_rejected(self):
        existing = (
            "#!/bin/sh\n"
            "# PROTOS_NATIVE_INSTALLER_MANAGED_V1\n"
            "# PROTOS_VERSION=v0.3.399\n"
        )
        result = self._run_case(existing_wrapper=existing)
        self.assertNotEqual(0, result["first"].returncode)
        self.assertEqual(existing, result["wrapper"])
        self.assertIn(
            "downgrade",
            result["first"].stderr.lower(),
        )

    def test_failed_smoke_preserves_existing_wrapper(self):
        existing = (
            "#!/bin/sh\n"
            "# PROTOS_NATIVE_INSTALLER_MANAGED_V1\n"
            "# PROTOS_VERSION=v0.3.397\n"
        )
        result = self._run_case(
            existing_wrapper=existing,
            failing_smoke=True,
        )
        self.assertNotEqual(0, result["first"].returncode)
        self.assertEqual(existing, result["wrapper"])
        self.assertFalse(result["version_dir"])
        self.assertIn(
            "smoke",
            result["first"].stderr.lower(),
        )
