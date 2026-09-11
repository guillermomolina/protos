# D085 — Static canonical Project Binding provider and language-server acquisition contract

Status: **RATIFIED — Candidate F′ selected**  
Decision family: `Dxxx` implementation-independent tooling architecture  
GitHub issue: #370  
Primary consumer: `LM009-G3` / #360  
Triggered by: D082 ratification at `3073a68d9c90a9aae74380af571fd8270fedf165`  
Allocated: 2026-09-11  
Approved: 2026-09-11  
Validation class: `GOVERNANCE_DOCUMENTATION_ONLY`

## Decision summary

Protos selects **Candidate F′ — Package-owned minimal canonical ProjectBinding
projection + bounded canonical source inventory + validated candidate-root
acquisition through a replaceable editor-neutral provider**.

D085 closes the project-binding authority question exposed by D082 without making
the language server a second Package Tool and without requiring live guest/Truffle
execution for ordinary static requests.

The selected contract is:

1. Package/project semantics remain owned by the canonical Protos package tooling.
   The language server and editor integration consume project authority; they do
   not independently reconstruct package semantics.
2. The package authority exposes a **minimal canonical project projection** that is
   sufficient to authenticate one exact project binding: project/root witness,
   exact workspace package identities, exact canonical package locations and a
   versioned/freshness identity sufficient to reject stale bindings.
3. The canonical projection is intentionally **not** a complete serialized list of
   every source file. Adding, removing or editing an ordinary `.protos` source must
   not require regenerating package metadata merely to make the source visible to
   static tooling.
4. A replaceable editor-neutral `ProjectBindingProvider` consumes one validated
   candidate root plus the canonical package projection and produces an immutable
   `ProjectBinding` snapshot for static tooling.
5. Source inventory is bounded strictly to package roots already authorized by the
   canonical projection. Inventory does not discover projects, workspace members,
   packages, dependencies or semantic roots.
6. A source is admitted to the inventory only when its package-relative path is the
   exact canonical inverse of the already-owned logical-module -> confined
   `.protos` source mapping. Exact case, confinement, collision and portability
   checks remain authoritative; arbitrary extension matching is insufficient.
7. LSP `workspaceFolder`, editor roots, command-line current directories or another
   host path may be supplied only as **candidate locations**. A candidate becomes a
   Protos project binding only after exact canonical validation at that location.
8. Baseline acquisition performs no upward/downward project search, recursive
   project discovery, nearest-manifest inference, URI-ancestry inference or
   open-document-based project creation.
9. Missing, stale, unsupported or invalid canonical package/project metadata fails
   the affected binding closed. The provider does not repair it, reinterpret it or
   fall back to guessed workspace membership.
10. Manifest, lock, workspace-membership, PackageId, canonical package-location or
    projection-generation changes invalidate the affected `ProjectBinding`. Source
    content/add/remove events inside already-authorized package roots may update the
    bounded source inventory incrementally without redefining project identity.
11. One language-server session may consume zero or more independent exact bindings.
    Duplicate or incompatible aliases for the same canonical project identity must
    not create parallel semantic identities.
12. The `ProjectBindingProvider` boundary is mechanism-independent. A future package
    daemon, BSP-like provider, remote build/project service or process-per-toolchain
    deployment may implement the same contract without changing D082/G3 semantics.
13. The baseline does **not** require a package daemon, long-lived Package Tool guest
    Process, persistent sidecar containing every source, remote service or LSP-local
    manifest/lock parser.
14. Ordinary runtime execution and users who do not start static tooling pay no
    ProjectBinding/index service cost.
15. D082 remains authoritative for per-project index ownership, exact live-document
    overlay, workspace-only baseline symbol scope, lazy/pay-for-use indexing and
    session-level query aggregation.

Ratifying D085 does **not** itself implement the ProjectBinding provider or LM009-G3.
It converts the blocker from an unresolved design decision into bounded implementation
work. G3 remains blocked until the selected provider/projection/source-inventory
prerequisite is published and validated.

## Existing repository boundary

The current repository already has useful exact authorities, but none by itself is
D085's complete static binding provider:

- `ProtosWorkspacePackageProjectIndex` binds an already-selected project root to an
  already-validated detached `ProtosPackageExecutionPlan`.
- `ProtosWorkspacePackageDirectoryIndex` binds plan package locations to exact
  confined physical workspace package directories.
- `ProtosWorkspacePackageSourceLookup` resolves an already-known
  `(PackageId, logicalModule)` to the exact confined `.protos` source.
