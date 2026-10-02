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

"""DIST010-B exact-revision D064 publication to GHCR as an OCI artifact.

Publishes only the D064 member of an already-verified DIST010 artifact set.
Identity is ``X`` (source revision) + ``H`` (SHA-256 of the raw D064 bytes) +
``M`` (OCI manifest digest); the ``rev-X`` tag is a discovery alias only.

This module never builds, regenerates, deletes, tags, or releases anything. It
speaks the OCI distribution API directly through the Python standard library so
that no extra client is required and the anonymous verification context cannot
inherit publisher credentials from a shared credential store.
"""

from __future__ import annotations

import argparse
import base64
from dataclasses import dataclass
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import sys
from typing import Protocol
import urllib.error
import urllib.parse
import urllib.request

from build_artifact_set import (
    KIND_D064,
    REPOSITORY,
    ArtifactSetError,
    artifact_names,
    verify_artifact_set,
)

REGISTRY = "ghcr.io"
PACKAGE = "guillermomolina/protos-stdlib-documentation"
PACKAGE_REFERENCE = f"{REGISTRY}/{PACKAGE}"
ARTIFACT_TYPE = "application/vnd.protos.stdlib-documentation.v1"
LAYER_MEDIA_TYPE = "application/vnd.protos.stdlib-documentation.v1+json"
MANIFEST_MEDIA_TYPE = "application/vnd.oci.image.manifest.v1+json"
EMPTY_MEDIA_TYPE = "application/vnd.oci.empty.v1+json"
EMPTY_BLOB = b"{}"
SOURCE_URL = f"https://github.com/{REPOSITORY}"
DIGEST_RE = re.compile(r"sha256:[0-9a-f]{64}")
REDIRECTS = (301, 302, 303, 307, 308)


class PublicationError(Exception):
    pass


class IdentityConflict(PublicationError):
    """Remote state for rev-X disagrees with the admitted X/H identity."""


class RegistryError(PublicationError):
    pass


class RegistryUncertain(RegistryError):
    """The outcome of a request is unknown (transport failure or 5xx)."""


class AnonymousReadDenied(PublicationError):
    """A credential-free consumer cannot read the package (not yet public)."""


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def digest_of(data: bytes) -> str:
    return "sha256:" + sha256_hex(data)


# --- Admission -------------------------------------------------------------


@dataclass(frozen=True)
class Admission:
    revision: str
    name: str
    data: bytes
    content_sha256: str

    @property
    def alias(self) -> str:
        return "rev-" + self.revision


def check_d064_provenance(data: bytes, revision: str) -> None:
    try:
        provenance = json.loads(data.decode("utf-8"))["provenance"]
        actual = (
            provenance["kind"], provenance["repository"], provenance["revision"]
        )
    except (UnicodeDecodeError, ValueError, KeyError, TypeError) as exc:
        raise IdentityConflict("D064 bytes are not a D064 document") from exc
    if actual != ("repositoryRevision", REPOSITORY, revision):
        raise IdentityConflict(
            f"D064 provenance {actual!r} is not repositoryRevision "
            f"{REPOSITORY}@{revision}"
        )


def select_d064(manifest: dict[str, object]) -> dict[str, object]:
    """Locate D064 through the canonical manifest, never by filename glob."""
    revision = manifest["revision"]
    entries = [
        entry for entry in manifest.get("artifacts", [])
        if isinstance(entry, dict) and entry.get("kind") == KIND_D064
    ]
    if len(entries) != 1:
        raise PublicationError(
            f"artifact set records {len(entries)} D064 members; expected 1"
        )
    entry = entries[0]
    expected_name = artifact_names(str(manifest["version"]))[KIND_D064]
    if entry.get("path") != expected_name:
        raise PublicationError(
            f"D064 member path {entry.get('path')!r} is not {expected_name!r}"
        )
    identity = entry.get("identity")
    if (
        not isinstance(identity, dict)
        or identity.get("provenance_repository") != REPOSITORY
        or identity.get("provenance_revision") != revision
    ):
        raise PublicationError("D064 manifest identity does not match the set")
    return entry


