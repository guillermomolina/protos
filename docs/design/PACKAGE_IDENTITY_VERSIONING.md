# Protos Package Identity and Versioning Audit

Status: exploratory architecture; non-normative
Related design: `docs/design/PACKAGE_DISTRIBUTION.md`
Related manifest audit: `docs/design/PACKAGE_MANIFEST_FORMAT.md`

This document performs the focused package-identity, release-version, content-
identity, authority, and lock-record audit left open by the package distribution
design. It does not implement package tooling, define Core language semantics,
reserve a `LIBxxx` work item, choose the dependency-constraint grammar, or commit
Protos to a particular public registry protocol.

Where this record is more specific than the earlier exploratory wording in
`PACKAGE_DISTRIBUTION.md`, this record refines that wording. In particular, a
package's durable logical identity must not be reconstructed from a registry URL,
repository URL, cache path, archive filename, human-readable package locator, or
content digest.

## Problem

Package systems often make one convenient string carry several unrelated jobs:

```text
human name
source location
trust authority
version-selection key
import prefix
runtime identity
```

That is simple initially but expensive to undo. A repository move can become an
API break, changing registries can create a new package identity, changing an
archive format can invalidate hashes, and renaming an organization can force
source-code rewrites.

Protos already has an important escape hatch: source code imports third-party
modules through a dependency alias such as `dep:parser/Parser`, while the host
resolver produces the canonical `ModuleKey`. Therefore package discovery,
package identity, package acquisition, and module identity do not need to share
one textual name.

The design objective is:

> Keep durable identity small and stable. Keep human naming, authority,
> retrieval, transport, and content verification explicit but independently
> replaceable where their semantics genuinely differ.

## Selected conceptual model

Use distinct concepts:

```text
PackageId
    stable opaque logical identifier for one package lineage

PackageLocator
    human-facing registry locator, illustratively scope/name

AuthorityIdentity
    authority whose metadata/release assertion was trusted during resolution

ReleaseVersion
    exact published version label for one release of a PackageId

ContentIdentity
    canonical digest of the logical package content tree

ArtifactDigest
    digest of one concrete downloaded transport artifact

RetrievalLocator
    endpoint/path/URL from which bytes happened to be obtained

ResolvedPackageInstance
    one exact immutable package node selected into a dependency graph
```

These are deliberately not aliases for one another.

## `PackageId`: durable logical identity

### Required property

Every package intended to participate in dependency resolution should have a
stable opaque `PackageId` generated once and retained across the package's normal
lifetime.

The `PackageId` must not be derived from:

- package name or scope;
- registry hostname or endpoint;
- Git/VCS repository URL;
- organization/user account name;
- release version;
- package content;
- filesystem path;
- current publisher signing key;
- selected Protos implementation/toolchain version.

The exact textual encoding and entropy size remain an implementation-format
choice for the package-system work item. The important property is that the ID is
opaque, collision-resistant for practical package creation, non-content-derived,
and stable under ordinary rename/move/transfer operations.

A future manifest will therefore need to carry or otherwise deterministically
recover this stable package ID. The current recommendation is that `protos init`
generates it and normal source control commits it with the package manifest.
Hand-editing it should be exceptional.

### Clone versus fork

A source clone used to develop the same package retains the same `PackageId`.

An independent fork that intends to become a different package lineage must use
a new `PackageId`, even if it begins with identical source. Tooling should make
this operation explicit so a copied manifest does not accidentally impersonate
the original package lineage.

A registry must reject unauthorized publication claiming an existing
`PackageId`. The ID itself is an identifier, not proof of ownership.

## Human package locator is not identity

Published packages need readable discovery names. The preferred initial registry
shape remains a scoped locator such as:

```text
community/parser
acme/http
```

The exact lexical grammar remains for the registry/naming audit, but an initial
public locator should strongly prefer canonical lowercase portable ASCII and
avoid case-folding or Unicode-normalization ambiguity. A separate display title
may use richer text because display metadata is not package identity.

A `PackageLocator` resolves through a registry authority to a `PackageId`.
Therefore:

```text
PackageLocator -> PackageId
```

is registry metadata, not an identity equation.

### Rename and scope transfer

Renaming a package or moving it between administrative scopes should be able to
preserve `PackageId`.

For example:

```text
old-team/parser  ----+
                     +--> PackageId P
new-team/parser  ----+
```

A registry supporting rename must retain the old published locator as a permanent
redirect/alias or tombstone bound to the same `PackageId`. A locator that has
identified a published package must never later resolve to a different
`PackageId`; reassigning it would create a dependency-confusion/supply-chain
hazard for old manifests.