- `ProtosPackageExecutionPlan` carries package identities, locations, exports and
  dependency edges, but is intentionally not a complete inventory of internal
  source modules.
- the canonical plan is currently built by ordinary Protos Package Tool code through
  `ExecutionPlan.build(projectTreeFilesystem)` and detached by the host preflight.
- manifest `[exports]` is a visibility boundary, not a package-internal source
  inventory.

D085 preserves these authorities instead of treating their present host/guest split as
permission to duplicate them in the language server.

## Why the complete source list is not package metadata

A full generated sidecar containing every source would be semantically workable, but it
would create unnecessary freshness coupling:

```text
create Foo.protos
    -> generated source list becomes stale
    -> Package Tool regeneration required
```

Ordinary mutable workspace sources are expected to change frequently. Package identity,
workspace membership and canonical package roots change much less frequently. F′ keeps
those rates separate:

```text
canonical package/project projection
    -> authorizes exact package roots and package identity

bounded source inventory
    -> observes current valid module sources only inside those roots
```

This keeps the source inventory live and incremental without allowing filesystem
presence to become project/package authority.

## Candidate-root acquisition

D082 already rejects equating an editor workspace folder with Protos project identity.
D085 nevertheless permits a folder/path to be used as an explicit **candidate**.

The distinction is normative tooling architecture:

```text
candidate path
    -> validate canonical project projection exactly there
    -> valid: create exact ProjectBinding
    -> absent/stale/invalid: no ProjectBinding
```

The baseline must not silently transform that into:

```text
candidate path
    -> walk parents/children
    -> find something project-like
    -> invent semantic project identity
```

This preserves editor neutrality: VS Code, another editor, a CLI, a test harness or a
future remote client may all submit candidates without becoming project authorities.

## Canonical bounded source inventory

The provider may enumerate physical entries **only after** package roots are canonically
bound. That enumeration is an implementation mechanism for observing members of an
already-authorized domain, not for deciding the domain itself.

For each candidate `.protos` regular source under one bound workspace package root, the
provider must derive the package-relative logical module under the existing portable
runtime-name rules and require exact round-trip equivalence through the canonical
logical-module -> source mapping. Invalid names, alternate spellings, traversal,
ambiguous case-fold collisions, escapes, unsupported entry kinds and paths outside the
bound package root are not inventory members.

Non-`.protos` files remain ordinary package/project files but do not become language
modules merely because they reside in the package tree.

External dependency and Standard Library roots remain outside the D082 baseline source
inventory even if another package subsystem can resolve them exactly. They may be added
later as separate immutable/coarser index layers.

## Invalidation and freshness

A binding is invalidated when an input that defines project/package identity changes,
including as applicable:

- canonical project/root projection generation or freshness witness;
- `protos.toml` resolution-relevant package/workspace identity;
- canonical `protos.lock` relation/freshness;
- workspace membership;
- PackageId;
- canonical package location or root custody.

After invalidation, static tooling must reacquire through the canonical provider before
continuing project-wide semantics. Stale metadata is not a reason to fall back to folder
scanning.

Within an already-valid binding, ordinary source add/remove/rename/content events may
incrementally update the source inventory and later D082 project index. A rename that
changes canonical logical-module identity is represented as removal plus addition of
exact module identities, not an inferred identity mutation.

## Comparative prior-art survey

The D085 audit compared project/build authority and source-membership architecture,
not merely symbol-search implementation.

### rust-analyzer

rust-analyzer provides the strongest abstract-model precedent. Concrete Cargo or
explicit project descriptions are lowered into an analyzer-facing project graph rather
than making each IDE feature reinterpret Cargo. `rust-project.json` demonstrates that
an external project authority can feed the same analysis model for non-Cargo build
systems.

Lesson: static analysis should consume a stable abstract project binding; the binding
producer can evolve independently.

### SourceKit-LSP + Build Server Protocol

BSP provides the strongest explicit authority boundary. Build targets and their sources
are supplied by the build system; target base directories and target URIs are not
implicitly source membership or semantic identity. SourceKit-LSP can therefore consume
build/project truth without treating editor folder layout as sufficient authority.

Lesson: project identity and source membership belong behind an explicit provider
contract. D085 adopts this separation without requiring the operational weight of a
BSP daemon today.

### Metals + BSP

Metals shows that the same build-server boundary can scale across materially different
build systems, including large target graphs. The editor/language tooling does not need
to embed every build tool's project semantics.

Lesson: a replaceable provider is a credible long-term escape path for large monorepos,
generated sources and remote builds.

### HLS + hie-bios

