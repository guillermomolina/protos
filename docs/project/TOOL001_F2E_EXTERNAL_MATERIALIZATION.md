# TOOL001-F2E — External Immutable-Package Execution

Status: **IN_PROGRESS through CLOSED F2E1B canonical byte-stream/hash contract**
Nature: non-normative Package Tool / host-integration project record
Allocated after: `TOOL001-F2D` workspace-only execution closure

## Purpose

`TOOL001-F2D` closed a complete workspace-only normal-execution subset. Registry
and Git nodes remain present in canonical lock-format 1 but are still rejected by
execution preflight.

F2E owns the next bounded continuation: make an **already selected and already
materialized immutable external node** executable only after the exact locked
content has been verified and converted into inert execution-plan data.

F2E does not own version discovery, fresh selection, update, registry protocol,
network transport, credentials or package publication.

## Mandatory inherited invariant

External source must never become executable merely because a host path, cache
directory, registry locator, Git URL, revision or matching PackageId was found.

Before an external source root enters `PackageExecutionPlan`, all of these must
hold:

```text
exact locked node identity
exact materialized source root
package-store confinement
recorded ContentIdentity verification
no ambient cache/store visibility
no version solving
no lock mutation
```

Registry and Git nodes therefore remain fail-closed until F2E reaches its final
integration slice.

## Fresh audit findings

### ContentIdentity tree canonicalization is not yet frozen

`docs/design/PACKAGE_IDENTITY_VERSIONING.md` defines ContentIdentity as the
algorithm-tagged digest of a versioned canonical logical package tree and gives
the illustrative method name `protos-package-tree-v1`.

That record explicitly leaves the exact tree algorithm to a focused
artifact-format audit. The future contract must define included files/manifest,
canonical relative path encoding, separator/case rules, symlink and special-entry
treatment, semantic/excluded metadata, deterministic bytes, resources and
machine-local exclusions.

Therefore no external store directory can yet be truthfully checked against a
locked ContentIdentity. An ad-hoc recursive host hash would silently invent
package semantics.

### Current Core Filesystem does not expose portable tree observation

The current standard Filesystem surface is intentionally narrow:

```text
open
replace
remove
```

It does not standardize broad tree observation such as directory enumeration,
stat/type queries, directory creation, symlink inspection or recursive traversal.

F2E must not bypass this with package-only host calls such as
`PackageNative.walkTree`, `hashDirectory` or ambient cache scanning.

After F2E1 freezes the exact observations required by
`protos-package-tree-v1`, F2E2 must re-audit the then-current general capability
surface. If portable semantics are still missing, record the proper general
Filesystem prerequisite rather than hiding it in Package Tool Java.

### Fetching is not normal execution

Normal execution consumes an already-selected exact graph. Acquisition is a
different authority boundary.

F2E scopes first to **already materialized** external content. Missing exact
content fails. Normal run must not silently resolve, query a registry, follow a
mutable Git ref, download, mutate the lock or write the store.

A later explicit `fetch` path may consume the exact lock under separately
designed store-write/network authority.

## Cost-aware decomposition

```text
F2E   external immutable-package execution                         IN_PROGRESS
F2E0  prerequisite audit + decomposition                           CLOSED
F2E1  protos-package-tree-v1 ContentIdentity contract              IN_PROGRESS
F2E1A logical-tree domain + portable path/entry-kind contract       CLOSED
F2E1B canonical byte stream + method/hash contract                  CLOSED
F2E1C independent conformance vectors + F2E1 closure                READY
F2E2  verified read-only package-store binding                     BLOCKED_BY_DEPENDENCIES
F2E3  external-node execution-plan construction                    BLOCKED_BY_DEPENDENCIES
F2E4  external canonical ModuleKey + source resolver               BLOCKED_BY_DEPENDENCIES
F2E5  public run integration + F2 external-execution closure       BLOCKED_BY_DEPENDENCIES
```

### F2E1 — canonical logical package tree

Freeze the exact `protos-package-tree-v1` contract before implementing
verification. Independent implementations must agree on the same digest for the
same logical package tree.

The audit must explicitly cover regular files, directories/empty directories if
semantic, symlinks/special entries, relative-path encoding/order, file bytes,
manifest/resources, ignored/generated/VCS content and concurrent mutation.

It must also freeze the persisted method/algorithm/digest relationship and
compatibility behavior for future methods/algorithms.

### F2E2 — verified read-only store binding

After E1 fixes required observations, design/implement the minimum general
capability path that can bind one **already-selected** store entry, confine
traversal to store authority and verify exactly the locked ContentIdentity.

