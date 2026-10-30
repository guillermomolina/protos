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
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest
import zipfile

import dist.tests.release.test_install_release_selection as fixtures


class InstallLocalNativeSnapshotTest(unittest.TestCase):
    INSTALLER = Path(__file__).resolve().parents[2] / "install.sh"
    VERSION = "0.3.398-SNAPSHOT"
    ROOT = f"protos-{VERSION}-native-linux-x86_64"

    def _write_archive(self, work, *, version=None, change=None,
                       damaged_checksum=False, smoke_fails=False):
        version = version or self.VERSION
        root = f"protos-{version}-native-linux-x86_64"
        original = fixtures.InstallNativeDownloadVerificationTest()
        source_files = original._files()
        files = {
            name.removeprefix(original.ROOT + "/"): data
            for name, data in source_files.items()
        }
        source = files["SOURCE.txt"].decode("utf-8")
        source = source.replace("artifact_kind=public-prerelease", "artifact_kind=development-native-distribution")
        source = source.replace("public_release=true", "public_release=false")
        source = source.replace("release_version=0.3.398\nrelease_tag=v0.3.398", "implementation_version=" + version)
        files["SOURCE.txt"] = source.encode("utf-8")
        files["bin/protos"] = r"""#!/bin/sh
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
if [ ! -f "$root/protos/lib/example.pr" ]; then
    false
elif [ "$1" = --version ]; then
    printf 'Protos VERSION\n'
elif [ "$1" = -e ]; then
    SMOKE
elif [ "$1" = relative.protos ]; then
    [ -f relative.protos ] && printf 'SNAPSHOT_CWD=PASS\n'
else
    false
fi
""".replace("VERSION", version).replace(
            "SMOKE", "false" if smoke_fails else "printf 'SNAPSHOT_SMOKE=PASS\\n'"
        ).encode("utf-8")
        runtime = files["RUNTIME.txt"].decode("utf-8")
        runtime = runtime.replace("distribution_format=protos-native-image-posix-v1\n", "distribution_format=protos-native-image-posix-v1\nartifact_kind=development-native-distribution\n")
        files["RUNTIME.txt"] = runtime.encode("utf-8")
        if change == "source":
            files["SOURCE.txt"] = files["SOURCE.txt"].replace(
                b"development-native-distribution", b"public-prerelease"
            )
        if change == "dual":
            files["SOURCE.txt"] += (
                b"artifact_kind=public-prerelease\npublic_release=true\n"
                b"release_version=0.3.398\nrelease_tag=v0.3.398\n"
            )
        if change == "readme":
            files["README.md"] = b"different content, same tag\n"
        checksums = [
            f"{hashlib.sha256(data).hexdigest()}  {name}"
            for name, data in sorted(files.items()) if name != "SHA256SUMS"
        ]
        files["SHA256SUMS"] = ("\n".join(checksums) + "\n").encode()
        if change == "content":
            files["README.md"] = b"modified after checksums were generated"
        if change == "extra":
            files["protos/lib/uninventoried.pr"] = b"not in SHA256SUMS\n"
        work.mkdir(parents=True, exist_ok=True)
        archive = work / (root + ".zip")
        with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as z:
            for name, data in files.items():
                info = zipfile.ZipInfo(root + "/" + name)
                info.create_system = 3
                mode = 0o755 if name in ("bin/protos", "libexec/protos-native") else 0o644
                info.external_attr = (stat.S_IFREG | mode) << 16
                info.compress_type = zipfile.ZIP_DEFLATED
                z.writestr(info, data)
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        if damaged_checksum:
            digest = "0" * 64
        Path(str(archive) + ".sha256").write_text(
            f"{digest}  {archive.name}\n", encoding="utf-8"
        )
        return archive

    def run_case(self, *, change=None, damaged_checksum=False, action="inspect", lifecycle=None,
                 glibc="2.40", arch="x86_64"):
        with tempfile.TemporaryDirectory(prefix="protos-local-snapshot-") as folder:
            work = Path(folder)
            archive = self._write_archive(
                work, change=change, damaged_checksum=damaged_checksum
            )
            commands = work / "commands"
            commands.mkdir()
            for tool, body in {
                "curl": "#!/bin/sh\nprintf 'UNEXPECTED_NETWORK_REQUEST\\n' >&2\nfalse\n",
                "uname": "#!/bin/sh\nif [ \"$1\" = -s ]; then printf 'Linux\\n'; else printf '%s\\n' " + arch + "; fi\n",
                "getconf": "#!/bin/sh\nprintf 'glibc " + glibc + "\\n'\n",
            }.items():
                target = commands / tool
                target.write_text(body, encoding="utf-8")
                target.chmod(0o755)
            env = os.environ.copy()
            env["PATH"] = str(commands) + os.pathsep + env.get("PATH", "")
            prefix = work / "prefix with spaces"
            arguments = ["--local-native", str(archive)]
            if action != "install":
                arguments.insert(0, "--" + action)
            result = subprocess.run(
                ["sh", str(self.INSTALLER), *arguments, "--prefix", str(prefix)],
                cwd=work, env=env, text=True, capture_output=True,
                check=False, timeout=25,
            )
            if action == "install" and result.returncode == 0:
                wrapper = prefix / "bin/protos"
                version_dir = (
                    prefix / "share/protos/versions" / ("v" + self.VERSION)
                )
                probe_env = env.copy()
                probe_env["HOME"] = "/"
                unrelated = work / "unrelated project"
                unrelated.mkdir()
                (unrelated / "relative.protos").write_text(
                    "fixture", encoding="utf-8"
                )

                version = subprocess.run(
                    [str(wrapper), "--version"], cwd=unrelated,
                    env=probe_env, capture_output=True, text=True,
                    check=False, timeout=10,
                )
                relative = subprocess.run(
                    [str(wrapper), "relative.protos"], cwd=unrelated,
                    env=probe_env, capture_output=True, text=True,
                    check=False, timeout=10,
                )
                second = subprocess.run(
                    ["sh", str(self.INSTALLER), *arguments,
                     "--prefix", str(prefix)],
                    cwd=work, env=env, capture_output=True, text=True,
                    check=False, timeout=25,
                )
                if lifecycle is not None:
                    return result, lifecycle(work, env, prefix, commands)
                return result, (
                    version_dir.is_dir(), version, relative, second
                )
            return result, prefix.exists()

    def test_valid_snapshot_inspection(self):
        result, created = self.run_case()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("SNAPSHOT_VERSION=" + self.VERSION, result.stdout)
        self.assertIn("LOCAL_NATIVE_VERIFIED=PASS", result.stdout)
        self.assertIn("INSPECTION=PASS", result.stdout)
        self.assertIn("prefix with spaces", result.stdout)
        self.assertFalse(created)
        self.assertNotIn("UNEXPECTED_NETWORK_REQUEST", result.stderr)

    def test_external_checksum_mismatch_rejected(self):
        result, created = self.run_case(damaged_checksum=True)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", result.stdout)
        self.assertIn("checksum mismatch", result.stderr)
        self.assertFalse(created)

    def test_internal_checksum_mismatch_rejected(self):
        result, created = self.run_case(change="content")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", result.stdout)
        self.assertIn("internal checksums", result.stderr)
        self.assertFalse(created)

    def test_official_source_disguised_as_snapshot_is_rejected(self):
        result, created = self.run_case(change="source")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", result.stdout)
        self.assertIn("source identity", result.stderr)
        self.assertFalse(created)

    def test_uninstall_cannot_be_combined_with_local_native(self):
        result, created = self.run_case(action="uninstall")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("requires install or --inspect", result.stderr)
        self.assertFalse(created)

    def test_local_snapshot_installs_and_runs_outside_cwd(self):
        result, outcome = self.run_case(action="install")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("INSTALL_STATUS=INSTALLED", result.stdout)
        exists, version, relative, second = outcome
        self.assertTrue(exists)
        self.assertEqual(0, version.returncode, version.stderr)
        self.assertIn("0.3.398-SNAPSHOT", version.stdout)
        self.assertEqual(0, relative.returncode, relative.stderr)
        self.assertIn("SNAPSHOT_CWD=PASS", relative.stdout)
        self.assertEqual(0, second.returncode, second.stderr)
        self.assertIn("INSTALL_STATUS=ALREADY_ACTIVE", second.stdout)
        self.assertNotIn("UNEXPECTED_NETWORK_REQUEST", result.stderr)


    def test_bad_checksum_prevents_snapshot_install(self):
        result, created = self.run_case(
            action="install", damaged_checksum=True
        )
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(created)

    def test_bad_internal_hash_prevents_snapshot_install(self):
        result, created = self.run_case(
            action="install", change="content"
        )
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(created)

    def test_snapshot_rollback_after_uninstall(self):
        def lifecycle(work, env, prefix, commands):
            base = ["sh", str(self.INSTALLER), "--prefix", str(prefix)]

            def invoke(*args):
                return subprocess.run(
                    [*base, *args], cwd=work, env=env,
                    capture_output=True, text=True, timeout=20,
                )

            uninstalled = invoke("--uninstall")
            restored = invoke("--rollback", "--tag", "v" + self.VERSION)
            wrapper = prefix / "bin/protos"
            launched = subprocess.run(
                [str(wrapper), "--version"], cwd=work, env=env,
                capture_output=True, text=True, timeout=10,
            ) if wrapper.is_file() else None
            return uninstalled, restored, launched

        initial, outcome = self.run_case(action="install", lifecycle=lifecycle)
        self.assertEqual(0, initial.returncode, initial.stderr)
        uninstalled, restored, launched = outcome
        self.assertEqual(0, uninstalled.returncode, uninstalled.stderr)
        self.assertEqual(0, restored.returncode, restored.stderr)
        self.assertIn("ROLLBACK_STATUS=ACTIVATED", restored.stdout)
        self.assertIsNotNone(launched)
        self.assertEqual(0, launched.returncode, launched.stderr)
        self.assertIn(self.VERSION, launched.stdout)
        self.assertNotIn("UNEXPECTED_NETWORK_REQUEST", restored.stderr)

    def test_active_snapshot_cannot_be_removed(self):
        def lifecycle(work, env, prefix, commands):
            wrapper = prefix / "bin/protos"
            original = wrapper.read_bytes()
            version_dir = (
                prefix / "share/protos/versions" / ("v" + self.VERSION)
            )
            rejected = subprocess.run(
                [
                    "sh", str(self.INSTALLER),
                    "--prefix", str(prefix),
                    "--remove-version", "--tag", "v" + self.VERSION,
                ],
                cwd=work, env=env, capture_output=True,
                text=True, timeout=20,
            )
            return rejected, original == wrapper.read_bytes(), version_dir.is_dir()

        initial, outcome = self.run_case(action="install", lifecycle=lifecycle)
        self.assertEqual(0, initial.returncode, initial.stderr)
        rejected, unchanged, retained = outcome
        self.assertNotEqual(0, rejected.returncode)
        self.assertIn("refusing to remove active version", rejected.stderr)
        self.assertTrue(unchanged)
        self.assertTrue(retained)

    def test_inactive_snapshot_removal_refuses_foreign_file(self):
        def lifecycle(work, env, prefix, commands):
            tree = prefix / "share/protos/versions" / ("v" + self.VERSION)
            base = ["sh", str(self.INSTALLER), "--prefix", str(prefix)]
            deactivated = subprocess.run(
                [*base, "--uninstall"], cwd=work, env=env,
                capture_output=True, text=True, timeout=20,
            )
            foreign = tree / "foreign-application-data.txt"
            foreign.write_text("KEEP_ME", encoding="utf-8")
            rejected = subprocess.run(
                [*base, "--remove-version", "--tag", "v" + self.VERSION],
                cwd=work, env=env, capture_output=True, text=True, timeout=20,
            )
            return (deactivated, rejected, foreign.read_text(encoding="utf-8"),
                    tree.is_dir(), (prefix / "bin/protos").exists())

        installed, state = self.run_case(action="install", lifecycle=lifecycle)
        self.assertEqual(0, installed.returncode, installed.stderr)
        deactivated, rejected, foreign, retained, wrapper = state
        self.assertEqual(0, deactivated.returncode, deactivated.stderr)
        self.assertNotEqual(0, rejected.returncode)
        self.assertIn("refusing unverified version", rejected.stderr)
        self.assertEqual("KEEP_ME", foreign)
        self.assertTrue(retained)
        self.assertFalse(wrapper)
        self.assertNotIn("UNEXPECTED_NETWORK_REQUEST", rejected.stderr)

    def test_inactive_snapshot_removal_refuses_nested_symlink(self):
        def lifecycle(work, env, prefix, commands):
            tree = prefix / "share/protos/versions" / ("v" + self.VERSION)
            base = ["sh", str(self.INSTALLER), "--prefix", str(prefix)]
            deactivated = subprocess.run(
                [*base, "--uninstall"], cwd=work, env=env,
                capture_output=True, text=True, timeout=20,
            )
            outside = work / "external-sentinel.txt"
            outside.write_text("PRESERVE", encoding="utf-8")
            (tree / "foreign-symlink").symlink_to(outside)
            rejected = subprocess.run(
                [*base, "--remove-version", "--tag", "v" + self.VERSION],
                cwd=work, env=env, capture_output=True, text=True, timeout=20,
            )
            return (deactivated, rejected, outside.read_text(encoding="utf-8"),
                    (tree / "foreign-symlink").is_symlink())

        installed, state = self.run_case(action="install", lifecycle=lifecycle)
        self.assertEqual(0, installed.returncode, installed.stderr)
        deactivated, rejected, outside, retained = state
        self.assertEqual(0, deactivated.returncode, deactivated.stderr)
        self.assertNotEqual(0, rejected.returncode)
        self.assertIn("refusing unverified version", rejected.stderr)
        self.assertEqual("PRESERVE", outside)
        self.assertTrue(retained)
        self.assertNotIn("UNEXPECTED_NETWORK_REQUEST", rejected.stderr)

    def test_remove_inactive_snapshot_respects_existing_lock(self):
        import fcntl

        def lifecycle(work, env, prefix, commands):
            tree = prefix / "share/protos/versions" / ("v" + self.VERSION)
            base = ["sh", str(self.INSTALLER), "--prefix", str(prefix)]
            deactivated = subprocess.run(
                [*base, "--uninstall"], cwd=work, env=env,
                capture_output=True, text=True, timeout=20,
            )
            lock = prefix / "share/protos/.installer.lock"
            with lock.open("a+b") as fd:
                fcntl.flock(fd.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                rejected = subprocess.run(
                    [*base, "--remove-version", "--tag", "v" + self.VERSION],
                    cwd=work, env=env, capture_output=True, text=True, timeout=20,
                )
                retained_while_locked = tree.is_dir()
                fcntl.flock(fd.fileno(), fcntl.LOCK_UN)
            removed = subprocess.run(
                [*base, "--remove-version", "--tag", "v" + self.VERSION],
                cwd=work, env=env, capture_output=True, text=True, timeout=20,
            )
            return (deactivated, rejected, retained_while_locked,
                    removed, tree.exists())

        installed, state = self.run_case(action="install", lifecycle=lifecycle)
        self.assertEqual(0, installed.returncode, installed.stderr)
        deactivated, rejected, retained, removed, exists = state
        self.assertEqual(0, deactivated.returncode, deactivated.stderr)
        self.assertNotEqual(0, rejected.returncode)
        self.assertIn("another Protos lifecycle operation", rejected.stderr)
        self.assertTrue(retained)
        self.assertEqual(0, removed.returncode, removed.stderr)
        self.assertIn("REMOVE_STATUS=REMOVED", removed.stdout)
        self.assertFalse(exists)
        self.assertNotIn("UNEXPECTED_NETWORK_REQUEST", rejected.stderr + removed.stderr)

    def test_parent_symlink_swap_cannot_delete_external_file(self):
        import shutil

        def lifecycle(work, env, prefix, commands):
            tree = prefix / "share/protos/versions" / ("v" + self.VERSION)
            base = ["sh", str(self.INSTALLER), "--prefix", str(prefix)]

            deactivated = subprocess.run(
                [*base, "--uninstall"], cwd=work, env=env,
                capture_output=True, text=True, timeout=20,
            )

            outside = work / "external-dir"
            outside.mkdir()
            sentinel = outside / "example.pr"
            sentinel.write_text("PRESERVE", encoding="utf-8")

            parent = tree / "protos/lib"
            real_rm = shutil.which("rm", path=env["PATH"])
            self.assertIsNotNone(real_rm)

            hook = commands / "rm"
            hook.write_text("""#!/bin/sh
case " $* " in
  *example.pr*)
    if [ ! -e "$RACE_MARKER" ]; then
      mv -- "$RACE_PARENT" "$RACE_PARENT.old" &&
      ln -s -- "$RACE_OUTSIDE" "$RACE_PARENT" &&
      : > "$RACE_MARKER"
    fi
    ;;
esac
exec "$RACE_REAL_RM" "$@"
""", encoding="utf-8")
            hook.chmod(0o755)

            marker = work / "race-triggered"
            race_env = env.copy()
            race_env.update({
                "RACE_MARKER": str(marker),
                "RACE_PARENT": str(parent),
                "RACE_OUTSIDE": str(outside),
                "RACE_REAL_RM": real_rm,
            })

            removed = subprocess.run(
                [*base, "--remove-version", "--tag", "v" + self.VERSION],
                cwd=work, env=race_env, capture_output=True,
                text=True, timeout=20,
            )
            return deactivated, removed, marker.exists(), sentinel.read_text()

        installed, outcome = self.run_case(action="install", lifecycle=lifecycle)
        self.assertEqual(0, installed.returncode, installed.stderr)

        deactivated, removed, triggered, preserved = outcome
        self.assertEqual(0, deactivated.returncode, deactivated.stderr)
        self.assertTrue(triggered, "the simulated race must occur")
        self.assertNotEqual(0, removed.returncode)
        self.assertEqual("PRESERVE", preserved)

    def test_remove_unknown_version_symlink_preserves_external_tree(self):
        def lifecycle(work, env, prefix, commands):
            versions = prefix / "share/protos/versions"
            outside = work / "outside-version"
            outside.mkdir()
            sentinel = outside / "keep.txt"
            sentinel.write_text("PRESERVE", encoding="utf-8")
            (versions / "v0.3.399-SNAPSHOT").symlink_to(outside, target_is_directory=True)
            rejected = subprocess.run(
                ["sh", str(self.INSTALLER), "--prefix", str(prefix),
                 "--remove-version", "--tag", "v0.3.399-SNAPSHOT"],
                cwd=work, env=env, capture_output=True, text=True, timeout=20,
            )
            return (rejected, sentinel.read_text(encoding="utf-8"),
                    (versions / "v0.3.399-SNAPSHOT").is_symlink(),
                    (prefix / "bin/protos").is_file())

        installed, state = self.run_case(action="install", lifecycle=lifecycle)
        self.assertEqual(0, installed.returncode, installed.stderr)
        rejected, sentinel, link, wrapper = state
        self.assertNotEqual(0, rejected.returncode)
        self.assertIn("refusing unverified version", rejected.stderr)
        self.assertEqual("PRESERVE", sentinel)
        self.assertTrue(link)
        self.assertTrue(wrapper)
        self.assertNotIn("UNEXPECTED_NETWORK_REQUEST", rejected.stderr)

    def test_snapshot_corruption_blocks_offline_rollback(self):
        def lifecycle(work, env, prefix, commands):
            root = prefix / "share/protos/versions" / ("v" + self.VERSION)
            wrapper = prefix / "bin/protos"
            base = ["sh", str(self.INSTALLER), "--prefix", str(prefix)]
            removed = subprocess.run(
                [*base, "--uninstall"], cwd=work, env=env,
                capture_output=True, text=True, timeout=20,
            )
            (root / "README.md").write_text("CORRUPTED", encoding="utf-8")
            rejected = subprocess.run(
                [*base, "--rollback", "--tag", "v" + self.VERSION],
                cwd=work, env=env, capture_output=True, text=True,
                timeout=20,
            )
            return removed, rejected, wrapper.exists(), (root / "README.md").read_text(encoding="utf-8")

        first, outcome = self.run_case(action="install", lifecycle=lifecycle)
        self.assertEqual(0, first.returncode, first.stderr)
        removed, rejected, wrapper_present, retained = outcome
        self.assertEqual(0, removed.returncode, removed.stderr)
        self.assertNotEqual(0, rejected.returncode)
        self.assertIn("full Native tree audit", rejected.stderr)
        self.assertFalse(wrapper_present)
        self.assertEqual("CORRUPTED", retained)
        self.assertNotIn("UNEXPECTED_NETWORK_REQUEST", rejected.stderr)

    def test_reinstall_refuses_modified_existing_snapshot(self):
        def lifecycle(work, env, prefix, commands):
            root = prefix / "share/protos/versions" / ("v" + self.VERSION)
            wrapper = prefix / "bin/protos"
            original_wrapper = wrapper.read_bytes()
            (root / "README.md").write_text("UNMANAGED_CHANGE", encoding="utf-8")
            repeated = subprocess.run(
                ["sh", str(self.INSTALLER), "--prefix", str(prefix),
                 "--local-native", str(work / (self.ROOT + ".zip"))],
                cwd=work, env=env, capture_output=True, text=True,
                timeout=25,
            )
            return (
                repeated,
                original_wrapper == wrapper.read_bytes(),
                (root / "README.md").read_text(encoding="utf-8"),
            )

        first, outcome = self.run_case(action="install", lifecycle=lifecycle)
        self.assertEqual(0, first.returncode, first.stderr)
        repeated, unchanged, retained = outcome
        self.assertNotEqual(0, repeated.returncode)
        self.assertIn("existing SNAPSHOT identity differs", repeated.stderr)
        self.assertTrue(unchanged)
        self.assertEqual("UNMANAGED_CHANGE", retained)
        self.assertNotIn("UNEXPECTED_NETWORK_REQUEST", repeated.stderr)

    def test_explicit_official_rollback_and_snapshot_reactivation(self):
        def lifecycle(work, env, prefix, commands):
            import shutil

            root = prefix / "share/protos/versions"
            snapshot = root / ("v" + self.VERSION)
            official = root / "v0.3.398"
            wrapper = prefix / "bin/protos"
            shutil.copytree(snapshot, official)
            release_fixture = fixtures.InstallNativeDownloadVerificationTest()
            release_files = release_fixture._files()
            (official / "SOURCE.txt").write_bytes(
                release_files[release_fixture.ROOT + "/SOURCE.txt"]
            )
            launcher = official / "bin/protos"
            text = launcher.read_text(encoding="utf-8")
            if text.count("Protos 0.3.398-SNAPSHOT") != 1:
                raise AssertionError("Unexpected snapshot launcher fixture")
            launcher.write_text(
                text.replace("Protos 0.3.398-SNAPSHOT", "Protos 0.3.398"),
                encoding="utf-8",
            )
            launcher.chmod(0o755)
            lines = [
                f"{hashlib.sha256(file.read_bytes()).hexdigest()}  {file.relative_to(official).as_posix()}"
                for file in sorted(official.rglob("*"))
                if file.is_file() and file.name != "SHA256SUMS"
            ]
            (official / "SHA256SUMS").write_text(
                "\n".join(lines) + "\n", encoding="utf-8"
            )
            base = ["sh", str(self.INSTALLER), "--prefix", str(prefix)]
            rolled = subprocess.run(
                [*base, "--rollback", "--tag", "v0.3.398"],
                cwd=work, env=env, capture_output=True, text=True, timeout=20,
            )
            official_result = subprocess.run(
                [str(wrapper), "--version"], cwd=work, env=env,
                capture_output=True, text=True, timeout=10,
            )
            activated = subprocess.run(
                [*base, "--local-native", str(work / (self.ROOT + ".zip"))],
                cwd=work, env=env, capture_output=True, text=True, timeout=25,
            )
            snapshot_result = subprocess.run(
                [str(wrapper), "--version"], cwd=work, env=env,
                capture_output=True, text=True, timeout=10,
            )
            return rolled, official_result, activated, snapshot_result, official.is_dir()

        first, outcome = self.run_case(action="install", lifecycle=lifecycle)
        self.assertEqual(0, first.returncode, first.stderr)
        rolled, official_result, activated, snapshot_result, retained = outcome
        self.assertEqual(0, rolled.returncode, rolled.stderr)
        self.assertEqual(0, official_result.returncode, official_result.stderr)
        self.assertIn("Protos 0.3.398", official_result.stdout)
        self.assertNotIn("SNAPSHOT", official_result.stdout)
        self.assertEqual(0, activated.returncode, activated.stderr)
        self.assertIn("INSTALL_STATUS=ACTIVATED_EXISTING", activated.stdout)
        self.assertEqual(0, snapshot_result.returncode, snapshot_result.stderr)
        self.assertIn(self.VERSION, snapshot_result.stdout)
        self.assertTrue(retained)
        self.assertNotIn("UNEXPECTED_NETWORK_REQUEST", activated.stderr)

    def test_local_snapshot_install_preserves_foreign_wrapper(self):
        def lifecycle(work, env, prefix, commands):
            wrapper = prefix / "bin/protos"
            foreign = b"#!/bin/sh\nprintf 'FOREIGN_COMMAND\n'\n"
            wrapper.write_bytes(foreign)
            wrapper.chmod(0o755)
            rejected = subprocess.run(
                ["sh", str(self.INSTALLER), "--prefix", str(prefix),
                 "--local-native", str(work / (self.ROOT + ".zip"))],
                cwd=work, env=env, capture_output=True, text=True,
                timeout=25,
            )
            return rejected, wrapper.read_bytes() == foreign

        first, outcome = self.run_case(action="install", lifecycle=lifecycle)
        self.assertEqual(0, first.returncode, first.stderr)
        rejected, preserved = outcome
        self.assertNotEqual(0, rejected.returncode)
        self.assertIn("non-managed protos command", rejected.stderr)
        self.assertTrue(preserved)
        self.assertNotIn("UNEXPECTED_NETWORK_REQUEST", rejected.stderr)
    def test_official_update_cannot_replace_active_snapshot(self):
        def lifecycle(work, env, prefix, commands):
            import json
            import stat
            import zipfile

            release = fixtures.InstallNativeDownloadVerificationTest()
            archive = work / release.ARCHIVE
            with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED) as z:
                for name, data in release._files().items():
                    info = zipfile.ZipInfo(name)
                    info.create_system = 3
                    executable = name.endswith(('/bin/protos', '/libexec/protos-native'))
                    info.external_attr = (stat.S_IFREG | (0o755 if executable else 0o644)) << 16
                    info.compress_type = zipfile.ZIP_DEFLATED
                    z.writestr(info, data)
            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            checksum = work / (release.ARCHIVE + '.sha256')
            checksum.write_text(f'{digest}  {archive.name}\n', encoding='utf-8')
            checksum_digest = hashlib.sha256(checksum.read_bytes()).hexdigest()
            manifest = fixtures.InstallNativeManifestInspectionTest()._manifest()
            manifest = manifest.replace('asset.1.archive_sha256=' + 'a' * 64,
                                        'asset.1.archive_sha256=' + digest)
            manifest = manifest.replace('asset.1.checksum_sha256=' + 'b' * 64,
                                        'asset.1.checksum_sha256=' + checksum_digest)
            (work / 'manifest.txt').write_text(manifest, encoding='utf-8')
            (work / 'releases.json').write_text(
                json.dumps([{'tag_name': release.TAG, 'draft': False}]),
                encoding='utf-8',
            )
            fake_curl = commands / 'curl'
            fake_curl.write_text(r"""#!/bin/sh
out=
url=
while [ "$#" -gt 0 ]; do
    if [ "$1" = -o ]; then
        shift
        out=$1
    else
        case "$1" in https://*) url=$1 ;; esac
    fi
    shift
done
case "$url" in
    *'/releases?per_page=100') cp "$PROTOS_TEST_RELEASES" "$out" ;;
    *'/RELEASE_MANIFEST.txt') cp "$PROTOS_TEST_MANIFEST" "$out" ;;
    *'.zip.sha256') cp "$PROTOS_TEST_CHECKSUM" "$out" ;;
    *'.zip') cp "$PROTOS_TEST_ZIP" "$out" ;;
    *) printf 'UNEXPECTED_NETWORK_REQUEST
' >&2; false ;;
esac
""", encoding='utf-8')
            fake_curl.chmod(0o755)
            official_env = env.copy()
            official_env.update({
                'PROTOS_TEST_RELEASES': str(work / 'releases.json'),
                'PROTOS_TEST_MANIFEST': str(work / 'manifest.txt'),
                'PROTOS_TEST_CHECKSUM': str(checksum),
                'PROTOS_TEST_ZIP': str(archive),
            })
            wrapper = prefix / 'bin/protos'
            before = wrapper.read_bytes()
            rejected = subprocess.run(
                ['sh', str(self.INSTALLER), '--prefix', str(prefix),
                 '--tag', release.TAG],
                cwd=work, env=official_env, capture_output=True,
                text=True, timeout=25,
            )
            return (rejected, wrapper.read_bytes() == before,
                    (prefix / 'share/protos/versions' / ('v' + self.VERSION)).is_dir())

        first, outcome = self.run_case(action='install', lifecycle=lifecycle)
        self.assertEqual(0, first.returncode, first.stderr)
        rejected, unchanged, retained = outcome
        self.assertNotEqual(0, rejected.returncode)
        self.assertIn('active SNAPSHOT requires explicit rollback', rejected.stderr)
        self.assertTrue(unchanged)
        self.assertTrue(retained)
        self.assertNotIn('UNEXPECTED_NETWORK_REQUEST', rejected.stderr)

    def _invoke(self, env, work, prefix, *args):
        return subprocess.run(
            ["sh", str(self.INSTALLER), "--prefix", str(prefix), *args],
            cwd=work, env=env, capture_output=True, text=True,
            check=False, timeout=25,
        )

    def test_snapshot_carrying_official_identity_keys_is_rejected(self):
        result, created = self.run_case(change="dual")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", result.stdout)
        self.assertIn("ambiguous local SNAPSHOT source identity", result.stderr)
        self.assertFalse(created)

    def test_uninventoried_zip_member_is_rejected(self):
        result, created = self.run_case(action="install", change="extra")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("coverage mismatch", result.stderr)
        self.assertFalse(created)

    def test_incompatible_glibc_is_rejected(self):
        result, created = self.run_case(action="install", glibc="2.30")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("host glibc is below", result.stderr)
        self.assertFalse(created)
        self.assertNotIn("UNEXPECTED_NETWORK_REQUEST", result.stderr)

    def test_incompatible_architecture_is_rejected(self):
        result, created = self.run_case(action="install", arch="aarch64")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("requires Linux x86_64", result.stderr)
        self.assertFalse(created)

    def test_same_tag_with_different_content_is_refused(self):
        def lifecycle(work, env, prefix, commands):
            wrapper = prefix / "bin/protos"
            readme = (prefix / "share/protos/versions" /
                      ("v" + self.VERSION) / "README.md")
            before = wrapper.read_bytes()
            installed = readme.read_bytes()
            other = self._write_archive(work / "other build", change="readme")
            rejected = self._invoke(env, work, prefix,
                                    "--local-native", str(other))
            return (rejected, before == wrapper.read_bytes(),
                    installed == readme.read_bytes())

        first, outcome = self.run_case(action="install", lifecycle=lifecycle)
        self.assertEqual(0, first.returncode, first.stderr)
        rejected, wrapper_unchanged, tree_unchanged = outcome
        self.assertNotEqual(0, rejected.returncode)
        self.assertIn("existing SNAPSHOT identity differs", rejected.stderr)
        self.assertTrue(wrapper_unchanged)
        self.assertTrue(tree_unchanged)
        self.assertNotIn("UNEXPECTED_NETWORK_REQUEST", rejected.stderr)

    def test_failed_snapshot_activation_keeps_previous_version(self):
        def lifecycle(work, env, prefix, commands):
            wrapper = prefix / "bin/protos"
            versions = prefix / "share/protos/versions"
            before = wrapper.read_bytes()
            broken = self._write_archive(
                work / "broken build", version="0.3.399-SNAPSHOT",
                smoke_fails=True,
            )
            rejected = self._invoke(env, work, prefix,
                                    "--local-native", str(broken))
            launched = subprocess.run(
                [str(wrapper), "--version"], cwd=work, env=env,
                capture_output=True, text=True, check=False, timeout=10,
            )
            return (rejected, before == wrapper.read_bytes(), launched,
                    (versions / "v0.3.399-SNAPSHOT").exists(),
                    list(versions.glob(".staging-*")))

        first, outcome = self.run_case(action="install", lifecycle=lifecycle)
        self.assertEqual(0, first.returncode, first.stderr)
        rejected, unchanged, launched, published, staging = outcome
        self.assertNotEqual(0, rejected.returncode)
        self.assertIn("smoke failed", rejected.stderr)
        self.assertTrue(unchanged)
        self.assertEqual(0, launched.returncode, launched.stderr)
        self.assertIn("Protos " + self.VERSION, launched.stdout)
        self.assertFalse(published)
        self.assertEqual([], staging)
