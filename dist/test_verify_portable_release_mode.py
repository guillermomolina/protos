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
import subprocess
import sys
import tempfile
import unittest
import zipfile

POM = """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.guillermomolina</groupId>
  <artifactId>protos</artifactId>
  <version>{version}</version>
</project>
"""

def run(args: list[str], *, cwd: Path, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=cwd, check=check, text=True, capture_output=True)

def git(root: Path, *args: str) -> str:
    return run(["git", *args], cwd=root).stdout.strip()

def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def write_candidate_archive(
    path: Path,
    *,
    version: str,
    candidate: str,
    baseline: str,
    dirty: str = "false",
) -> None:
    root_name = f"protos-{version}"
    source = "\n".join(
        [
            f"implementation_version={version}",
            f"source_revision={candidate}",
            f"source_dirty={dirty}",
            "source_repository=https://github.com/guillermomolina/protos",
            f"source_path=/tree/{candidate}",
            "artifact_kind=public-prerelease",
            "public_release=true",
            f"release_baseline_revision={baseline}",
            f"release_baseline_version={version}-SNAPSHOT",
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
        "SOURCE.txt": source.encode("utf-8"),
        "RUNTIME.txt": runtime.encode("utf-8"),
        "LICENSE.TXT": b"fixture\n",
    }
    sums = "\n".join(
        f"{sha256(data)}  {name}" for name, data in sorted(entries.items())
    ) + "\n"
    entries["SHA256SUMS"] = sums.encode("utf-8")
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            archive.writestr(f"{root_name}/{name}", data)

class VerifyPortableReleaseModeTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="protos-e3c1-")
        top = Path(self.temp.name)
        self.repo = top / "repo"
        self.artifacts = top / "artifacts"
        self.repo.mkdir()
        self.artifacts.mkdir()
        (self.repo / "dist").mkdir()

        source_root = Path(__file__).resolve().parents[1]
        shutil.copy2(source_root / "dist/release_identity.py", self.repo / "dist/release_identity.py")
        shutil.copy2(source_root / "dist/verify_portable.py", self.repo / "dist/verify_portable.py")

        git(self.repo, "init", "-q")
        git(self.repo, "config", "user.email", "fixture@example.invalid")
        git(self.repo, "config", "user.name", "Fixture")

        (self.repo / "pom.xml").write_text(POM.format(version="0.2.230-SNAPSHOT"), encoding="utf-8")
        git(self.repo, "add", "pom.xml", "dist/release_identity.py", "dist/verify_portable.py")
        git(self.repo, "commit", "-q", "-m", "baseline")
        self.baseline = git(self.repo, "rev-parse", "HEAD")

        (self.repo / "pom.xml").write_text(POM.format(version="0.2.230"), encoding="utf-8")
        git(self.repo, "add", "pom.xml")
        git(self.repo, "commit", "-q", "-m", "candidate")
        self.candidate = git(self.repo, "rev-parse", "HEAD")

        self.archive = self.artifacts / "protos-0.2.230-posix-jvm.zip"
        write_candidate_archive(
            self.archive,
            version="0.2.230",
            candidate=self.candidate,
            baseline=self.baseline,
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def verify(self, *extra: str, check: bool = True) -> subprocess.CompletedProcess[str]:
        return run(
            [
                sys.executable,
                "dist/verify_portable.py",
                "--archive",
                str(self.archive),
                *extra,
            ],
            cwd=self.repo,
            check=check,
        )

    def test_public_prerelease_mode_accepts_exact_candidate_lineage(self) -> None:
        result = self.verify(
            "--public-prerelease",
            "--release-baseline",
            self.baseline,
            "--require-clean-source",
        )
        self.assertIn("DIST_ARTIFACT_MODE_CHECK: PASS mode=public-prerelease", result.stdout)
        self.assertIn("DIST001_B2_VERIFY: PASS", result.stdout)

    def test_default_development_mode_rejects_public_archive(self) -> None:
        result = self.verify("--require-clean-source", check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("development distribution project version", result.stderr)

    def test_public_mode_rejects_dirty_release_metadata(self) -> None:
        write_candidate_archive(
            self.archive,
            version="0.2.230",
            candidate=self.candidate,
            baseline=self.baseline,
            dirty="true",
        )
        result = self.verify(
            "--public-prerelease",
            "--release-baseline",
            self.baseline,
            "--require-clean-source",
            check=False,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("public prerelease archive must identify clean source", result.stderr)

    def test_public_mode_requires_exact_matching_baseline(self) -> None:
        result = self.verify(
            "--public-prerelease",
            "--release-baseline",
            "0" * 40,
            "--require-clean-source",
            check=False,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("release baseline revision does not expose pom.xml", result.stderr)

if __name__ == "__main__":
    unittest.main(verbosity=2)
