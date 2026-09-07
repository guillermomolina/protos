# TOOL001-F2D — Workspace Execution Preflight and PackageExecutionPlan

Status: **IN_PROGRESS through CLOSED F2D3C1 read-only Package Tool preflight**
Nature: non-normative Package Tool / host-integration design
Design checkpoint: 2026-09-07

## Purpose

F2A/F2B/F2C now provide:

```text
canonical physical protos.lock
physical root/member manifests
semantic ResolutionRootV1
canonical resolution-input digest
read-only stale comparison
```

The remaining normal-execution boundary is to convert that already-validated
project state into the exact inert plan consumed by a mechanical package-backed
module resolver.

F2D deliberately starts with a complete **workspace-only** execution subset.
That subset is independently useful and does not require a package store,
ContentIdentity tree verification, registry access, Git fetching, network
authority, or candidate selection.

A lock containing registry or Git nodes is not partially executed. Until exact
external materialization exists, workspace preflight rejects such a graph.

## F2D decomposition

```text
F2D1     plan ABI + runtime-name/preflight contract               CLOSED
F2D2     pure workspace execution-state + plan construction       CLOSED
F2D3     mechanical host handoff + workspace run parent           IN_PROGRESS
F2D3A    immutable host DTO + defensive plan detach               CLOSED
F2D3B    exact workspace package-backed module resolver           CLOSED
F2D3B1    package identity + source mechanism                     CLOSED
F2D3B1A   canonical workspace ModuleKey codec                     CLOSED
F2D3B1B   physical source mechanism parent                        CLOSED
F2D3B1B1   selected project-root anchor + detached package index  CLOSED
F2D3B1B2   exact member-location directory binding                CLOSED
F2D3B1B2A  exact direct-child directory lookup                    CLOSED
F2D3B1B2B  confined canonical member-location traversal           CLOSED
F2D3B1B2C  immutable package -> physical-directory binding        CLOSED
F2D3B1B3   logical module -> exact regular .protos source         CLOSED
F2D3B2    resolver routing                                        CLOSED
F2D3B2A  self: routing                                            CLOSED
F2D3B2B  dep: edge/export routing                                 CLOSED
F2D3B2C  std: delegation + resolver closure                       CLOSED
F2D3C    command preflight + tool/application authority split     IN_PROGRESS
F2D3C1   read-only Package Tool preflight -> detached plan        CLOSED
F2D3C2   detached plan -> separately-authorized application       READY
F2D3C3   public workspace-run wiring + F2D3/F2D closure           BLOCKED_BY_DEPENDENCIES
```

F2D is bounded to workspace-only execution. Closing it will not claim external
locked-node execution.

After F2D closes, a fresh audit may allocate the external-materialization
continuation required for registry/Git nodes. TOOL001-F2 and TOOL001-F therefore
remain open.

## Why workspace-only is a valid first execution subset

Workspace nodes are intentionally mutable development state. Their lock identity
is PackageId plus the root/member relation; they do not require immutable
ContentIdentity.

The root lock still fixes:

- the authoritative root PackageId;
- every participating workspace-member PackageId;
- every declaring-node alias -> exact target-node edge;
- the resolution-input digest proving the resolver-affecting manifest state is
  current.

Therefore a graph containing only workspace nodes can be validated and executed
without inventing immutable external-package storage.

By contrast, registry/Git nodes carry mandatory ContentIdentity in lock-format
1. A host must not turn a locator, Git URL, cache filename, directory scan, or
download result into executable package authority without materialization and
content verification.

## Two views of physical manifest state

F2B intentionally excludes `[exports]` from `ResolutionRootV1`, because exports
affect module visibility after package selection rather than dependency
selection. F2C's current `assemble(...)` therefore cannot by itself be the whole
execution-plan input.

F2D2 must preserve two related views from the same parsed physical manifests:

```text
ProjectExecutionStateV1 {
    resolutionRoot: ResolutionRootV1

    packages: [
        {
            location: "" | canonical workspace member location
            packageId: PackageId
            manifest: full ManifestV1
        }
    ]
}
```

The root package uses `location = ""`.

`resolutionRoot` remains exactly the F2B digest input. `packages[*].manifest`
retains execution-only metadata such as `exports`.