def admit_bytes(
    revision: str, entry: dict[str, object], data: bytes
) -> Admission:
    content_sha256 = sha256_hex(data)
    if content_sha256 != entry.get("sha256"):
        raise PublicationError(
            "D064 bytes do not match the SHA-256 recorded in the artifact set"
        )
    check_d064_provenance(data, revision)
    return Admission(revision, str(entry["path"]), data, content_sha256)


def admit(directory: Path, expect_revision: str | None) -> Admission:
    try:
        manifest = verify_artifact_set(directory, expect_revision=expect_revision)
    except ArtifactSetError as exc:
        raise PublicationError("artifact set is not admitted: " + str(exc)) from exc
    entry = select_d064(manifest)
    # Re-read and re-hash: H is computed from the exact bytes that get pushed.
    data = (directory / str(entry["path"])).read_bytes()
    return admit_bytes(str(manifest["revision"]), entry, data)


# --- OCI content -----------------------------------------------------------


def render_manifest(admission: Admission) -> bytes:
    # No timestamps: the same X and H always produce the same manifest, so M
    # is reproducible and a retry cannot mint a second identity for X.
    document = {
        "schemaVersion": 2,
        "mediaType": MANIFEST_MEDIA_TYPE,
        "artifactType": ARTIFACT_TYPE,
        "config": {
            "mediaType": EMPTY_MEDIA_TYPE,
            "digest": digest_of(EMPTY_BLOB),
            "size": len(EMPTY_BLOB),
        },
        "layers": [
            {
                "mediaType": LAYER_MEDIA_TYPE,
                "digest": "sha256:" + admission.content_sha256,
                "size": len(admission.data),
                "annotations": {
                    "org.opencontainers.image.title": admission.name,
                },
            }
        ],
        "annotations": {
            "org.opencontainers.image.revision": admission.revision,
            "org.opencontainers.image.source": SOURCE_URL,
        },
    }
    return json.dumps(document, sort_keys=True, separators=(",", ":")).encode()


def d064_layer(manifest_bytes: bytes) -> dict[str, object]:
    try:
        manifest = json.loads(manifest_bytes.decode("utf-8"))
        layers = manifest["layers"]
    except (UnicodeDecodeError, ValueError, KeyError, TypeError) as exc:
        raise IdentityConflict("remote manifest is malformed") from exc
    if manifest.get("artifactType") != ARTIFACT_TYPE:
        raise IdentityConflict("remote manifest is not a D064 artifact")
    if (
        not isinstance(layers, list)
        or len(layers) != 1
        or not isinstance(layers[0], dict)
        or layers[0].get("mediaType") != LAYER_MEDIA_TYPE
        or not isinstance(layers[0].get("digest"), str)
    ):
        raise IdentityConflict("remote manifest does not hold exactly one D064 layer")
    return layers[0]


# --- Registry access -------------------------------------------------------


@dataclass(frozen=True)
class Response:
    status: int
    headers: dict[str, str]  # lower-cased names
    body: bytes


class Transport(Protocol):
    def request(
        self, method: str, url: str, headers: dict[str, str], body: bytes | None
    ) -> Response: ...


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):  # type: ignore[no-untyped-def]
        return None


class UrllibTransport:
    """Stateless HTTPS transport: no cookies, no credential store, no redirects."""

    def request(
        self, method: str, url: str, headers: dict[str, str], body: bytes | None
    ) -> Response:
        request = urllib.request.Request(url, data=body, method=method, headers=headers)
        opener = urllib.request.build_opener(_NoRedirect)
        try:
            with opener.open(request, timeout=120) as response:
                return Response(
                    response.status,
                    {k.lower(): v for k, v in response.headers.items()},
                    response.read(),
                )
        except urllib.error.HTTPError as exc:
            return Response(
                exc.code, {k.lower(): v for k, v in exc.headers.items()}, exc.read()
            )
        except (urllib.error.URLError, OSError) as exc:
            path = urllib.parse.urlsplit(url).path
            raise RegistryUncertain(f"{method} {path}: {exc}") from exc