Ownership transfer is similarly an authorization change, not a package identity
change.

The package manager may offer to rewrite a manifest to the current canonical
locator for readability, but an existing locked graph must not need to change
merely because the human locator was renamed.

## Authority is separate from transport

### `AuthorityIdentity`

A package resolver needs to know which authority's package/release metadata it is
trusting. This is distinct from the endpoint currently used to fetch that
metadata.

Conceptually:

```text
AuthorityIdentity
    stable identity of the registry/publication authority

RetrievalLocator
    current HTTPS endpoint, CDN, mirror, proxy, local cache, vendor directory...
```

Changing a CDN hostname, registry API endpoint, corporate proxy, or cache path
must not create a new package identity or release.

A pure mirror that is trusted as a transport replacement for an authority does
not become a new package authority merely because bytes came from a different
URL.

The exact representation and authentication of `AuthorityIdentity` are left for
the registry/trust design. It must not be silently derived from whichever URL was
first contacted during a build.

### Cross-authority migration

Moving publication from one independent authority to another is more security-
sensitive than changing a mirror. Protos must not infer continuity merely because
both authorities expose the same locator string or self-declared `PackageId`.

A future registry protocol may support explicit continuity/migration evidence
from the previous authority or another separately designed trust mechanism.
Until then, changing authority is an explicit dependency-source change even when
the package retains its logical `PackageId`.

## Release identity and versioning

### Release relation

A release belongs to a package:

```text
ReleaseIdentity = (PackageId, ReleaseVersion)
```

A version is therefore not a package identity. `1.0.0` and `2.0.0` of the same
package remain the same logical package lineage and may coexist as distinct
resolved instances in one graph when dependency edges require them.

This deliberately avoids Go-style major-version suffixes becoming part of the
package's durable identity/import prefix.

### Initial version value

Use strict Semantic Versioning 2.0.0 as the initial published `ReleaseVersion`
value model, with one intentional restriction:

```text
MAJOR.MINOR.PATCH[-PRERELEASE]
```

Published Protos package versions should initially reject SemVer build metadata
(`+...`).

Reasons:

- SemVer build metadata does not participate in precedence, so two published
  strings differing only in build metadata can have equal ordering precedence;
- Protos already has explicit `ContentIdentity`, artifact descriptors, target
  metadata, and future build metadata locations for facts about exact bits;
- a package release version should remain the unambiguous human release label,
  not another container for transport/build identity.

Therefore examples include:

```text
1.0.0
1.4.2
2.0.0-alpha.1
2.0.0-rc.3
```

and the initial published package model rejects:

```text
v1.2.3          # non-canonical prefix
1.2             # missing patch component
01.2.3          # leading zero
1.2.3+linux     # build metadata not part of initial ReleaseVersion model
```

Pre-release ordering follows SemVer 2.0.0. The dependency-constraint language is
a separate design question; selecting SemVer version values does **not** silently
select npm, Cargo, Maven, or another ecosystem's rules for `^`, `~`, major-zero,
prerelease range admission, compatible-version unification, or highest/minimum
selection.

### Release immutability

For an authority accepting a published release:

```text
(PackageId, ReleaseVersion) -> exactly one ContentIdentity
```

must be immutable.

Publishing a different content tree under an already-used release version is
forbidden, including after yanking, deprecation, account transfer, rename, or
registry migration. A retired version identifier is never recycled for different
content.

Yanking changes eligibility for future resolution, not the release's content or
identity. Existing lockfiles may continue to name/fetch the immutable release
according to registry retention policy.

Mutable development state belongs to explicit workspace/path dependencies and
must not be modeled as a published `SNAPSHOT`, `latest`, or other mutable release
identity.

### Mutable labels are selectors, not versions

Names such as:

```text
latest
stable
beta
nightly
main
```

may eventually exist as CLI/discovery selectors, registry channels, or VCS
references, but they are not `ReleaseVersion` values and never appear as the
exact selected version of an immutable locked registry node.

If a user asks a dependency-management command to resolve a mutable selector,
the resolver must convert it to an exact release/revision before ordinary locked
execution.

## `ContentIdentity`: logical package tree

`ContentIdentity` identifies the exact logical package contents independently of
how those contents were transported.

Conceptually it must include a versioned canonicalization method plus an
algorithm-tagged digest, for example structurally:

```text
method:    protos-package-tree-v1
algorithm: sha256
digest:    ...
```

The exact canonical tree algorithm remains a focused artifact-format audit, but
its design must define at least:

- included package files and manifest content;
- canonical relative path encoding;
- path separator and case rules;
- treatment/rejection of symlinks and special filesystem entries;
- metadata that is semantic versus intentionally excluded;
- deterministic byte representation;
- resource inclusion;
- exclusion of VCS metadata, caches, build products, absolute paths, usernames,
  timestamps, and other machine-local state.

The canonicalization method itself is versioned. Changing tree encoding in the
future must not cause old digests to be reinterpreted under a new algorithm.

### Digest agility

Every persisted digest must name its hash algorithm. Do not store a bare hex
string whose meaning is permanently implied to be one algorithm.

A future transition can publish/accept more than one digest for the same content
while old lockfiles remain verifiable with their recorded method+algorithm.

## `ArtifactDigest`: transport bytes

A concrete registry artifact such as:

```text
package-1.0.0.zip
package-1.0.0.tar.zst
```

has its own raw-byte digest. That is `ArtifactDigest`, not `ContentIdentity`.

Two transport artifacts may legitimately differ byte-for-byte while unpacking to
the same canonical package tree:

```text
ZIP bytes     -> ArtifactDigest A --+
                                    +--> ContentIdentity C
TAR.ZST bytes -> ArtifactDigest B --+
```

This distinction permits future changes in archive format, compression level,
metadata envelope, CDN representation, or transport protocol without redefining
the logical package content.

Artifact digests are valuable for validating raw downloads before unpacking and
for cache integrity, but the dependency graph must not use an archive digest as
its package identity.

## Resolved package instances

### Immutable published node

An immutable registry-backed node is conceptually identified for module-loading
purposes by:

```text
PackageId
ReleaseVersion
ContentIdentity
```

with source/provenance metadata kept alongside it.

Within one resolution graph the invariant is stronger:

> One `(PackageId, ReleaseVersion)` may correspond to only one
> `ContentIdentity`.

If two registries/metadata sources claim the same package ID and version with
different content, resolution fails rather than picking by source order.

Two retrieval locations providing the same locked content do not create two
package instances.

### VCS node

A VCS dependency should resolve mutable branches/tags to an exact revision and
verify the resulting canonical package content:

```text
PackageId
exact VCS revision
ContentIdentity
```

The repository URL is retrieval/provenance metadata, not the package's logical
identity. If a repository moves and the package retains its `PackageId`, a later
explicit source update may point to the new location without requiring source
code using `dep:<alias>/...` to change.

### Workspace/path node

An explicit workspace/path dependency is mutable development state and is not an
immutable published artifact. Its stable package lineage still uses `PackageId`,
but ordinary source edits must not require regenerating the dependency lock after
every line change.

The dependency graph records the explicit workspace/member relation. The build
cache may hash the current source tree separately for compilation reuse. On
publication, unresolved local-only dependencies are rejected or must be converted
to publishable identities according to the publishing contract.

## Package-backed `ModuleKey`

The runtime `ModuleKey` should identify package-backed modules from the resolved
package instance, not from how it was located.

For immutable external packages, conceptually:

```text
(PackageId,
 exact release/revision,
 ContentIdentity,
 logical module name)
```

Do not include:

- dependency alias (`dep:foo` versus another alias);
- package display locator;
- registry/CDN/mirror URL;
- local cache path;
- archive filename;
- transport artifact digest;
- credentials or proxy configuration.

Consequences:

- two aliases in one Actor that resolve to the same package instance/module
  produce the same canonical `ModuleKey` and therefore the same Actor-local
  module instance under existing Core module-cache semantics;
- changing a mirror/cache does not duplicate module identity;
- two package versions remain distinct;
- the same declared package version with conflicting content cannot silently
  collapse.

For mutable workspace members, the host resolver may use a canonical workspace
package key based on the stable `PackageId` plus the explicit workspace context
and logical module name; cross-process source-tree hashes belong to build-cache
identity, not Core module-instance semantics.

## Lockfile consequences

The physical `protos.lock` encoding remains separately open, but this audit fixes
the categories of persistent information needed for package identity.

An immutable package node in a lock graph should record at least:

```text
PackageId
AuthorityIdentity / source provenance kind
exact ReleaseVersion or exact VCS revision
ContentIdentity (method + hash algorithm + digest)
dependency edges: local alias -> exact locked node
```

A current/canonical human `PackageLocator` may also be recorded for diagnostics
and readable diffs, but it is not the node identity. A package rename therefore
must not require a graph change solely to preserve execution semantics.

The authoritative graph should not contain machine-local facts such as:

- cache/store paths;
- user home directories;
- credentials/tokens;
- proxy addresses;
- currently selected mirror/CDN endpoints;
- timestamps recording when resolution occurred;
- absolute workspace filesystem paths.

