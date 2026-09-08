# TOOL001-F2E — External Immutable-Package Execution

Status: **IN_PROGRESS — F2E1/F2E2 CLOSED; F2E3 READY; F2E4/F2E5 dependency-gated**
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

### ContentIdentity tree canonicalization was open; F2E1 now closes it

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
F2E1  protos-package-tree-v1 ContentIdentity contract              CLOSED
F2E1A logical-tree domain + portable path/entry-kind contract       CLOSED
F2E1B canonical byte stream + method/hash contract                  CLOSED
F2E1C independent conformance vectors + F2E1 closure                CLOSED
F2E2  verified read-only package-store binding                     CLOSED
F2E2A  captured-Filesystem ContentIdentity canonicalizer/verifier  CLOSED
F2E2B  exact selected-root capture + verified-capture host custody CLOSED
F2E2C same-capture integration + F2E2 closure                      CLOSED
F2E3  external-node execution-plan construction                    READY
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

## F2E1 final closure and F2E2 prerequisite result

F2E1 is CLOSED by A/B/C. The exact package-tree identity is owned by
`PACKAGE_CONTENT_IDENTITY.md` and `PACKAGE_CONTENT_IDENTITY_VECTORS.md`.

The required E2 post-E1 capability audit now has a concrete answer. Verification
needs confined directory enumeration, exact stored child names, entry-kind
observation without following links/special entries, regular-file acquisition,
and one stable logical snapshot or fail-closed mutation detection.

Current standard Filesystem exposes `open`, `replace` and `remove`, but not that
tree-observation boundary. LIB004 already records that directory
enumeration/stat/symlink inspection cannot be manufactured from ambient host
APIs. A package-only Java/NIO recursive verifier would violate F2E0, so B009
records the missing general semantic owner and F2E2 is BLOCKED.

## D046 / B009 normative resolution

D046 / specification revision `0.1.383` closes the semantic gap identified
after F2E1 without moving Package Tool policy into Java.

The general Core surface is:

```text
Filesystem.entries(path)
Filesystem.captureTree(path)
```

The critical pattern is **capture then verify then use the same captured
Filesystem**. `captureTree` need not freeze a hostile mutable source at one
physical instant. It produces a fresh immutable logical tree from exact
race-safe selections; F2E2 will compute the closed `protos-package-tree-v1`
identity over that captured capability and only admit it when the digest matches
the lock. Execution must then bind the same verified capture, never re-open the
original store Paths.

B009 therefore moves to READY. F2E2 remains dependency-blocked until I024
publishes the general D046 implementation.


## Post-I024 dependency closure

I024-D closes the general D046 implementation/conformance prerequisite and B009.
`TOOL001-F2E2` is now READY. Its next implementation must capture the exact
already-selected package-store root through the provisioned Filesystem, validate
`protos-package-tree-v1` against that immutable captured Filesystem, and hand the
same captured authority to subsequent execution planning. It must not re-read the
mutable/source store tree after verification and must not introduce package-only
Java/NIO traversal.

F2E3/F2E4/F2E5 remain dependency-gated in order behind F2E2.

## F2E2 decomposition refinement

The post-I024 audit confirms that the missing work is no longer one indivisible
implementation step. Three responsibilities have different semantic and host
failure surfaces and must remain separate:

```text
F2E2   verified read-only package-store binding                    IN_PROGRESS
F2E2A  captured-Filesystem ContentIdentity canonicalizer/verifier  CLOSED
F2E2B  exact selected-root capture + verified-capture host custody READY
F2E2C  same-capture integration + F2E2 closure                     BLOCKED_BY_DEPENDENCIES
```

### F2E2A — captured-Filesystem ContentIdentity policy

A owns package policy and is implemented in bundled Protos.

Its input is already the immutable read-only Filesystem produced by D046. A
must implement the closed `protos-package-tree-v1` contract without acquiring,
discovering or reopening a store root:

- enumerate recursively only through `Filesystem.entries`;
- reject link/other entries rather than following or silently ignoring them;
- validate the exact canonical portable path domain, including sibling ASCII
  case-fold collisions;
- require exact root regular `protos.toml`;
- include every valid regular-file leaf and treat directories only as structure;
- read exact regular bytes through ordinary Filesystem/File facilities;
- serialize the frozen E1B path/content map exactly, including arbitrary-size
  minimal base-128 varuint framing and canonical unsigned ASCII path order;
