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

import base64
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
import urllib.parse

DIST = Path(__file__).resolve().parent
if str(DIST) not in sys.path:
    sys.path.insert(0, str(DIST))

import build_artifact_set
from build_artifact_set import MANIFEST_NAME, write_envelope
import publish_d064_oci as pub
from publish_d064_oci import (
    AnonymousReadDenied,
    Credentials,
    IdentityConflict,
    PublicationError,
    Registry,
    RegistryUncertain,
    Response,
)
from test_build_artifact_set import D064, OTHER_REVISION, REVISION, VERSION, populate

TOKEN = "ghp_SECRET_publisher_token"
PUSH_BEARER = "bearer-push"
ANON_BEARER = "bearer-anon"
PREFIX = f"/v2/{pub.PACKAGE}/"


def sha(data: bytes) -> str:
    return "sha256:" + hashlib.sha256(data).hexdigest()


class FakeGhcr:
    """In-memory GHCR: Bearer challenge, blobs, manifests, tags, visibility."""

    def __init__(self, *, public: bool = True) -> None:
        self.public = public
        self.blobs: dict[str, bytes] = {}
        self.manifests: dict[str, bytes] = {}
        self.tags: dict[str, str] = {}
        self.calls: list[tuple[str, str, dict[str, str]]] = []
        self.uploads = 0
        self.fail_manifest_put_uncertain = False
        self.persist_uncertain_put = True
        self.tag_after_push: str | None = None
        self.blob_override: bytes | None = None
        self.digest_header_override: str | None = None

    def request(self, method, url, headers, body):  # type: ignore[no-untyped-def]
        self.calls.append((method, url, dict(headers)))
        parts = urllib.parse.urlsplit(url)
        assert parts.netloc == "ghcr.io", url
        if parts.path == "/token":
            return self._token(parts, headers)
        auth = headers.get("Authorization")
        if auth not in ("Bearer " + PUSH_BEARER, "Bearer " + ANON_BEARER):
            return Response(401, {"www-authenticate": (
                'Bearer realm="https://ghcr.io/token",service="ghcr.io",'
                f'scope="repository:{pub.PACKAGE}:pull"')}, b"")
        anonymous = auth == "Bearer " + ANON_BEARER
        if anonymous and not self.public:
            return Response(403, {}, b"denied")
        path = parts.path.removeprefix(PREFIX)
        if path == "blobs/uploads/" and method == "POST":
            return Response(202, {"location": f"{PREFIX}blobs/uploads/u1?s=1"}, b"")
        if path.startswith("blobs/uploads/") and method == "PUT":
            digest = urllib.parse.parse_qs(parts.query)["digest"][0]
            assert sha(body) == digest
            self.blobs[digest] = body
            self.uploads += 1
            return Response(201, {}, b"")
        kind, _, ref = path.partition("/")
        if kind == "blobs":
            if ref not in self.blobs:
                return Response(404, {}, b"")
            data = self.blob_override if self.blob_override is not None and \
                method == "GET" and ref != sha(b"{}") else self.blobs[ref]
            return Response(200, {}, data if method == "GET" else b"")
        if kind == "manifests" and method == "PUT":
            assert not anonymous
            digest = sha(body)
            if self.fail_manifest_put_uncertain:
                if self.persist_uncertain_put:
                    self.manifests[digest] = body
                    self.tags[ref] = digest
                raise RegistryUncertain("PUT manifest: connection reset")
            self.manifests[digest] = body
            self.tags[ref] = self.tag_after_push or digest
            return Response(201, {"docker-content-digest":
                                  self.digest_header_override or digest}, b"")
        if kind == "manifests":
            digest = self.tags.get(ref, ref)
            if digest not in self.manifests:
                return Response(404, {}, b"")
            return Response(200, {"docker-content-digest": digest},
                            self.manifests[digest])
        raise AssertionError((method, url))

    def _token(self, parts, headers):  # type: ignore[no-untyped-def]
        scope = urllib.parse.parse_qs(parts.query)["scope"][0]
        auth = headers.get("Authorization")
        if auth is None:
            assert scope.endswith(":pull"), scope
            return Response(200, {}, json.dumps({"token": ANON_BEARER}).encode())
        expected = "Basic " + base64.b64encode(f"bot:{TOKEN}".encode()).decode()
        assert auth == expected and scope.endswith(":pull,push"), scope
        return Response(200, {}, json.dumps({"token": PUSH_BEARER}).encode())

    def seed(self, manifest: bytes, blobs: list[bytes], tag: str) -> str:
        for blob in blobs:
            self.blobs[sha(blob)] = blob
        self.manifests[sha(manifest)] = manifest
        self.tags[tag] = sha(manifest)
        return sha(manifest)

    def methods(self) -> set[str]:
        return {method for method, _, _ in self.calls}