hie-bios explicitly places responsibility on the build tool to describe the environment
needed for a source/component. HLS consumes that description rather than reimplementing
Cabal, Stack or another build system.

Lesson: Package Tool describes; language tooling consumes. This is highly aligned with
Protos authority philosophy.

### clangd

clangd cleanly separates compilation/project inputs from live/background/static/remote
index layers and proves that local live state can coexist with very large persistent or
remote indexes.

Lesson: D085 must not encode today's local source observation mechanism into semantic
identity. A future remote provider/index remains possible.

### gopls

gopls has excellent Session/View/Snapshot incremental custody, but it also supports
project/build inference from workspace/open-file context. That inference is useful for
Go ergonomics but is deliberately not copied into Protos.

Lesson: retain incremental snapshots; reject editor-open-state as project identity.

### TypeScript / tsserver

Configured and externally supplied projects establish project membership before
language-service navigation. Inferred projects provide useful fallback ergonomics but
would violate D082/D085 if translated into Protos project identity.

Lesson: the external-project family is useful precedent; inferred-project fallback is
not.

### Pyright

Pyright demonstrates efficient per-workspace program state and prioritization of open
files, but config/open/import discovery is looser than the exact identity boundary
selected for Protos.

Lesson: per-project state scales; source/project authority still needs a stricter
Protos-specific owner.

### Ruby LSP

Ruby LSP demonstrates live source reindexing and process-per-root isolation when roots
require incompatible Ruby/dependency environments.

Lesson: process-per-project/toolchain is a valid future deployment escape path and need
not be baseline architecture today.

### Eclipse JDT LS

JDT proves that explicit project models plus persistent background indexes can scale to
very large mature codebases, at the cost of a much heavier workspace institution.

Lesson: persistent/project services are available when scale demands them but should
not be paid as baseline Protos machinery.

### OCaml-LSP + Dune/Merlin

OCaml tooling reinforces delegating build/project truth to Dune/Merlin while exposing
the danger of stale build-derived state when it is not layered cleanly with current
editor content.

Lesson: project binding and current source content are separate contracts.

### Dart Analysis Server

Dart's workspace evolution demonstrates memory/scaling costs when project/package
analysis contexts proliferate excessively.

Lesson: D085 should authorize exact package domains while D082 retains project-level,
not heavyweight-per-package, index ownership.

### ElixirLS

ElixirLS demonstrates the usability and baseline cost of indexing workspace,
dependencies and Standard Library together.

Lesson: broad dependency/stdlib search remains an optional future layer, not baseline
D085 scope.

## Prior-art suitability for D085

Scores are 0–10 and measure suitability as precedent for this decision, not overall
tool quality.

| System | Future endurance | Scalability | Protos philosophy | Total |
| --- | ---: | ---: | ---: | ---: |
| rust-analyzer | 10 | 10 | 9 | 29 |
| SourceKit-LSP + BSP | 10 | 9 | 10 | 29 |
| Metals + BSP | 10 | 10 | 9 | 29 |
| clangd | 10 | 10 | 8 | 28 |
| HLS + hie-bios | 9 | 7 | 10 | 26 |
| gopls | 9 | 9 | 6 | 24 |
| TypeScript / tsserver | 9 | 9 | 6 | 24 |
| Pyright | 8 | 8 | 7 | 23 |
| Eclipse JDT LS | 9 | 9 | 5 | 23 |
| OCaml-LSP + Dune/Merlin | 8 | 6 | 8 | 22 |
| Ruby LSP | 8 | 6 | 7 | 21 |
| Dart Analysis Server | 8 | 7 | 6 | 21 |
| ElixirLS | 7 | 6 | 5 | 18 |

The recurring architecture is:

```text
canonical package/build authority
        -> project graph/binding projection
        -> replaceable project provider
        -> per-project semantic/index state
        <- live editor content overlay
```

F′ takes this shape while keeping the initial mechanism substantially smaller than a
full build-server protocol.

## Candidate set

### A — shared host-native package/project model

Move or duplicate canonical project/manifest/lock interpretation into a host-native
library consumed by runtime, Package Tool and LSP.

Rejected for baseline. It can scale computationally but would either move package
policy away from the existing ordinary-Protos Package Tool ownership or create a
synchronization burden between two semantic implementations.

### B — complete generated ProjectBinding sidecar

Package Tool generates an immutable descriptor including every source module, and the
language server consumes it without interpreting package semantics.

Rejected in favor of F′. Authority is clean, but source additions/removals would make a
complete sidecar stale too often and couple ordinary source editing to metadata
regeneration.