- support the mandatory `protos-package-tree-v1` + `sha256` pair through the
  existing standard SHA256 library;
- compare exact lowercase digest identity fail-closed; and
- on successful verification, return/pass through the **same supplied captured
  Filesystem** rather than another path, re-capture or reconstructed authority.

`std:io/Files.readAllBytes` may provide the already-published complete binary
File-read/cleanup mechanism. A must not duplicate host traversal in Java, decide
package-store layout, fetch content, scan by PackageId/version, mutate the lock,
extend PackageExecutionPlan, install a resolver, or choose how captured backing
crosses the later host boundary.

A is independently executable because ContentIdentity membership,
canonicalization, method/algorithm support and verification semantics are already
closed by F2E1 and D046.

### F2E2B — exact selected-root capture and host custody

B owns the irreducible host-mechanical boundary deliberately excluded from A.

It starts from **one exact store entry already selected by the caller/upper
mechanism**. It must not infer that entry from PackageId, version, locator,
registry authority, Git URL, directory basename or ambient store contents.

B must provision/confine the general read-only Filesystem authority for that
selected root, obtain one D046 immutable capture, expose that capture to A for
verification, and preserve custody of that exact verified captured backing for
the later F2E3/F2E4 handoff. The concrete custody representation is intentionally
not selected by this decomposition: B must re-audit current I024 captured-backend
and Package Tool Process-lifetime mechanics before choosing it.

The important invariant is fixed now:

```text
selected mutable/materialized root M
        |
        | capture exactly once for this binding
        v
immutable capture C
        |
        | A verifies ContentIdentity(C)
        v
verified C
        |
        +----> later planning/resolver handoff uses C
```

No step after verification may re-open `M` to obtain executable bytes.

B remains host mechanism only. It must not reimplement canonical path/hash policy,
parse locks/manifests, solve versions, scan/fetch the store, or make an external
node executable.

### F2E2C — integrated same-capture closure

C composes A and B and publishes final F2E2 evidence.

It must exercise the frozen F2E1 fixed vectors through the production
ContentIdentity implementation and prove at the integration boundary that:

- valid exact content verifies;
- mismatched content/method/algorithm and invalid tree shapes fail closed;
- source/store mutation after capture cannot alter the verified bytes;
- the authority handed forward is the same immutable capture that was verified;
- Package Tool verification receives no ambient store visibility; and
- no package-specific Java/NIO tree walker appears.

C closes F2E2 only after those properties and the full applicable test suite pass.
At that point `TOOL001-F2E3` becomes READY. F2E4/F2E5 remain dependency-gated.

This decomposition does not change F2E1, D046, I024, lock format, ContentIdentity
bytes, package-store physical layout, acquisition/fetch policy or public run
semantics.

## F2E2A closure — captured-Filesystem ContentIdentity policy

F2E2A is CLOSED.

Published bundled-Protos implementation:

```text
self:ContentIdentity

digest(capturedFilesystem)
    -> { method, algorithm, hex }

verify(capturedFilesystem, expectedContentIdentity)
    -> same capturedFilesystem on exact match
```

The implementation consumes the already-captured Filesystem supplied by its
caller. It never calls `captureTree`, inspects a host/store path, searches by
PackageId/version, performs registry/Git acquisition, or introduces Java/NIO tree
walking.

`digest` implements the closed `protos-package-tree-v1` policy directly in
Protos:

- iterative directory traversal through `Filesystem.entries`, so physical tree
  depth does not create one recursive Protos frame per directory;
- exact E1A portable-ASCII segment validation plus Windows-reserved basename
  rejection and per-directory ASCII-case-fold collision rejection;
- exact root regular `protos.toml` requirement;
- link/other rejection without following;
- directories as structure only and every valid regular leaf as identity input;
- exact regular bytes through the existing `std:io/Files.readAllBytes` helper;
- canonical complete-path unsigned ASCII ordering through
  `std:collections/Array.sort`;
- E1B FILE/END framing and minimal arbitrary-precision base-128 varuint;
- standard `std:crypto/SHA256` over the canonical stream; and
- exactly 64 lowercase hexadecimal digest digits under method
  `protos-package-tree-v1` and algorithm `sha256`.