The implementation should avoid parsing the same manifest twice merely to
recover exports. F2D2 may extend `self:ResolutionRoot` with a richer assembly
entry while preserving the already-published `assemble(...) -> ResolutionRootV1`
surface.

## Runtime portable-name policy v1

Schema v1 structurally preserves dependency aliases, export keys, and internal
module Strings. F2D1 closes their initial runtime semantic validator.

One **portable segment** is:

```text
ASCII letter
followed by zero or more:
    ASCII letter
    ASCII digit
    _
```

A portable segment is rejected when its complete ASCII-case-insensitive spelling
is one of:

```text
CON PRN AUX NUL
COM1 COM2 COM3 COM4 COM5 COM6 COM7 COM8 COM9
LPT1 LPT2 LPT3 LPT4 LPT5 LPT6 LPT7 LPT8 LPT9
```

A **portable logical module name** is one or more portable segments separated by
literal `/`.

There are no empty segments, `.`/`..`, backslash separators, Unicode
normalization, case folding, extension suffixes, drive syntax, absolute paths,
environment expansion, or host-native separator interpretation.

This intentionally reuses the portable segment discipline already implemented
for `std:` module resolution. Package-backed names do not inherit the
standard-library-only rejection of first segment `core`; `core` is not a
privileged package module name.

### Dependency alias

A schema-v1 dependency alias used by runtime resolution must be exactly one
portable segment.

This makes:

```text
dep:<alias>/<public-export-name>
```

unambiguous without escape syntax.

The structural manifest parser remains unchanged: a TOML key outside this
runtime domain may still be parsed as schema-v1 data, but execution preflight
fails at the runtime-name invariant.

### Export key

Each `[exports]` key is one portable logical module name and is the
package-public name visible to consumers.

### Export value

Each `[exports]` value is one portable logical module name and names the
package-internal module selected by that public export.

Duplicate decoded TOML keys are already rejected by TOML/schema ownership. Two
distinct export keys may map to the same internal module; that is an intentional
public alias, not duplicate module identity.

### `self:` and `dep:` interpretation

For a module executing as package node N:

```text
self:<logical-module>
```

selects `<logical-module>` inside N directly. It is not restricted by N's
exports table.

For:

```text
dep:<alias>/<public-export-name>
```

preflight/runtime resolution:

1. finds exactly one locked dependency edge owned by N with that alias;
2. obtains the target node T;
3. looks up `<public-export-name>` in T's current validated manifest exports;
4. obtains T's internal logical module name;
5. resolves that internal module in T's source root.

Consumers cannot bypass exports by spelling another file inside T.

`std:` remains owned by the standard-distribution resolver and does not pass
through the package graph.

Bare/other module specifier policy remains unchanged by F2D1.

## Workspace-only lock reconciliation

F2D2 must load one canonical lock and one `ProjectExecutionStateV1`, then fail
closed unless all of the following hold.

### Header / stale state

- `LockFile.isStale(...)` is false;
- lock-format is 1;
- resolver-version is 1;
- resolution method/algorithm/digest are already validated by F2B3.

A missing, unreadable, non-canonical, or malformed lock remains an ordinary
failure. Preflight must not classify those states as merely stale.

### Root

The lock root must be exactly:

```text
workspace <current root PackageId>
```

### Workspace members

The lock workspace-member set must equal the physical active root workspace
exactly:

```text
declaration String -> workspace PackageId
```

No missing, extra, duplicate, renamed, or PackageId-mismatched member is
permitted.

The declaration is the canonical member location selected by F2B2/F2C.

### External nodes

For the bounded F2D workspace subset:

```text
registryNodes.size() == 0
gitNodes.size() == 0
```

Any external node is an explicit unsupported-materialization failure. Preflight
must not:

- ignore it;
- replace it with a workspace package of the same PackageId;
- search cache/store directories;
- fetch it;
- trust locator/fetch provenance as source content;
- skip ContentIdentity verification.

### Dependency edges

Every participating manifest dependency must be a path dependency normalized by
F2C to a workspace target.

For every declaring package:

```text
current dependency alias set
==
locked dependency alias set for declaring workspace ref
```