### C — Package Tool subprocess/service on bind/refresh

Ask the canonical Package Tool to construct/detach a full binding whenever the LSP
needs one.

Deferred as a future provider implementation, not baseline requirement. It preserves
Package Tool authority better than a duplicate parser, but process/guest startup,
failure/lifecycle and refresh cost are unnecessary when a smaller projection can carry
the stable authority boundary.

### D — dedicated static manifest/lock reader in tooling/LSP

Reconstruct the needed project subset directly from `protos.toml` and `protos.lock` in
the language-service implementation.

Rejected. This is a second Package Tool/project authority and creates long-term semantic
drift risk.

### E — full binding descriptor supplied by the editor/client

Make VS Code or another client supply exact project roots, plans and source inventories.

Rejected as authority. A client may supply candidate locations, but it must not become
the semantic producer of PackageId, workspace membership or module identity.

### F′ — minimal canonical projection + bounded live source inventory — SELECTED

Package Tool/package authority owns a minimal stable projection. An editor-neutral
replaceable provider validates candidate roots, binds only the exact authorized package
roots and derives current source inventory solely within those roots through exact
canonical module-path round trips.

This separates stable project/package identity from high-churn workspace source
membership while preserving one authority.

### G — defer G3

Keep workspace symbols unsupported until a project projection emerges naturally from
other package work.

Semantically safe, but rejected after the authority boundary has been sufficiently
specified and evidenced. It would deliver no feature and would leave pressure for
future ad-hoc discovery.

### H — live BSP-like package/project provider now

Introduce a long-lived provider/server protocol that returns bindings/sources and emits
project-graph changes.

Strong future architecture, deferred for present scale. It adds daemon/protocol
lifecycle, compatibility, retry, failure and operational machinery before generated
sources/remote builds/large build graphs establish a need.

### I — extend `protos.lock` as the ProjectBinding carrier

Add source/project binding data directly to the canonical dependency lock.

Rejected for baseline. Lock authority and project/source inventory have different
change rates and purposes; ordinary source addition/removal should not create lockfile
churn.

## Mandatory ten-axis comparison

Scores are 1–5. Confidence is HIGH unless noted.

| Criterion | A | B | C | D | E | F′ | G | H | I |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariants | 4 | 5 | 5 | 2 | 3 | 5 | 5 | 5 | 4 |
| Protos alignment | 2 | 5 | 4 | 1 | 2 | 5 | 5 | 4 | 3 |
| Future-option resilience | 3 | 4 | 4 | 2 | 4 | 5 | 5 | 5 | 3 |
| Scalability | 5 | 3 | 3 | 4 | 4 | 5 | 5 | 5 | 4 |
| Conceptual simplicity | 2 | 4 | 3 | 3 | 3 | 4 | 5 | 2 | 3 |
| Portability / implementation freedom | 3 | 5 | 4 | 3 | 4 | 5 | 5 | 5 | 4 |
| Runtime / resource cost | 4 | 4 | 2 | 4 | 4 | 5 | 5 | 3 | 5 |
| Failure / operability | 4 | 4 | 3 | 2 | 3 | 5 | 5 | 3 | 4 |
| Reversibility / migration | 2 | 3 | 4 | 2 | 3 | 5 | 5 | 4 | 3 |
| Evidence maturity / implementation risk | 4 | 4 | 4 | 3 | 4 | 4 | 5 | 5 | 4 |
| **Total / 50** | **33** | **41** | **38** | **26** | **34** | **48** | **50** | **41** | **37** |

Arithmetic is not the decision authority. G scores highest because doing nothing has
minimal correctness and migration risk, but it supplies no ProjectBinding prerequisite
and therefore no route to the requested G3 feature. H is highly future-capable but pays
an unnecessary institution/lifecycle cost at present scale. F′ is the strongest actual
provider architecture.

### Three-axis owner comparison

| Candidate | Future endurance /10 | Scalability /10 | Protos philosophy /10 |
| --- | ---: | ---: | ---: |
| **F′** | **10** | **10** | **10** |
| H live BSP-like provider now | 10 | 10 | 8 |
| B complete sidecar | 8 | 6 | 9 |
| C Package Tool subprocess | 8 | 6 | 8 |
| I extend lock | 7 | 9 | 7 |
| E client descriptor | 8 | 8 | 5 |
| A host-native package model | 7 | 10 | 4 |
| D second static reader | 4 | 8 | 2 |
| G defer | 10 | 10 | 10, feature = 0 |

## Future-scenario stress test

### Large mutable workspaces

