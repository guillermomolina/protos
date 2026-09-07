# TOOL001-F2D — Workspace Execution Preflight and PackageExecutionPlan

Status: **IN_PROGRESS through CLOSED F2D3A host-detach implementation**
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
F2D1   plan ABI + runtime-name/preflight contract             CLOSED
F2D2   pure workspace execution-state + plan construction     CLOSED
F2D3   mechanical host handoff + workspace run parent         IN_PROGRESS
F2D3A  immutable host DTO + defensive plan detach             CLOSED
F2D3B  exact workspace package-backed module resolver         READY
F2D3C  command preflight + tool/application authority split   BLOCKED_BY_DEPENDENCIES
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