No ambient scan by PackageId/name/version is allowed.

### F2E3 — external execution-plan construction

Extend Protos-owned preflight only after E2 returns verified immutable material.

Registry identity must preserve at least:

```text
PackageId
exact ReleaseVersion
ContentIdentity
```

Git identity must preserve at least:

```text
PackageId
exact revision
ContentIdentity
```

Retrieval locator, registry locator, authority endpoint, cache/store path and
dependency alias remain provenance/lookup data.

### F2E4 — host resolver extension

Defensively detach the extended plan and mechanically bind verified external
source roots.

Canonical immutable external module identity is the exact immutable package
instance plus internal logical module. Mirrors/store paths/aliases never enter
ModuleKey identity.

### F2E5 — public run integration and F2 closure

Extend the published `protos run <entry> [args...]` path to mixed
workspace/external exact graphs without changing the CLI entry convention.

F2E5 may close F2 only when missing/corrupt/mismatched external material fails
before application authority begins, normal execution performs no solving/fetch
or lock mutation, and store verification authority does not leak into the
application Process.

## Explicit exclusions

F2E does not by itself implement registry discovery, networking/HTTP,
credentials, fresh resolution/update, fetch/store-write acquisition, archive
transport, ArtifactDigest download validation, publication or store GC.

## F2E0 closure

Direct executable continuation is premature for two independent reasons:

1. the exact ContentIdentity logical tree contract remains open;
2. the current general Filesystem surface does not yet expose portable tree
   observation sufficient to implement an unknown future contract.

The correct next work is F2E1, not an external resolver shortcut.

After publication:

```text
TOOL001-F2D   CLOSED
TOOL001-F2E   IN_PROGRESS
TOOL001-F2E0  CLOSED
TOOL001-F2E1  READY
TOOL001-F2E2  BLOCKED_BY_DEPENDENCIES: TOOL001-F2E1
TOOL001-F2E3  BLOCKED_BY_DEPENDENCIES: TOOL001-F2E2
TOOL001-F2E4  BLOCKED_BY_DEPENDENCIES: TOOL001-F2E3
TOOL001-F2E5  BLOCKED_BY_DEPENDENCIES: TOOL001-F2E4
TOOL001-F2    CLOSED: NO
TOOL001-F     CLOSED: NO
TOOL001       CLOSED: NO
```

## F2E1 decomposition refinement and F2E1A closure

The ContentIdentity contract is decomposed because three boundaries can be
reviewed independently without publishing temporary semantics:

```text
F2E1A  logical-tree domain + portable path/entry-kind contract     CLOSED
F2E1B  canonical byte stream + method/hash contract                READY
F2E1C  independent conformance vectors + F2E1 closure              BLOCKED_BY_DEPENDENCIES
```

E1A is owned in `docs/design/PACKAGE_CONTENT_IDENTITY.md`.

It freezes the abstract logical package tree before selecting serialization:
ContentIdentity observes an already-materialized package payload, not an arbitrary
source checkout. Every valid regular-file leaf in that payload is semantic.
There is no hidden `.gitignore`, VCS configuration, build-directory heuristic,
timestamp/permission rule or file-extension allowlist.

The root must contain exact regular file `protos.toml`. Directories are structural
only; empty directories are non-semantic. Symbolic links and other traversal/
special-entry forms are rejected in v1. File content is exact bytes and all host
metadata is excluded. Package payload paths use the conservative portable ASCII
artifact-path domain selected by E1A, including case-fold collision and Windows
reserved-name rejection.

E1A does not yet define the canonical byte stream or digest. E1B owns that
serialization/hash boundary. E1C then publishes independent fixed vectors and
closes parent E1. F2E2 remains blocked on the whole E1 parent, not merely E1A.

## F2E1B closure — canonical stream + digest support

E1B freezes one binary serialization over the E1A map. Entries sort by exact
unsigned ASCII path bytes. The stream begins with the method-domain separator
`protos-package-tree-v1` plus NUL, then zero or more tagged FILE records and one
END tag. Each path/content field is length-prefixed with the canonical minimal
arbitrary-precision unsigned base-128 varuint; file bytes are fed directly and no
directory/metadata/per-file-hash records are introduced.

Current ContentIdentity support hashes that complete canonical stream with
standard SHA-256 and emits 64 lowercase hexadecimal digits under the separately
persisted `sha256` algorithm token. Method and digest algorithm remain orthogonal:
tree/serialization changes require a new method token, while a future hash
transition over exactly the same stream may use a new algorithm token.

E1C is now READY to publish independently computed fixed vectors and close F2E1.
F2E2 remains blocked on the parent F2E1 closure.
