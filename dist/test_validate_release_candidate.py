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

sys.dont_write_bytecode = True
import unittest
import zipfile


POM = """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.guillermomolina</groupId>
  <artifactId>protos</artifactId>
  <version>{version}</version>
</project>
"""


def run(
    args: list[str],
    *,
    cwd: Path,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=cwd,
        check=check,
        text=True,
        capture_output=True,
    )


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
) -> None:
    root_name = f"protos-{version}"
    source = "\n".join(
        [
            f"implementation_version={version}",
            f"source_revision={candidate}",
            "source_dirty=false",
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
    entries["SHA256SUMS"] = (
        "\n".join(f"{sha256(data)}  {name}" for name, data in sorted(entries.items()))
        + "\n"
    ).encode("utf-8")

    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            archive.writestr(f"{root_name}/{name}", data)


class CandidateGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="protos-e3c3-")
        top = Path(self.temp.name)
        self.repo = top / "repo"
        self.remote = top / "origin.git"
        self.artifacts = top / "artifacts"
        self.envelope = top / "envelope"
        self.repo.mkdir()
        self.artifacts.mkdir()

        source_root = Path(__file__).resolve().parents[1]
        (self.repo / "dist").mkdir()
        (self.repo / "spec").mkdir()

        for name in [
            "prepare_release_metadata.py",
            "verify_release_metadata.py",
            "validate_release_candidate.py",
        ]:
            shutil.copy2(source_root / "dist" / name, self.repo / "dist" / name)

        (self.repo / "dist/validate_portable.sh").write_text(
            """#!/bin/sh
set -eu
printf '%s\n' "$@" > .b5-args
case " $* " in
  *" --public-prerelease "*) ;;
  *) exit 31 ;;
esac
case " $* " in
  *" --require-clean-source "*) ;;
  *) exit 32 ;;
esac
echo "FIXTURE_RELEASE_B5: PASS"
""",
            encoding="utf-8",
        )
        (self.repo / "dist/validate_portable.sh").chmod(0o755)

        git(self.repo, "init", "-q")
        git(self.repo, "config", "user.email", "fixture@example.invalid")
        git(self.repo, "config", "user.name", "Fixture")
        run(["git", "init", "--bare", "-q", str(self.remote)], cwd=top)
        git(self.repo, "remote", "add", "origin", str(self.remote))

        (self.repo / "pom.xml").write_text(
            POM.format(version="0.2.230-SNAPSHOT"),
            encoding="utf-8",
        )
        (self.repo / "spec/PROTOS_SPEC_CHANGELOG.md").write_text(
            "# Protos Language Specification Changelog\n\n"
            "## [0.1.382] - 2026-09-07\n\nfixture\n",
            encoding="utf-8",
        )
        git(self.repo, "add", ".")
        git(self.repo, "commit", "-q", "-m", "baseline")
        self.baseline = git(self.repo, "rev-parse", "HEAD")

        (self.repo / "pom.xml").write_text(
            POM.format(version="0.2.230"),
            encoding="utf-8",
        )
        git(self.repo, "add", "pom.xml")
        git(self.repo, "commit", "-q", "-m", "candidate")
        self.candidate = git(self.repo, "rev-parse", "HEAD")
        git(self.repo, "push", "-q", "-u", "origin", "HEAD:main")

        self.archive = self.artifacts / "protos-0.2.230-posix-jvm.zip"
        write_candidate_archive(
            self.archive,
            version="0.2.230",
            candidate=self.candidate,
            baseline=self.baseline,
        )

        sys.path.insert(0, str(self.repo / "dist"))
        try:
            import prepare_release_metadata as prepare_module
        finally:
            sys.path.pop(0)

        class Args:
            archive = str(self.archive)
            spec_revision = "0.1.382"
            capability = ["Portable execution is validated outside the checkout."]
            limitation = ["GraalVM Community JDK 22 remains externally required."]
            output_dir = str(self.envelope)

        prepare_module.prepare(Args())
        self.audit = top / "RELEASE_CANDIDATE_AUDIT.txt"
        self.write_audit()

    def tearDown(self) -> None:
        for name in [
            "prepare_release_metadata",
            "verify_release_metadata",
            "validate_release_candidate",
        ]:
            sys.modules.pop(name, None)
        self.temp.cleanup()

    def write_audit(self, **overrides: str) -> None:
        values = {
            "release_candidate_audit_format": "protos-release-candidate-audit-v1",
            "candidate_selection_authorized": "true",
            "selection_authorization_basis": "explicit-user-decision",
            "release_publication_authorized": "false",
            "source_revision": self.candidate,
            "release_baseline_revision": self.baseline,
            "release_version": "0.2.230",
            "release_tag": "v0.2.230",
            "specification_revision": "0.1.382",
            "capabilities_review": "PASS",
            "limitations_review": "PASS",
            "known_blockers_review": "PASS",
        }
        values.update(overrides)
        self.audit.write_text(
            "\n".join(f"{key}={value}" for key, value in values.items()) + "\n",
            encoding="utf-8",
        )

    def refresh_candidate_materials(self, *, spec_revision: str = "0.1.382") -> None:
        self.candidate = git(self.repo, "rev-parse", "HEAD")
        if self.archive.exists():
            self.archive.unlink()
        write_candidate_archive(
            self.archive,
            version="0.2.230",
            candidate=self.candidate,
            baseline=self.baseline,
        )

        if self.envelope.exists():
            shutil.rmtree(self.envelope)

        sys.path.insert(0, str(self.repo / "dist"))
        try:
            import prepare_release_metadata as prepare_module
        finally:
            sys.path.pop(0)

        class Args:
            archive = str(self.archive)
            capability = ["Portable execution is validated outside the checkout."]
            limitation = ["GraalVM Community JDK 22 remains externally required."]
            output_dir = str(self.envelope)

        args = Args()
        args.spec_revision = spec_revision
        prepare_module.prepare(args)
        self.write_audit(
            source_revision=self.candidate,
            specification_revision="0.1.382",
        )

    def validate(self, *, check: bool = True) -> subprocess.CompletedProcess[str]:
        return run(
            [
                sys.executable,
                "dist/validate_release_candidate.py",
                "--archive",
                str(self.archive),
                "--envelope-dir",
                str(self.envelope),
                "--candidate-audit",
                str(self.audit),
            ],
            cwd=self.repo,
            check=check,
        )

    def test_accepts_exact_candidate_and_composes_b5(self) -> None:
        result = self.validate()
        self.assertIn("DIST001_E3C3_CANDIDATE_GATE: PASS", result.stdout)
        self.assertIn("RELEASE_PUBLICATION_AUTHORIZED: NO", result.stdout)
        b5_args = (self.repo / ".b5-args").read_text(encoding="utf-8")
        self.assertIn("--public-prerelease", b5_args)
        self.assertIn("--release-baseline", b5_args)
        self.assertIn(self.baseline, b5_args)

    def test_rejects_missing_explicit_candidate_selection(self) -> None:
        self.write_audit(candidate_selection_authorized="false")
        result = self.validate(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("candidate_selection_authorized mismatch", result.stderr)

    def test_rejects_release_publication_authorization(self) -> None:
        self.write_audit(release_publication_authorized="true")
        result = self.validate(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("release_publication_authorized mismatch", result.stderr)

    def test_rejects_failed_known_blocker_review(self) -> None:
        self.write_audit(known_blockers_review="FAIL")
        result = self.validate(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("known_blockers_review mismatch", result.stderr)

    def test_rejects_stale_specification_revision(self) -> None:
        self.write_audit(specification_revision="0.1.381")
        result = self.validate(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("specification_revision mismatch", result.stderr)

    def test_rejects_candidate_revision_mismatch(self) -> None:
        self.write_audit(source_revision="c" * 40)
        result = self.validate(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("source_revision mismatch", result.stderr)

    def test_rejects_stale_envelope_specification_revision(self) -> None:
        self.refresh_candidate_materials(spec_revision="0.1.381")
        result = self.validate(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("release envelope specification_revision mismatch", result.stderr)

    def test_rejects_non_release_candidate_lineage(self) -> None:
        (self.repo / "unexpected.txt").write_text("not release preparation\n", encoding="utf-8")
        git(self.repo, "add", "unexpected.txt")
        git(self.repo, "commit", "-q", "-m", "unrelated candidate mutation")
        self.refresh_candidate_materials()
        result = self.validate(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("candidate lineage contains non-release-preparation paths", result.stderr)

    def test_rejects_existing_local_tag(self) -> None:
        git(self.repo, "tag", "v0.2.230")
        result = self.validate(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("release tag already exists locally", result.stderr)

    def test_rejects_existing_remote_tag(self) -> None:
        git(self.repo, "tag", "v0.2.230")
        git(self.repo, "push", "-q", "origin", "refs/tags/v0.2.230")
        git(self.repo, "tag", "-d", "v0.2.230")
        result = self.validate(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("release tag already exists on origin", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
