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

"""Focused regression tests for Native/JVM selection in bin/protos."""

from __future__ import annotations

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
LAUNCHER = ROOT / "bin" / "protos"


class NativeLauncherTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="protos-native-launcher-")
        self.work = Path(self.temp.name)
        self.cwd = self.work / "unrelated-cwd"
        self.cwd.mkdir()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def distribution(self, name: str) -> Path:
        root = self.work / name
        (root / "bin").mkdir(parents=True)
        shutil.copy2(LAUNCHER, root / "bin" / "protos")
        (root / "bin" / "protos").chmod(0o755)
        return root

    @staticmethod
    def executable(path: Path, content: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        path.chmod(0o755)

    @staticmethod
    def native_source_metadata(root: Path, version: str) -> None:
        (root / "SOURCE.txt").write_text(
            "\n".join(
                [
                    "implementation_version=" + version,
                    "source_revision=" + "0" * 40,
                    "",
                ]
            ),
            encoding="utf-8",
        )

    def test_native_payload_precedes_all_java_selection(self) -> None:
        root = self.distribution("native")
        native = root / "libexec" / "protos-native"
        self.native_source_metadata(root, "7.8.9-SNAPSHOT")
        poison_java = self.work / "java-must-not-run"

        self.executable(
            native,
            """#!/usr/bin/env sh
set -eu
printf 'PROTOS_HOME=%s\\n' "${PROTOS_HOME:-}"
printf 'PROTOS_IMPLEMENTATION_VERSION=%s\\n' "${PROTOS_IMPLEMENTATION_VERSION:-}"
printf 'ARGC=%s\\n' "$#"
i=0
for arg do
    printf 'ARG_%s=%s\\n' "$i" "$arg"
    i=$((i + 1))
done
""",
        )
        self.executable(
            poison_java,
            """#!/usr/bin/env sh
echo 'JAVA_WAS_EXECUTED' >&2
exit 97
""",
        )

        env = os.environ.copy()
        env["PROTOS_JAVA"] = str(poison_java)
        env["JAVA_HOME"] = str(self.work / "also-invalid-java-home")
        env["PROTOS_IMPLEMENTATION_VERSION"] = "forged-by-caller"

        result = subprocess.run(
            [str(root / "bin" / "protos"), "--version", "argument with spaces"],
            cwd=self.cwd,
            env=env,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            "\n".join(
                [
                    f"PROTOS_HOME={root}",
                    "PROTOS_IMPLEMENTATION_VERSION=7.8.9-SNAPSHOT",
                    "ARGC=2",
                    "ARG_0=--version",
                    "ARG_1=argument with spaces",
                    "",
                ]
            ),
            result.stdout,
        )
        self.assertNotIn("forged-by-caller", result.stdout)
        self.assertNotIn("JAVA_WAS_EXECUTED", result.stderr)

    def test_native_payload_requires_distribution_identity(self) -> None:
        root = self.distribution("native-without-source")
        self.executable(
            root / "libexec" / "protos-native",
            """#!/usr/bin/env sh
echo 'NATIVE_WAS_EXECUTED'
""",
        )

        env = os.environ.copy()
        env["PROTOS_IMPLEMENTATION_VERSION"] = "forged-by-caller"

        result = subprocess.run(
            [str(root / "bin" / "protos"), "--version"],
            cwd=self.cwd,
            env=env,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("NATIVE_WAS_EXECUTED", result.stdout)
        self.assertIn("distribution source metadata is missing", result.stderr)

    def test_portable_jvm_distribution_still_uses_java_without_native_payload(
        self,
    ) -> None:
        root = self.distribution("jvm")
        (root / "lib").mkdir()
        (root / "lib" / "protos.jar").write_bytes(b"fixture")
        (root / "RUNTIME.txt").write_text(
            "\n".join(
                [
                    "java_feature=25",
                    "java_version=25.0.4.1.1",
                    "java_vendor_contains=GraalVM",
                    "",
                ]
            ),
            encoding="utf-8",
        )

        fake_java = self.work / "fake-java"
        self.executable(
            fake_java,
            """#!/usr/bin/env sh
set -eu
if [ "${1:-}" = "-XshowSettings:properties" ]; then
    cat >&2 <<'EOF'
    java.specification.version = 25
    java.version = 25.0.4.1.1
    java.vendor = GraalVM Community
    java.vendor.version = GraalVM CE 25.4.4.1.1
EOF
    exit 0
fi
printf 'JAVA_EXECUTED=1\\n'
printf 'PROTOS_HOME=%s\\n' "${PROTOS_HOME:-}"
printf 'PROTOS_IMPLEMENTATION_VERSION=%s\\n' "${PROTOS_IMPLEMENTATION_VERSION:-<unset>}"
printf 'ARGS=%s\\n' "$*"
""",
        )

        env = os.environ.copy()
        env["PROTOS_JAVA"] = str(fake_java)
        env.pop("JAVA_HOME", None)
        env["PROTOS_IMPLEMENTATION_VERSION"] = "forged-by-caller"

        result = subprocess.run(
            [str(root / "bin" / "protos"), "--help"],
            cwd=self.cwd,
            env=env,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("JAVA_EXECUTED=1", result.stdout)
        self.assertIn(f"PROTOS_HOME={root}", result.stdout)
        self.assertIn("PROTOS_IMPLEMENTATION_VERSION=<unset>", result.stdout)
        self.assertIn(
            "com.guillermomolina.protos.cli.ProtosCli --help",
            result.stdout,
        )


if __name__ == "__main__":
    unittest.main()