F′ does not require serializing every source into package metadata. The source inventory
can update incrementally inside authorized roots while the project projection remains
stable. D082 then updates only the affected per-project index.

### Many packages in one project

Package roots are supplied by canonical project authority; the provider may shard or
parallelize bounded inventory internally without changing package/module identity. The
language server still owns one D082 project index domain rather than one heavyweight
analysis universe per package.

### Many independent projects

Bindings remain isolated. LSP aggregates only at query time. A failure/stale projection
in one project does not authorize a fallback scan and does not poison another binding.

### Generated/non-filesystem sources

Today's bounded physical inventory does not pretend to solve generated/virtual sources.
If such sources become real, the replaceable provider boundary can evolve to H/BSP-like
source enumeration while preserving the same ProjectBinding consumer model.

### Remote builds and very large monorepos

A future provider may live out of process or remotely and stream/project snapshots
without changing G3. Persistent/remote indexing remains D082-layer optimization, not
D085 identity.

### Multiple incompatible toolchains

A future editor integration may assign bindings to separate toolchain-matched language
server processes. Because project identity is per-binding, no global in-process semantic
identity needs to be unwound.

### Native Image / alternate runtime / Bytecode DSL

The contract is inert project/package/source metadata. It does not expose Truffle
Contexts, CallTargets, nodes or guest object identities. Another backend can implement
the same provider/projection contract.

### Actors, Tasks, Processes and distributed runtime execution

Project binding is static tooling authority and does not consult runtime Process/Actor
state. Runtime scaling remains orthogonal.

## Failure model

F′ fails closed when:

- a candidate location has no exact canonical project projection;
- canonical package/project metadata is stale, malformed or unsupported;
- an authorized package location escapes/conflicts with its project boundary;
- two package identities/locations are ambiguous under the owning policy;
- an inventory entry cannot make an exact logical-module/source round trip;
- case/path ambiguity would produce more than one physical spelling;
- a client tries to promote a loose/open document into project identity;
- a metadata-level project change invalidates an existing binding.

Failure is local to the affected binding. Document-local parser features may continue
for loose/open documents under the already-published LM009-G behavior, but project-wide
features must not guess.

## Strongest argument against F′

F′ creates a new canonical projection boundary that does not yet exist in production
code. That projection must be versioned/fresh enough to prove package/project authority,
and designing/maintaining it adds machinery that pure deferral would avoid.

A full live provider such as BSP would also centralize freshness and generated-source
support more naturally.

The reason to select F′ now is proportionality: the stable authority facts needed by G3
are substantially smaller and slower-changing than the live source set. A minimal
projection plus replaceable provider closes the current problem without committing
Protos to daemon/protocol infrastructure before the ecosystem requires it.

## Regret scenario and escape path

The most plausible regret scenario is that future Protos projects acquire generated,
conditional, remote or target-specific sources whose membership cannot be faithfully
observed by bounded package-root enumeration.

The escape path is intentionally built into F′: replace the local provider with a
Package Tool/BSP-like live provider that supplies the same ProjectBinding/source domain
from authoritative build/project state. D082's per-project index and overlay contract do
not change.

A second regret case is that different project roots require incompatible toolchains.
The binding contract can be hosted one-process-per-project/toolchain without changing
project identity.

## Intentionally deferred

D085 does not select:

- exact Java/class/API names of the provider/projection implementation;
- final serialized spelling or filename of a canonical projection if an on-disk
  representation is used;
- whether the first implementation computes the projection in-process during an
  explicit package operation, emits an artifact, or exposes another bounded host
  projection, provided one package authority is preserved;
- generated/virtual-source membership;
- dependency/Standard Library workspace-symbol scope;
- persistent/remote symbol index storage;
- workspace-symbol fuzzy matching/ranking/result caps;
- G4 definition identity/resolution;
- LM009-H completion/hover/signature/references semantics;
- activation of a long-lived BSP-like provider;
- process-per-toolchain hosting absent a real incompatible-toolchain requirement.

Any implementation choice that would create a second package authority, make client
paths semantic identity, broaden source membership beyond authorized package roots or
require a new public persistent/protocol contract beyond F′ must stop at the appropriate
approval gate.

## Approval evidence

The project owner explicitly approved Candidate F′ on 2026-09-11 after the exhaustive
cross-language/tool audit and explicit future-endurance, scalability and Protos-
philosophy comparison.

The approved refined name is:

**Package-owned minimal canonical ProjectBinding projection + bounded canonical source
inventory + validated candidate-root acquisition through a replaceable editor-neutral
provider.**
