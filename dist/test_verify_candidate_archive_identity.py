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
import io
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

sys.dont_write_bytecode = True

PUBLIC = "0.2.236"
SNAPSHOT = PUBLIC + "-SNAPSHOT"
TAG = "v" + PUBLIC
ROOT_NAME = "protos-" + PUBLIC
ARCHIVE_NAME = ROOT_NAME + "-posix-jvm.zip"


def run(args: list[str], *, cwd: Path, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=cwd,
        check=check,
        text=True,
        capture_output=True,
        env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
    )


def git(root: Path, *args: str) -> str:
    return run(["git", *args], cwd=root).stdout.strip()


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def jar_bytes(version: str) -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as jar:
        jar.writestr(
            "META-INF/MANIFEST.MF",
            "Manifest-Version: 1.0\r\n"
            "Implementation-Title: protos\r\n"
            f"Implementation-Version: {version}\r\n"
            "\r\n",
        )
        jar.writestr("payload.txt", "fixture\n")
    return buffer.getvalue()


class CandidateArchiveIdentityVerifierTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="protos-e4c2-")
        self.top = Path(self.temp.name)
        self.repo = self.top / "repo"
        self.candidate_worktree = self.top / "candidate"
        self.repo.mkdir()
        (self.repo / "dist").mkdir()
        (self.repo / "docs/project").mkdir(parents=True)

        source = Path(__file__).resolve().parents[1] / "dist/verify_candidate_archive_identity.py"
        shutil.copy2(source, self.repo / "dist/verify_candidate_archive_identity.py")

        git(self.repo, "init", "-q")
        git(self.repo, "config", "user.email", "fixture@example.invalid")
        git(self.repo, "config", "user.name", "Fixture")

        (self.repo / ".gitignore").write_text("target/\n", encoding="utf-8")
        (self.repo / "pom.xml").write_text(
            f"""<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.guillermomolina</groupId>
  <artifactId>protos</artifactId>
  <version>{SNAPSHOT}</version>
</project>
""",
            encoding="utf-8",
        )
        git(self.repo, "add", ".gitignore", "pom.xml")
        git(self.repo, "commit", "-q", "-m", "baseline")
        self.baseline = git(self.repo, "rev-parse", "HEAD")

        git(
            self.repo,
            "worktree",
            "add",
            "--detach",
            str(self.candidate_worktree),
            self.baseline,
        )
        pom = self.candidate_worktree / "pom.xml"
        pom.write_text(
            pom.read_text(encoding="utf-8").replace(SNAPSHOT, PUBLIC),
            encoding="utf-8",
        )
        git(self.candidate_worktree, "add", "pom.xml")
        git(self.candidate_worktree, "commit", "-q", "-m", "candidate")
        self.candidate = git(self.candidate_worktree, "rev-parse", "HEAD")

        self.selection = self.repo / "docs/project/DIST001_E4_SELECTION.txt"
        self.artifact_record = self.repo / "docs/project/DIST001_E4_CANDIDATE_ARTIFACT.txt"
        self.archive = (
            self.candidate_worktree
            / "target"
            / "distributions"
            / ARCHIVE_NAME
        )
        self.write_selection()
        self.build_archive()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write_selection(self, **overrides: str) -> None:
        values = {
            "selection_format": "protos-dist001-e4-selection-v1",
            "selection_authorized": "true",
            "selection_authorization_basis": "explicit-user-decision",
            "release_publication_authorized": "false",
            "release_baseline_revision": self.baseline,
            "release_baseline_version": SNAPSHOT,
            "release_version": PUBLIC,
            "release_tag": TAG,
            "specification_revision": "0.1.382",
            "i023_status": "CLOSED",
            "b007_status": "CLOSED",
            "candidate_source_revision": self.candidate,
            "git_tag_created": "false",
            "github_release_created": "false",
            "release_assets_published": "false",
        }
        values.update(overrides)
        self.selection.write_text(
            "\n".join(f"{k}={v}" for k, v in values.items()) + "\n",
            encoding="utf-8",
        )

    def write_artifact_record(self, digest: str, **overrides: str) -> None:
        values = {
            "artifact_record_format": "protos-dist001-e4-candidate-artifact-v1",
            "release_baseline_revision": self.baseline,
            "candidate_source_revision": self.candidate,
            "release_version": PUBLIC,
            "archive_name": ARCHIVE_NAME,
            "archive_sha256": digest,
            "archive_built": "true",
            "archive_identity_independently_verified": "false",
            "release_envelope_generated": "false",
            "candidate_audit_materialized": "false",
            "release_publication_authorized": "false",
            "git_tag_created": "false",
            "github_release_created": "false",
            "release_assets_published": "false",
        }
        values.update(overrides)
        self.artifact_record.write_text(
            "\n".join(f"{k}={v}" for k, v in values.items()) + "\n",
            encoding="utf-8",
        )

    def build_archive(
        self,
        *,
        source_overrides: dict[str, str] | None = None,
        runtime_overrides: dict[str, str] | None = None,
        jar_version: str = PUBLIC,
        corrupt_internal_path: str | None = None,
    ) -> None:
        source = {
            "implementation_version": PUBLIC,
            "source_revision": self.candidate,
            "source_dirty": "false",
            "source_repository": "https://github.com/guillermomolina/protos",
            "source_path": "/tree/" + self.candidate,
            "artifact_kind": "public-prerelease",
            "public_release": "true",
            "release_baseline_revision": self.baseline,
            "release_baseline_version": SNAPSHOT,
            "release_version": PUBLIC,
            "release_tag": TAG,
        }
        if source_overrides:
            source.update(source_overrides)

        runtime = {
            "distribution_format": "protos-portable-posix-jvm-v1",
            "java_feature": "22",
            "java_vendor_contains": "GraalVM",
            "java_distribution": "GraalVM Community Edition for JDK 22",
            "truffle_runtime_version": "24.0.0",
            "optimizing_runtime": "HotSpotTruffleRuntime",
            "runtime_evidence": "docs/project/PERF002_TRUFFLE_COMPILABILITY.md",
            "unsupported_runtime_override": "PROTOS_ALLOW_UNSUPPORTED_RUNTIME=1",
        }
        if runtime_overrides:
            runtime.update(runtime_overrides)

        payload: dict[str, bytes] = {
            "SOURCE.txt": ("\n".join(f"{k}={v}" for k, v in source.items()) + "\n").encode(),
            "RUNTIME.txt": ("\n".join(f"{k}={v}" for k, v in runtime.items()) + "\n").encode(),
            "DEPENDENCIES.txt": b"fixture dependencies\n",
            "LICENSE.TXT": b"license\n",
            "README.md": b"# fixture\n",
            "bin/protos": b"#!/bin/sh\n",
            "lib/protos.jar": jar_bytes(jar_version),
            "lib/runtime/truffle-runtime-24.0.0.jar": b"runtime\n",
        }

        checksum_lines = [
            f"{sha256(data)}  {rel}"
            for rel, data in sorted(payload.items())
        ]
        payload["SHA256SUMS"] = ("\n".join(checksum_lines) + "\n").encode()

        if corrupt_internal_path is not None:
            payload[corrupt_internal_path] = payload[corrupt_internal_path] + b"corruption"

        self.archive.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(self.archive, "w", zipfile.ZIP_DEFLATED) as archive:
            for rel, data in sorted(payload.items()):
                archive.writestr(ROOT_NAME + "/" + rel, data)

        digest = hashlib.sha256(self.archive.read_bytes()).hexdigest()
        self.write_artifact_record(digest)

    def invoke(self, *, check: bool = True) -> subprocess.CompletedProcess[str]:
        return run(
            [
                sys.executable,
                "dist/verify_candidate_archive_identity.py",
                "--repository-root",
                str(self.repo),
                "--selection",
                str(self.selection),
                "--artifact-record",
                str(self.artifact_record),
                "--expect-verification-state",
                "false",
            ],
            cwd=self.repo,
            check=check,
        )

    def test_accepts_exact_persisted_archive_identity(self) -> None:
        result = self.invoke()
        self.assertIn("DIST001_E4C2_ARCHIVE_IDENTITY: PASS", result.stdout)

    def test_rejects_external_archive_digest_mismatch(self) -> None:
        self.write_artifact_record("0" * 64)
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("external archive SHA-256 mismatch", result.stderr)

    def test_rejects_source_candidate_identity_mismatch(self) -> None:
        self.build_archive(source_overrides={"source_revision": "f" * 40})
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("SOURCE.txt exact identity mismatch", result.stderr)

    def test_rejects_source_dirty_public_archive(self) -> None:
        self.build_archive(source_overrides={"source_dirty": "true"})
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("SOURCE.txt exact identity mismatch", result.stderr)

    def test_rejects_runtime_contract_mismatch(self) -> None:
        self.build_archive(runtime_overrides={"java_feature": "21"})
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("RUNTIME.txt exact contract mismatch", result.stderr)

    def test_rejects_jar_implementation_version_mismatch(self) -> None:
        self.build_archive(jar_version="0.2.235")
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Implementation-Version mismatch", result.stderr)

    def test_rejects_internal_checksum_value_mismatch(self) -> None:
        self.build_archive(corrupt_internal_path="README.md")
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("internal checksum value mismatch for README.md", result.stderr)

    def test_rejects_already_verified_artifact_record_when_false_expected(self) -> None:
        digest = hashlib.sha256(self.archive.read_bytes()).hexdigest()
        self.write_artifact_record(
            digest,
            archive_identity_independently_verified="true",
        )
        result = self.invoke(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("archive_identity_independently_verified mismatch", result.stderr)

    def test_accepts_verified_artifact_record_when_true_expected(self) -> None:
        digest = hashlib.sha256(self.archive.read_bytes()).hexdigest()
        self.write_artifact_record(
            digest,
            archive_identity_independently_verified="true",
        )
        result = run(
            [
                sys.executable,
                "dist/verify_candidate_archive_identity.py",
                "--repository-root",
                str(self.repo),
                "--selection",
                str(self.selection),
                "--artifact-record",
                str(self.artifact_record),
                "--expect-verification-state",
                "true",
            ],
            cwd=self.repo,
        )
        self.assertIn("DIST001_E4C2_ARCHIVE_IDENTITY: PASS", result.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