For a VCS source, a fetch locator may need to be persisted as source/provenance
metadata so a cold machine knows where to obtain the exact revision. That locator
remains explicitly non-identifying and may be replaced by an authorized mirror
or later explicit source update while the package/revision/content identity stays
unchanged.

`ArtifactDigest` need not be a mandatory part of dependency graph identity.
Registry/cache metadata may associate one or more transport artifacts and their
raw digests with the locked `ContentIdentity`. This preserves the ability to
change transport representation without forcing dependency re-resolution.

The lockfile format must separately carry its own format/resolver version as
already identified by the package-distribution audit.

## Package publication and registry invariants

A conforming future registry design should preserve at least these invariants:

1. a `PackageId` is never reassigned to an unrelated package;
2. publication claiming an existing `PackageId` requires authorization;
3. a locator once used for a published package is never reassigned to a different
   `PackageId`;
4. rename/scope transfer may update the canonical locator while retaining old
   locator continuity;
5. one `(PackageId, ReleaseVersion)` is permanently bound to one
   `ContentIdentity`;
6. yanking/deprecation does not mutate release content;
7. mirrors may change retrieval location without changing package/release
   identity;
8. registry endpoints may evolve independently of `PackageId`;
9. package ownership/account transfer changes authorization, not identity;
10. deleted/tombstoned names and versions must not later mean different code.

These rules intentionally make registry metadata append/redirect/tombstone heavy
rather than name-reuse friendly. The storage cost is preferable to silent
supply-chain identity reuse.

## Stress tests

### Organization rename

```text
before: oldorg/parser
PackageId: P

after:  neworg/parser
PackageId: P
```

Source importing `dep:parser/...` is unchanged. Existing locks remain bound to P.
The registry retains `oldorg/parser -> P` continuity.

### Git repository move

```text
old URL -> PackageId P, revision R, content C
new URL -> PackageId P, revision R, content C
```

An explicit source update changes retrieval metadata only. Package/module
identity remains P/R/C.

### Registry CDN migration

```text
cdn1.example -> artifact bytes A -> content C
cdn2.example -> artifact bytes A -> content C
```

No manifest, lock graph, package identity, or module identity changes.

### Archive format migration

```text
foo.zip     -> ArtifactDigest A -> ContentIdentity C
foo.tar.zst -> ArtifactDigest B -> ContentIdentity C
```

The resolver sees the same logical package content.

### Conflicting authority metadata

```text
authority A: P 1.2.3 -> content C1
authority B: P 1.2.3 -> content C2
```

The graph must not silently pick one. The same package/release tuple claiming
different content is an integrity conflict.

### Major-version coexistence

```text
A -> parser P @ 1.9.0
B -> parser P @ 2.1.0
```

Both may exist in the locked graph because release version is part of resolved
instance identity. Import aliases are resolved in each importing package's own
dependency context. No `/v2` rename or second PackageId is intrinsically required.

### Independent fork

```text
original: PackageId P
fork used as a different package: PackageId Q
```

Even if the fork initially has identical content, it is a different logical
package lineage. Retaining P is appropriate only for an authorized clone/mirror/
continuation of the same package.

## Prior-art comparison

### Go modules

Go's module path is the module's canonical identity and import-path prefix and is
usually tied to where source can be found. This makes the system understandable
but causes repository/module-path moves and major-version path changes to become
visible to consumers.

**Protos take:** keep the useful explicit module/package graph but avoid making a
retrieval/location path the durable package identity. `dep:` aliases already give
Protos the indirection needed to do so cleanly.

Reference:
https://go.dev/ref/mod
https://go.dev/doc/modules/gomod-ref

### Cargo / Rust

Cargo's `PackageId` combines package name, version, and source; its `SourceId`
represents the package source. Cargo source replacement allows mirrors while
preserving the conceptual source being replaced, which validates keeping mirror
selection out of program source.

**Protos take:** retain explicit provenance and source replacement/mirror
indirection, but separate durable package lineage from source URL and release
version so rename/source migration do not automatically create a new package
identity.

References:
https://doc.rust-lang.org/cargo/reference/resolver.html
https://doc.rust-lang.org/cargo/reference/source-replacement.html

### Swift Package Manager registry

Swift's package registry identifies packages with a readable scoped
`scope.package-name` identifier.

**Protos take:** scoped human locators are good discovery/user-facing metadata,
but Protos can keep an additional stable opaque package ID because imports are
already dependency-relative. This avoids making administrative scope movement a
logical identity change.