@dataclass(frozen=True)
class Credentials:
    username: str
    token: str = ""

    def __repr__(self) -> str:
        return f"Credentials(username={self.username!r}, token=<redacted>)"


def parse_challenge(header: str) -> dict[str, str]:
    if not header.lower().startswith("bearer "):
        raise RegistryError("registry did not offer a Bearer challenge")
    return dict(re.findall(r'(\w+)="([^"]*)"', header[7:]))


class Registry:
    """Minimal OCI distribution client for one package.

    ``credentials=None`` is a credential-free consumer: it only ever presents
    anonymous Bearer tokens it obtained itself.
    """

    def __init__(self, transport: Transport, credentials: Credentials | None):
        self._transport = transport
        self._credentials = credentials
        self._token: str | None = None

    @property
    def anonymous(self) -> bool:
        return self._credentials is None

    def _url(self, kind: str, reference: str) -> str:
        return f"https://{REGISTRY}/v2/{PACKAGE}/{kind}/{reference}"

    def _authenticate(self, challenge: str) -> None:
        params = parse_challenge(challenge)
        realm = urllib.parse.urlsplit(params.get("realm", ""))
        if realm.scheme != "https" or realm.netloc != REGISTRY:
            raise RegistryError("registry token realm is not https://" + REGISTRY)
        actions = "pull" if self.anonymous else "pull,push"
        query = urllib.parse.urlencode(
            {"service": params.get("service", REGISTRY),
             "scope": f"repository:{PACKAGE}:{actions}"}
        )
        headers: dict[str, str] = {}
        if self._credentials is not None:
            pair = f"{self._credentials.username}:{self._credentials.token}"
            headers["Authorization"] = "Basic " + base64.b64encode(
                pair.encode()
            ).decode()
        response = self._transport.request(
            "GET", realm.geturl() + "?" + query, headers, None
        )
        token = None
        if response.status == 200:
            try:
                payload = json.loads(response.body.decode("utf-8"))
                token = payload.get("token") or payload.get("access_token")
            except (UnicodeDecodeError, ValueError, AttributeError):
                token = None
        if not token:
            if self.anonymous:
                raise AnonymousReadDenied(
                    f"anonymous token request failed (HTTP {response.status})"
                )
            raise RegistryError(
                f"registry authentication failed (HTTP {response.status})"
            )
        self._token = token

    def _send(
        self, method: str, url: str, headers: dict[str, str] | None = None,
        body: bytes | None = None,
    ) -> Response:
        authenticated = False
        for _ in range(6):
            sent = dict(headers or {})
            on_registry = urllib.parse.urlsplit(url).netloc == REGISTRY
            # Never forward a registry token to a redirected storage host.
            if self._token and on_registry:
                sent["Authorization"] = "Bearer " + self._token
            response = self._transport.request(method, url, sent, body)
            if response.status == 401 and on_registry and not authenticated:
                self._authenticate(response.headers.get("www-authenticate", ""))
                authenticated = True
                continue
            if response.status in REDIRECTS and method in ("GET", "HEAD"):
                url = urllib.parse.urljoin(url, response.headers.get("location", ""))
                continue
            if response.status >= 500:
                path = urllib.parse.urlsplit(url).path
                raise RegistryUncertain(f"{method} {path}: HTTP {response.status}")
            return response
        raise RegistryError(f"{method} {urllib.parse.urlsplit(url).path}: too many hops")

    def _fail(self, method: str, url: str, response: Response) -> PublicationError:
        message = (
            f"{method} {urllib.parse.urlsplit(url).path}: HTTP {response.status}"
        )
        if self.anonymous and response.status in (401, 403, 404):
            return AnonymousReadDenied(message)
        return RegistryError(message)

    def resolve(self, tag: str) -> str | None:
        """Return the digest of the manifest the tag names, or None if absent.

        The digest is computed from the returned bytes; a registry-supplied
        Docker-Content-Digest header is only cross-checked, never trusted alone.
        """
        url = self._url("manifests", tag)
        response = self._send("GET", url, {"Accept": MANIFEST_MEDIA_TYPE})
        if response.status == 404 and not self.anonymous:
            return None
        if response.status != 200:
            raise self._fail("GET", url, response)
        digest = digest_of(response.body)
        header = response.headers.get("docker-content-digest")
        if header is not None and header != digest:
            raise RegistryError(
                f"registry digest header {header} disagrees with manifest bytes"
            )
        return digest

    def fetch(self, kind: str, digest: str) -> bytes:
        if DIGEST_RE.fullmatch(digest) is None:
            raise IdentityConflict("remote digest is not sha256: " + digest)
        url = self._url(kind, digest)
        headers = {"Accept": MANIFEST_MEDIA_TYPE} if kind == "manifests" else {}
        response = self._send("GET", url, headers)
        if response.status != 200:
            raise self._fail("GET", url, response)
        if digest_of(response.body) != digest:
            raise IdentityConflict(f"downloaded {kind} bytes do not match {digest}")
        return response.body

    def push_blob(self, data: bytes) -> None:
        digest = digest_of(data)
        if self._send("HEAD", self._url("blobs", digest)).status == 200:
            return
        start = f"https://{REGISTRY}/v2/{PACKAGE}/blobs/uploads/"
        response = self._send("POST", start, {"Content-Length": "0"}, b"")
        location = response.headers.get("location")
        if response.status != 202 or not location:
            raise self._fail("POST", start, response)
        url = urllib.parse.urljoin(start, location)
        url += ("&" if "?" in url else "?") + urllib.parse.urlencode({"digest": digest})
        response = self._send(
            "PUT", url, {"Content-Type": "application/octet-stream"}, data
        )
        if response.status != 201:
            raise self._fail("PUT", url, response)

    def put_manifest(self, tag: str, body: bytes) -> str | None:
        url = self._url("manifests", tag)
        response = self._send("PUT", url, {"Content-Type": MANIFEST_MEDIA_TYPE}, body)
        if response.status != 201:
            raise self._fail("PUT", url, response)
        return response.headers.get("docker-content-digest")


