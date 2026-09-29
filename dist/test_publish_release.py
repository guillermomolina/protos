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

from dataclasses import replace
import hashlib
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

sys.dont_write_bytecode = True

import publish_release


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


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
        env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
    )


class PublishReleaseTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="protos-dist005-d6-")
        self.root = Path(self.temp.name)
        self.candidate = self.root / "candidate"
        self.envelope = self.candidate / "target" / "release-envelope-1.2.3"
        self.distributions = self.candidate / "target" / "distributions"
        self.envelope.mkdir(parents=True)
        self.distributions.mkdir(parents=True)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write_authorization(
        self,
        *,
        candidate: str = "b" * 40,
        version: str = "1.2.3",
        tag: str = "v1.2.3",
        manifest_sha: str = "a" * 64,
        authorized: str = "true",
        extra: dict[str, str] | None = None,
    ) -> Path:
        values = {
            "publication_authorization_format":
                publish_release.AUTHORIZATION_FORMAT,
            "release_publication_authorized": authorized,
            "authorization_basis": "explicit-user-decision",
            "candidate_source_revision": candidate,
            "release_version": version,
            "release_tag": tag,
            "release_manifest_sha256": manifest_sha,
            "github_release_prerelease": "true",
            "github_release_draft": "false",
        }
        if extra:
            values.update(extra)

        path = self.root / "authorization.txt"
        path.write_text(
            "\n".join(f"{key}={value}" for key, value in values.items()) + "\n",
            encoding="utf-8",
        )
        return path

    def make_prepared(self) -> publish_release.PreparedRelease:
        native_bytes = b"native archive\n"
        portable_bytes = b"portable archive\n"

        native = self.distributions / "protos-1.2.3-native-linux-x86_64.zip"
        portable = self.distributions / "protos-1.2.3-posix-jvm.zip"
        native.write_bytes(native_bytes)
        portable.write_bytes(portable_bytes)

        native_sha = sha256(native_bytes)
        portable_sha = sha256(portable_bytes)

        native_checksum = self.envelope / (native.name + ".sha256")
        portable_checksum = self.envelope / (portable.name + ".sha256")
        native_checksum.write_text(
            native_sha + "  " + native.name + "\n",
            encoding="utf-8",
        )
        portable_checksum.write_text(
            portable_sha + "  " + portable.name + "\n",
            encoding="utf-8",
        )

        notes = self.envelope / publish_release.NOTES_NAME
        notes.write_text("# Protos 1.2.3\n", encoding="utf-8")

        manifest = self.envelope / publish_release.MANIFEST_NAME
        manifest.write_text("manifest\n", encoding="utf-8")

        authorization = publish_release.Authorization(
            candidate_source_revision="b" * 40,
            release_version="1.2.3",
            release_tag="v1.2.3",
            release_manifest_sha256=publish_release.sha256_file(manifest),
        )

        assets = (
            publish_release.Asset(
                kind="native",
                role="recommended-first-run",
                archive_name=native.name,
                checksum_name=native_checksum.name,
                archive_sha256=native_sha,
                checksum_sha256=publish_release.sha256_file(native_checksum),
                archive_path=native,
                checksum_path=native_checksum,
            ),
            publish_release.Asset(
                kind="portable-jvm",
                role="compatibility-fallback",
                archive_name=portable.name,
                checksum_name=portable_checksum.name,
                archive_sha256=portable_sha,
                checksum_sha256=publish_release.sha256_file(portable_checksum),
                archive_path=portable,
                checksum_path=portable_checksum,
            ),
        )

        return publish_release.PreparedRelease(
            authorization=authorization,
            candidate=self.candidate,
            envelope_dir=self.envelope,
            manifest_path=manifest,
            manifest={
                "release_notes_sha256":
                    publish_release.sha256_file(notes),
                "release_baseline_revision": "a" * 40,
            },
            notes_path=notes,
            assets=assets,
            title="Protos 1.2.3",
        )

    def write_complete_fixture(
        self,
        **manifest_overrides: str,
    ) -> tuple[Path, Path]:
        native = self.distributions / "protos-1.2.3-native-linux-x86_64.zip"
        portable = self.distributions / "protos-1.2.3-posix-jvm.zip"
        native.write_bytes(b"native\n")
        portable.write_bytes(b"portable\n")

        native_sha = publish_release.sha256_file(native)
        portable_sha = publish_release.sha256_file(portable)

        native_checksum = self.envelope / (native.name + ".sha256")
        portable_checksum = self.envelope / (portable.name + ".sha256")

        native_checksum.write_text(
            native_sha + "  " + native.name + "\n",
            encoding="utf-8",
        )
        portable_checksum.write_text(
            portable_sha + "  " + portable.name + "\n",
            encoding="utf-8",
        )

        notes = self.envelope / publish_release.NOTES_NAME
        notes.write_text("# Protos 1.2.3\n", encoding="utf-8")

        values = {
            "release_envelope_format":
                publish_release.MULTI_RELEASE_FORMAT,
            "distribution_model":
                publish_release.DISTRIBUTION_MODEL,
            "release_version": "1.2.3",
            "release_tag": "v1.2.3",
            "prerelease": "true",
            "source_repository":
                publish_release.CANONICAL_SOURCE_REPOSITORY,
            "source_revision": "b" * 40,
            "release_baseline_revision": "a" * 40,
            "release_baseline_version": "1.2.3-SNAPSHOT",
            "specification_revision": "0.1.999",
            "asset_count": "2",
            "asset.0.kind": "native",
            "asset.0.role": "recommended-first-run",
            "asset.0.archive": native.name,
            "asset.0.archive_sha256": native_sha,
            "asset.0.checksum": native_checksum.name,
            "asset.0.checksum_sha256":
                publish_release.sha256_file(native_checksum),
            "asset.1.kind": "portable-jvm",
            "asset.1.role": "compatibility-fallback",
            "asset.1.archive": portable.name,
            "asset.1.archive_sha256": portable_sha,
            "asset.1.checksum": portable_checksum.name,
            "asset.1.checksum_sha256":
                publish_release.sha256_file(portable_checksum),
            "release_notes": publish_release.NOTES_NAME,
            "release_notes_sha256":
                publish_release.sha256_file(notes),
        }
        values.update(manifest_overrides)

        manifest = self.envelope / publish_release.MANIFEST_NAME
        manifest.write_text(
            "\n".join(f"{key}={value}" for key, value in values.items()) + "\n",
            encoding="utf-8",
        )

        authorization = self.write_authorization(
            manifest_sha=publish_release.sha256_file(manifest),
        )
        return authorization, manifest

    def test_valid_exact_authorization_parses(self) -> None:
        path = self.write_authorization()
        authorization = publish_release.parse_authorization(path)
        self.assertEqual(authorization.candidate_source_revision, "b" * 40)
        self.assertEqual(authorization.release_version, "1.2.3")
        self.assertEqual(authorization.release_tag, "v1.2.3")

    def test_authorization_false_is_rejected(self) -> None:
        path = self.write_authorization(authorized="false")
        with self.assertRaisesRegex(SystemExit, "release_publication_authorized"):
            publish_release.parse_authorization(path)

    def test_authorization_unknown_key_is_rejected(self) -> None:
        path = self.write_authorization(extra={"unexpected": "value"})
        with self.assertRaisesRegex(SystemExit, "key set mismatch"):
            publish_release.parse_authorization(path)

    def test_authorization_release_tag_mismatch_is_rejected(self) -> None:
        path = self.write_authorization(tag="v9.9.9")
        with self.assertRaisesRegex(SystemExit, "release_tag"):
            publish_release.parse_authorization(path)

    def test_authorization_duplicate_key_is_rejected(self) -> None:
        path = self.write_authorization()
        path.write_text(
            path.read_text(encoding="utf-8")
            + "release_version=1.2.3\n",
            encoding="utf-8",
        )
        with self.assertRaisesRegex(SystemExit, "duplicate key"):
            publish_release.parse_authorization(path)

    def test_authorization_malformed_line_is_rejected(self) -> None:
        path = self.write_authorization()
        path.write_text(
            path.read_text(encoding="utf-8")
            + "this-is-not-a-key-value-line\n",
            encoding="utf-8",
        )
        with self.assertRaisesRegex(SystemExit, "malformed line"):
            publish_release.parse_authorization(path)

    def test_manifest_digest_mismatch_is_rejected(self) -> None:
        authorization, _manifest = self.write_complete_fixture()
        authorization.write_text(
            authorization.read_text(encoding="utf-8").replace(
                "release_manifest_sha256="
                + publish_release.sha256_file(
                    self.envelope / publish_release.MANIFEST_NAME
                ),
                "release_manifest_sha256=" + "f" * 64,
            ),
            encoding="utf-8",
        )

        with mock.patch.object(
            publish_release,
            "require_candidate_state",
        ), mock.patch.object(
            publish_release,
            "require_canonical_remote",
        ), mock.patch.object(
            publish_release,
            "verify_release_metadata",
        ):
            with self.assertRaisesRegex(SystemExit, "manifest digest mismatch"):
                publish_release.prepare_publication(
                    authorization_path=authorization,
                    candidate=self.candidate,
                    envelope_dir=self.envelope,
                )

    def test_manifest_release_identity_mismatch_is_rejected(self) -> None:
        authorization, _manifest = self.write_complete_fixture(
            release_tag="v1.2.4",
        )
        with mock.patch.object(
            publish_release,
            "require_candidate_state",
        ), mock.patch.object(
            publish_release,
            "require_canonical_remote",
        ), mock.patch.object(
            publish_release,
            "verify_release_metadata",
        ):
            with self.assertRaisesRegex(SystemExit, "release_tag mismatch"):
                publish_release.prepare_publication(
                    authorization_path=authorization,
                    candidate=self.candidate,
                    envelope_dir=self.envelope,
                )

    def init_git_candidate(self) -> str:
        run(["git", "init", "-q"], cwd=self.candidate)
        run(
            ["git", "config", "user.email", "fixture@example.invalid"],
            cwd=self.candidate,
        )
        run(
            ["git", "config", "user.name", "Fixture"],
            cwd=self.candidate,
        )
        (self.candidate / "pom.xml").write_text(
            "<project><version>1.2.3</version></project>\n",
            encoding="utf-8",
        )
        run(["git", "add", "pom.xml"], cwd=self.candidate)
        run(["git", "commit", "-q", "-m", "candidate"], cwd=self.candidate)
        head = run(["git", "rev-parse", "HEAD"], cwd=self.candidate).stdout.strip()
        run(["git", "switch", "--detach", "-q", head], cwd=self.candidate)
        return head

    def test_candidate_sha_mismatch_is_rejected(self) -> None:
        head = self.init_git_candidate()
        authorization = publish_release.Authorization(
            candidate_source_revision=("c" * 40 if head != "c" * 40 else "d" * 40),
            release_version="1.2.3",
            release_tag="v1.2.3",
            release_manifest_sha256="a" * 64,
        )
        with mock.patch.object(
            publish_release,
            "require_release_only_lineage",
        ):
            with self.assertRaisesRegex(SystemExit, "candidate HEAD mismatch"):
                publish_release.require_candidate_state(
                    self.candidate,
                    authorization,
                    {"release_baseline_revision": "a" * 40},
                )

    def test_dirty_candidate_is_rejected(self) -> None:
        head = self.init_git_candidate()
        (self.candidate / "dirty.txt").write_text("dirty\n", encoding="utf-8")
        authorization = publish_release.Authorization(
            candidate_source_revision=head,
            release_version="1.2.3",
            release_tag="v1.2.3",
            release_manifest_sha256="a" * 64,
        )
        with mock.patch.object(
            publish_release,
            "require_release_only_lineage",
        ):
            with self.assertRaisesRegex(SystemExit, "must be clean"):
                publish_release.require_candidate_state(
                    self.candidate,
                    authorization,
                    {"release_baseline_revision": "a" * 40},
                )

    def test_attached_candidate_is_rejected(self) -> None:
        head = self.init_git_candidate()
        run(["git", "switch", "-q", "-c", "attached"], cwd=self.candidate)
        authorization = publish_release.Authorization(
            candidate_source_revision=head,
            release_version="1.2.3",
            release_tag="v1.2.3",
            release_manifest_sha256="a" * 64,
        )
        with mock.patch.object(
            publish_release,
            "require_release_only_lineage",
        ):
            with self.assertRaisesRegex(SystemExit, "attached to branch"):
                publish_release.require_candidate_state(
                    self.candidate,
                    authorization,
                    {"release_baseline_revision": "a" * 40},
                )

    def release_object(
        self,
        prepared: publish_release.PreparedRelease,
        asset_names: tuple[str, ...] = (),
    ) -> dict[str, object]:
        return {
            "tag_name": prepared.authorization.release_tag,
            "name": prepared.title,
            "prerelease": True,
            "draft": False,
            "body": prepared.notes_path.read_text(encoding="utf-8"),
            "assets": [
                {"name": name, "id": index + 1}
                for index, name in enumerate(asset_names)
            ],
        }

    def test_conflicting_release_identity_is_rejected(self) -> None:
        prepared = self.make_prepared()
        conflicts = {
            "tag_name": "v9.9.9",
            "name": "Wrong title",
            "prerelease": False,
            "draft": True,
            "body": "Wrong body\n",
        }
        for field, wrong in conflicts.items():
            with self.subTest(field=field):
                release = self.release_object(prepared)
                release[field] = wrong
                with self.assertRaisesRegex(SystemExit, field + " mismatch"):
                    publish_release.inspect_release_identity(prepared, release)

    def test_unexpected_release_asset_is_rejected(self) -> None:
        prepared = self.make_prepared()
        release = self.release_object(prepared)
        release["assets"] = [{"name": "unexpected.bin", "id": 1}]
        with self.assertRaisesRegex(SystemExit, "unexpected assets"):
            publish_release.inspect_release_identity(prepared, release)

    def test_mismatched_existing_asset_is_rejected(self) -> None:
        prepared = self.make_prepared()
        first = prepared.publication_paths()[0]
        release = self.release_object(prepared, (first.name,))
        with mock.patch.object(
            publish_release,
            "fetch_asset_bytes",
            return_value=b"wrong bytes",
        ):
            with self.assertRaisesRegex(SystemExit, "published asset digest mismatch"):
                publish_release.inspect_release_identity(prepared, release)

    def test_complete_exact_existing_release_is_idempotent(self) -> None:
        prepared = self.make_prepared()
        paths = prepared.publication_paths()
        release = self.release_object(
            prepared,
            tuple(path.name for path in paths),
        )
        bytes_by_id = {
            index + 1: path.read_bytes()
            for index, path in enumerate(paths)
        }

        with mock.patch.object(
            publish_release,
            "fetch_asset_bytes",
            side_effect=lambda _prepared, asset_id: bytes_by_id[asset_id],
        ):
            missing = publish_release.inspect_release_identity(prepared, release)

        self.assertEqual(missing, ())

    def test_partial_exact_release_returns_only_missing_assets(self) -> None:
        prepared = self.make_prepared()
        paths = prepared.publication_paths()
        existing = paths[:2]
        release = self.release_object(
            prepared,
            tuple(path.name for path in existing),
        )
        bytes_by_id = {
            index + 1: path.read_bytes()
            for index, path in enumerate(existing)
        }

        with mock.patch.object(
            publish_release,
            "fetch_asset_bytes",
            side_effect=lambda _prepared, asset_id: bytes_by_id[asset_id],
        ):
            missing = publish_release.inspect_release_identity(prepared, release)

        self.assertEqual(
            missing,
            tuple(path.name for path in paths[2:]),
        )

    def test_local_tag_command_is_lightweight_and_exact(self) -> None:
        prepared = self.make_prepared()
        calls: list[list[str]] = []

        def fake_run(
            _cwd: Path,
            args: list[str],
            *,
            check: bool = True,
        ) -> subprocess.CompletedProcess[str]:
            calls.append(args)
            return subprocess.CompletedProcess(args, 0, "", "")

        with mock.patch.object(
            publish_release,
            "run_text",
            side_effect=fake_run,
        ), mock.patch.object(
            publish_release,
            "local_tag_exists_exact",
            return_value=True,
        ):
            publish_release.create_local_tag(prepared)

        self.assertEqual(
            calls[0],
            [
                "git",
                "tag",
                "v1.2.3",
                "b" * 40,
            ],
        )
        self.assertNotIn("--force", calls[0])

    def test_release_creation_metadata_is_exact(self) -> None:
        prepared = self.make_prepared()
        calls: list[list[str]] = []

        def fake_run(
            _cwd: Path,
            args: list[str],
            *,
            check: bool = True,
        ) -> subprocess.CompletedProcess[str]:
            calls.append(args)
            return subprocess.CompletedProcess(args, 0, "", "")

        with mock.patch.object(
            publish_release,
            "run_text",
            side_effect=fake_run,
        ):
            publish_release.create_release(prepared)

        command = calls[0]
        self.assertEqual(command[:4], ["gh", "release", "create", "v1.2.3"])
        self.assertIn("--verify-tag", command)
        self.assertEqual(command[command.index("--title") + 1], "Protos 1.2.3")
        self.assertEqual(
            command[command.index("--notes-file") + 1],
            str(prepared.notes_path),
        )
        self.assertIn("--prerelease", command)
        self.assertNotIn("--draft", command)

    def test_exact_five_asset_upload_excludes_release_notes(self) -> None:
        prepared = self.make_prepared()
        calls: list[list[str]] = []

        def fake_run(
            _cwd: Path,
            args: list[str],
            *,
            check: bool = True,
        ) -> subprocess.CompletedProcess[str]:
            calls.append(args)
            return subprocess.CompletedProcess(args, 0, "", "")

        names = tuple(path.name for path in prepared.publication_paths())
        self.assertEqual(len(names), 5)
        self.assertNotIn(publish_release.NOTES_NAME, names)

        with mock.patch.object(
            publish_release,
            "run_text",
            side_effect=fake_run,
        ):
            publish_release.upload_missing_assets(prepared, names)

        command = calls[0]
        self.assertNotIn("--clobber", command)
        for path in prepared.publication_paths():
            self.assertIn(str(path), command)
        self.assertNotIn(str(prepared.notes_path), command)

    def test_normal_publication_mutation_order(self) -> None:
        prepared = self.make_prepared()
        order: list[str] = []

        state = publish_release.PublicationState(
            local_tag=False,
            remote_tag=False,
            release_exists=False,
            missing_assets=tuple(
                path.name for path in prepared.publication_paths()
            ),
        )

        with mock.patch.object(
            publish_release,
            "prepare_publication",
            side_effect=lambda **_kwargs: order.append("verify") or prepared,
        ), mock.patch.object(
            publish_release,
            "inspect_public_state",
            return_value=state,
        ), mock.patch.object(
            publish_release,
            "require_local_identity",
            side_effect=lambda _prepared: order.append("identity"),
        ), mock.patch.object(
            publish_release,
            "create_local_tag",
            side_effect=lambda _prepared: order.append("local-tag"),
        ), mock.patch.object(
            publish_release,
            "push_remote_tag",
            side_effect=lambda _prepared: order.append("push-tag"),
        ), mock.patch.object(
            publish_release,
            "create_release",
            side_effect=lambda _prepared: order.append("release"),
        ), mock.patch.object(
            publish_release,
            "upload_missing_assets",
            side_effect=lambda _prepared, _missing: order.append("assets"),
        ), mock.patch.object(
            publish_release,
            "verify_published",
            side_effect=lambda _prepared: order.append("post-verify"),
        ), mock.patch.object(
            publish_release,
            "emit_pass",
            side_effect=lambda _prepared: order.append("pass"),
        ):
            publish_release.publish(
                authorization_path=self.root / "authorization",
                candidate=self.candidate,
                envelope_dir=self.envelope,
            )

        self.assertEqual(
            order,
            [
                "verify",
                "identity",
                "local-tag",
                "push-tag",
                "release",
                "assets",
                "post-verify",
                "pass",
            ],
        )

    def test_exact_remote_tag_without_release_resumes_without_local_tag(self) -> None:
        prepared = self.make_prepared()
        order: list[str] = []

        state = publish_release.PublicationState(
            local_tag=False,
            remote_tag=True,
            release_exists=False,
            missing_assets=tuple(
                path.name for path in prepared.publication_paths()
            ),
        )

        with mock.patch.object(
            publish_release,
            "prepare_publication",
            return_value=prepared,
        ), mock.patch.object(
            publish_release,
            "inspect_public_state",
            return_value=state,
        ), mock.patch.object(
            publish_release,
            "require_local_identity",
        ), mock.patch.object(
            publish_release,
            "create_local_tag",
            side_effect=lambda _prepared: order.append("local-tag"),
        ), mock.patch.object(
            publish_release,
            "push_remote_tag",
            side_effect=lambda _prepared: order.append("push-tag"),
        ), mock.patch.object(
            publish_release,
            "create_release",
            side_effect=lambda _prepared: order.append("release"),
        ), mock.patch.object(
            publish_release,
            "upload_missing_assets",
            side_effect=lambda _prepared, _missing: order.append("assets"),
        ), mock.patch.object(
            publish_release,
            "verify_published",
            side_effect=lambda _prepared: order.append("verify"),
        ), mock.patch.object(
            publish_release,
            "emit_pass",
        ):
            publish_release.publish(
                authorization_path=self.root / "authorization",
                candidate=self.candidate,
                envelope_dir=self.envelope,
            )

        self.assertEqual(order, ["release", "assets", "verify"])

    def test_complete_existing_release_performs_no_mutation(self) -> None:
        prepared = self.make_prepared()
        state = publish_release.PublicationState(
            local_tag=False,
            remote_tag=True,
            release_exists=True,
            missing_assets=(),
        )

        with mock.patch.object(
            publish_release,
            "prepare_publication",
            return_value=prepared,
        ), mock.patch.object(
            publish_release,
            "inspect_public_state",
            return_value=state,
        ), mock.patch.object(
            publish_release,
            "require_local_identity",
        ) as identity, mock.patch.object(
            publish_release,
            "create_local_tag",
        ) as local_tag, mock.patch.object(
            publish_release,
            "push_remote_tag",
        ) as push_tag, mock.patch.object(
            publish_release,
            "create_release",
        ) as create_release, mock.patch.object(
            publish_release,
            "upload_missing_assets",
        ) as upload, mock.patch.object(
            publish_release,
            "verify_published",
        ) as verify, mock.patch.object(
            publish_release,
            "emit_pass",
        ):
            publish_release.publish(
                authorization_path=self.root / "authorization",
                candidate=self.candidate,
                envelope_dir=self.envelope,
            )

        identity.assert_not_called()
        local_tag.assert_not_called()
        push_tag.assert_not_called()
        create_release.assert_not_called()
        upload.assert_not_called()
        verify.assert_called_once_with(prepared)

    def test_partial_exact_release_uploads_only_missing(self) -> None:
        prepared = self.make_prepared()
        missing = tuple(
            path.name
            for path in prepared.publication_paths()[2:]
        )
        state = publish_release.PublicationState(
            local_tag=False,
            remote_tag=True,
            release_exists=True,
            missing_assets=missing,
        )

        with mock.patch.object(
            publish_release,
            "prepare_publication",
            return_value=prepared,
        ), mock.patch.object(
            publish_release,
            "inspect_public_state",
            return_value=state,
        ), mock.patch.object(
            publish_release,
            "require_local_identity",
        ), mock.patch.object(
            publish_release,
            "create_local_tag",
        ) as local_tag, mock.patch.object(
            publish_release,
            "push_remote_tag",
        ) as push_tag, mock.patch.object(
            publish_release,
            "create_release",
        ) as create_release, mock.patch.object(
            publish_release,
            "upload_missing_assets",
        ) as upload, mock.patch.object(
            publish_release,
            "verify_published",
        ), mock.patch.object(
            publish_release,
            "emit_pass",
        ):
            publish_release.publish(
                authorization_path=self.root / "authorization",
                candidate=self.candidate,
                envelope_dir=self.envelope,
            )

        local_tag.assert_not_called()
        push_tag.assert_not_called()
        create_release.assert_not_called()
        upload.assert_called_once_with(prepared, missing)

    def test_post_publication_verification_failure_prevents_pass(self) -> None:
        prepared = self.make_prepared()
        state = publish_release.PublicationState(
            local_tag=True,
            remote_tag=True,
            release_exists=True,
            missing_assets=(),
        )

        with mock.patch.object(
            publish_release,
            "prepare_publication",
            return_value=prepared,
        ), mock.patch.object(
            publish_release,
            "inspect_public_state",
            return_value=state,
        ), mock.patch.object(
            publish_release,
            "verify_published",
            side_effect=SystemExit("verification failed"),
        ), mock.patch.object(
            publish_release,
            "emit_pass",
        ) as emit:
            with self.assertRaises(SystemExit):
                publish_release.publish(
                    authorization_path=self.root / "authorization",
                    candidate=self.candidate,
                    envelope_dir=self.envelope,
                )

        emit.assert_not_called()

    def test_real_lightweight_local_tag_is_reused_exactly(self) -> None:
        prepared = self.make_prepared()

        run(["git", "init", "-q"], cwd=self.candidate)
        run(
            ["git", "config", "user.email", "fixture@example.invalid"],
            cwd=self.candidate,
        )
        run(
            ["git", "config", "user.name", "Fixture"],
            cwd=self.candidate,
        )
        run(["git", "add", "target"], cwd=self.candidate)
        run(["git", "commit", "-q", "-m", "candidate"], cwd=self.candidate)
        head = run(
            ["git", "rev-parse", "HEAD"],
            cwd=self.candidate,
        ).stdout.strip()
        run(
            ["git", "tag", prepared.authorization.release_tag, head],
            cwd=self.candidate,
        )

        prepared = replace(
            prepared,
            authorization=replace(
                prepared.authorization,
                candidate_source_revision=head,
            ),
        )
        self.assertTrue(publish_release.local_tag_exists_exact(prepared))

    def test_real_annotated_local_tag_is_rejected(self) -> None:
        prepared = self.make_prepared()

        run(["git", "init", "-q"], cwd=self.candidate)
        run(
            ["git", "config", "user.email", "fixture@example.invalid"],
            cwd=self.candidate,
        )
        run(
            ["git", "config", "user.name", "Fixture"],
            cwd=self.candidate,
        )
        run(["git", "add", "target"], cwd=self.candidate)
        run(["git", "commit", "-q", "-m", "candidate"], cwd=self.candidate)
        head = run(
            ["git", "rev-parse", "HEAD"],
            cwd=self.candidate,
        ).stdout.strip()
        run(
            [
                "git",
                "tag",
                "-a",
                prepared.authorization.release_tag,
                "-m",
                "annotated",
                head,
            ],
            cwd=self.candidate,
        )

        prepared = replace(
            prepared,
            authorization=replace(
                prepared.authorization,
                candidate_source_revision=head,
            ),
        )
        with self.assertRaisesRegex(SystemExit, "not lightweight"):
            publish_release.local_tag_exists_exact(prepared)

    def test_conflicting_local_tag_is_rejected(self) -> None:
        prepared = self.make_prepared()

        def fake_run(
            _cwd: Path,
            args: list[str],
            *,
            check: bool = True,
        ) -> subprocess.CompletedProcess[str]:
            if args[:3] == ["git", "show-ref", "--verify"]:
                return subprocess.CompletedProcess(args, 0, "c" * 40 + "\n", "")
            if args[:3] == ["git", "cat-file", "-t"]:
                return subprocess.CompletedProcess(args, 0, "commit\n", "")
            self.fail("unexpected command " + repr(args))

        with mock.patch.object(
            publish_release,
            "run_text",
            side_effect=fake_run,
        ):
            with self.assertRaisesRegex(SystemExit, "conflicting commit"):
                publish_release.local_tag_exists_exact(prepared)

    def test_conflicting_remote_tag_is_rejected(self) -> None:
        prepared = self.make_prepared()
        ref = "refs/tags/v1.2.3"

        with mock.patch.object(
            publish_release,
            "run_text",
            return_value=subprocess.CompletedProcess(
                ["git"],
                0,
                "c" * 40 + "\t" + ref + "\n",
                "",
            ),
        ):
            with self.assertRaisesRegex(SystemExit, "conflicting commit"):
                publish_release.remote_tag_exists_exact(prepared)

    def test_exact_remote_tag_is_resumable(self) -> None:
        prepared = self.make_prepared()
        ref = "refs/tags/v1.2.3"

        with mock.patch.object(
            publish_release,
            "run_text",
            return_value=subprocess.CompletedProcess(
                ["git"],
                0,
                "b" * 40 + "\t" + ref + "\n",
                "",
            ),
        ):
            self.assertTrue(
                publish_release.remote_tag_exists_exact(prepared)
            )

    def test_failure_after_public_tag_reports_partial_state(self) -> None:
        prepared = self.make_prepared()
        state = publish_release.PublicationState(
            local_tag=False,
            remote_tag=True,
            release_exists=False,
            missing_assets=tuple(
                path.name for path in prepared.publication_paths()
            ),
        )

        with mock.patch.object(
            publish_release,
            "prepare_publication",
            return_value=prepared,
        ), mock.patch.object(
            publish_release,
            "inspect_public_state",
            return_value=state,
        ), mock.patch.object(
            publish_release,
            "require_local_identity",
        ), mock.patch.object(
            publish_release,
            "create_release",
            side_effect=SystemExit("simulated release creation failure"),
        ), mock.patch.object(
            publish_release,
            "report_partial_state",
        ) as report, mock.patch.object(
            publish_release,
            "emit_pass",
        ) as emit:
            with self.assertRaisesRegex(SystemExit, "simulated"):
                publish_release.publish(
                    authorization_path=self.root / "authorization",
                    candidate=self.candidate,
                    envelope_dir=self.envelope,
                )

        report.assert_called_once_with(prepared)
        emit.assert_not_called()

    def test_verified_partial_state_exposes_safe_resume(self) -> None:
        prepared = self.make_prepared()
        release = self.release_object(prepared)
        output = io.StringIO()

        with mock.patch.object(
            publish_release,
            "remote_tag_exists_exact",
            return_value=True,
        ), mock.patch.object(
            publish_release,
            "fetch_release",
            return_value=release,
        ), mock.patch.object(
            publish_release,
            "inspect_release_identity",
            return_value=tuple(
                path.name for path in prepared.publication_paths()
            ),
        ), mock.patch("sys.stdout", output):
            publish_release.report_partial_state(prepared)

        rendered = output.getvalue()
        self.assertIn("PARTIAL_REMOTE_TAG=EXACT", rendered)
        self.assertIn("PARTIAL_GITHUB_RELEASE=EXACT", rendered)
        self.assertIn("PARTIAL_STATE_VERIFIED=PASS", rendered)
        self.assertIn(
            "SAFE_RESUME=RERUN_SAME_AUTHORIZED_PUBLICATION",
            rendered,
        )

    def test_static_publication_safety_guards(self) -> None:
        source = Path(publish_release.__file__).read_text(encoding="utf-8")
        forbidden = [
            '"--force"',
            "'--force'",
            '"--clobber"',
            "'--clobber'",
            '"release", "delete"',
            "'release', 'delete'",
            '"tag", "-d"',
            "'tag', '-d'",
        ]
        for fragment in forbidden:
            self.assertNotIn(fragment, source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