and every locked target must equal the F2C normalized target PackageId.

No missing or extra lock edge is accepted.

This validation is intentionally stronger than the resolution-input digest:
the digest proves that resolver inputs match the lock's recorded input identity;
edge reconciliation proves that the canonical body is the exact workspace graph
those inputs are expected to execute.

Registry/Git dependency declarations fail the bounded F2D subset even if their
lock nodes are otherwise structurally valid.

## PackageExecutionPlanV1

The Protos-owned workspace plan is ordinary inert data conceptually equivalent
to:

```text
PackageExecutionPlanV1 {
    generation: 1

    root: workspace-node-ref

    packages: [
        {
            ref: workspace-node-ref
            location: "" | canonical member location

            exports: Map<
                portable-public-export-name,
                portable-internal-logical-module-name
            >
        }
    ]

    dependencies: [
        {
            declaring: workspace-node-ref
            alias: portable-segment
            target: workspace-node-ref
        }
    ]
}
```

The plan contains no Filesystem capability, File, Process, Closure, resolver,
network/store authority, mutable cursor, package-manager object, or lockfile
text.

For the workspace subset, source location remains repository-relative. The host
already owns the selected physical project root at command bootstrap and can
mechanically bind:

```text
location ""
    -> project root

location "a/b"
    -> project root / a / b
```

after defensive validation against the same portable relative-component rule.

No host absolute path is repository identity.

## Plan ownership and mutability boundary

The ordinary Protos plan is trusted tool output but not direct mutable resolver
state.

At handoff, the host must:

1. validate the complete generation-1 shape;
2. validate every String/domain/ref again defensively;
3. verify root/member relative locations stay under the selected project root;
4. copy/detach all plan data into an immutable host DTO;
5. install the resolver only from that detached DTO.

Later mutation of the Protos Arrays/Maps/objects must not mutate the installed
application resolver.

Project code never receives the original tool Process/activation or the plan
object as authority.

## Canonical package-backed module identity

For the workspace subset, a resolved module key must distinguish:

```text
workspace PackageId
internal logical module name
```

within the installed resolution plan.

Dependency aliases and export aliases are lookup relations, not module identity.
Thus two imports reaching the same workspace PackageId and same internal logical
module produce the same canonical ModuleKey inside that resolver, independent of
which dependency alias/export alias reached it.

The exact host string serialization of that ModuleKey remains an internal
implementation detail of F2D3; it must not accidentally include absolute cache
or checkout paths.

## Source loading

The host resolver maps a validated internal logical module name to:

```text
<package source root>/<segment>.../<final-segment>.protos
```

using exact case-sensitive portable names.

It must reject:

- case-fold ambiguity;
- missing/non-regular target;
- symlink/real-path escape;
- path components outside the portable logical-name grammar.

The existing standard-library resolver provides implementation precedent, but
package source roots and package graph identity remain distinct resolver policy.

## Preflight authority separation

F2D3 must keep tool and application authority separate.

Workspace run preflight receives only the explicit authority needed to:

```text
read project tree manifests
read protos.lock
```

It performs no lock mutation and no version selection.

After the inert plan is detached, the package-tool execution domain ends. The
application starts with the exact package-backed resolver and only application
capabilities. It does not inherit the Package Tool Filesystem merely because
preflight used it.

## F2D2 exact scope

F2D2 is READY to implement pure Protos:

- runtime-name validators;
- richer physical `ProjectExecutionStateV1` assembly preserving full ManifestV1;
- canonical lock load/stale gate over that state;
- root/member equality validation;
- workspace-only external-node rejection;
- exact path dependency edge reconciliation;
- export validation/projection;
- ordinary inert `PackageExecutionPlanV1`.

F2D2 does not change `ProtosCli` or install a host resolver.

## F2D3 exact scope

After F2D2 publishes, F2D3 may add the narrow host bridge/resolver and
command-scoped workspace preflight needed to execute the plan.

F2D3 must remain mechanical. It must not parse TOML/lock syntax, perform stale
policy, select versions, interpret path dependencies, decide exports, scan
packages, or materialize external nodes.

## External-node boundary after F2D

Registry/Git execution requires a separate continuation with, at minimum:

```text
exact locked-node lookup
materialized source root
ContentIdentity verification
package-store confinement
no ambient visibility
```

Fetching may be a distinct later operation. Normal execution may fetch an exact
already-selected immutable artifact only under an explicitly designed
capability/policy, but it never resolves a new version.

F2D1 does not allocate that continuation prematurely. It records the dependency
so a future slice cannot make external nodes executable by weakening plan
validation.

## F2D2 implementation closure

F2D2 is CLOSED.

Published bundled-Protos surfaces:

```text
self:ResolutionRoot.assembleState(projectTreeFilesystem)
self:RuntimeNames
self:ExecutionPlan.build(projectTreeFilesystem)
```

`assembleState` performs the same single physical root/member parse as the
existing `assemble`, but returns both the unchanged F2B `resolutionRoot` and
full parsed ManifestV1 package records. `assemble` remains the compatibility
projection returning only `resolutionRoot`.

`ExecutionPlan.build` loads one canonical lock through the same explicit
read-only tree Filesystem, validates the F2B3 header against the assembled root,
rejects registry/Git nodes, reconciles the exact root/member mapping and every
workspace path dependency alias/target, validates/copies exports through the
F2D1 portable runtime-name contract and returns a fresh ordinary
PackageExecutionPlanV1.

The builder performs no lock mutation, version selection, external
materialization, cache scanning, fetch, CLI dispatch or host resolver
installation.

`TOOL001-F2D3` is now READY for the mechanical host detach/resolver +
command-scoped preflight boundary.


## F2D3 decomposition refinement and F2D3A closure

The former monolithic F2D3 implementation is now formally decomposed because
three independently valid boundaries exist and have materially different failure
surfaces:

```text
F2D3A  ordinary Protos plan -> immutable host DTO
F2D3B  detached DTO -> exact workspace self:/dep:/std: resolver
F2D3C  command-scoped preflight -> separate application execution
```

F2D3A is CLOSED.

Published host-internal surfaces:

```text
ProtosPackageExecutionPlan
ProtosPackageExecutionPlanAdapter.detach(rawPlan, projectRoot)
```

The adapter accepts only the exact generation-1 ordinary plan shape frozen by
F2D1/F2D2, validates workspace refs, root/package/location uniqueness, in-root
real directories, dependency referential integrity and portable alias/export
runtime names, then recursively copies the data into immutable Java records,
Lists and Maps.

PackageId remains opaque: F2D3A does not invent a PackageId alphabet or
normalization rule.

Mutating the original Protos Arrays/Maps/objects after `detach` cannot mutate the
detached host DTO. F2D3A does not install a module resolver, parse TOML or lock
syntax, perform stale policy, select dependencies, scan/fetch packages, change
CLI dispatch, or create application authority.

F2D3B is READY and consumes only this detached DTO plus the already-selected
workspace project root and standard-library resolver.

## F2D3B refinement and F2D3B1A closure

The resolver parent is further decomposed before implementation:

```text
F2D3B
├── F2D3B1  package identity + source mechanism
│   ├── F2D3B1A  canonical workspace ModuleKey codec       CLOSED
│   └── F2D3B1B  package-root binding + exact source path  READY
└── F2D3B2  resolver routing
    ├── F2D3B2A  self: routing                             dependency-gated
    ├── F2D3B2B  dep: edge/export routing                  dependency-gated
    └── F2D3B2C  std: delegation + resolver closure        dependency-gated
```

This decomposition keeps identity independent from filesystem lookup and keeps
source lookup independent from import-specifier routing.

F2D3B1A is CLOSED.

The host-only canonical workspace module key is:

```text
pkg-workspace:v1:<base64url-no-padding UTF-8 PackageId>:<base64url-no-padding UTF-8 internal-logical-module>
```

The serialized spelling is an internal implementation detail, not repository or
package identity. Its invariants are:

- the identity inputs are exactly workspace PackageId and internal logical module;
- PackageId remains opaque and exact;
- internal logical module must satisfy the already-frozen F2D1 portable logical
  module-name contract;
- dependency aliases, export aliases, workspace locations, project-root paths,
  checkout/cache locations and Filesystem identity never enter the key;