# --- Publication -----------------------------------------------------------


def verify_remote(registry: Registry, digest: str, admission: Admission) -> None:
    """Acquire by immutable digest M and require exact H and provenance X."""
    layer = d064_layer(registry.fetch("manifests", digest))
    if layer["digest"] != "sha256:" + admission.content_sha256:
        raise IdentityConflict(
            f"{digest} holds D064 {layer['digest']}, not sha256:"
            f"{admission.content_sha256}"
        )
    data = registry.fetch("blobs", str(layer["digest"]))
    if sha256_hex(data) != admission.content_sha256:
        raise IdentityConflict("downloaded D064 bytes do not match H")
    check_d064_provenance(data, admission.revision)


@dataclass
class Result:
    admission: Admission
    manifest_digest: str = ""
    existing: bool = False
    authenticated: str = "NOT_RUN"
    anonymous: str = "NOT_RUN"

    def lines(self) -> list[str]:
        complete = self.anonymous == "PASS"
        return [
            f"SOURCE_REVISION={self.admission.revision}",
            f"D064_CONTENT_SHA256={self.admission.content_sha256}",
            f"OCI_MANIFEST_DIGEST={self.manifest_digest}",
            f"DISCOVERY_ALIAS={self.admission.alias}",
            f"PACKAGE_REFERENCE={PACKAGE_REFERENCE}",
            f"EXISTING_PUBLICATION={'YES' if self.existing else 'NO'}",
            f"IDEMPOTENT_RETRY={'YES' if self.existing else 'NO'}",
            f"AUTHENTICATED_VERIFICATION={self.authenticated}",
            f"ANONYMOUS_VERIFICATION={self.anonymous}",
            "D064_PROVENANCE_VERIFICATION="
            + ("PASS" if self.authenticated == "PASS" else "NOT_RUN"),
            "PUBLIC_RELEASE_CREATED=NO",
            "D064_PUBLICATION="
            + ("PASS" if complete else "AWAITING_PUBLIC_VISIBILITY"),
        ]