Reference:
https://github.com/swiftlang/swift-package-manager/blob/main/Documentation/PackageRegistry/Registry.md

### npm

npm package scopes are ownership/naming namespaces. In particular, scoped
packages cannot simply be transferred to another scope; moving scope requires a
new package name.

**Protos take:** do not make administrative scope ownership the durable package
identity. Retain old locators as redirects/tombstones and preserve `PackageId`
through an authorized transfer.

Reference:
https://docs.npmjs.com/transferring-a-package-from-a-user-account-to-another-user-account/

### Semantic Versioning

SemVer 2.0.0 provides a familiar, precise ordering model for published releases,
including prereleases. It also explicitly excludes build metadata from version
precedence.

**Protos take:** use strict SemVer ordering for the initial release-value model,
but reject build metadata in published Protos package versions so equal-
precedence version strings cannot become competing release identities. Keep
constraint semantics as a separate Protos resolver design.

Reference:
https://semver.org/

### OCI content descriptors

OCI uses explicit content descriptors/digests and algorithm-tagged digest syntax;
its image model also distinguishes manifests/content from transport/retrieval
mechanics.

**Protos take:** every persisted digest is algorithm-tagged, and logical package
content is identified independently from a concrete compressed package artifact.

References:
https://specs.opencontainers.org/image-spec/descriptor/
https://specs.opencontainers.org/image-spec/manifest/

### Nix content-addressing

Nix demonstrates that content-addressing should depend on intrinsic object
content/graph information rather than incidental production details, and that
immutable store objects can be cached independently of the dependency declaration
that selected them.

**Protos take:** the package store caches immutable content identities; storage
location is not package identity. Protos does not adopt Nix store-path semantics
or its full build model.

Reference:
https://nix.dev/manual/nix/2.35/store/store-object/content-address.html

## Rejected alternatives

### Name/scope as the only package identity

Rejected as the durable internal model because organizational rename or scope
transfer would necessarily create a new logical package or require global source
rewrites.

### Registry URL as package identity

Rejected because registry API/CDN/domain migration and mirrors are transport /
authority concerns, not package semantics.

### Git repository URL as package identity

Rejected because repository moves/forks and hosting-provider migration should not
intrinsically redefine a package lineage.

### Content hash as `PackageId`

Rejected because every legitimate release changes content. Content addressing is
excellent for immutable release verification/store lookup but wrong for the
stable identity spanning releases.

### Publisher key as `PackageId`

Rejected because ownership transfer and key rotation are normal security
operations and should not redefine the package. Publisher authorization belongs
to the authority/trust layer.

### Major version in package identity

Rejected initially. Major-version coexistence is represented by different exact
release nodes of the same PackageId rather than by creating a new package
identity solely because SemVer major changed.

### Build metadata in published ReleaseVersion

Rejected initially. Build/target/artifact facts have dedicated identity layers;
allowing equal-precedence SemVer build variants as release IDs adds ambiguity
without a demonstrated package-level need.

## What this design commits versus leaves open

### Strong exploratory commitments before implementation

```text
PackageId is stable, opaque, and not location/name/content-derived.
PackageLocator is human-facing and renameable.
AuthorityIdentity is not a download URL.
RetrievalLocator is non-semantic.
ReleaseVersion is separate from PackageId.
Published releases are immutable.
ContentIdentity is separate from ArtifactDigest.
Digests carry method/algorithm identity.
Same PackageId + ReleaseVersion cannot map to different content in one graph.
Major versions do not inherently create new PackageIds.
Workspace/path packages are mutable development nodes, not fake mutable releases.
```

### Deliberately open

- exact `PackageId` binary/text format and generation algorithm;
- exact package locator/scoping grammar and registry API;
- exact authority identity/authentication representation;
- registry federation and cross-authority migration proof;
- exact content-tree canonicalization method;
- initial hash algorithm and future multi-digest transition policy;
- physical package archive format;
- physical `protos.lock` encoding;
- dependency constraint syntax and resolution algorithm;
- whether public/private dependency exposure becomes a first-class resolver rule;
- signatures, transparency logs, publisher keys, revocation and recovery;
- target/native artifact descriptors.

## Recommended next audit

Before package implementation, the next high-cost choice is the **dependency
constraint and resolver model**:

- exact requirement grammar;
- highest-versus-minimum selection policy;
- compatible-version unification;
- coexistence rules;
- prerelease admission;
- major-zero behavior;
- public-dependency identity exposure;
- conflict diagnostics;
- deterministic lock generation.

That audit can build on stable package/release/content identities without needing
to reopen names, mirrors, caches, or archive formats.