- the workspace-specific domain prevents a future external immutable package key
  from accidentally colliding with this bounded mutable-workspace identity;
- decoding accepts only the canonical URL-safe base64 spelling without padding
  and revalidates the logical module name.

`ProtosWorkspacePackageModuleKey` does not implement `ProtosModuleResolver`, load
source, touch a Filesystem, interpret `self:`/`dep:`/`std:`, inspect the
PackageExecutionPlan graph, or change CLI behavior.

The focal test is intentionally Java: canonical ModuleKey is normatively an
internal host/runtime concept and need not be exposed as a Protos object. Protos
source conformance resumes in the routing slices where `import(String)` behavior
becomes observable.


## F2D3B1B refinement and F2D3B1B1 closure

The former `package-root binding + exact source path` slice is further
decomposed before physical lookup work:

```text
F2D3B1B1  selected project-root anchor + detached package index  CLOSED
F2D3B1B2  exact member-location directory binding                READY
F2D3B1B3  logical module -> exact regular .protos source         dependency-gated
```

The separation is intentional:

- selecting/anchoring the host project root and indexing inert plan records is a
  representation step;
- turning a workspace `location` String into a physical member directory is a
  path/confinement step;
- turning a portable logical module into a physical source file is a distinct
  exact-case/source-type step.

F2D3B1B1 is CLOSED.

`ProtosWorkspacePackageProjectIndex.bind(projectRoot, detachedPlan)`:

- normalizes the selected host project-root Path;
- resolves that selected root once to a real directory;
- keeps both selected and real root as host-only state;
- indexes detached package nodes by exact opaque PackageId and exact plan
  `location` String;
- rejects empty/duplicate PackageIds, duplicate locations, empty package sets,
  and disagreement between the plan root ref and the unique empty root
  location;
- provides exact lookup by PackageId/location for later resolver mechanics.

B1B1 deliberately does **not** traverse a non-empty workspace member location.
It therefore does not yet decide exact-case host lookup, symlink behavior, or
member-directory confinement. Those belong to B1B2.

B1B1 also does not inspect logical module names, append `.protos`, read source,
construct ModuleKeys, implement `self:`/`dep:`/`std:` routing, or touch CLI
dispatch.

Its focal is Java intentionally: this is host Path/DTO indexing mechanics, not
observable Protos `import(String)` behavior. Protos-owned conformance resumes
when routing becomes observable in B2.

## F2D3B1B2 refinement and F2D3B1B2A closure

B1B2 is split before traversal into:

```text
F2D3B1B2A  exact direct-child directory lookup             CLOSED
F2D3B1B2B  confined canonical member-location traversal    READY
F2D3B1B2C  immutable package -> physical-directory binding dependency-gated
```

B1B2A receives one already-separated workspace location component and enumerates the already-selected parent directory. It compares the stored child filename String exactly; it does not pass that semantic component to host path parsing, case-fold it, Unicode-normalize it, search recursively, or infer a package from a basename. Empty String, `.`, `..`, and `/` are rejected at this one-component boundary.

This slice deliberately does not call `toRealPath()` on the child and does not decide whether a directory symlink remains inside the selected project root. B1B2B owns multi-component traversal plus complete real-path/symlink confinement. B1B2C later applies that closed traversal to the detached B1B1 package index.

The focal remains Java because this is host `Path` mechanics only. No Protos-observable import behavior begins here.

After publication:

```text
TOOL001-F2D3B1B2A CLOSED: YES
TOOL001-F2D3B1B2B READY: YES
TOOL001-F2D3B1B2C BLOCKED_BY_DEPENDENCIES: TOOL001-F2D3B1B2B
TOOL001-F2D3B1B2 CLOSED: NO
TOOL001-F2D3B1B3 BLOCKED_BY_DEPENDENCIES: TOOL001-F2D3B1B2C
```

## F2D3B1B2B closure — confined canonical member-location traversal

B1B2B consumes exactly one already-canonical non-root workspace member location. It validates the detached host spelling defensively, splits only on literal `/`, and uses the closed B1B2A primitive for every physical child selection. It does not use `Path.resolve(component)` to reinterpret a semantic component as host path syntax.