def publish(
    admission: Admission,
    publisher: Registry,
    consumer: Registry,
) -> Result:
    """Publish (or idempotently confirm) rev-X -> M for the admitted X/H."""
    if consumer is publisher or not consumer.anonymous or publisher.anonymous:
        raise PublicationError("publisher and anonymous consumer must be distinct")
    result = Result(admission)
    expected = render_manifest(admission)
    existing = publisher.resolve(admission.alias)
    if existing is not None:
        verify_remote(publisher, existing, admission)
        result.existing = True
        digest = existing
    else:
        digest = digest_of(expected)
        publisher.push_blob(EMPTY_BLOB)
        publisher.push_blob(admission.data)
        try:
            reported = publisher.put_manifest(admission.alias, expected)
        except RegistryUncertain:
            reported = None  # Resolve and verify below; never blindly repush.
        if reported is not None and reported != digest:
            raise RegistryError(
                f"registry reported manifest {reported}, expected {digest}"
            )

    resolved = publisher.resolve(admission.alias)
    if resolved is None:
        raise RegistryError(
            f"{admission.alias} is absent after publication; outcome unknown, "
            "rerun to resolve and verify"
        )
    if resolved != digest:
        raise IdentityConflict(
            f"{admission.alias} resolves to {resolved}, expected {digest}"
        )
    verify_remote(publisher, digest, admission)
    result.manifest_digest = digest
    result.authenticated = "PASS"

    try:
        verify_remote(consumer, digest, admission)
    except AnonymousReadDenied:
        result.anonymous = "FAIL_PACKAGE_NOT_PUBLIC"
        return result
    result.anonymous = "PASS"
    return result


def lock(directory: Path):  # type: ignore[no-untyped-def]
    """Serialize local publications that use the same artifact set."""
    handle = (directory.parent / (directory.name + ".publish.lock")).open("w")
    try:
        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        handle.close()
        raise PublicationError("another D064 publication holds the lock")
    return handle


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Publish the D064 member of a verified DIST010 artifact set to "
            f"{PACKAGE_REFERENCE} as rev-<X> and verify anonymous acquisition "
            "by digest. Never builds, deletes, tags, or releases. Credentials: "
            "GHCR_USERNAME and GHCR_TOKEN (write:packages)."
        )
    )
    parser.add_argument("--artifact-set", required=True, type=Path)
    parser.add_argument("--expect-revision")
    args = parser.parse_args()

    username = os.environ.get("GHCR_USERNAME", "")
    token = os.environ.get("GHCR_TOKEN", "")
    if not username or not token:
        raise SystemExit("d064 publication failed: GHCR_USERNAME/GHCR_TOKEN unset")
    try:
        handle = lock(args.artifact_set)
        try:
            admission = admit(args.artifact_set, args.expect_revision)
            result = publish(
                admission,
                Registry(UrllibTransport(), Credentials(username, token)),
                Registry(UrllibTransport(), None),
            )
        finally:
            handle.close()
    except PublicationError as exc:
        raise SystemExit("d064 publication failed: " + str(exc))
    print("\n".join(result.lines()))
    if result.anonymous != "PASS":
        print(
            f"Make https://github.com/users/guillermomolina/packages/container/"
            f"package/{PACKAGE.split('/', 1)[1]} public, then rerun.",
            file=sys.stderr,
        )
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