`verify` validates the recorded method/algorithm/digest shape fail-closed before
tree hashing, compares the exact resulting digest, and returns the same supplied
captured Filesystem object only on a match. It does not recapture or reconstruct
authority.

The F2E2A behavioral fixtures remain Protos-owned under
`protos/tests/package-tool/content-identity/**`. Their host materialization and
string-binding mechanics are executed by the already-published single
`ProtosPackageToolProtosTest` Java bridge. No `ProtosPackageToolContentIdentityTest`
or other per-corpus Java wrapper is introduced. Fixture execution is RootActor-
local through `ProtosRootTaskExecution`, so D046 Future observation occurs in the
same execution model used by the consolidated TOOL001 runner.

The production implementation is checked against the frozen E1 vectors for
minimal content, canonical ordering/empty-file framing, binary content, 128/300
varuint boundaries and both exact-case identities. A pure Protos fixture checks
the standalone frozen varuint values through `2^70`. Representative negative
evidence covers missing root manifest, invalid artifact character, reserved
basename, sibling ASCII-case-fold collision, symbolic-link rejection,
unsupported method/algorithm and malformed digest spelling.

F2E2C intentionally remains the owner of the complete cross-boundary fixed-vector
and equivalence matrix together with the same-capture custody proof. F2E2A does
not claim that final integration closure.

After this slice:

```text
TOOL001-F2E2   IN_PROGRESS
TOOL001-F2E2A  CLOSED
TOOL001-F2E2B  READY
TOOL001-F2E2C  BLOCKED_BY_DEPENDENCIES: TOOL001-F2E2B
TOOL001-F2E3   BLOCKED_BY_DEPENDENCIES: TOOL001-F2E2
```

No normative Protos specification, lock format, ContentIdentity bytes,
package-store layout, acquisition/fetch policy, captured-backend custody design,
PackageExecutionPlan shape, resolver identity or public-run semantics change in
F2E2A.

Implementation note: exact file bytes are awaited into ordinary local values before
the canonical record object is constructed. Record construction is therefore
suspension-free while preserving the frozen path/content map and the same
ContentIdentity bytes.


## F2E2B closure — exact selected-root capture and host custody

F2E2B is CLOSED after explicit project-owner approval of the ownership/lifetime
architecture on 2026-09-08 following alternative, scalability and future-evolution review.

The selected boundary is one run-scoped host custody object for one exact immutable capture:

```text
selected materialized root M
        |
        | exact D046 capture, once
        v
immutable captured authority C
        |
        +--> fresh Filesystem(C, PackageToolDomain)
        |         |
        |         +--> F2E2A verifies ContentIdentity(C)
        |
        +--> retain SAME C after Package Tool Process termination
                  |
                  +--> later fresh Filesystem(C, application/resolver domain)
```

The production host machinery is `ProtosCapturedFilesystemCustody`. It owns the exact
`CapturedBackend` and its release callback independently of any Protos Actor-domain wrapper.
`materialize(activation)` creates a fresh standard structurally read-only `Filesystem` bound to
that activation's execution domain while delegating to the same captured backend. The source NIO
backend is closed immediately after the one selected-root capture, so later materialization never
reopens the original store `Path`. `close()` releases the run-owned captured backing exactly once.

The standard Filesystem bridge now has one host-internal captured-capability rematerialization
entry that reuses the existing D046 read-only adapter; no second Filesystem semantics or Actor
transfer exception is introduced. The current NIO implementation factors its already-published
secure capture machinery so host custody can capture the selected authority root without inventing
a second traversal/copy path.

The durable contract is intentionally backing-opaque. Today's implementation-managed temporary
blob backing is not part of F2E2B identity or policy; a future in-memory, CAS/deduplicated,
copy-on-write/versioned or remote immutable representation may implement the same custody without
changing `Filesystem`, F2E2A verification or the later execution boundary. There is no global
capture registry and no cross-run mutable coordination point.

F2E2B deliberately does not associate custody with `PackageId`, registry/Git node identity or a
`PackageExecutionPlan` record. That mapping remains F2E3 work. The detached execution plan stays
inert and contains no Filesystem/backend/Process/resolver/store authority. F2E2B also does not
select CAS persistence, cache GC, fetch, acquisition, store layout or distributed transport.