After each exact child selection, B1B2B resolves that directory to its real path before another component is traversed. The resolved directory must remain beneath the selected real project root. Therefore a directory symlink is accepted only when its target remains inside that root; a symlink/alias that resolves outside fails closed. Traversal continues from the resolved in-root directory, and the final result is the real member directory.

B1B2B owns no PackageId or whole-plan relation. It does not enumerate the detached package index, bind root `location = ""`, search recursively, inspect module names, append `.protos`, construct ModuleKeys, implement `self:`/`dep:`/`std:` routing, or touch CLI/application authority. Those boundaries remain B1B2C, B1B3, B2 and C respectively.

The focal remains Java because the slice is host filesystem/path mechanics. No observable Protos import behavior starts here.

After publication:

```text
TOOL001-F2D3B1B2A CLOSED: YES
TOOL001-F2D3B1B2B CLOSED: YES
TOOL001-F2D3B1B2C READY: YES
TOOL001-F2D3B1B2 CLOSED: NO
TOOL001-F2D3B1B3 BLOCKED_BY_DEPENDENCIES: TOOL001-F2D3B1B2C
```

## F2D3B1B2C closure — immutable package-directory binding

B1B2C composes the already-closed host boundaries instead of adding new path
semantics. It consumes one `ProtosWorkspacePackageProjectIndex`, iterates only
the detached plan packages already indexed by B1B1, binds root `location = ""`
exactly to the anchored real project root, and sends every non-root canonical
location through B1B2B.

The resulting host records pair the exact detached `PackageNode` with its real
physical directory and are indexed immutably by exact opaque PackageId and exact
canonical location. There is no basename inference, recursive search, ambient
workspace discovery, case folding, Unicode normalization, or fallback path. If
any indexed non-root member cannot be bound by B1B2B, construction fails before
a package-backed resolver can use the plan.

B1B2C still does not inspect logical module names, append `.protos`, read source,
construct ModuleKeys, implement `self:`/`dep:`/`std:` routing, or touch CLI/
application authority. B1B3 now owns the distinct logical-module -> exact source
file mapping boundary.

The focal remains Java because this is host Path + detached DTO integration.
Observable Protos import conformance remains deferred to B2 routing.

After publication:

```text
TOOL001-F2D3B1B2A CLOSED: YES
TOOL001-F2D3B1B2B CLOSED: YES
TOOL001-F2D3B1B2C CLOSED: YES
TOOL001-F2D3B1B2  CLOSED: YES
TOOL001-F2D3B1B3  READY: YES
TOOL001-F2D3B1B    CLOSED: NO
TOOL001-F2D3B1     CLOSED: NO
TOOL001-F2D3B      CLOSED: NO
```

## F2D3B1B3 closure — exact confined package source lookup

B1B3 consumes one exact opaque workspace PackageId plus one already-frozen
portable internal logical module name. It obtains only the package directory
already bound by B1B2C; no manifest, workspace, basename or ambient filesystem
search participates.

The logical name is defensively revalidated through `ProtosPackageRuntimeNames`.
Each non-final portable segment is selected from the current physical directory
by exact stored spelling. The final validated segment is selected as exactly
`<segment>.protos`. Every lookup also counts ASCII case-fold-equivalent directory
entries and fails when more than one spelling exists, preserving deterministic
portable behavior on a case-sensitive host rather than accepting a tree that
would be ambiguous on a case-insensitive host.

After every selected directory and after the final source selection, the real
path must remain beneath the selected package root. This confinement is
intentionally package-local, not merely project-local: a symlink may stay inside
its own package, but it cannot reach another workspace member's source. The
final real target must be a regular file. Missing, wrong-case-only,
case-ambiguous, non-regular and escaping targets fail closed.

The returned physical path is source location only. Module identity remains the
closed B1A `PackageId + internal logical module` ModuleKey domain; physical paths,
symlink targets, dependency aliases and export aliases do not enter identity.
B1B3 performs no source read, `self:`/`dep:`/`std:` routing, import dispatch,
CLI wiring or application-authority transition.

The focal remains Java because B1B3 is still host source-location mechanics.
Observable package import behavior begins in B2A and should primarily use Protos
source conformance.

After publication:

```text
TOOL001-F2D3B1B3 CLOSED: YES
TOOL001-F2D3B1B  CLOSED: YES
TOOL001-F2D3B1   CLOSED: YES
TOOL001-F2D3B2   READY: YES
TOOL001-F2D3B2A  READY: YES
TOOL001-F2D3B2B  BLOCKED_BY_DEPENDENCIES: TOOL001-F2D3B2A
TOOL001-F2D3B2C  BLOCKED_BY_DEPENDENCIES: TOOL001-F2D3B2B
TOOL001-F2D3B     CLOSED: NO
TOOL001-F2D3C     CLOSED: NO
```

## F2D3B2A closure — importer-relative `self:` routing

B2A introduces the first package-backed `ProtosModuleResolver` behavior over the
already-closed B1 representation/source mechanism. Construction consumes only
the selected project root plus the already-detached immutable
`ProtosPackageExecutionPlan`; it composes B1B1/B1B2/B1B3 mechanically and does
not re-read manifests, lockfiles or package policy.

Application bootstrap does not resolve an ambient `self:` with no package
context. Instead `entryModule(logicalModule)` explicitly selects the plan root
PackageId, validates that exact root-package source through B1B3 and emits the
closed B1A canonical ModuleKey. This keeps root-entry selection distinct from
ordinary importer-relative routing.

For an executing package module N, `self:<logical-module>` requires the
importing ModuleKey to decode in the workspace B1A domain and its exact PackageId
to belong to the installed plan. The target logical name is then located only in
that same PackageId through B1B3 and encoded with the same PackageId. The
package's exports map is deliberately not consulted: F2D1 defines `self:` as
direct access inside N.

An absent importing ModuleKey, a foreign ModuleKey, or a workspace ModuleKey for
a PackageId outside the installed plan fails closed. B2A also keeps `dep:`,
`std:` and bare/other spellings unsupported; B2B and B2C own those later routing
steps. `loadSource` accepts only canonical workspace keys belonging to the plan
and reads the exact B1B3-confined source as UTF-8.

The focal now uses real Protos source modules executed through the Core import
and module-runtime path. It proves root and member `self:` imports, PackageId
preservation and direct access to a non-exported internal member module. Java is
only the host harness and negative-boundary assertion layer.

After publication:

```text
TOOL001-F2D3B1   CLOSED: YES
TOOL001-F2D3B2   IN_PROGRESS
TOOL001-F2D3B2A  CLOSED: YES
TOOL001-F2D3B2B  READY: YES
TOOL001-F2D3B2C  BLOCKED_BY_DEPENDENCIES: TOOL001-F2D3B2B
TOOL001-F2D3B     CLOSED: NO
TOOL001-F2D3C     CLOSED: NO
```

## F2D3B2B closure — exact dependency edge/export routing

B2B extends the same B2A package resolver; it does not introduce a parallel
resolver or re-read package metadata. Construction indexes only the detached
`PackageExecutionPlan.dependencies` relation after confirming every declaring
and target PackageId belongs to the already-bound workspace package index. The
index key is the exact pair `(declaring PackageId, alias)`. Duplicate aliases for
one declaring package and targets outside the installed plan fail closed even
when a host caller manually constructs the DTO instead of using the normal F2D3A
adapter.

For an executing workspace package module N,
`dep:<alias>/<public-export-name>` first requires N's canonical B1A ModuleKey and
therefore its exact PackageId. The first `/` separates the one-segment dependency
alias from the public export logical name; the remaining public export may itself
contain portable `/`-separated segments. Both are revalidated through the frozen
runtime-name ABI.

The resolver then follows exactly N's detached alias edge to target package T,
looks up the public export String exactly in T's detached exports map, obtains
that export's internal logical module name, revalidates it defensively, and asks
B1B3 for that exact target-package source. The returned identity is exactly
`T PackageId + internal logical module`. Dependency aliases, public export names,
workspace locations and physical paths therefore remain lookup relations and do
not enter ModuleKey identity.

There is no fallback from a missing export to a physical/internal module name.
Consequently even an existing `Hidden.protos` in T is unreachable through
`dep:<alias>/Hidden` unless T explicitly exports the public name `Hidden`.
Wrong-case aliases/exports, missing edges, malformed routes and attempts by a
package to reuse another declaring package's alias fail closed. Once dependency
resolution enters T, later `self:` imports naturally remain relative to T because
the canonical returned ModuleKey carries T's PackageId.

