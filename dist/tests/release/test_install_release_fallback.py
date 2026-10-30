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

"""I131: latest eligible published Native release selection, without network."""

import json
import os
import shutil
from pathlib import Path
import subprocess
import tempfile
import unittest

import dist.tests.release.test_install_release_selection as i131_manifest_fixture


class InstallNativeReleaseFallbackTest(unittest.TestCase):
    INSTALLER = Path(__file__).resolve().parents[2] / "install.sh"

    @staticmethod
    def _manifest(tag):
        return i131_manifest_fixture.InstallNativeManifestInspectionTest()._manifest(tag)

    @classmethod
    def _requires_newer_glibc(cls, tag):
        manifest = cls._manifest(tag)

        changes = (
            (
                "asset.1.libc_abi_min=2.39\n",
                "asset.1.libc_abi_min=2.41\n",
            ),
            (
                "asset.1.platform_identity="
                "linux/x86_64/glibc/2.39/dynamic/compatibility\n",
                "asset.1.platform_identity="
                "linux/x86_64/glibc/2.41/dynamic/compatibility\n",
            ),
        )

        for old, new in changes:
            if manifest.count(old) != 1:
                raise AssertionError(
                    f"Unexpected manifest fixture field: {old!r}"
                )
            manifest = manifest.replace(old, new, 1)

        return manifest

    @classmethod
    def _different_architecture(cls, tag):
        manifest = cls._manifest(tag)

        old = "asset.1.target_arch=x86_64\n"
        new = "asset.1.target_arch=aarch64\n"

        if manifest.count(old) != 1:
            raise AssertionError("Unexpected Native architecture field")

        return manifest.replace(old, new, 1)

    def _run_case(
        self,
        *,
        newest_manifest,
        older_manifest=None,
        pinned=False,
        fail_newest_download=False,
    ):
        if older_manifest is None:
            older_manifest = self._manifest("v0.3.398")

        with tempfile.TemporaryDirectory() as workspace:
            root = Path(workspace)

            commands = root / "commands"
            commands.mkdir()

            releases = root / "releases.json"
            releases.write_text(
                json.dumps([
                    {"tag_name": "v0.3.398", "draft": False},
                    {"tag_name": "v0.3.400", "draft": False},
                    {
                        "tag_name": "v0.3.500",
                        "draft": True,
                    },
                    {
                        "tag_name": "v0.3.399-SNAPSHOT",
                        "draft": False,
                    },
                ]),
                encoding="utf-8",
            )

            newest = root / "newest-manifest.txt"
            newest.write_text(
                newest_manifest,
                encoding="utf-8",
            )

            older = root / "older-manifest.txt"
            older.write_text(
                older_manifest,
                encoding="utf-8",
            )

            requests = root / "requests.log"
            requests.write_text("", encoding="utf-8")

            curl = commands / "curl"
            curl.write_text(
                r"""#!/bin/sh
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

printf '%s\n' "$request_url" >> "$I131_FALLBACK_REQUESTS"

case "$request_url" in
    *'/releases?per_page=100')
        cp "$I131_FALLBACK_RELEASES" "$destination"
        ;;
    *'/v0.3.400/RELEASE_MANIFEST.txt')
        if [ "$I131_FALLBACK_FAIL_NEWEST" = yes ]; then
            printf 'synthetic manifest download failure\n' >&2
            false
        else
            cp "$I131_FALLBACK_NEWEST" "$destination"
        fi
        ;;
    *'/v0.3.398/RELEASE_MANIFEST.txt')
        cp "$I131_FALLBACK_OLDER" "$destination"
        ;;
    *)
        printf 'unexpected release request: %s\n' "$request_url" >&2
        false
        ;;
esac
""",
                encoding="utf-8",
            )
            curl.chmod(0o755)

            uname = commands / "uname"
            uname.write_text(
                "#!/bin/sh\n"
                "if [ \"$1\" = \"-s\" ]; then\n"
                "    printf 'Linux\\n'\n"
                "else\n"
                "    printf 'x86_64\\n'\n"
                "fi\n",
                encoding="utf-8",
            )
            uname.chmod(0o755)

            getconf = commands / "getconf"
            getconf.write_text(
                "#!/bin/sh\n"
                "printf 'glibc 2.39\\n'\n",
                encoding="utf-8",
            )
            getconf.chmod(0o755)

            environment = os.environ.copy()
            environment["PATH"] = (
                str(commands) +
                os.pathsep +
                environment.get("PATH", "")
            )
            environment["I131_FALLBACK_RELEASES"] = str(releases)
            environment["I131_FALLBACK_NEWEST"] = str(newest)
            environment["I131_FALLBACK_OLDER"] = str(older)
            environment["I131_FALLBACK_REQUESTS"] = str(requests)
            environment["I131_FALLBACK_FAIL_NEWEST"] = (
                "yes" if fail_newest_download else "no"
            )

            arguments = ["--inspect"]

            if pinned:
                arguments.extend([
                    "--tag",
                    "v0.3.400",
                ])

            result = subprocess.run(
                ["sh", str(self.INSTALLER), *arguments],
                cwd=root,
                env=environment,
                text=True,
                capture_output=True,
                check=False,
                timeout=20,
            )

            requested_urls = requests.read_text(
                encoding="utf-8"
            ).splitlines()

            return result, requested_urls

    def test_newer_glibc_release_uses_older_compatible_native(self):
        result, urls = self._run_case(
            newest_manifest=self._requires_newer_glibc(
                "v0.3.400"
            ),
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("RELEASE_TAG=v0.3.398", result.stdout)
        self.assertIn("INSPECTION=PASS", result.stdout)
        self.assertIn("skipping v0.3.400", result.stderr)

        newest_url = next(
            i for i, url in enumerate(urls)
            if "/v0.3.400/RELEASE_MANIFEST.txt" in url
        )
        older_url = next(
            i for i, url in enumerate(urls)
            if "/v0.3.398/RELEASE_MANIFEST.txt" in url
        )

        self.assertLess(newest_url, older_url)

    def test_different_architecture_release_uses_older_native(self):
        result, urls = self._run_case(
            newest_manifest=self._different_architecture(
                "v0.3.400"
            ),
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("RELEASE_TAG=v0.3.398", result.stdout)
        self.assertIn("skipping v0.3.400", result.stderr)
        self.assertTrue(any(
            "/v0.3.398/RELEASE_MANIFEST.txt" in url
            for url in urls
        ))

    def test_pinned_incompatible_release_never_falls_back(self):
        result, urls = self._run_case(
            newest_manifest=self._requires_newer_glibc(
                "v0.3.400"
            ),
            pinned=True,
        )

        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("INSPECTION=PASS", result.stdout)
        self.assertIn("glibc", result.stderr.lower())
        self.assertFalse(any(
            "/v0.3.398/RELEASE_MANIFEST.txt" in url
            for url in urls
        ))

    def test_manifest_download_failure_never_falls_back(self):
        result, urls = self._run_case(
            newest_manifest=self._manifest("v0.3.400"),
            fail_newest_download=True,
        )

        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("INSPECTION=PASS", result.stdout)
        self.assertIn(
            "cannot fetch official release manifest",
            result.stderr,
        )
        self.assertFalse(any(
            "/v0.3.398/RELEASE_MANIFEST.txt" in url
            for url in urls
        ))

    def test_no_compatible_published_native_fails_closed(self):
        result, urls = self._run_case(
            newest_manifest=self._requires_newer_glibc(
                "v0.3.400"
            ),
            older_manifest=self._requires_newer_glibc(
                "v0.3.398"
            ),
        )

        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("INSPECTION=PASS", result.stdout)
        self.assertIn(
            "no published Native release supports",
            result.stderr,
        )
        self.assertTrue(any(
            "/v0.3.398/RELEASE_MANIFEST.txt" in url
            for url in urls
        ))


class InstallNativePreflightTest(unittest.TestCase):
    INSTALLER = Path(__file__).resolve().parents[2] / "install.sh"

    REQUIRED_COMMANDS = (
        "sh",
        "id",
        "curl",
        "awk",
        "mktemp",
        "grep",
        "rm",
        "cat",
        "uname",
        "getconf",
        "sha256sum",
        "cmp",
        "unzip",
        "wc",
        "flock",
        "mv",
        "chmod",
        "mkdir",
        "rmdir",
        "sed",
        "find",
    )

    def _run_missing_tool(self, missing, arguments):
        with tempfile.TemporaryDirectory() as workspace:
            root = Path(workspace)
            command_dir = root / "isolated-commands"
            command_dir.mkdir()

            for name in self.REQUIRED_COMMANDS:
                if name == missing:
                    continue

                destination = command_dir / name

                if name == "curl":
                    destination.write_text(
                        "#!/bin/sh\n"
                        "printf 'UNEXPECTED_NETWORK_REQUEST\\n' >&2\n"
                        "false\n",
                        encoding="utf-8",
                    )
                    destination.chmod(0o755)
                    continue

                resolved = shutil.which(name)

                if resolved is None:
                    self.skipTest(
                        f"Host lacks prerequisite fixture tool: {name}"
                    )

                destination.symlink_to(resolved)

            environment = os.environ.copy()
            environment["PATH"] = str(command_dir)
            environment["HOME"] = str(root / "isolated-home")

            (root / "isolated-home").mkdir()

            shell = shutil.which("sh")

            if shell is None:
                self.skipTest("Host lacks sh")

            return subprocess.run(
                [shell, str(self.INSTALLER), *arguments],
                cwd=root,
                env=environment,
                text=True,
                capture_output=True,
                check=False,
                timeout=10,
            )

    def test_install_requires_flock_before_network(self):
        result = self._run_missing_tool("flock", [])

        self.assertNotEqual(0, result.returncode)
        self.assertIn(
            "missing required command: flock",
            result.stderr,
        )
        self.assertNotIn(
            "UNEXPECTED_NETWORK_REQUEST",
            result.stderr,
        )

    def test_verify_requires_sha256sum_before_network(self):
        result = self._run_missing_tool(
            "sha256sum",
            ["--verify-download"],
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn(
            "missing required command: sha256sum",
            result.stderr,
        )
        self.assertNotIn(
            "UNEXPECTED_NETWORK_REQUEST",
            result.stderr,
        )

    def test_release_listing_requires_cat_before_network(self):
        result = self._run_missing_tool(
            "cat",
            ["--list-releases"],
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn(
            "missing required command: cat",
            result.stderr,
        )
        self.assertNotIn(
            "UNEXPECTED_NETWORK_REQUEST",
            result.stderr,
        )

    def test_install_requires_find_before_network(self):
        result = self._run_missing_tool("find", [])

        self.assertNotEqual(0, result.returncode)
        self.assertIn(
            "missing required command: find",
            result.stderr,
        )
        self.assertNotIn(
            "UNEXPECTED_NETWORK_REQUEST",
            result.stderr,
        )

    def test_uninstall_requires_grep_before_managed_access(self):
        result = self._run_missing_tool(
            "grep",
            ["--uninstall"],
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn(
            "missing required command: grep",
            result.stderr,
        )
        self.assertNotIn(
            "no recognized managed Native installation",
            result.stderr,
        )