class PublishD064Test(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self._tmp.name) / "artifact-set"
        self.dir.mkdir()
        populate(self.dir)
        write_envelope(self.dir, revision=REVISION, version=VERSION)
        self.fake = FakeGhcr()

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def admission(self) -> pub.Admission:
        return pub.admit(self.dir, REVISION)

    def run_publish(self, admission: pub.Admission | None = None) -> pub.Result:
        return pub.publish(
            admission or self.admission(),
            Registry(self.fake, Credentials("bot", TOKEN)),
            Registry(self.fake, None),
        )

    def other_d064(self, revision: str = REVISION) -> bytes:
        return (json.dumps({"provenance": {
            "kind": "repositoryRevision",
            "repository": "guillermomolina/protos",
            "revision": revision,
        }, "other": True}) + "\n").encode()

    # --- admission ---

    def test_verified_artifact_set_is_admitted(self) -> None:
        admission = self.admission()
        data = (self.dir / D064).read_bytes()
        self.assertEqual(admission.revision, REVISION)
        self.assertEqual(admission.name, D064)
        self.assertEqual(admission.data, data)
        self.assertEqual(admission.content_sha256, hashlib.sha256(data).hexdigest())
        self.assertEqual(admission.alias, "rev-" + REVISION)

    def test_missing_manifest_rejected(self) -> None:
        (self.dir / MANIFEST_NAME).unlink()
        with self.assertRaisesRegex(PublicationError, MANIFEST_NAME):
            self.admission()

    def test_missing_d064_rejected(self) -> None:
        (self.dir / D064).unlink()
        with self.assertRaisesRegex(PublicationError, "missing"):
            self.admission()

    def test_unexpected_revision_rejected(self) -> None:
        with self.assertRaisesRegex(PublicationError, "not expected"):
            pub.admit(self.dir, OTHER_REVISION)

    def test_changed_d064_bytes_rejected_by_envelope(self) -> None:
        with (self.dir / D064).open("ab") as handle:
            handle.write(b" \n")
        with self.assertRaises(PublicationError):
            self.admission()

    def manifest(self) -> dict:
        return json.loads((self.dir / MANIFEST_NAME).read_bytes())

    def test_wrong_kind_or_path_rejected(self) -> None:
        for field, value in (("kind", "stdlib-documentation-coverage"),
                             ("path", "../" + D064), ("path", "other.json")):
            with self.subTest(field=field, value=value):
                manifest = self.manifest()
                for entry in manifest["artifacts"]:
                    if entry["path"] == D064:
                        entry[field] = value
                with self.assertRaises(PublicationError):
                    pub.select_d064(manifest)

    def test_duplicate_d064_entry_rejected(self) -> None:
        manifest = self.manifest()
        entry = pub.select_d064(manifest)
        manifest["artifacts"].append(dict(entry))
        with self.assertRaisesRegex(PublicationError, "2 D064"):
            pub.select_d064(manifest)

    def test_recorded_hash_mismatch_rejected(self) -> None:
        entry = dict(pub.select_d064(self.manifest()), sha256="0" * 64)
        with self.assertRaisesRegex(PublicationError, "recorded"):
            pub.admit_bytes(REVISION, entry, (self.dir / D064).read_bytes())

    def test_actual_h_mismatch_rejected(self) -> None:
        entry = pub.select_d064(self.manifest())
        with self.assertRaisesRegex(PublicationError, "recorded"):
            pub.admit_bytes(REVISION, entry, self.other_d064())

    def test_wrong_provenance_repository_rejected(self) -> None:
        data = (self.dir / D064).read_bytes().replace(
            b"guillermomolina/protos", b"someone/protos")
        entry = dict(pub.select_d064(self.manifest()),
                     sha256=hashlib.sha256(data).hexdigest())
        with self.assertRaisesRegex(IdentityConflict, "provenance"):
            pub.admit_bytes(REVISION, entry, data)

    def test_wrong_provenance_revision_rejected(self) -> None:
        data = self.other_d064(OTHER_REVISION)
        entry = dict(pub.select_d064(self.manifest()),
                     sha256=hashlib.sha256(data).hexdigest())
        with self.assertRaisesRegex(IdentityConflict, "provenance"):
            pub.admit_bytes(REVISION, entry, data)

    # --- publication ---

    def test_absent_alias_publishes_and_verifies(self) -> None:
        admission = self.admission()
        result = self.run_publish(admission)
        manifest = pub.render_manifest(admission)
        self.assertEqual(result.manifest_digest, sha(manifest))
        self.assertEqual(self.fake.tags["rev-" + REVISION], sha(manifest))
        self.assertEqual(self.fake.blobs["sha256:" + admission.content_sha256],
                         admission.data)
        self.assertFalse(result.existing)
        lines = result.lines()
        for expected in (
            f"SOURCE_REVISION={REVISION}",
            f"D064_CONTENT_SHA256={admission.content_sha256}",
            f"OCI_MANIFEST_DIGEST={sha(manifest)}",
            f"DISCOVERY_ALIAS=rev-{REVISION}",
            "PACKAGE_REFERENCE=ghcr.io/guillermomolina/protos-stdlib-documentation",
            "EXISTING_PUBLICATION=NO",
            "IDEMPOTENT_RETRY=NO",
            "AUTHENTICATED_VERIFICATION=PASS",
            "ANONYMOUS_VERIFICATION=PASS",
            "D064_PROVENANCE_VERIFICATION=PASS",
            "PUBLIC_RELEASE_CREATED=NO",
            "D064_PUBLICATION=PASS",
        ):
            self.assertIn(expected, lines)

    def test_manifest_is_deterministic_oci_artifact(self) -> None:
        admission = self.admission()
        first = pub.render_manifest(admission)
        self.assertEqual(first, pub.render_manifest(self.admission()))
        manifest = json.loads(first)
        self.assertEqual(manifest["artifactType"], pub.ARTIFACT_TYPE)
        self.assertEqual(manifest["mediaType"], pub.MANIFEST_MEDIA_TYPE)
        layer = pub.d064_layer(first)
        self.assertEqual(layer["mediaType"], pub.LAYER_MEDIA_TYPE)
        self.assertEqual(layer["digest"], "sha256:" + admission.content_sha256)
        self.assertNotIn(b"created", first)

    def test_same_x_same_h_retry_is_idempotent(self) -> None:
        first = self.run_publish()
        calls = len(self.fake.calls)
        uploads = self.fake.uploads
        second = self.run_publish()
        self.assertTrue(second.existing)
        self.assertEqual(second.manifest_digest, first.manifest_digest)
        self.assertEqual(self.fake.uploads, uploads)
        self.assertNotIn("PUT", {m for m, _, _ in self.fake.calls[calls:]})
        self.assertIn("IDEMPOTENT_RETRY=YES", second.lines())

    def seed_conflict(self, d064: bytes, revision: str = REVISION) -> None:
        admission = pub.Admission(REVISION, D064, d064,
                                  hashlib.sha256(d064).hexdigest())
        self.fake.seed(pub.render_manifest(admission), [b"{}", d064],
                       "rev-" + revision)

    def test_existing_alias_with_different_h_fails_closed(self) -> None:
        self.seed_conflict(self.other_d064())
        before = dict(self.fake.tags)
        with self.assertRaisesRegex(IdentityConflict, "holds D064"):
            self.run_publish()
        self.assertEqual(self.fake.tags, before)
        self.assertNotIn("PUT", self.fake.methods())

    def test_existing_alias_with_wrong_provenance_fails_closed(self) -> None:
        # Same H claimed by the manifest, but the stored blob disagrees.
        admission = self.admission()
        self.fake.seed(pub.render_manifest(admission), [b"{}"], "rev-" + REVISION)
        wrong = self.other_d064(OTHER_REVISION)
        self.fake.blobs["sha256:" + admission.content_sha256] = wrong
        with self.assertRaises(IdentityConflict):
            self.run_publish()
        self.assertNotIn("PUT", self.fake.methods())

    def test_existing_alias_with_foreign_manifest_fails_closed(self) -> None:
        self.fake.seed(b'{"layers":[]}', [], "rev-" + REVISION)
        with self.assertRaises(IdentityConflict):
            self.run_publish()

    def test_alias_resolving_to_unexpected_digest_fails(self) -> None:
        foreign = b'{"foreign":true}'
        self.fake.manifests[sha(foreign)] = foreign
        self.fake.tag_after_push = sha(foreign)
        with self.assertRaisesRegex(IdentityConflict, "resolves to"):
            self.run_publish()

    def test_reported_push_digest_must_match_expected(self) -> None:
        self.fake.digest_header_override = "sha256:" + "e" * 64
        with self.assertRaisesRegex(pub.RegistryError, "reported manifest"):
            self.run_publish()

    def test_download_by_digest_with_changed_bytes_fails(self) -> None:
        self.fake.blob_override = (self.dir / D064).read_bytes() + b" "
        with self.assertRaisesRegex(IdentityConflict, "do not match"):
            self.run_publish()

    def test_download_by_digest_with_wrong_provenance_fails(self) -> None:
        wrong = self.other_d064(OTHER_REVISION)
        admission = pub.Admission(REVISION, D064, wrong,
                                  hashlib.sha256(wrong).hexdigest())
        with self.assertRaisesRegex(IdentityConflict, "provenance"):
            self.run_publish(admission)

    def test_uncertain_push_resolves_and_verifies_without_repush(self) -> None:
        self.fake.fail_manifest_put_uncertain = True
        result = self.run_publish()
        self.assertEqual(result.anonymous, "PASS")
        puts = [u for m, u, _ in self.fake.calls if m == "PUT" and "manifests" in u]
        self.assertEqual(len(puts), 1)

    def test_uncertain_push_with_absent_alias_fails(self) -> None:
        self.fake.fail_manifest_put_uncertain = True
        self.fake.persist_uncertain_put = False
        with self.assertRaisesRegex(pub.RegistryError, "outcome unknown"):
            self.run_publish()

    def test_private_package_is_not_admitted(self) -> None:
        self.fake.public = False
        result = self.run_publish()
        self.assertEqual(result.authenticated, "PASS")
        self.assertEqual(result.anonymous, "FAIL_PACKAGE_NOT_PUBLIC")
        self.assertIn("D064_PUBLICATION=AWAITING_PUBLIC_VISIBILITY", result.lines())
        # After the owner makes it public, the same command completes.
        self.fake.public = True
        retry = self.run_publish()
        self.assertTrue(retry.existing)
        self.assertEqual(retry.anonymous, "PASS")

    def test_anonymous_consumer_never_presents_publisher_credentials(self) -> None:
        consumer = Registry(self.fake, None)
        admission = self.admission()
        pub.publish(admission, Registry(self.fake, Credentials("bot", TOKEN)),
                    consumer)
        start = len(self.fake.calls)
        pub.verify_remote(consumer, sha(pub.render_manifest(admission)), admission)
        for _, url, headers in self.fake.calls[start:]:
            self.assertNotIn(TOKEN, url)
            self.assertNotIn(headers.get("Authorization"),
                             ("Bearer " + PUSH_BEARER,))
            self.assertFalse(headers.get("Authorization", "").startswith("Basic"))
        with self.assertRaises(PublicationError):
            registry = Registry(self.fake, Credentials("bot", TOKEN))
            pub.publish(admission, registry, registry)

    def test_token_is_not_disclosed(self) -> None:
        credentials = Credentials("bot", TOKEN)
        self.assertNotIn(TOKEN, repr(credentials))
        self.fake.public = False
        result = self.run_publish()
        self.assertNotIn(TOKEN, "\n".join(result.lines()))
        self.fake.seed(b'{"layers":[]}', [], "rev-" + REVISION)
        try:
            self.run_publish()
        except PublicationError as exc:
            self.assertNotIn(TOKEN, str(exc))
        for method, url, headers in self.fake.calls:
            if headers.get("Authorization", "").startswith("Basic"):
                self.assertEqual(urllib.parse.urlsplit(url).path, "/token")

    def test_credentials_never_sent_to_foreign_token_realm(self) -> None:
        class Foreign(FakeGhcr):
            def request(self, method, url, headers, body):  # type: ignore[no-untyped-def]
                self.calls.append((method, url, dict(headers)))
                return Response(401, {"www-authenticate":
                                      'Bearer realm="https://evil.example/token"'}, b"")
        fake = Foreign()
        registry = Registry(fake, Credentials("bot", TOKEN))
        with self.assertRaisesRegex(pub.RegistryError, "realm"):
            registry.resolve("rev-" + REVISION)
        self.assertEqual(len(fake.calls), 1)

    def test_resolve_cross_checks_registry_digest_header(self) -> None:
        class Lying(FakeGhcr):
            def request(self, method, url, headers, body):  # type: ignore[no-untyped-def]
                response = super().request(method, url, headers, body)
                if "/manifests/" in url and response.status == 200:
                    return Response(200, {"docker-content-digest":
                                          "sha256:" + "d" * 64}, response.body)
                return response
        self.fake = Lying()
        with self.assertRaisesRegex(pub.RegistryError, "disagrees"):
            self.run_publish()

    def test_publisher_never_builds_releases_or_deletes(self) -> None:
        def forbidden(*args, **kwargs):  # type: ignore[no-untyped-def]
            raise AssertionError(f"publisher spawned a process: {args!r}")
        with mock.patch.object(subprocess, "run", forbidden), \
                mock.patch.object(subprocess, "Popen", forbidden), \
                mock.patch.object(build_artifact_set, "build", forbidden), \
                mock.patch.object(build_artifact_set, "extract_documentation",
                                  forbidden):
            result = self.run_publish()
        self.assertEqual(result.anonymous, "PASS")
        self.assertLessEqual(self.fake.methods(), {"GET", "HEAD", "POST", "PUT"})
        source = Path(pub.__file__).read_text(encoding="utf-8")
        for marker in ("import subprocess", "mvn", "make ", "construction_steps",
                       "extract_documentation", "prepare_release",
                       "publish_release", "git ", '"gh"', "DELETE"):
            self.assertNotIn(marker, source)
        pushed_tags = [urllib.parse.urlsplit(u).path.rsplit("/", 1)[1]
                       for m, u, _ in self.fake.calls
                       if m == "PUT" and "/manifests/" in u]
        self.assertEqual(pushed_tags, ["rev-" + REVISION])


if __name__ == "__main__":
    unittest.main()