Focused evidence proves that source deletion after capture cannot change bytes observed through
either the Package Tool-domain or a separately bootstrapped application-domain view; those views
are fresh non-identical `ProtosFilesystemValue` objects backed by the same captured backend. It
also proves deterministic idempotent host-custody release and rejection of rematerialization after
close. Existing I024 capture/materialization focals plus the complete suite remain required by the
publication launcher.

After this slice:

```text
TOOL001-F2E2   IN_PROGRESS
TOOL001-F2E2A  CLOSED
TOOL001-F2E2B  CLOSED
TOOL001-F2E2C  READY
TOOL001-F2E3   BLOCKED_BY_DEPENDENCIES: TOOL001-F2E2
```

No normative Protos specification, lock format, ContentIdentity bytes, package-store physical
layout, acquisition/fetch policy, `PackageExecutionPlan` authority model, resolver identity or
public-run semantics change in F2E2B. Implementation version becomes `0.2.262-SNAPSHOT`.

## F2E2C closure — same-capture verification integration

F2E2C is CLOSED and closes parent F2E2.

The production composition boundary is `ProtosPackageContentVerification`. It accepts one exact
already-selected materialized package root plus the inert expected ContentIdentity fields. It does
not discover a root by PackageId/version/locator, scan a store, fetch content, parse a lock, or
perform package-specific tree traversal.

The host path is deliberately one-way:

```text
selected materialized root M
        |
        | ProtosCapturedFilesystemCustody.captureSelectedRoot(M)
        | capture exactly once; source authority closes
        v
run-owned immutable capture C
        |
        | fresh Filesystem(C, PackageToolDomain)
        v
self:ContentIdentity.verify(view(C), expected)
        |
        | integration requires result === supplied view(C)
        v
return custody(C) after Package Tool Process TERMINATED
        |
        +--> later fresh Filesystem(C, application/resolver domain)
```

The Package Tool verification Process receives no ambient/root Filesystem for the selected store.
Its only package-content authority is the captured Filesystem materialized from custody; method,
algorithm and hex are inert String inputs. Java does not duplicate ContentIdentity validation:
unsupported method/algorithm/digest spelling, digest mismatch and invalid captured-tree shape all
fail through the already-published bundled-Protos `self:ContentIdentity` policy.

On every unsuccessful verification path, host custody is closed rather than handed forward. On
success the verifier must return exactly the supplied Package Tool-domain Filesystem object, after
which that Process is terminated before the still-open custody is returned. A later domain receives
a fresh non-transferable Filesystem wrapper backed by the same immutable capture C; neither the
source Path nor the Package Tool Filesystem crosses the Actor/Process boundary.

Focused integration evidence composes the frozen F2E1 vectors already exercised through the single
`ProtosPackageToolProtosTest` with the new host gate. It proves a frozen binary-tree vector verifies,
the verification Process terminates with no root Filesystem, deleting/replacing the source after
verification cannot change bytes visible through a later independently bootstrapped domain,
post-capture source additions remain invisible, digest mismatch fails closed, unsupported
method/algorithm fail in Protos policy, and an invalid captured tree is not admitted. The production
host gate contains no Java/NIO tree walker and never reopens source content after verification.

F2E2 therefore now provides the required verified immutable-material boundary for the next slice.
F2E3 may decide how exact registry/Git nodes map to their already-verified run-local custody, but C
does not pre-select that PackageId/registry/Git association, extend `ProtosPackageExecutionPlan`,
install a resolver or change public run behavior.

After this slice:

```text
TOOL001-F2E2   CLOSED
TOOL001-F2E2A  CLOSED
TOOL001-F2E2B  CLOSED
TOOL001-F2E2C  CLOSED
TOOL001-F2E3   READY
TOOL001-F2E4   BLOCKED_BY_DEPENDENCIES: TOOL001-F2E3
TOOL001-F2E5   BLOCKED_BY_DEPENDENCIES: TOOL001-F2E4
```

No normative Protos specification, lock format, ContentIdentity bytes, captured-Filesystem
semantics, package-store physical layout, acquisition/fetch policy, PackageExecutionPlan authority,
ModuleKey identity, resolver behavior, public-run semantics or capture-backing policy changes in
F2E2C.