The focal is Protos-source behavior executed through Core import/module runtime.
It proves a root dependency import through a public facade, target-local `self:`
continuation, canonical identity convergence across different alias/export
relations, export-bypass rejection and declaring-package edge ownership. Java
remains only the host fixture/negative-boundary harness.

B2B still does not delegate `std:` or close the resolver. B2C owns standard
resolver composition, rejection of all remaining unsupported spellings and final
B2/B closure.

After publication:

```text
TOOL001-F2D3B1   CLOSED: YES
TOOL001-F2D3B2   IN_PROGRESS
TOOL001-F2D3B2A  CLOSED: YES
TOOL001-F2D3B2B  CLOSED: YES
TOOL001-F2D3B2C  READY: YES
TOOL001-F2D3B     CLOSED: NO
TOOL001-F2D3C     CLOSED: NO
```

## F2D3B2C closure — Standard Library delegation and resolver closure

B2C completes the workspace resolver without absorbing Standard Library policy. Exact `std:` specifiers are delegated unchanged, with the importing ModuleKey context, to one explicitly selected Standard Library resolver. Successful delegated keys must remain in the `std:` domain and source loading for those keys delegates back to the same resolver.

Workspace keys remain owned by B1A/B1B3. Bare/other specifiers and foreign keys fail closed. The two-argument constructor remains package-only with a rejecting Standard Library delegate; F2D3C owns selection/installation of the complete three-argument resolver during command-scoped preflight.

After publication:

```text
TOOL001-F2D3B2   CLOSED: YES
TOOL001-F2D3B2C  CLOSED: YES
TOOL001-F2D3B     CLOSED: YES
TOOL001-F2D3C     READY: YES
TOOL001-F2D3      CLOSED: NO
TOOL001-F2D       CLOSED: NO
```

## F2D3C decomposition refinement and F2D3C1 closure

F2D3C is further decomposed because tool preflight, application execution and
public driver wiring have different authority and failure surfaces:

```text
F2D3C1  read-only Package Tool preflight -> detached plan
F2D3C2  detached plan -> separately-authorized application Process
F2D3C3  public workspace-run wiring + F2D3/F2D closure
```

C1 is CLOSED. `ProtosWorkspacePackagePreflight.build(...)` creates one fresh
semantic Package Tool Process under the exact bundled `package` resolver. The
Process receives empty arguments/environment, no standard streams and no default
Filesystem. Its initial activation receives exactly one additional capability:
`projectTreeFilesystem`, backed by `ProtosNioReadOnlyTreeFilesystemBackend`
confined to the selected project root.

The tool Process executes only the already-published mechanical wrapper:

```protos
Plan: import("self:ExecutionPlan")
Plan.build(projectTreeFilesystem)
```

The returned ordinary Protos plan is defensively detached through the closed
F2D3A adapter while the tool Process is still alive. The tool Process is then
terminated before C1 returns. The public C1 result is only the immutable
`ProtosPackageExecutionPlan`; no activation, Process, Filesystem, resolver,
stream, tool module instance or mutable Protos plan escapes the boundary.

Because the project authority backend is read-only, C1 cannot publish or rewrite
`protos.lock` and cannot mutate `protos.toml`. Stale/malformed/external-node
policy remains owned by the bundled `ExecutionPlan.build` implementation and
fails before any application Process exists.

C1 does not create an application Process, select an application entry, install
application capabilities, add a CLI command or reserve public syntax. C2 owns
the separately-authorized application execution mechanism; C3 owns final driver
wiring only after C2 closes.

After publication:

```text
TOOL001-F2D3B   CLOSED: YES
TOOL001-F2D3C   IN_PROGRESS
TOOL001-F2D3C1  CLOSED: YES
TOOL001-F2D3C2  READY: YES
TOOL001-F2D3C3  BLOCKED_BY_DEPENDENCIES: TOOL001-F2D3C2
TOOL001-F2D3     CLOSED: NO
TOOL001-F2D      CLOSED: NO
```
